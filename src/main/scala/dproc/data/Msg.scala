package dproc.data

import dproc.gard.protocol.Rules.GardM
import dproc.lazo.protocol.data.{Bonds, LazoE, LazoF, LazoM}

final case class Msg[M, S, T](
    sender: S,               // block sender
    minGenJs: Set[M],        // minimal generative justifications
    txs: List[T],            // transactions executed in a block
    offences: Set[M],        // offences computed by the message
    finalFringe: Set[M],     // final fringe computed by the message
    partitionFringe: Set[M], // partition fringe computed by the message
    finalized: Set[T],       // finalization computed by the message
    rejected: Set[T],        // finalization computed by the message
    merge: Set[T],           // rejections on conflict set merge computed by the message
    bonds: Bonds[S],
    lazinessTolerance: Int,
    ejectionThreshold: Int,
    expirationThreshold: Int
)

object Msg {

  final case class WithId[M, S, T](id: M, m: Msg[M, S, T])

  def toLazoM[M, S, T](m: Msg[M, S, T]) =
    LazoM(
      m.sender,
      m.minGenJs,
      m.offences,
      LazoF(m.finalFringe, m.finalFringe),
      LazoE(m.bonds, m.lazinessTolerance, m.ejectionThreshold, m.expirationThreshold),
      m.txs.isEmpty
    )

  def toLazoE[M, S, T](m: Msg[M, S, T]) =
    LazoE(m.bonds, m.lazinessTolerance, m.ejectionThreshold, m.expirationThreshold)

  def toGardM[M, S, T](m: Msg[M, S, T]): GardM[M, T] = GardM(m.txs.toSet, m.finalFringe)
}
