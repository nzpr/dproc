package dproc.shared

import cats.effect.Sync
import cats.syntax.all._
import dproc.DProc
import dproc.lazo.data.Lazo
import dproc.lazo.protocol.data.LazoM

class DebugException[M, S, T](state: DProc.ST[M, S, T]) extends Exception

object DebugException {

  def checkForInvalidFringe[F[_]: Sync, M, S](lazo: Lazo[M, S], lazoM: LazoM[M, S]) = {
    val errMsg =
      s"Error. Latest fringe ${lazo.fringes.lastOption} sees new fringes ${lazoM.fringes.fFringe}"
    new Exception(errMsg)
      .raiseError[F, Unit]
      .unlessA(
        ((lazo.fringes.lastOption
          .map { case (_, msgs) => msgs.flatMap(lazo.seenMap) }
          .getOrElse(Set()) diff lazoM.fringes.fFringe) intersect lazoM.fringes.fFringe).isEmpty
      )
  }
}
