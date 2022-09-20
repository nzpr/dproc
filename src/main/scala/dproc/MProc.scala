package dproc

import cats.effect.kernel.{Async, Temporal}
import cats.effect.{Ref, Resource, Sync}
import cats.syntax.all._
import dproc.Errors.e0x0000
import dproc.MProc.ProcessingResult
import dproc.MessageLogic.validateMessage
import dproc.data.{ExeEngine, Msg}
import dproc.gard.protocol.Rules.GardM
import dproc.lazo.data.Lazo
import dproc.lazo.protocol.rules._
import dproc.meld.protocol.data.MeldM
import dproc.shared.{DebugException, Diag}
import dproc.shared.Log.{noTraceResource, TraceF}
import fs2.Stream
import fs2.concurrent.Channel

import scala.concurrent.duration.DurationInt

final case class MProc[F[_], M, S, T](
    stRef: Ref[F, MProc.ST[M]],
    resultStream: Stream[F, (M, ProcessingResult[M, S, T], Set[M])],
    accept: Msg.WithId[M, S, T] => F[Unit]
)

/** Message processor - add messages to the state. */
object MProc {
  final case class ST[M](processing: Set[M]) {
    def begin(m: M): (ST[M], Boolean) =
      if (processing.contains(m)) this       -> false
      else copy(processing = processing + m) -> true

    def done(m: M): (ST[M], Unit) = copy(processing = processing - m) -> ()
  }
  def emptyST[M]: ST[M] = ST(Set.empty[M])

  def validateAndExecute[F[_]: Sync, M, S, T: Ordering](
      m: Msg.WithId[M, S, T],
      s: DProc.ST[M, S, T],
      exeEngine: ExeEngine[F, M, S, T]
  ) = {
    val ok = Lazo.canAdd(m.m.minGenJs, m.m.sender, s.lazo)
    val conflictSetF = Sync[F]
      .delay(Dag.between(m.m.minGenJs, m.m.finalFringe, s.lazo.seenMap))
      .map(_.flatMap(s.meld.txsMap))
    val mkMeldSome = conflictSetF.flatMap(
      MeldM.create[F, M, T](
        s.meld,
        m.m.txs,
        _,
        m.m.finalized,
        m.m.rejected,
        exeEngine.conflicts,
        exeEngine.depends
      )
    )
    val offOptT = validateMessage(m.id, m.m, s, exeEngine).swap.toOption

    // invalid messages do not participate in merge and are not accounted for double spend guard
    val offCase =
      offOptT.map(off => (Msg.toLazoM(m.m), none[MeldM[T]], none[GardM[M, T]], off.some))
    // if msg is valid - Meld state and Gard state should be updated
    val validCase =
      mkMeldSome.map(_.some).map((Msg.toLazoM(m.m), _, Msg.toGardM(m.m).some, none[Offence]))

    new Exception(e0x0000).raiseError.whenA(!ok) >> offCase.getOrElseF(validCase)
  }

  /** Attempt to add message to processor. */
  def add[F[_]: Sync, M, S, T: Ordering](
      m: Msg.WithId[M, S, T],
      pStRef: Ref[F, DProc.ST[M, S, T]],
      stRef: Ref[F, ST[M]],
      exeEngine: ExeEngine[F, M, S, T],
      tracer: F[M] => Resource[F, TraceF[F]]
  ): F[Either[Ignored, (M, ProcessingResult[M, S, T], Set[M])]] =
    tracer(m.id.pure).use { implicit trace =>
      def validate(s: DProc.ST[M, S, T]) = {
        implicit val ls = s.lazo
        import dproc.lazo.syntax.all._
        for {
          _ <- trace(s"validating")
          r <- validateAndExecute(m, s, exeEngine)

          (lazoM, meldMOpt, gardMOpt, offenceOpt) = r
          _ <- trace(s"Offs are not supposed to happen in this simulation ($offenceOpt)")
                .whenA(offenceOpt.nonEmpty)
          le <- lazoM.computeExtended(s.lazo)
          _  <- DebugException.checkForInvalidFringe(s.lazo, lazoM)
          x <- pStRef
                .modify { curSt =>
                  val (newSt, (result, unlocked)) = curSt
                    .done(
                      m.id,
                      le,
                      meldMOpt,
                      gardMOpt,
                      offenceOpt,
                      m.m.finalized,
                      m.m.rejected
                    )
                  (newSt, (result, unlocked))
                }
          (r, u) = x
        } yield (m.id, r, u).asRight[Ignored]
      }

      trace(s"received ${m.id}") >>
        Sync[F].bracket(stRef.modify(_.begin(m.id))) { proceed =>
          if (!proceed)
            ignoredDuplicate.asLeft[(M, ProcessingResult[M, S, T], Set[M])].pure[F] <*
              trace(s"ignored by processor as duplicate")
          else
            pStRef
              .modify { _.start(m) }
              .flatMap {
                case Some(s) => validate(s)
                case None =>
                  ignoredDetached.asLeft[(M, ProcessingResult[M, S, T], Set[M])].pure[F] <*
                    trace(s"ignored as detached")
              }
        }(_ => stRef.modify(_.done(m.id)))
    }

  def apply[F[_]: Async, M, S, T: Ordering, P](
      id: P,
      pStRef: Ref[F, DProc.ST[M, S, T]],
      exeEngine: ExeEngine[F, M, S, T],
      threadsNum: Int
  ): F[MProc[F, M, S, T]] =
    for {
      stRef     <- Ref.of[F, ST[M]](emptyST)
      procQueue <- Channel.unbounded[F, Msg.WithId[M, S, T]]
    } yield {
      val out: Stream[F, (M, ProcessingResult[M, S, T], Set[M])] = procQueue.stream
        .parEvalMap(threadsNum) { m =>
//          val tracer: F[M] => Resource[F, TraceF[F]] = tracerResource[F, P, M](id, _, false)
          val tracer: F[M] => Resource[F, TraceF[F]] = _ => noTraceResource[F]
          add[F, M, S, T](m, pStRef, stRef, exeEngine, tracer)
        }
        // TODO remove this debug
        .collect { case Right(x) => x }
        .evalTap {
          case (_, ProcessingResult(_, _, _, _, offenceOpt), _) =>
            Temporal[F].sleep(100.milliseconds) >>
              pStRef.get
                .flatMap { x =>
                  println(offenceOpt)
                  new DebugException(x)
                    .raiseError[F, Either[
                      Ignored,
                      (M, ProcessingResult[M, S, T], Set[M])
                    ]]
                }
                .whenA(offenceOpt.nonEmpty)
        }

      new MProc(stRef, out, procQueue.send(_).void)
    }

  trait Ignored
  final case object IgnoredDuplicate extends Ignored
  final case object IgnoredDetached  extends Ignored

  final case class ProcessingResult[M, S, T](
      newState: DProc.ST[M, S, T],
      garbage: Set[M],
      finalized: Set[T],
      rejected: Set[T],
      offenceOpt: Option[Offence]
  )

  val ignoredDuplicate: Ignored = IgnoredDuplicate
  val ignoredDetached: Ignored  = IgnoredDetached
}
