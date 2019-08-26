package ai.mantik.executor.docker.api

import ai.mantik.executor.docker.NameGenerator
import ai.mantik.executor.docker.NameGenerator.NodeName
import ai.mantik.testutils.TestBase

class NameGeneratorSpec extends TestBase {

  "generateNodeName" should "work" in {
    val generator = NameGenerator("prefix")
    val (generator2, name) = generator.nodeName("foo")
    name shouldBe NodeName("prefix-foo", "foo")
    name.payloadProviderName shouldBe "prefix-foo-pp"

    val (generator3, name2) = generator2.nodeName("foo")
    name2 shouldBe name
    generator3 shouldBe generator2

    val (generator4, name3) = generator3.nodeName("fooÄ")
    val (generator5, name4) = generator4.nodeName("fooÖ")

    val (generator6, name5) = generator5.nodeName("bar")

    name3 shouldBe NodeName("prefix-foo0", "foo0")
    name4 shouldBe NodeName("prefix-foo1", "foo1")
    name5 shouldBe NodeName("prefix-bar", "bar")
    generator5.usedNames shouldBe Set("foo", "foo0", "foo1")
    generator6.mapping shouldBe Map(
      "foo" -> "foo",
      "fooÄ" -> "foo0",
      "fooÖ" -> "foo1",
      "bar" -> "bar"
    )
  }

  it should "not give out numerical internal names" in {
    val generator = NameGenerator("prefix")
    val (generator2, name) = generator.nodeName("1")
    name shouldBe NodeName("prefix-n1", "n1")
    name.payloadProviderName shouldBe "prefix-n1-pp"

    generator2.nodeName("1")._2.containerName shouldBe "prefix-n1"
    generator2.nodeName("n1")._2.containerName shouldBe "prefix-n10"
  }

  it should "not give out empty names" in {
    val generator = NameGenerator("prefix")
    val (generator2, name) = generator.nodeName("")
    name shouldBe NodeName("prefix-n0", "n0")
    val (generator3, name2) = generator2.nodeName("")
    name2 shouldBe NodeName("prefix-n0", "n0")
    val (_, name3) = generator3.nodeName("n0")
    name3 shouldBe NodeName("prefix-n00", "n00")
    generator3 shouldBe generator2
  }

  "generateRootName" should "fail with count < 0" in {
    intercept[IllegalArgumentException] {
      NameGenerator.generateRootName(-1)
    }
  }

  it should "give nice examples" in {
    NameGenerator.generateRootName(0) shouldBe NameGenerator.Prefix
    NameGenerator.generateRootName(1).size shouldBe NameGenerator.Prefix.length + 1
    NameGenerator.generateRootName(2).size shouldBe NameGenerator.Prefix.length + 2
  }

  val examples5 = for (i <- 0 until 100) yield NameGenerator.generateRootName(5)

  it should "be a little bit random" in {
    examples5.distinct.size shouldBe >=(80)
  }

  it should "not contain easy to misunderstood characters" in {
    examples5.foreach { c =>
      withClue(s"Checking ${c}") {
        c.intersect("Il0O") shouldBe empty
      }
    }
  }
}
