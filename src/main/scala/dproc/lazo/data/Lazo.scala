package dproc.lazo.data

import cats.syntax.all._
import dproc.lazo.syntax.all._
import dproc.lazo.protocol.data.{Bonds, LazoE, LazoM}
import Lazo._
import cats.data.Kleisli
import dproc.lazo.protocol.rules.Basic.validateBasic
import dproc.lazo.protocol.rules.Dag.{computeFJS, computeMGJS}
import dproc.lazo.protocol.rules.{InvalidBasic, InvalidFringe, Offence}

import scala.collection.concurrent.TrieMap

/** State supporting Lazo protocol. */
final case class Lazo[M, S](
    dagData: Map[M, DagData[M, S]],  // dag data
    exeData: Map[Int, ExeData[S]],   // execution data for all fringes
    fringes: Map[Int, Set[M]],       // all final fringes in the scope of Lazo
    fringesR: Map[Set[M], Int],      // all final fringes in the scope of Lazo
    seenMap: Map[M, Set[M]],         // all messages in the state seen by a message
    selfChildMap: Map[M, Option[M]], // self child (if exists) for a message,
    offences: Set[M],                // offences detected
    latestF: Int,
    latestMessages: Set[M],
    woSelfChild: Set[M],      // messages without self child - which means it is either genesis of newly bonded
    trustAssumption: LazoE[S] // bonds and other data from the state that is trusted by the node.
    // It us either local bonds file and others or data read from the LFS which node is bootstrapping from
) { self =>
  override def equals(obj: Any): Boolean = obj match {
    case x: Lazo[_, _] => x.latestMessages == this.latestMessages
    case _             => false
  }

  private val memoSt = TrieMap[Any, Any]()

  private def memoize[A, B](prefix: String, f: A => B): A => B = { key =>
    memoSt.getOrElseUpdate((prefix, key), f(key)).asInstanceOf[B]
  }

  def baseBondsOpt = memoize("baseBondsOpt", LazoOps(self).bondsMap)
  def fjsOpt =
    memoize(
      "fjsOpt",
      (mgj: Set[M]) => baseBondsOpt(mgj).map(_.activeSet).map(LazoOps(self).fjs(_)(mgj))
    )
  def selfJOpt =
    memoize(
      "selfJOpt",
      (mgjAndSender: (Set[M], S)) =>
        baseBondsOpt(mgjAndSender._1)
          .map(_.activeSet)
          .flatMap(LazoOps(self).selfJOpt(_, mgjAndSender._2)(mgjAndSender._1))
    )

  def seen  = memoize("seen", LazoOps(self).seen)
  def lfIdx = memoize("lfIdx", LazoOps(self).lfIdx)

  def add(
      id: M,
      m: LazoM.Extended[M, S],
      offenceOpt: Option[Offence]
  ): (Lazo[M, S], Set[M]) = {

    val fringeId = offenceOpt match {
      case Some(InvalidBasic())      => FRINGE_IDX_INVALID
      case Some(InvalidFringe(_, _)) => FRINGE_IDX_INVALID
      case _ =>
        val nextF        = latestF + 1
        val nextFringeId = if (nextF == F_ID_MAX) F_ID_MIN else nextF
        fringesR.getOrElse(m.fringes.fFringe, nextFringeId)
    }
    val newFringeIdOpt = (fringeId != FRINGE_IDX_INVALID && !fringes.contains(fringeId))
      .guard[Option]
      .as(fringeId)
    val newLatestF  = newFringeIdOpt.getOrElse(latestF)
    val newOffences = offenceOpt.fold(offences)(_ => offences + id)
    // Attempt to prune the state. Laziness tolerance is read from the latest fringe in the
    // state before message adding.
    val (dataToPrune, fringesToPrune) = newFringeIdOpt
      .flatMap(_ => m.lfIdx)
      .map(exeData(_).lazinessTolerance)
      .fold((Set.empty[M], Set.empty[Int]))(prune(this, latestF, _))
    val newDagData = dagData + (id -> DagData(
      m.mgj,
      m.fjs,
      m.offences,
      fringeId,
      m.sender,
      m.stateful,
      m.fringes.pFringe
    )) -- dataToPrune
    val newFringes = newFringeIdOpt.fold(fringes)(fId => fringes + (fId -> m.fringes.fFringe)) -- fringesToPrune
    val newFringesR = newFringeIdOpt.fold(fringesR)(fId => fringesR + (m.fringes.fFringe -> fId)) -- fringesToPrune
      .map(fringes)
    val newSelfChildMap = m.selfJOpt
      .map(sjId => selfChildMap + (id -> none[M]) + (sjId -> id.some))
      .getOrElse(selfChildMap + (id -> none[M])) -- dataToPrune
    // TODO would be good not tp prune each value in a seenMap. But this is not strictly correct because
    //  can lead to a cycle situation when old messages see ids that are assigned to new ones.
    val newSeenMap        = seenMap -- dataToPrune + (id -> (m.seen -- dataToPrune))
    val newExeData        = exeData + (fringeId -> ExeData(m.state.lazinessTolerance, m.state.bonds)) -- fringesToPrune
    val newLatestMessages = m.selfJOpt.foldLeft(latestMessages + id)(_ - _)
    val newWoSelfChild    = m.selfJOpt.fold(woSelfChild + id)(_ => woSelfChild) -- dataToPrune

    val newLazo = copy(
      dagData = newDagData,
      exeData = newExeData,
      fringes = newFringes,
      fringesR = newFringesR,
      seenMap = newSeenMap,
      selfChildMap = newSelfChildMap,
      latestF = newLatestF,
      offences = newOffences,
      latestMessages = newLatestMessages,
      woSelfChild = newWoSelfChild
    )

    (newLazo, dataToPrune)
  }

  lazy val mgjs: Set[M] =
    computeMGJS(latestMessages, (x: M, y: M) => seenMap.get(x).exists(_.contains(y)))

  def contains(m: M): Boolean = dagData.contains(m)
}

