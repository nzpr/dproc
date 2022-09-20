package dproc.lazo.protocol.rules

import cats.syntax.all._
import dproc.lazo.data.Lazo
import dproc.lazo.protocol.data.{Bonds, LazoF}
import dproc.lazo.protocol.rules.Dag._

object Finality {

//  /** Messages that are available to constitute the new fringe. */
//  def nextFringeTarget(
//      view: Set[Int],
//      curFringe: Set[Int],
//      selfChildOpt: Int => Option[Int],
//      selfParentOpt: Int => Option[Int],
//      justificationsF: Int => Set[Int],
//      isDescendant: (Int, Int) => Boolean,
//      sender: Int => Int,
//      offencesDetected: Set[Int]
//  ): Set[Int] = {
//    val childFringe = curFringe.map(selfChildOpt).flatten intersect view
//    val ceilFringe  = ceiling(childFringe.map(justificationsF) + childFringe, isDescendant, sender)
//    val validFringe = ceilFringe.map { x =>
//      if (offencesDetected.contains(x)) {
//        assert(
//          selfParentOpt(x).isDefined,
//          "Unexpected DAG. Invalid message does not have a valid parent."
//        )
//        selfParentOpt(x).get
//      } else x
//    }
//    validFringe
//  }

//  /** Find the next final fringe. Returns fringe advancement and whether  */
//  def findFringeAdvancement(
//      view: Set[Int],
//      justifications: Set[Int],
//      seenAsValid: Set[Int] => Set[Int],
//      baseFringe: Set[Int],
//      bondsMap: Bonds,
//      selfChildOpt: Int => Option[Int],
//      selfParentOpt: Int => Option[Int],
//      sender: Int => Int,
//      justificationsF: Int => Set[Int],
//      isDescendant: (Int, Int) => Boolean,
//      offencesDetected: Set[Int]
//  ): Option[Set[Int]] = {
//    val targetFringe =
//      nextFringeTarget(
//        view,
//        baseFringe,
//        selfChildOpt,
//        selfParentOpt,
//        justificationsF,
//        isDescendant,
//        sender,
//        offencesDetected
//      )
//    val allAcross = targetFringe == bondsMap.activeSet
//    if (allAcross) {
//      // in case of all across, supermajority of justifications have to see each message as valid
//      targetFringe
//        .forall { x =>
//          bondsMap.isSuperMajority(
//            justifications.filter(y => seenAsValid(Set(y)).contains(x)).map(sender)
//          )
//        }
//        .guard[Option]
//        .as(targetFringe)
//    } else {
//      // otherwise seek for formation of the hard partition of supermajority
//      val hpOpt = findHardPartition(
//        baseFringe,
//        justifications,
//        justificationsF,
//        isDescendant,
//        sender,
//        seenAsValid
//      )
//      hpOpt
//    }
//  }

//  /** Fringe advancement is final if more then supermajority of senders advanced. */
//  def advancementIsFinal[S](bondsMap: Bonds[S], advancedSenders: Set[S]): Option[Set[S]] =
//    bondsMap.isSuperMajority(advancedSenders).guard[Option].as(advancedSenders)

//  /** Find partition that cannot overlap with any other partition found on top of the same base fringe. */
//  def findPartition[M, S](
//      baseFringe: Set[M],
//      justifications: Set[M],
//      jssF: M => Set[M],
//      isDescendant: (M, M) => Boolean,
//      sender: M => S,
//      seen: Set[M] => Set[M]
//  ): Option[Set[M]] = {
//    val underFringe = seen(baseFringe)
//    val lvl1        = justifications diff underFringe
//    val lvl2        = floor(lvl1.map(jssF), isDescendant, sender) diff underFringe
//    val lvl3        = floor(lvl2.map(jssF), isDescendant, sender) diff underFringe
//
//    // Check whether we have 2 levels that meet the partition criteria
//    def settled(lvl1: Set[M], lvl2: Set[M]): Boolean = {
//      val sameSenders = lvl1.map(sender) == lvl2.map(sender)
//      val partSenders = lvl1.map(sender)
//      lazy val safeOutstanding = //check senders out of partition
//        (lvl1.flatMap(jssF).filterNot(j => partSenders.contains(sender(j))) ++
//          lvl2.flatMap(jssF).filterNot(j => partSenders.contains(sender(j))))
//          .groupBy(sender)
//          // either the same message or different having the same justifications
//          .forall { case (_, x) => x.size == 1 || x.map(jssF).size == 1 }
//      sameSenders && safeOutstanding
//    }
//
//    if (lvl3.nonEmpty && settled(lvl1, lvl2)) lvl3.some else none[Set[M]]
//  }

//  def findPartition[M, S](
//      base: Set[M],            // current final fringe
//      childrenF: M => List[M], // children function
//      senderF: M => S,         // sender function
//      highestF: List[M] => M,  // find highest message across (those seeing all others)
//      jssF: M => Set[M],       // justifications
//      isSupermajority: Set[S] => Boolean
//  ): Option[Set[S]] = {
//
//    // Compute the next level
//    def nextLvl(curLvl: Set[M]): Set[M] = {
//      val children        = curLvl.map(childrenF) // load children
//      val curLvlPartition = curLvl.map(senderF)
//      // find senders of the partition - those having a child for each item in curLvl
//      val lvlPartition = children.map(_.map(senderF)).foldLeft(curLvlPartition) {
//        case (acc, chSenders) => acc intersect chSenders.toSet
//      }
//      // highest fringe containing senders of partition across is the next lvl
//      children.map(_.filter(m => lvlPartition.contains(senderF(m)))).map(highestF)
//    }
//
//    // Check whether we have 2 levels that meet the partition criteria
//    def settled(lvl1: Set[M], lvl2: Set[M]): Boolean = {
//      val sameSenders = lvl1.map(senderF) == lvl2.map(senderF)
//      val partSenders = lvl1.map(senderF)
//      lazy val safeOutstanding = //check senders out of partition
//        (lvl1.flatMap(jssF).filterNot(j => partSenders.contains(senderF(j))) ++
//          lvl2.flatMap(jssF).filterNot(j => partSenders.contains(senderF(j))))
//          .groupBy(senderF)
//          // either the same message or different having the same justifications
//          .forall { case (_, x) => x.size == 1 || x.map(jssF).size == 1 }
//      sameSenders && safeOutstanding
//    }
//
//    @tailrec
//    def step(lvl1: Set[M]): Option[Set[S]] = {
//      val lvl2 = nextLvl(lvl1)     // jump the next level
//      val p    = lvl2.map(senderF) // potential partition
//      if (!isSupermajority(p)) none[Set[S]] // if its not SM - no partition of SM will be found
//      else if (settled(lvl1, lvl2)) p.some  // if settled - partition is found
//      else step(lvl2)                       // still chance to find partition
//    }
//
//    // start jumping from current fringe
//    step(base)
//  }

