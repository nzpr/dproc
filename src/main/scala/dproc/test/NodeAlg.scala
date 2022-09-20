package dproc.test

trait NodeAlg[F[_]]

final case class RcT[F[_], T](tx: T)         extends NodeAlg[F]
final case class MkM[F[_], S](s: S)          extends NodeAlg[F]
final case class VaM[F[_], S, M](s: S, m: M) extends NodeAlg[F]
