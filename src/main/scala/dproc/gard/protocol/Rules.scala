package dproc.gard.protocol

/** Double spend guard that allows transaction to be included in a message seeing fringe from N to N + ExpT
  * where N is the fringe number that is recorded in transaction body and ExpT is param defined by the network. */
object Rules {

  /**
    * Data required for the protocol.
    * @param tx transaction ID
    * @param fringe fringe computed by the message that transaction is part of
    */
  final case class GardM[M, T](txs: Set[T], fringe: Set[M])

  def fringeBasedGuard[T](
      tx: T,
      N: Int,
      expT: Int,
      allTxsMatching: Set[Int] => Set[T]
  ): Boolean =
    allTxsMatching((N - expT to N).toSet).contains(tx)
}