  /** Find partition that cannot overlap with any other partition found on top of the same base fringe. */
  def findSafeFull[M, S](
      bonds: Bonds[S],
      target: Set[M],
      justifications: Set[M],
      jssF: M => Set[M],
      isDescendant: (M, M) => Boolean,
      sender: M => S,
      seenBySome: Set[M] => Set[M]
  ): Option[Set[M]] =
    if (bonds.activeSet == target.map(sender)) {
      val underTarget = seenBySome(target)
      val lvl1        = justifications diff underTarget
      val lvl2        = floor(lvl1.map(jssF), isDescendant, sender) diff underTarget
      val lvl3        = floor(lvl2.map(jssF), isDescendant, sender) diff underTarget
      (lvl3.map(sender) == target.map(sender)).guard[Option].as(target)
    } else none[Set[M]]

  def computeFringes[M, S](mgjs: Set[M], lazo: Lazo[M, S]): LazoF[M] = {
    val isDescendant = (x: M, y: M) => lazo.seenMap.get(x).exists(_.contains(y))
    val latestFfOpt  = lazo.lfIdx(mgjs)
    val bonds        = latestFfOpt.map(lazo.exeData(_: Int).bondsMap).getOrElse(lazo.trustAssumption.bonds)
    val latestLazoF =
      for {
        latestFf <- latestFfOpt.map(lazo.fringes)
        latestPf <- mgjs.headOption.map(lazo.dagData(_: M).partitionFringe)
      } yield LazoF(latestFf, latestPf)

    val justifications = computeFJS(
      mgjs,
      bonds.activeSet,
      lazo.dagData(_: M).jss,
      isDescendant,
      lazo.dagData(_: M).sender
    )

    latestLazoF match {
      case Some(x @ LazoF(fF, _)) =>
        val target = (fF.flatMap(lazo.selfChildMap) ++
          (lazo.woSelfChild diff fF diff fF.flatMap(lazo.seenMap)))
          .filter(inTheView(_, mgjs, lazo.seenMap))

        val pOpt = findSafeFull[M, S](
          bonds,
          target,
          justifications,
          lazo.dagData(_: M).jss,
          isDescendant,
          lazo.dagData(_: M).sender,
          seenBySome(_, lazo.seenMap)
        )
        pOpt match {
          case Some(p) if bonds.isSuperMajority(p.map(lazo.dagData(_: M).sender)) => {
            val log =
              s"""fFringe found $p for bonded ${bonds.activeSet} target $target
                    on latestFringeIdxOpt ${latestFfOpt} ${lazo
                .fringes({ latestFfOpt.get })}
                    and mgjs $mgjs (${mgjs.map(x => x -> lazo.dagData(x).fringeIdx)})
                    and jss ${justifications}"""
            LazoF(p, p)
          }
          case Some(p) => {
            val log = s"pFringe found $p"
            x.copy(pFringe = p)
          }
          case None => {
            val log = s"No new fringe found, remains $x"
            x
          }
        }
      case None =>
        LazoF(Set.empty[M], Set.empty[M])
    }
  }
  //    val latestFIdx   = mgjs.map(lazo.dagData(_).fringeIdx).max
//    val latestFringe = lazo.fringes(latestFIdx)
//    val bondsMap     = lazo.exeData(latestFIdx)
//    val bonds        = latestFringe.map(lazo.dagData(_).sender)
//    val justifications = computeFullJS(
//      mgjs,
//      lazo.dagData(_).mgjss,
//      (a, x) => lazo.seenMap.get(x).exists(_.contains(a)),
//      bonds,
//      lazo.dagData(_).sender
//    )
//    val fFringe = findFringeAdvancement(
//      mgjs,
//      justifications,
//      _.flatMap(lazo.seenMap),
//      latestFringe,
//      bondsMap,
//      lazo.selfChildMap
//    ).getOrElse(latestFringe)
//    LazoF(fFringe, fFringe)
}
