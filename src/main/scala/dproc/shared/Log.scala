package dproc.shared

import cats.Applicative
import cats.data.Chain
import cats.effect.{Concurrent, Ref, Resource}
import cats.syntax.all._

object Log {
  type TraceState[F[_]] = Ref[F, Chain[String]]
  type TraceF[F[_]]     = String => F[Unit]
  def mkLog[F[_]: Concurrent]: F[TraceState[F]] =
    Ref.of[F, Chain[String]](Chain.empty)

  def mkAppender[F[_]](log: TraceState[F]): TraceF[F] =
    entry => log.update(chain => chain.append(entry))

  def collectLog[F[_]](log: TraceState[F]): F[Chain[String]] = log.get

  def noTraceResource[F[_]: Applicative]: Resource[F, TraceF[F]] =
    Resource.make(((_: String) => ().pure[F]).pure[F])(_ => ().pure[F])
  def tracerResource[F[_]: Concurrent, S, M](
      processId: S,
      setTraceId: F[M],
      inbound: Boolean
  ): Resource[F, TraceF[F]] =
    Resource
      .make(mkLog) { x =>
        for {
          log <- collectLog(x)
          x   <- setTraceId
        } yield println(
          s"\nTrace for ($inbound) $x @ $processId: \n${log.toList.mkString("\n")}"
        )
      }
      .map(mkAppender)
}
