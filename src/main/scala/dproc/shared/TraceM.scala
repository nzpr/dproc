package dproc.shared

import cats.effect.kernel.Resource

trait TraceM[F[_], ID] {
  def trace: _ => F[ID] => Resource[F, ID]
}
