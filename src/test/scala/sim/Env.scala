package sim

import cats.{Applicative, FlatMap}
import cats.effect.kernel.Temporal
import cats.syntax.all._
import dproc.DProc
import dproc.data.{ExeEngine, Msg}
import fs2.{Pipe, Stream}
import Sim._
import cats.effect.Ref
import cats.effect.std.Random
import dproc.lazo.protocol.data.LazoE

import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration}

/** Mocks for real world things. */
object Env {

  def randomTGen[F[_]: Random] = Random[F].nextString(10)

//  def transactionStream[F[_]: Temporal: Random]: Stream[F, String] =
//    Stream.awakeEvery[F](2.seconds).evalMap { _ =>
//      val delayF =
//        Random[F].nextIntBounded(100).map(DurationInt(_).milliseconds).flatTap(Temporal[F].sleep)
//      delayF.flatMap(_ => randomTGen)
//    }

  def broadcast[F[_]: Temporal](
      peers: List[(String, DProc[F, M, S, T])],
      time: Duration
  ): Pipe[F, Msg.WithId[M, S, T], Unit] =
    _.evalMap { x =>
      Temporal[F].sleep(time) >> peers.traverse {
        case (id, dProc) =>
          //println(s"$id <- ${x.id}").pure
          dProc.acceptMsg(x)
      }.void
    }

//  def waitUntil[F[_]: Monad: Timer](condition: F[Boolean]): F[Unit] =
//    ().pure[F].tailRecM { _ =>
//      condition.map { r =>
//        if (r) ().asRight[F[Unit]] else Timer[F].sleep(50.milliseconds).asLeft[Unit]
//      }
//    }
//
//  def idealBCastAwaitingParents[F[_]: Concurrent: Timer](
//      peers: List[(String, DProc[F, M, S, T])]
//  ): Pipe[F, Msg.WithId[M, S, T], Unit] =
//    _.evalTap(_ => Timer[F].sleep(100.milliseconds)).through(idealBroadcast(peers))

  def deployToRandom[F[_]: FlatMap: Random, M](sinks: List[M => F[Unit]]): Pipe[F, M, Unit] =
    _.evalMap(m => Random[F].nextIntBounded(sinks.size).flatMap(r => sinks(r)(m)))

  // ids for messages of particular sender (no equivocation)
  // instead of using cryptographic hashing function
  def dummyIds(sender: S): LazyList[String] =
    LazyList.unfold(Map.empty[S, Int]) { acc =>
      val next   = acc.getOrElse(sender, 0) + 1
      val newAcc = acc + (sender -> next)
      val id     = s"$next@$sender"
      (id, newAcc).some
    }

  // Execution engine that does not change anything related to Lazo protocol,
  // unable to detect conflicts, dependencies and invalid execution
  def dummyExeEngine[F[_]: Temporal](genesisState: LazoE[S], exeTime: Duration) =
    ExeEngine[F, M, S, T](
      computeLazoData = _ => genesisState.pure,
      validate = _ => Temporal[F].sleep(exeTime).as(true),
      (_, _) => false.pure, // no conflicts TODO maintain map on tx generation to simulate conflicts and deps
      (_, _) => false.pure  // no dependencies
    )
}
