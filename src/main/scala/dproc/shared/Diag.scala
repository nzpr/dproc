package dproc.shared

import cats.effect.Sync
import cats.syntax.all._

import scala.concurrent.duration.{Duration, FiniteDuration}

object Diag {
  def time[R](block: => R, prefix: String = ""): R = {
    val t0     = System.nanoTime()
    val result = block // call-by-name
    val t1     = System.nanoTime()
    println("Elapsed time: " + (t1 - t0).toFloat / 10e6 + s"ms. $prefix")
    result
  }

  def duration[F[_]: Sync, A](block: => F[A], prefix: String = ""): F[A] =
    for {
      t0 <- Sync[F].delay(System.nanoTime)
      a  <- block
      t1 = System.nanoTime
      m  = Duration.fromNanos(t1 - t0)
    } yield {
      println(s"Elapsed time: ${showTime(m)}. $prefix")
      a
    }

  def showTime(d: FiniteDuration): String = {
    val ns   = 1d
    val ms   = 1e6 * ns
    val sec  = 1000 * ms
    val min  = 60 * sec
    val hour = 60 * min
    val m    = d.toNanos
    if (m >= hour) s"${m / hour} hour"
    else if (m >= min) s"${m / min} min"
    else if (m >= sec) s"${m / sec} sec"
    else if (m >= ms) s"${m / ms} ms"
    else s"${m / 1e6d} ms"
  }
}
