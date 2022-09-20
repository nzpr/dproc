import cats.effect.IO
import cats.syntax.all._
import dproc.lazo.protocol.data.LazoF
import fs2.Stream
import fs2.concurrent.Channel
import org.scalatest.flatspec.AnyFlatSpec
import dproc.shared.Diag.time

class Test extends AnyFlatSpec {
  it should "run" in {
    val r = for {
      q <- Channel.unbounded[IO, Int]
    } yield {
      val s    = Stream(1, 2, 3, 4, 5, 6, 7).covary[IO]
      val one  = s.evalMap(q.send).map(_ => println("1"))
      val two  = s.map(_ => println("2"))
      val tree = q.stream.map(_ => println(s"3")).evalMap(_ => ().pure[IO])
      val four = tree.filter(_ => true).evalTap(_ => println(s"4").pure[IO])
      val five = four.filter(_ => true).map(_ => println(s"5"))
      val six  = five.filter(_ => true).map(_ => println(s"6"))
      Stream(s.map(_ => ()), one, two, tree, four, five, six).parJoin(50)
    }

    import cats.effect.unsafe.implicits.global
    r.flatMap(_.compile.drain).unsafeRunSync()
  }
  it should "run1" in {

    import cats.effect.IO
    import cats.effect.unsafe.implicits.global
    import cats.syntax.all._

//    val rndIO = Random.scalaUtilRandom[IO].unsafeRunSync()

    val rndSet = List.range(1, 10).pure[IO] //.traverse(_ => rndIO.nextInt)

    val t = for {
      s1 <- rndSet
      s2 <- rndSet
      t1 = time(s1.toSet)
      t2 = time(s2.toSet)
      t3 = t1 -- t2
    } yield (t1, t2, t3)

    t.unsafeRunSync()
  }

  it should "run2" in {
    trait Finalizer[F[_]] {
      def computeFringe[M](mgjs: Set[M]): F[LazoF[M]]
    }

    trait Merger[F[_]] {
      def resolve[T](cSet: Set[T]): F[Set[T]]

      def merge[T, S](base: S, x: Set[T]): F[S]
    }

    trait View[F[_]] {
      def mgjs: F[Set[?]]

      def offences: F[Set[?]]
    }
  }
}
