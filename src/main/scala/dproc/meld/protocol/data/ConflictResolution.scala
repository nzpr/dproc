package dproc.meld.protocol.data

final case class ConflictResolution[T](accepted: Set[T], rejected: Set[T])
