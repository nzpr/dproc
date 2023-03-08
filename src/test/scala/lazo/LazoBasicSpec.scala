package lazo

import cats.syntax.all._
import dproc.lazo.protocol.rules.Basic._
import dproc.lazo.protocol.rules.Dag.computeFJS
import dproc.lazo.protocol.rules._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.annotation.tailrec

class LazoBasicSpec extends AnyFlatSpec with Matchers {

  "unambiguity violation" should "be detected" in {
    val eqs = Map(0 -> 10, 1 -> 10) // sender 10 equivocates by sending 0 and 1
    val ok  = Map(2 -> 11, 3 -> 12) // sender do not equivocate
    val all = eqs ++ ok
    unambiguity(eqs.keySet, all.keySet, all) shouldBe none[Offence]
    unambiguity(Set(), all.keySet, all) shouldBe InvalidUnambiguity(Map(10 -> Set(0, 1))).some
    unambiguity(Set(), ok.keySet, ok) shouldBe none[Offence]
  }

  "continuity violation" should "be detected" in {
    val selfJsMgjs = Set(0, 1, 2, 3)
    val seen       = selfJsMgjs.map(_ -> true).toMap
    continuity(seen, selfJsMgjs) shouldBe none[Offence]
    continuity(seen + (0 -> false), selfJsMgjs) shouldBe InvalidContinuity(Set(0)).some
  }

  "frugality violation" should "be detected" in {
    val prevOffences = Map(4 -> 10)
    val jssWrong     = Map(0 -> 10, 1 -> 11, 2 -> 12)
    val jssGood      = Map(1 -> 11, 2 -> 12)
    frugality(jssGood.keySet, prevOffences.keySet, jssGood ++ prevOffences) shouldBe none[Offence]
    frugality(jssWrong.keySet, prevOffences.keySet, jssWrong ++ prevOffences) shouldBe
      InvalidFrugality(Set(0)).some
  }

  // Message cannot be added if offender is in the view.

  // Message cannot be added if sender is not bonded in the view.

  // Justifications should be derived correctly from parents
  /** Ejections */
  // New ejection data should contain partition computed at the head position

  // New ejection data when exceeding size of ejection threshold should drop the latest item.

  /** Finality */
  // Next fringe target should find the highest messages for each sender
  // Partition should not be found when justification levels observe different justifications from senders outSe the partition

  /** Tests for fringe ceiling, floor, etc */
  /** Tests for transaction expiration. */
//  "Full justifications" should "be computed correctly" in {}
//  "Full justifications" should "be computed correctly in when bonding happens" in {
//    val target = Set(0)
//    val jssF   = Map(0 -> Map.empty[Int, Int])
//    val isAncestor = {
//      val ancestorMap = Map.empty[Int, Set[Int]]
//      (x: Int, y: Int) => ancestorMap.get(x).exists(_.contains(y))
//    }
//    val bonded  = Set(1, 2, 3, 4, 5) // 5 senders bonded
//    val senderF = Map(0 -> 1) // genesis created by sender 1
//    val jss     = computeFullJS(target, jssF, isAncestor, bonded, senderF)
//    // latest message is the same for each bonded sender ()
//    val ref = Map(5 -> 0, 1 -> 0, 2 -> 0, 3 -> 0, 4 -> 0)
//    jss shouldBe ref
//  }
  def seen[M](m: M, jss: M => Set[M]) = {
    @tailrec
    def go(acc: Set[M], js: Set[M]): Set[M] =
      if (js.isEmpty) acc
      else {
        val nextJss = js.flatMap(jss)
        go(acc ++ nextJss, nextJss)
      }
    go(Set.empty[M], jss(m))
  }

  "Full justifications" should "be computed correctly in when bonding happens" in {
    val target      = Set(2)
    val jss         = Map(2 -> Set(1), 1 -> Set(0), 0 -> Set.empty[Int])
    val ancestorMap = jss.keys.map(k => k -> seen[Int](k, jss)).toMap
    val isAncestor  = (x: Int, y: Int) => ancestorMap.get(x).exists(_.contains(y))
    val bonded      = Set(1, 2, 3, 4, 5) // 5 senders bonded
    val senderF     = Map(0 -> 1) // genesis created by sender 1
    val computed    = computeFJS(target, bonded, jss, isAncestor, senderF)
    // latest message is the same for each bonded sender ()
    val ref = Map(5 -> 0, 1 -> 0, 2 -> 0, 3 -> 0, 4 -> 0)
    computed shouldBe ref
  }
}
