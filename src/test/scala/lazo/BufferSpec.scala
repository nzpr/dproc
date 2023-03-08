package lazo

import dproc.Buffer
import dproc.shared.Log
import lazo.BufferSpec._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class BufferSpec extends AnyFlatSpec with Matchers {
  "adding message with missing deps" should "be OK" in {
    afterAdd1.missingMap shouldBe Map(1 -> Set(0))
    afterAdd1.childMap shouldBe Map(0   -> Set(1))

    afterAdd2.missingMap shouldBe Map(1 -> Set(0), 2 -> Set(1))
    afterAdd2.childMap shouldBe Map(0   -> Set(1), 1 -> Set(2))

    afterAdd4.missingMap shouldBe Map(1 -> Set(0), 2 -> Set(1), 4 -> Set(2, 3))
    afterAdd4.childMap shouldBe Map(0   -> Set(1), 1 -> Set(2), 2 -> Set(4), 3 -> Set(4))

    afterAdd5.missingMap shouldBe Map(1 -> Set(0), 2 -> Set(1), 4 -> Set(2, 3), 5 -> Set(2))
    afterAdd5.childMap shouldBe Map(0   -> Set(1), 1 -> Set(2), 2 -> Set(4, 5), 3 -> Set(4))

    unlocked0 shouldBe Set(1)
    afterDone0.missingMap shouldBe Map(2 -> Set(1), 4 -> Set(2, 3), 5 -> Set(2))
    afterDone0.childMap shouldBe Map(1   -> Set(2), 2 -> Set(4, 5), 3 -> Set(4))

    unlocked1 shouldBe Set(2)
    afterDone1.missingMap shouldBe Map(4 -> Set(2, 3), 5 -> Set(2))
    afterDone1.childMap shouldBe Map(2   -> Set(4, 5), 3 -> Set(4))

    unlocked2 shouldBe Set(5)
    afterDone2.missingMap shouldBe Map(4 -> Set(3))
    afterDone2.childMap shouldBe Map(3   -> Set(4))

    unlocked3 shouldBe Set(4)
    afterDone3.missingMap shouldBe Map()
    afterDone3.childMap shouldBe Map()
  }
}
object BufferSpec {
  val empty                   = Buffer.empty[Int]
  val afterAdd1               = empty.add(1, Set(0))
  val afterAdd2               = afterAdd1.add(2, Set(1))
  val afterAdd4               = afterAdd2.add(4, Set(2, 3))
  val afterAdd5               = afterAdd4.add(5, Set(2))
  val (afterDone0, unlocked0) = afterAdd5.complete(0)
  val (afterDone1, unlocked1) = afterDone0.complete(1)
  val (afterDone2, unlocked2) = afterDone1.complete(2)
  val (afterDone3, unlocked3) = afterDone2.complete(3)
}