object Lazo {
  // Fringe index for messages that are declared as invalid due to offences that prevent to compute valid fringe
  val FRINGE_IDX_INVALID = Int.MinValue
  val F_ID_MIN           = Int.MinValue + 1
  val F_ID_MAX           = Int.MaxValue

  /**
    * DAG data about the message.
    * @param mgjs minimal generative justifications
    * @param jss justifications can be derived from mgjs and seen map, but it can be costly, so better to store it
    * @param fringeIdx index if the final fringe
    * @param sender sender
    * @param stateful whether the message
    */
  final case class DagData[M, S](
      mgjs: Set[M],
      jss: Set[M],
      offences: Set[M],
      fringeIdx: Int,
      sender: S,
      stateful: Boolean,
      partitionFringe: Set[M]
  )

  /** Data required for the protocol that should be provided by the execution engine. */
  final case class ExeData[S](lazinessTolerance: Int, bondsMap: Bonds[S])

  def empty[M, S](initExeData: LazoE[S]): Lazo[M, S] = Lazo(
    dagData = Map.empty[M, DagData[M, S]],
    exeData = Map.empty[Int, ExeData[S]],
    fringes = Map.empty[Int, Set[M]],
    fringesR = Map.empty[Set[M], Int],
    seenMap = Map.empty[M, Set[M]],
    selfChildMap = Map.empty[M, Option[M]],
    latestF = F_ID_MIN - 1,
    offences = Set(),
    latestMessages = Set(),
    woSelfChild = Set(),
    trustAssumption = initExeData
  )

  /** Prune the state upon finding the new fringe. */
  def prune[M, S](
      state: Lazo[M, S],
      latestFringeIdx: Int,
      lazinessTolerance: Int
  ): (Set[M], Set[Int]) =
    if (latestFringeIdx > F_ID_MIN + lazinessTolerance) {
      // Pruned state won't be able to process any message with fringe below prune fringe.
      val pruneFringeIdx = latestFringeIdx - lazinessTolerance
      val dataToPrune    = state.fringes(pruneFringeIdx).flatMap(state.seenMap)
      val fringesToPrune = state.fringes.collect { case (i, _) if i < pruneFringeIdx => i }
      (dataToPrune, fringesToPrune.toSet)
    } else Set.empty[M] -> Set.empty[Int]

  /** Whether message can be added to the state. */
  def canAdd[M, S](minGenJs: Set[M], sender: S, state: Lazo[M, S]): Boolean = {
    // whether all justifications are in the state
    val jsProcessed = minGenJs.forall(state.dagData.contains)
    // if an offender is detected in the view - there should not be any future message added;
    // this is necessary to meet the frugality basic rule.
    lazy val notFromOffender = {
      val selfJs             = state.selfJOpt(minGenJs, sender)
      val notFromEquivocator = selfJs.size <= 1
      lazy val selfJsIsValid = selfJs.forall(!state.offences.contains(_))
      notFromEquivocator && selfJsIsValid
    }
    jsProcessed && notFromOffender
  }

  /** Validate message for basic rules against the state. */
  def checkBasicRules[M, S](
      m: LazoM[M, S],
      state: Lazo[M, S]
  ): Option[InvalidBasic] = {
    val selfParentOpt = m.mgj.find(state.dagData(_).sender == m.sender)
    val latestFIdxOpt = LazoOps(state).lfIdx(m.mgj)
    val bondsMap      = latestFIdxOpt.map(state.exeData(_).bondsMap).getOrElse(m.state.bonds)
    val justifications = computeFJS(
      m.mgj,
      bondsMap.activeSet,
      state.dagData(_: M).jss,
      (x: M, y: M) => state.seenMap.get(x).exists(_.contains(y)),
      state.dagData(_: M).sender
    )
    val seen    = (target: M) => m.mgj.exists(state.seenMap(_).contains(target))
    val senderF = state.dagData(_: M).sender
    validateBasic(
      justifications,
      m.offences,
      selfParentOpt.map(state.dagData(_).mgjs).getOrElse(Set()),
      selfParentOpt.map(state.dagData(_).offences).getOrElse(Set()),
      justifications.map(j => j -> state.dagData(j).offences).toMap,
      senderF,
      seen
    ).toOption
  }

  final case class LazoOps[M, S](private val s: Lazo[M, S]) {
    // idx of the latest fringe across mgjs
    def lfIdx: Set[M] => Option[Int] = _.map(s.dagData(_: M).fringeIdx).lastOption

    // latest bonds map for a view defined by mgjs
    def bondsMap: Set[M] => Option[Bonds[S]] =
      (Kleisli(lfIdx) andThen Kleisli[Option, Int, Bonds[S]](s.exeData(_: Int).bondsMap.some)).run

    // derive full justification set from mgjs
    def fjs(activeSet: Set[S]) =
      (mgjs: Set[M]) =>
        computeFJS(
          mgjs,
          activeSet,
          s.dagData(_: M).jss,
          (x: M, y: M) => s.seenMap.get(x).exists(_.contains(y)),
          s.dagData(_: M).sender
        )

    // all messages seen through view defined by mgjs
    def seen = (mgjs: Set[M]) => mgjs ++ mgjs.flatMap(s.seenMap)

    // self justification option
    def selfJOpt(activeSet: Set[S], sender: S): Set[M] => Option[M] =
      fjs(activeSet) andThen (x => x.find(s.dagData(_).sender == sender))
  }

}
