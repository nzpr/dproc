package dproc

final case class Buffer[M](childMap: Map[M, Set[M]], missingMap: Map[M, Set[M]]) {
  def add(m: M, missing: Set[M]): Buffer[M] = {
    val newChildMap = missing.foldLeft(childMap) {
      case (acc, d) => acc + (d -> acc.get(d).map(_ + m).getOrElse(Set(m)))
    }
    val newMissingMap = if (missing.isEmpty) missingMap else missingMap + (m -> missing)

    Buffer(newChildMap, newMissingMap)
  }

  def complete(m: M): (Buffer[M], Set[M]) = {
    val awaiting              = childMap.getOrElse(m, Set())
    val (adjusted, unaltered) = missingMap.view.partition { case (m, _) => awaiting.contains(m) }
    val (done, altered)       = adjusted.mapValues(_ - m).partition { case (_, deps) => deps.isEmpty }
    val doneSet               = done.keys.toSet

    val newChildMap = childMap - m
    val newMissingMap = (unaltered ++ altered).filterNot {
      case (k, _) => (doneSet + m).contains(k)
    }.toMap
    Buffer(newChildMap, newMissingMap) -> doneSet
  }
}

object Buffer {
  def empty[M] = Buffer[M](Map.empty[M, Set[M]], Map.empty[M, Set[M]])
}
