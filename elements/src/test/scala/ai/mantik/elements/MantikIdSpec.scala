package ai.mantik.elements

import ai.mantik.testutils.TestBase
import io.circe.Json
import io.circe.syntax._

class MantikIdSpec extends TestBase {

  it should "have support for anonymous" in {
    val notAnonym = MantikId("foo", "bar")
    notAnonym.isAnonymous shouldBe false
    val anonym = ItemId.generate().asAnonymousMantikId
    anonym.isAnonymous shouldBe true
    anonym.version shouldBe MantikId.DefaultVersion
    anonym.name should startWith(MantikId.AnonymousPrefix)
    MantikId.fromString(anonym.toString) shouldBe anonym
  }

  val validNames = Seq(
    "abcd", "abcd-foo", "abcd1234", "abcd_hole", "foo.bar", "@T5wDUM0YqqfzyXCMc--E0ahv-omYHIpslfmK_rtDzlQ"
  )

  val invalidNames = Seq(
    "", "Invalid", "  invalid", "invalid ", "-invalid", "invalid-", "abcd/foo", "bob:bar"
  )

  "validateName" should "work" in {
    validNames.foreach { validName =>
      withClue(validName + " should be detected as being valid") {
        MantikId.nameViolations(validName) shouldBe empty
      }
    }
    invalidNames.foreach { invalidName =>
      withClue(invalidName + " should be detected as being invalid") {
        MantikId.nameViolations(invalidName) shouldBe Seq("Invalid Name")
      }
    }
    val item = ItemId.generate()
    val mantikItem = MantikId.anonymous(item)
    withClue(s"Also anonymous items like ${item} -> ${mantikItem} should be acceptable") {
      mantikItem.violations shouldBe empty
    }
  }

  val validVersions = Seq(
    "head", "1.2", "2", "1-foo", MantikId.DefaultVersion
  )

  val invalidVersions = Seq(
    "HEAD", " 1.2", "1.2 ", "-2", "2-", ".", ".2", "2."
  )

  "validateVersion" should "work" in {
    validVersions.foreach { version =>
      withClue(version + " should be detected as being valid") {
        MantikId.versionViolations(version) shouldBe empty
      }
    }
    invalidVersions.foreach { version =>
      withClue(version + " should be detected as being invalid") {
        MantikId.versionViolations(version) shouldBe Seq("Invalid Version")
      }
    }
  }

  "violations" should "check multiple violations" in {
    val bad = MantikId(
      name = "Foo Bar",
      version = "BIG",
      account = "bad Account"
    )
    bad.violations should contain theSameElementsAs Seq(
      "Invalid Version",
      "Invalid Name",
      "Invalid Account"
    )
  }

  val examples = Seq(
    MantikId(name = "foo") -> "foo",
    MantikId(account = "nob", name = "foo") -> "nob/foo",
    MantikId(account = "nob", name = "foo", version = "v1") -> "nob/foo:v1",
    MantikId(name = "foo", version = "v1") -> "foo:v1"
  )

  "toString/fromString" should "decode various elements" in {
    examples.foreach {
      case (example, serialized) =>
        example.violations shouldBe empty
        example.toString shouldBe serialized
        MantikId.fromString(serialized) shouldBe example
    }
  }

  "JSON Encoding" should "work" in {
    examples.foreach {
      case (example, serialized) =>
        example.asJson shouldBe Json.fromString(serialized)
        Json.fromString(serialized).as[MantikId] shouldBe Right(example)
    }
  }

  "auto conversion" should "work" in {
    val a: MantikId = "user/foo:bar" // auto converted by implicit
    val b: MantikId = MantikId("user/foo:bar")
    val c: MantikId = MantikId(name = "foo", account = "user", version = "bar")
    a shouldBe c
    b shouldBe c
  }
}
