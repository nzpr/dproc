package dproc

import cats.data.Writer
import cats.effect.Ref
import cats.effect.kernel.Async
import cats.syntax.all._
import dproc.DProc.ST
import dproc.MProc.ProcessingResult
import dproc.data.{ExeEngine, Msg, StateWithTxs}
import dproc.lazo.syntax.all.lazoMESyntax
import dproc.shared.Diag
import fs2.{Pipe, Stream}
import fs2.concurrent.Channel
import gard.data.Gard
import gard.protocol.Rules.GardM
import lazo.data.Lazo
import lazo.protocol.data.{LazoE, LazoM}
import lazo.protocol.rules.Offence
import meld.data.Meld
import meld.protocol.data.{ConflictResolution, MeldM}

import scala.collection.concurrent.TrieMap

/**
  * Instance of an observer process.
  *
  * @param stateRef Ref holding the process state
  * @param gcStream stream of message that are garbage collected
  * @param finStream stream of finalized transactions
  * @param acceptMsg callback to make the process accept the block
  * @tparam F effect type
  */
final case class DProc[F[_], M, S, T](
    stateRef: Ref[F, ST[M, S, T]],               // state of the process - all data supporting the protocol
    ppStateRef: Ref[F, MProp.ST[M, S, T]],       // state of the proposer
    pcStateRef: Ref[F, MProc.ST[M]],             // state of the message processor
    dProcStream: Stream[F, Unit],                // main stream that launches the process
    output: Stream[F, Msg.WithId[M, S, T]],      // stream of output messages
    gcStream: Stream[F, Set[M]],                 // stream of messages garbage collected
    finStream: Stream[F, ConflictResolution[T]], // stream of finalized transactions
    acceptMsg: Msg.WithId[M, S, T] => F[Unit],   // callback to make process accept the inbound message
    acceptTx: T => F[Unit]                       // callback to make the process accept transaction
)

object DProc {

  def connect[F[_]: Async, M, S, T](
      dProc: DProc[F, M, S, T],
      broadcast: Pipe[F, Msg.WithId[M, S, T], Unit],
      finalized: Pipe[F, ConflictResolution[T], Unit],
      gced: Set[M] => F[Unit]
  ): Stream[F, Unit] =
    dProc.dProcStream concurrently
      dProc.output.through(broadcast) concurrently
      dProc.finStream.through(finalized) concurrently
      dProc.gcStream.evalTap(gced)

  final case class WithId[F[_], Id, M, S, T](id: Id, p: DProc[F, M, S, T])

  /** The state of the process are state supporting all protocols that the process run. */
  final case class ST[M, S, T](
      lazo: Lazo[M, S],
      meld: Meld[M, T],
      gard: Gard[M, T],
      buffer: Buffer[M]
  ) {

    def start(msg: Msg.WithId[M, S, T]): (ST[M, S, T], Option[ST[M, S, T]]) = {
      lazy val a = {
        // if already in the state - ignore
        if (lazo.contains(msg.id)) this -> none[ST[M, S, T]]
        // if mgs is empty - genesis case, pass through if no final fringe yet found
        else if (msg.m.minGenJs.isEmpty) this -> lazo.fringes.isEmpty.guard[Option].as(this)
        else
          msg.m.minGenJs.partition(lazo.contains) match {
            // if all deps are in the state
            case (_, missingDeps) if missingDeps.isEmpty =>
              val fromOffender = {
                val selfJs               = msg.m.minGenJs.filter(lazo.dagData(_).sender == msg.m.sender)
                val fromEquivocator      = selfJs.size > 1
                lazy val selfJsIsOffence = selfJs.headOption.exists(lazo.offences.contains)
                fromEquivocator || selfJsIsOffence
              }
              // ignore from offenders in the view
              if (fromOffender) this -> none[ST[M, S, T]]
              // or process
              else this -> this.some
            case (presentDeps, missingDeps) if presentDeps.isEmpty =>
              // if missing dep exists in buffer - add msg to buffer as well
              if (true /*missingDeps.exists(buffer.missingMap.contains)*/ ) {
                val newBuffer = buffer.add(msg.id, missingDeps)
                copy(buffer = newBuffer) -> none[ST[M, S, T]]
              }
              // if not a single dependency present neither in the state nor in buffer
              else this -> none[ST[M, S, T]]
            case (_, missingDeps) =>
              val newBuffer = buffer.add(msg.id, missingDeps)
              copy(buffer = newBuffer) -> none[ST[M, S, T]]
          }
      }
//      Diag.time(a, "start")
      a
    }

    def done(
        id: M,
        lazoM: LazoM.Extended[M, S],
        meldMOpt: Option[MeldM[T]],
        gardMOpt: Option[GardM[M, T]],
        offenceOpt: Option[Offence],
        finalized: Set[T],
        rejected: Set[T]
    ): (ST[M, S, T], (ProcessingResult[M, S, T], Set[M])) = {
      lazy val a = {
        val (newLazo, gc)         = lazo.add(id, lazoM, offenceOpt)
        val newMeld               = meldMOpt.map(meld.add(id, _, gc)).getOrElse(meld)
        val newGard               = gardMOpt.map(gard.add).getOrElse(gard)
        val (newBuffer, unlocked) = buffer.complete(id)
        val newSt                 = DProc.ST(newLazo, newMeld, newGard, newBuffer)
        (newSt, (ProcessingResult(newSt, gc, finalized, rejected, offenceOpt), unlocked))
      }
//      Diag.time(a, "done")
      a
    }
  }

