package dproc.meld.data

import dproc.meld.protocol.Rules
import dproc.meld.protocol.data.{ConflictResolution, MeldM}

/** Meld is an addition to Lazo protocol that supports speculative execution / block merge. */
final case class Meld[M, T](
    txsMap: Map[M, Set[T]],
    conflictsMap: Map[T, Set[T]], // Conflicts computed withing the Meld state
    dependentMap: Map[T, Set[T]], // Dependencies computed withing the Meld state
    finalized: Set[T],
    rejected: Set[T]
) {
  def add(lazoId: M, m: MeldM[T], garbage: Set[M]): Meld[M, T] = {
    val garbageT        = garbage.flatMap(txsMap)
    val newConflictsMap = conflictsMap ++ m.conflictMap -- garbageT
    val newDependentMap = dependentMap ++ m.dependsMap -- garbageT
    val newTxMap        = txsMap + (lazoId -> m.txs.toSet) -- garbage
    val newFinalized    = finalized ++ m.finalized -- garbageT
    val newRejected     = rejected ++ m.rejected -- garbageT
    copy(
      conflictsMap = newConflictsMap,
      dependentMap = newDependentMap,
      txsMap = newTxMap,
      finalized = newFinalized,
      rejected = newRejected
    )
  }
}

object Meld {
  def empty[M, T]: Meld[M, T] = Meld(
    Map.empty[M, Set[T]],
    Map.empty[T, Set[T]],
    Map.empty[T, Set[T]],
    Set.empty[T],
    Set.empty[T]
  )

//  def resolve[M, T: Ordering](target: Set[M], meld: Meld[M, T]): ConflictResolution[T] = {
//    val conflictSet = target.flatMap(meld.txsMap)
//    // TODO mergeable channels
//    val r = Rules.resolveConflictSet[T, Int](
//      conflictSet,
//      meld.finalized,
//      meld.rejected,
//      (_: T) => 0L,
//      meld.conflictsMap,
//      meld.dependentMap,
//      Map(),
//      Map()
//    )
//    ConflictResolution(r._1, r._2)
//  }
  def resolve[M, T: Ordering](target: Set[M], meld: Meld[M, T]): ConflictResolution[T] =
    ConflictResolution(meld.txsMap.view.filterKeys(target).values.flatten.toSet, Set())
}
