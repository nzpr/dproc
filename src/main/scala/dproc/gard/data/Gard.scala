package dproc.gard.data

import dproc.gard.protocol.Rules.{fringeBasedGuard, GardM}

import scala.collection.immutable.SortedMap

final case class Gard[M, T](
    fringeIndices: Map[Set[M], Int],
    txsByFringe: SortedMap[Int, Set[T]]
) {

  /** Modify the state by adding data about new message. */
  def add(d: GardM[M, T]): Gard[M, T] = {
    val fringeIdx =
      fringeIndices.getOrElse(d.fringe, txsByFringe.lastOption.map(_._1).getOrElse(Int.MinValue))
    val newFi = fringeIndices + (d.fringe -> fringeIdx)
    val newMbf =
      txsByFringe.updated(fringeIdx, txsByFringe.get(fringeIdx).map(_ ++ d.txs).getOrElse(d.txs))
    Gard(newFi, newMbf)
  }

  /** Check whether transaction is a double spend. */
  def isDoubleSpend(tx: T, fringe: Set[M], expT: Int): Boolean =
    fringeBasedGuard(
      tx,
      fringeIndices.getOrElse(fringe, 0),
      expT,
      _.flatMap(txsByFringe.getOrElse(_, Set()))
    )
}

object Gard {
  def empty[M, T] = Gard(Map.empty[Set[M], Int], SortedMap.empty[Int, Set[T]])
}