  def emptyST[M, S, T](trust: LazoE[S]): ST[M, S, T] =
    ST(Lazo.empty(trust), Meld.empty, Gard.empty, Buffer.empty)

  // Max Number of messages processed concurrently. When exceeding messages are queued
  val processorConcurrency = 1000

  def apply[F[_]: Async, M, S, T: Ordering](
      id: S,
      initPool: List[T],
      initState: ST[M, S, T],
      exeEngine: ExeEngine[F, M, S, T],
      idGen: Msg[M, S, T] => F[M]
  ): F[DProc[F, M, S, T]] =
    for {
      // channel for incoming blocks; some load balancing can be applied by modifying this queue
      mQ <- Channel.unbounded[F, Msg.WithId[M, S, T]]
      // channel for incoming user signed transactions
      tQ <- Channel.unbounded[F, T]
      // channel for outbound messages
      oQ <- Channel.unbounded[F, Msg.WithId[M, S, T]]
      // channel for garbage collect
      gcQ <- Channel.unbounded[F, Set[M]]
      // channel for finalization results
      fQ <- Channel.unbounded[F, ConflictResolution[T]]
      // states
      stRef <- Ref.of[F, ST[M, S, T]](initState)
      plRef <- Ref.of[F, List[T]](initPool)
      // proposer
      proposer <- MProp(id, exeEngine, idGen)
      // message processor
      processor <- MProc(id, stRef, exeEngine, processorConcurrency)
    } yield {
      // steam if incoming messages
      val mS = mQ.stream
      // stream of incoming tx
      val tS = tQ.stream.evalTap(t => plRef.update(t +: _))
      // message receiver
      val blockStore = new TrieMap[M, Msg.WithId[M, S, T]]
      val receive = mS
        .evalTap(x => blockStore.putIfAbsent(x.id, x).pure)
        .evalMap(processor.accept)
      val propose = proposer.msgStream
        .evalTap(mQ.send)
        .filter { case Msg.WithId(_, Msg(s, _, _, _, _, _, _, _, _, _, _, _, _)) => s == id }
        .evalMap(oQ.send)
      val process = {
        // stream of states after new message processed
        val afterAdded = processor.resultStream.evalMap {
          case (id, x, unlocked) =>
            unlocked.toList.traverse(x => mQ.send(blockStore(x))).as(blockStore.remove(id)) >>
              plRef.get.map(x -> _)
        }
        // stream of states after new transaction received
        val afterNewTx = tS
          .fold(initPool) { case (pool, tx) => pool :+ tx }
          .evalMap(newPool => stRef.get.map(_ -> newPool))
        // stream of states that the process should act on
        val statesStream = afterAdded
          .evalTap {
            case (ProcessingResult(_, garbage, finalized, rejected, _), _) =>
              gcQ.send(garbage) >> fQ.send(ConflictResolution(finalized, rejected))
          }
          .map { case (x, txs) => x.newState -> txs } merge afterNewTx
        statesStream
          .map {
            case (st, txs) =>
              StateWithTxs(st, txs)
          }
          .evalMap(proposer.attempt)
      }

      val stream = process concurrently receive concurrently propose

      new DProc[F, M, S, T](
        stRef,
        proposer.stRef,
        processor.stRef,
        stream,
        oQ.stream,
        gcQ.stream,
        fQ.stream,
        mQ.send(_).void,
        tQ.send(_).void
      )
    }
}
