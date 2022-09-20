package sim

import cats.effect._
import cats.effect.unsafe.{IORuntime, IORuntimeConfig, Scheduler}
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import dproc.shared.Diag
import org.scalatest.freespec.AnyFreeSpec

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext

class Starve extends AnyFreeSpec {

  "My Code " - {
    "works" in {
      Starve.run.unsafeRunSync()
    }
  }
}

object Starve extends IOApp.Simple {
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

object fStarve extends IOApp.Simple {
  val cpuNum = Runtime.getRuntime.availableProcessors()
  // leave 3 cpu not used (just in case)
  val par = cpuNum - 2

  val fibs: LazyList[Int] = 0 #:: fibs.scanLeft(1)(_ + _)
  def fib(n: Int)         = fibs.take(n).lastOption

  // repeat stage 100k times
  def burn(fx: IO[Any]) = fs2.Stream.repeatEval(fx).take(100000)

  // tiny stage, just sum two numbers
  val tiny = IO.delay(1 + 1)
  // complex stage, compute fib
  val big = IO.delay(fib(10000))

  // run one stream
  def burn1(fx: IO[Any]) = burn(fx).compile.drain
  // run streams concurrently (-2 cpus just in case)
  def burn10(fx: IO[Any]) = fs2.Stream(burn(fx)).repeatN(par).parJoin(par).compile.drain
  // run with parallel - this hangs when par == num of CPUs, do not stuck when there are some free cpus left
  // some info here https://github.com/typelevel/cats-effect/issues/3392
  def burn10_1(fx: IO[Any]): IO[Unit]   = (1 to par).toList.parTraverse_(_ => burn1(fx))
  def burn10_1_1(fx: IO[Any]): IO[Unit] = LazyList.continually(burn1(fx)).take(par).parSequence_

  // this uncommented overrides work stealing thread pool with fixed thread pool to work like CE2
  // if this is enabled - burn10_1 does not hang
  protected override def runtime: IORuntime = {
    val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(par))
    val s  = Scheduler.createDefaultScheduler()
    IORuntime(ec, ec, s._1, s._2, IORuntimeConfig())
  }

  override def run: IO[Unit] =
    burn1(tiny) >> burn1(tiny) >> burn1(tiny) >> // warmup
      // tiny stage
      IO.println(s"1 stream tiny") >> Diag.duration(burn1(tiny)) >>
      IO.println(s"$par stream, parJoin $par tiny") >> Diag.duration(burn10(tiny)) >>
      IO.println(s"$par stream, parSequence $par tiny") >> Diag.duration(burn10_1(tiny)) >>
      // expensive stage
      IO.println(s"1 stream fib") >> Diag.duration(burn1(big)) >>
      IO.println(s"$par stream, parJoin $par fib") >> Diag.duration(burn10(big)) >>
      IO.println(s"$par stream, parTraverse_ $par fib") >> Diag.duration(burn10_1(big)) >>
      IO.println(s"$par stream, parSequence_ $par fib") >> Diag.duration(burn10_1_1(big))
}
