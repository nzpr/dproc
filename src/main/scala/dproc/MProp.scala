package dproc

import cats.effect.{Async, Deferred, Ref}
import cats.syntax.all._
import dproc.MProp.ST
import dproc.MessageLogic.createMessage
import dproc.data.{ExeEngine, Msg, StateWithTxs}
import dproc.shared.Log.noTraceResource
import fs2.Stream
import fs2.concurrent.Channel

final case class MProp[F[_], M, S, T](
    stRef: Ref[F, ST[M, S, T]],
    msgStream: Stream[F, Msg.WithId[M, S, T]],
    attempt: StateWithTxs[M, S, T] => F[Unit]
)

/** Message proposer - create messages on top of the state. */
object MProp {
  final case class ST[M, S, T](
      sender: S,
      proposingOn: Option[StateWithTxs[M, S, T]],
      latest: Option[StateWithTxs[M, S, T]],
      extraStateful: Set[M],
      extraAttestations: Set[M],
      awaitingPrev: Option[Set[M]]
  ) {
    private def prevReceived(stateWithTxs: StateWithTxs[M, S, T], prev: Set[M]): Boolean =
      stateWithTxs.state.lazo.latestMessages.exists { m =>
        val d = stateWithTxs.state.lazo.dagData(m)
        (sender == d.sender) && (prev == d.mgjs)
      }

    def tryBegin(newStWTx: StateWithTxs[M, S, T]): (ST[M, S, T], Option[StateWithTxs[M, S, T]]) =
      this match {
        // since newStWTx might contain older state because of how stream concurrency works, this check is required
        case ST(_, _, Some(curLatest), _, _, _)
            if newStWTx.state.lazo.latestMessages.forall(curLatest.state.lazo.dagData.contains) =>
          this -> none[StateWithTxs[M, S, T]]
        // mint message only when proposer is idle and previously created block is added
        case ST(_, None, _, _, _, awaitingPrevOpt) =>
          awaitingPrevOpt match {
            case Some(awaiting) if { !prevReceived(newStWTx, awaiting) } =>
              this -> none[StateWithTxs[M, S, T]]
            case _ =>
              val newCurSt = newStWTx.some
              val newSt    = ST(sender, newCurSt, newCurSt, Set(), Set(), none[Set[M]])
              (newSt, newCurSt)
          }
        // or update the state with new data
        case _ @ST(_, Some(proposingOn), Some(latest), extraStateful, extraAttestations, _) =>
          // new messages received compared to the previous stream item
          val newMs = newStWTx.state.lazo.latestMessages -- latest.state.lazo.latestMessages --
            extraStateful -- extraAttestations
          val (newSs, newAs) = newMs.partition { m =>
            newStWTx.state.lazo.dagData(m).stateful ||
            newStWTx.state.lazo.dagData(m).mgjs.exists(extraStateful)
          }
          val newSt = ST(sender, proposingOn.some, newStWTx.some, newSs, newAs, awaitingPrev)
          (newSt, none[StateWithTxs[M, S, T]])
      }

    def done: (ST[M, S, T], (Set[M], Set[M])) = {
      // mark proposer idle
      val newCurSt = none[StateWithTxs[M, S, T]]
      // attempt to update mgjs
      val mgjs = proposingOn.get.state.lazo.mgjs
      val r    = (mgjs, proposingOn.get.state.lazo.offences)
      copy(proposingOn = newCurSt, latest = newCurSt, awaitingPrev = mgjs.some) -> r
    }
  }

  def emptyST[M, S, T](sender: S): ST[M, S, T] =
    ST(sender, none[StateWithTxs[M, S, T]], none[StateWithTxs[M, S, T]], Set(), Set(), none[Set[M]])

  def apply[F[_]: Async, M, S, T: Ordering](
      sender: S,
      exeEngine: ExeEngine[F, M, S, T],
      idGen: Msg[M, S, T] => F[M]
  ): F[MProp[F, M, S, T]] =
    for {
      stRef        <- Ref.of[F, ST[M, S, T]](emptyST(sender))
      proposeQueue <- Channel.unbounded[F, StateWithTxs[M, S, T]]
    } yield {
      // stream making messages
      val mintStream = proposeQueue.stream.evalMap {
        case StateWithTxs(st, txs) =>
          Deferred[F, M].flatMap { d =>
            noTraceResource.use { implicit trace =>
//            tracerResource(sender, d.get, true).use { implicit trace =>
              createMessage(txs, sender, st, exeEngine)
                .flatMap { m =>
                  stRef
                    .modify(_.done)
                    .map {
                      case (minGenJs, offences) => m.copy(minGenJs = minGenJs, offences = offences)
                    }
                }
                .flatMap(m => idGen(m).flatTap(d.complete).map(id => Msg.WithId(id, m)))
            }
          }

      }

      // stream updating the state of proposer, optionally triggering message creation
      def attempt(newStWTx: StateWithTxs[M, S, T]) =
        stRef.modify(_.tryBegin(newStWTx)).flatMap(_.traverse(proposeQueue.send).void)

      new MProp[F, M, S, T](stRef, mintStream, attempt)
    }
}
