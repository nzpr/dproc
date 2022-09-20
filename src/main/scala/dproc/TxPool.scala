package dproc
import cats.effect.Ref
import dproc.TxPool.ST

final case class TxPool[F[_], TId, T](st: Ref[F, ST[TId]])

object TxPool {
  final case class ST[TId]()
  def apply[F[_]] = {}
}
