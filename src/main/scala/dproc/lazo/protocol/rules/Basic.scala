package dproc.lazo.protocol.rules

import cats.syntax.all._

object Basic {

  /**
    * Message has to see everything that the self justification seen.
    * @param seen seen predicate
    * @param selfJsMgs minimal generative set of self justification
    */
  def continuity[M](seen: M => Boolean, selfJsMgs: Set[M]): Option[InvalidContinuity[M]] = {
    val notSeen = selfJsMgs.filterNot(seen)
    notSeen.nonEmpty.guard[Option].as(InvalidContinuity(notSeen))
  }

  /** Message should not add new messages to the view of self parent created by offenders
    * detected in the view of self parent.*/
  def frugality[M, S](
      justifications: Set[M],
      selfParentOffences: Set[M],
      sender: M => S
  ): Option[InvalidFrugality[M]] = {
    val offenders        = selfParentOffences.map(sender)
    val offenderJs       = justifications.filter(j => offenders.contains(sender(j)))
    val newFromOffenders = offenderJs -- selfParentOffences
    newFromOffenders.nonEmpty.guard[Option].as(InvalidFrugality(newFromOffenders))
  }

  /** Messages disagreeing with offences declared by justification should record that
    * justification as an offence. */
  def integrity[M](
      offences: Set[M],
      jssOffences: Map[M, Set[M]]
  ): Option[InvalidIntegrity[M]] = {
    val pardons = jssOffences.collect { case (m, offs) if (offs -- offences).isEmpty => m }
    pardons.isEmpty.guard[Option].as(InvalidIntegrity(pardons.toSet))
  }

  /** Message observing equivocations have to declare them as offences. */
  def unambiguity[M, S](
      offences: Set[M],
      justifications: Set[M],
      sender: M => S
  ): Option[InvalidUnambiguity[S, M]] = {
    val pardons = justifications.groupBy(sender).collect {
      case (s, jss) if jss.size > 1 && (jss -- offences).nonEmpty => s -> jss
    }
    pardons.nonEmpty.guard[Option].as(InvalidUnambiguity(pardons))
  }

  /** The order of validation matters here. */
  def validateBasic[M, S](
      justifications: Set[M],
      offences: Set[M],
      selfJsMgs: Set[M],
      selfParentOffences: Set[M],
      jssOffences: Map[M, Set[M]],
      senderF: M => S,
      seen: M => Boolean
  ): Either[Unit, InvalidBasic] =
    for {
      _ <- unambiguity(offences, justifications, senderF).toRight(())
      _ <- continuity(seen, selfJsMgs: Set[M]).toRight(())
      _ <- frugality(offences, selfParentOffences, senderF).toRight(())
      r <- integrity(offences, jssOffences).toRight(())
    } yield r
}
