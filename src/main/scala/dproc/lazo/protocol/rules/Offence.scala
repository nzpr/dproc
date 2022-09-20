package dproc.lazo.protocol.rules

sealed trait Offence

class InvalidBasic extends Offence
object InvalidBasic { def unapply(x: InvalidBasic) = true }

// Message violates continuity rule
final case class InvalidContinuity[M](forgotten: Set[M]) extends InvalidBasic

// Message violates frugality rule
final case class InvalidFrugality[M](offendersAccepted: Set[M]) extends InvalidBasic

// Message violates integrity rule
final case class InvalidIntegrity[M](mismatch: Set[M]) extends InvalidBasic

// Message does not mark equivocations as offences
final case class InvalidUnambiguity[S, M](pardons: Map[S, Set[M]]) extends InvalidBasic

// Message computes invalid fringe
final case class InvalidFringe[M](shouldBe: Set[M], is: Set[M]) extends Offence

// Message computes invalid conflict resolution.
final case class InvalidFringeResolve[T](invAccept: Set[T]) extends Offence

// Message computes invalid final state from the fringe.
final case class InvalidFringeState() extends Offence

// Message puts in list of transactions one that are prohibited due to expiration
final case class InvalidDoubleSpend[T](doubleSpends: Set[T]) extends Offence

// Message computes invalid conflict resolution of the conlict set
final case class InvalidResolution[T](shouldBe: Set[T]) extends Offence

// Message computes invalid blockchain state
final case class InvalidPreState() extends Offence
final case class InvalidExec()     extends Offence

object Offence { def iexec: Offence = InvalidExec() }
