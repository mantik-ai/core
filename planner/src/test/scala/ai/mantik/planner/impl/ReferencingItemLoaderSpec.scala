package ai.mantik.planner.impl

import ai.mantik.planner.repository.Errors
import ai.mantik.planner.repository.Errors.NotFoundException
import ai.mantik.testutils.{ AkkaSupport, TestBase }

import scala.concurrent.Future

class ReferencingItemLoaderSpec extends TestBase with AkkaSupport {

  case class Item(name: String)

  def makeNet(x: (String, Seq[String])*): Map[String, Seq[String]] = x.toMap

  class SimpleLoader(map: Map[String, Seq[String]]) extends ReferencingItemLoader[String, Item](
    loader = id => map.get(id) match {
      case Some(i) => Future.successful(Item(id))
      case None    => Future.failed(new Errors.NotFoundException(""))
    },
    dependencyExtractor = x => map.getOrElse(x.name, Nil)
  )

  it should "work for a empty example" in {
    val dependencies = makeNet(
      "a" -> Nil
    )
    val loader = new SimpleLoader(dependencies)
    await(loader.loadWithHull("a")) shouldBe Seq(Item("a"))
    intercept[NotFoundException] {
      await(loader.loadWithHull("b"))
    }
  }

  it should "work for a simple depencency" in {
    val dependencies = makeNet(
      "a" -> Seq("b", "c"),
      "b" -> Seq(),
      "c" -> Seq("d", "b"),
      "d" -> Seq("e"),
      "e" -> Nil
    )
    val loader = new SimpleLoader(dependencies)
    await(loader.loadWithHull("e")) shouldBe Seq(Item("e"))
    await(loader.loadWithHull("d")) shouldBe Seq(Item("d"), Item("e"))
    await(loader.loadWithHull("c")) shouldBe Seq(Item("c"), Item("d"), Item("b"), Item("e"))
    await(loader.loadWithHull("a")) shouldBe Seq(Item("a"), Item("b"), Item("c"), Item("d"), Item("e"))
  }
}
