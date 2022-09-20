package sim

import cats.effect._
import cats.effect.unsafe.{IORuntime, IORuntimeConfig, Scheduler}
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import dproc.shared.Diag
import org.scalatest.freespec.AnyFreeSpec

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext

class ConcurrentScala extends AnyFreeSpec {
  "My Code " - {
    "works" in {
      import cats.effect.unsafe.implicits.global
      ConcurrentScala.run.unsafeRunSync()
    }
  }
}

object ConcurrentScala1 extends IOApp.Simple {

  val run = {
    println(s"Starving $computeWorkerThreadCount cpus")
    0.until(100).toList parTraverse_ { i =>
      IO {
        println(s"running #$i")
        while (true) {}
      }
    }
  }
}

object ConcurrentScala extends IOApp.Simple {

  val cpuNum = Runtime.getRuntime.availableProcessors()
  // full throttle concurrency
  val par = cpuNum - 2
  // leave 2 cpu not used to leave some room
  val safePar = cpuNum - 2

  val fibs: LazyList[Int] = 0 #:: fibs.scanLeft(1)(_ + _)

  // compute fibonacci sequence
  def fibSeq(n: Int) = fibs.take(n).lastOption

  // tiny stage, just sum two numbers
  def simple[F[_]: Sync] = Sync[F].delay(1 + 1)

  // complex stage, compute fib
  def complex[F[_]: Sync] = Sync[F].delay(fibSeq(10000))

  // repeat stage 1000 times
  def burn(stage: IO[Any]): IO[List[Any]] = List.range(0, 10e3.intValue).traverse(_ => stage)

  // run one stream
  def burn1(fx: IO[Any]) = burn(fx)

  // run streams concurrently (-2 cpus just in case)
  def burnFs2(fx: IO[Any]) = {
    val procs = fs2.Stream.eval(burn(fx)).covary[IO].repeatN(par)
    fs2.Stream(procs).parJoin(par).compile.drain
  }

  // run with parallel - this hangs when par == num of CPUs, do not stuck when there are some free cpus left
  // some info here https://github.com/typelevel/cats-effect/issues/3392
  def burnParTraverse(fx: IO[Any]): IO[Unit] =
    (1 to par).toList.parTraverse_(_ => burn(fx))

  def burnParSequence(fx: IO[Any]): IO[Unit] =
    LazyList.continually(burn(fx)).take(par).toList.parSequence_

  // this uncommented overrides work stealing thread pool with fixed thread pool to work like CE2
  // if this is enabled - burn10_1 does not hang
  protected override def runtime: IORuntime = {
    val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(par))
    val s  = Scheduler.createDefaultScheduler()
    IORuntime(ec, ec, s._1, s._2, IORuntimeConfig())
  }

  override def run: IO[Unit] = {
    val tiny = simple[IO]
    val big  = complex[IO]
    burn1(tiny) >> burn1(tiny) >> burn1(tiny) >> // warmup
      // tiny stage
      IO.println(s"1 stream tiny") >> Diag.duration(burn1(tiny)) >>
      IO.println(s"fs2 parJoin $par tiny") >> Diag.duration(burnFs2(tiny)) >>
      IO.println(s"parSequence $par tiny") >> Diag.duration(burnParTraverse(tiny)) >>
      IO.println(s"parSequence $par tiny") >> Diag.duration(burnParSequence(tiny)) >>
      // expensive stage
      IO.println(s"1 stream fib") >> Diag.duration(burn1(big)) >>
      IO.println(s"fs2 parJoin $par big") >> Diag.duration(burnFs2(big)) >>
      IO.println(s"parTraverse_ $par big") >> Diag.duration(burnParTraverse(big)) >>
      IO.println(s"parSequence_ $par big") >> Diag.duration(burnParSequence(big))
  }
}
