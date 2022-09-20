package dproc.test

import cats.Monad
import cats.syntax.all._
import dproc.lazo.protocol.data.LazoF

object Alg {

  trait DAG[F[_]] extends Monad[F]

  trait Finalizer[F[_]] {
    def computeFringe[M](mgjs: Set[M]): F[LazoF[M]]
  }

  trait Merger[F[_]] {
    def resolve[T](cSet: Set[T]): F[Set[T]]

    def merge[T, S](base: S, x: Set[T]): F[S]
  }

  trait View[F[_]] {
    def mgjs[M]: F[Set[M]]
    def offences[M]: F[Set[M]]
  }

  def createMessage[F[_]: Monad, M](finalizer: Finalizer[F], merger: Merger[F], view: View[F]) =
    for {
      mgjs    <- view.mgjs[M]
      fringes <- finalizer.computeFringe(mgjs)
      _       <- merger.resolve(fringes.fFringe)
    } yield ()

  final case class Dag[_]()

  implicit val finDagImpl = new Finalizer[Dag] {
    override def computeFringe[M](mgjs: Set[M]): Dag[LazoF[M]] = ???
  }

}
