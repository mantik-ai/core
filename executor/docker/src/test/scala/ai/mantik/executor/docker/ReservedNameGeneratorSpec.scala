package ai.mantik.executor.docker

import ai.mantik.testutils.{ AkkaSupport, TestBase }

import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Random

class ReservedNameGeneratorSpec extends TestBase with AkkaSupport {

  trait Env {

    object poolLock

    val pool = mutable.Set.empty[String]
    val MaxSpace = 1500
    val TestSize = 1400

    class Backend extends ReservedNameGenerator.Backend {

      override def lookupAlreadyTaken(): Future[Set[String]] = {
        Future {
          val result: Set[String] = poolLock.synchronized {
            pool.toSet
          }
          result
        }
      }

      override def generate(prefix: String, taken: Set[String]): String = generateSingleExec(taken)

      @tailrec
      private def generateSingleExec(reserved: Set[String]): String = {
        tryGenerate(reserved) match {
          case Some(ok) => ok
          case None     => generateSingleExec(reserved)
        }
      }

      private def tryGenerate(reserved: Set[String]): Option[String] = {
        // This will lead to collisions but should work for 100 times
        val candidate = Math.floorMod(Math.abs(Random.nextInt()), MaxSpace).toString
        if (reserved.contains(candidate)) {
          None
        } else {
          Some(candidate)
        }
      }
    }

    val generator = new ReservedNameGenerator(new Backend())
  }

  it should "work for a lot of elements" in new Env {
    // there is only place for MaxSpace elements, so we will have collisions
    val futures = for (i <- 0 until TestSize) yield {
      generator.reserve { s =>
        Future {
          poolLock.synchronized {
            pool += s
          }
          s
        }
      }
    }
    val values = await(Future.sequence(futures))
    withClue("There should be no duplicates") {
      values.distinct.size shouldBe values.size
    }
  }

  it should "work with a custom prefix" in new Env {
    // note: the prefix is ignored in this test, but it will fetch the list each time
    val futures = for (i <- 0 until TestSize) yield {
      generator.reserveWithPrefix("prefix") { s =>
        Future {
          poolLock.synchronized {
            if (pool.contains(s)) {
              println(s"Collision for ${s}")
            }
            pool += s
          }
          s
        }
      }
    }
    val values = await(Future.sequence(futures))
    withClue("There should be no duplicates") {
      values.distinct.size shouldBe values.size
    }
  }

}
