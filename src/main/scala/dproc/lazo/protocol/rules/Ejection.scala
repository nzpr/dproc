package dproc.lazo.protocol.rules

import dproc.lazo.protocol.data.Bonds
import scala.collection.immutable.Queue

/**
  * Ejection rule is the following:
  * Sender is ejected IIF it did not participate in partitions computed by N latest self messages,
  * where N is a parameter.
  */
object Ejection {
//
//  // For ejection purposes only N self ancestors are required, therefore queue of length N is enough.
//  type EjectionData = Queue[Set[Int]]
//
//  /**
//    * Compute data supporting ejection logic for the new message
//    * @param selfParentOpt ejection data for the self parent (if exists)
//    * @param partition partition computed for the message
//    * @param ejectionThreshold system parameter, the bigger the number the higher the tolerance for validator laziness.
//    * @return ejection data for the new message
//    */
//  def compute(
//      selfParentOpt: Option[EjectionData],
//      partition: Set[Int],
//      ejectionThreshold: Int
//  ): EjectionData =
//    selfParentOpt
//      .map { sp =>
//        if (sp.size == ejectionThreshold) sp.dequeue._2.enqueue(Set(partition))
//        else sp.dequeue._2
//      }
//      .getOrElse(Queue.empty[Set[Int]])
//
//  def eject(bondsMap: Bonds[S], ejectionData: EjectionData): Set[Int] =
//    bondsMap.activeSet -- ejectionData.iterator.flatten.toSet
}
