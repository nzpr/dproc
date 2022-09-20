package sim

import cats.effect.kernel.Async
import cats.effect.std.{Console, Random}
import cats.effect.{IO, IOApp, Ref}
import cats.syntax.all._
import cats.{Applicative, Parallel}
import dproc.DProc
import dproc.data.Msg
import dproc.lazo.protocol.data.{Bonds, LazoE, LazoF, LazoM}
import dproc.meld.protocol.data.ConflictResolution
import fs2.{Pipe, Stream}
import org.scalatest.freespec.AnyFreeSpec
import sim.Env.{broadcast, dummyExeEngine, dummyIds}

import scala.concurrent.duration.DurationInt

class Sim extends AnyFreeSpec {
  "My Code " - {
    "works" in {
      import cats.effect.unsafe.implicits.global
      Sim.run.unsafeRunSync()
    }
  }
}

object Sim extends IOApp.Simple {
  // Dummy types for message id, sender id and transaction id
  type M = String
  type S = String
  type T = String

  // TODO make distribution for delays
  // time spent for block to be executed
  val exeDelay = 0.milliseconds
  // time for message to reach peers
  val propDelay = 0.milliseconds
  // time to compute message hash
  val hashDelay = 0.milliseconds
  // number of blocks to be produced by each sender
  val numBlocks = 50

  /** Make instance of a process - peer or the network.
    * Init with last finalized state (lfs as the simplest). */
  def mkPeer[F[_]: Async](
      id: S,
      lfs: DProc.ST[M, S, T],
      lfsExe: LazoE[S]
  ): F[DProc[F, M, S, T]] = {
    val ids = dummyIds(id).take(numBlocks)
    val exe = dummyExeEngine(lfsExe, exeDelay)
    Ref.of(ids.toList).flatMap { r =>
      val hasher = (_: Msg[M, S, T]) =>
        Async[F].sleep(hashDelay) >> r.modify { case head :: tail => (tail, head) }
      DProc[F, M, S, T](id, initPool = List.empty[T], lfs, exe, hasher)
    }
  }

  /** Make the computer, init all peers with lfs. */
  def mkNet[F[_]: Async](lfs: LazoM[M, S]): F[List[(S, DProc[F, M, S, T])]] =
    lfs.state.bonds.activeSet.toList.traverse { vId =>
      mkPeer(vId, DProc.emptyST(lfs.state), lfs.state).map(vId -> _)
    }

  /** Init simulation. Return list of streams representing processes of the computer. */
  def sim[F[_]: Async: Parallel: Random: Console](size: Int): F[List[Stream[F, Unit]]] = {
    val senders = Iterator.range(0, size).map(n => s"s#$n").toList
    // Create lfs message, it has no parents, sees no offences and final fringe is empty set
    val genesisBonds = Bonds(senders.map(_ -> 100L).toMap)
    val genesisExec  = LazoE(genesisBonds, 10000, 10000, 10000)
    val lfs          = LazoM[M, S]("s#0", Set(), Set(), LazoF(Set(), Set()), genesisExec, stateful = true)
    val genesisM = Msg.WithId(
      s"0@${senders.head}",
      Msg[M, S, T](
        senders.head,
        Set(),
        List(),
        Set(),
        Set(),
        Set(),
        Set(),
        Set(),
        Set(),
        genesisExec.bonds,
        genesisExec.lazinessTolerance,
        genesisExec.ejectionThreshold,
        genesisExec.expirationThreshold
      )
    )

    mkNet(lfs).flatMap { net =>
      // bootstrap all processes
      val bootstrap = Stream.eval[F, Unit](
        net.traverse(_._2.acceptMsg(genesisM)) >> Console[F].println("Bootstrap done")
      )
      // connect processes ports, build a process stream
      val x = net.map {
        case (self, dProc) =>
          val notSelf = net.filterNot { case (s, _) => s == self }
          DProc.connect[F, M, S, T](
            dProc,
            broadcast(notSelf, propDelay),
            crWithLog.andThen(_.void),
            (_: Set[M]) => ().pure[F]
          )
      }
      bootstrap.compile.drain.as(x)
    }
  }

  def crWithLog[F[_]: Applicative]: Pipe[F, ConflictResolution[T], ConflictResolution[T]] =
    (in: Stream[F, ConflictResolution[T]]) =>
      in.mapAccumulate(0) { case (acc, cr) => (acc + cr.accepted.size) -> cr }
        .evalTap(x => println(s"Finalized ${x._1} deploys").pure[F])
        .map(_._2)

  override def run: IO[Unit] =
    Random.scalaUtilRandom[IO].flatMap { implicit rndIO =>
      Sim.sim[IO](4).flatMap { processes =>
        Stream.emits(processes).parJoin(processes.size).compile.drain
      }
    }
}
