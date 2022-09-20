package dproc.lazo.protocol.data

import cats.Monad
import cats.effect.kernel.Sync
import cats.syntax.all._
import dproc.lazo.data.Lazo

/**
  * Lazo message.
  *
  * @param sender creator of the message.
  * @param mgj mgjs define the view that the message agreed on.
  * @param offences messages that the messages disagree with.
  * @param fringes fringes computed by the message.
  * @param state execution data for the the new final fringe found by the message. Provided by execution engine.
  */
final case class LazoM[M, S](
    sender: S,
    mgj: Set[M],
    offences: Set[M],
    fringes: LazoF[M],
    state: LazoE[S],
    stateful: Boolean
)

object LazoM {

  /** Data required by protocol that can be derived from LazoM.
    * Might be computationally expensive so this is on a data type. */
  final case class LazoMExt[M, S](
      fjs: Set[M],
      selfJOpt: Option[M],
      seen: Set[M],
      baseBonds: Bonds[S],
      lfIdx: Option[Int]
  )

  final case class Extended[M, S](
      lazoM: LazoM[M, S],
      ext: LazoMExt[M, S]
  )

  trait LazoMSyntax {
    implicit final def lazoMSyntax[M, S](x: LazoM[M, S]): LazoMOps[M, S] = new LazoMOps(x)
  }

  trait LazoMESyntax {
    implicit final def lazoMESyntax[M, S](x: LazoM.Extended[M, S]): LazoMEOps[M, S] =
      new LazoMEOps(x)
  }

  final class LazoMOps[M, S](private val x: LazoM[M, S]) extends AnyVal {
    def fjs(implicit state: Lazo[M, S]): Set[M] =
      state.fjsOpt(x.mgj).getOrElse(x.mgj) // this orElse is for genesis case
    def selfJOpt(implicit state: Lazo[M, S]): Option[M] = state.selfJOpt(x.mgj, x.sender)
    def seen(implicit state: Lazo[M, S]): Set[M]        = state.seen(x.mgj)
    def baseBonds(implicit state: Lazo[M, S]): Bonds[S] =
      state.baseBondsOpt(x.mgj).getOrElse(x.state.bonds)
    def lfIdx(implicit state: Lazo[M, S]): Option[Int] = state.lfIdx(x.mgj)

    def computeExtended[F[_]: Sync](state: Lazo[M, S]) = {
      implicit val s = state
      for {
        fjs       <- Sync[F].delay(fjs)
        selfJOpt  <- Sync[F].delay(selfJOpt)
        seen      <- Sync[F].delay(seen)
        baseBonds <- Sync[F].delay(baseBonds)
        lfIdx     <- Sync[F].delay(lfIdx)
      } yield LazoM.Extended(x, LazoMExt(fjs, selfJOpt, seen, baseBonds, lfIdx))
    }
  }

  final class LazoMEOps[M, S](private val x: LazoM.Extended[M, S]) extends AnyVal {
    def sender: S           = x.lazoM.sender
    def mgj: Set[M]         = x.lazoM.mgj
    def offences: Set[M]    = x.lazoM.offences
    def fringes: LazoF[M]   = x.lazoM.fringes
    def state: LazoE[S]     = x.lazoM.state
    def stateful: Boolean   = x.lazoM.stateful
    def fjs: Set[M]         = x.ext.fjs
    def selfJOpt: Option[M] = x.ext.selfJOpt
    def seen: Set[M]        = x.ext.seen
    def baseBonds: Bonds[S] = x.ext.baseBonds
    def lfIdx: Option[Int]  = x.ext.lfIdx
  }
}
