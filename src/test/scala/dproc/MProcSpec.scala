package dproc

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class MProcSpec extends AnyFlatSpec with Matchers {
//
//  "proposer" should "not propose equivocations" in {
//    implicit val c = Concurrent[Task]
//    implicit val t = Timer[Task]
//    implicit val s = monix.execution.Scheduler.global
//
//    /** No concurrent messages should be produced. */
//    def out[F[_]: Concurrent: Timer] = {
//      val someSt   = StateWithTxs(DProc.emptyST, List())
//      val requests = Stream(someSt, someSt, someSt, someSt).covary[F]
//      val exeEngine = ExeEngine[F](
//        (_, _) => Timer[F].sleep(300.milliseconds).as(0), // imitate 300 ms delay on execution
//        _ => LazoE(Bonds(Map()), 0, 0, 0).pure[F]
//      )
//      val mergeEngine =
//        MergeEngine[F]((_, _) => 0.pure[F], (_, _) => false.pure[F], (_, _) => false.pure[F])
//      val prop = MProp[Int, Int](0, requests, exeEngine, mergeEngine)
//      prop.flatMap(_.msgStream.interruptAfter(500.milliseconds).compile.toList)
//    }
//
//    val proposedNum = out[Task].map(_.size).runSyncUnsafe()
//    proposedNum shouldBe 1
//  }
//
//  "proposer" should "emit message for each input, if no races" in {
//    implicit val c = Concurrent[Task]
//    implicit val t = Timer[Task]
//    implicit val s = monix.execution.Scheduler.global
//
//    /** No concurrent messages should be produced. */
//    def out1[F[_]: Concurrent: Timer] = {
//      val someSt   = StateWithTxs(DProc.emptyST, List())
//      val requests = Stream(someSt, someSt, someSt, someSt).metered[F](100.milliseconds).covary[F]
//      val exeEngine = ExeEngine[F](
//        (_, _) => 0.pure[F],
//        _ => LazoE(Bonds(Map()), 0, 0, 0).pure[F]
//      )
//      val mergeEngine =
//        MergeEngine[F]((_, _) => 0.pure[F], (_, _) => false.pure[F], (_, _) => false.pure[F])
//      val prop = MProp(0, requests, exeEngine, mergeEngine)
//      prop.flatMap(_.msgStream.interruptAfter(1.second).compile.toList)
//    }
//
//    val proposedNum = out1[Task].runSyncUnsafe()
//    proposedNum.size shouldBe 4
//  }
}
