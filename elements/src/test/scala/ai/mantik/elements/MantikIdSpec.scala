package ai.mantik.elements

import ai.mantik.testutils.TestBase

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
    withClue(s"Also anonymous items like ${item} -> ${mantikItem} should be acceptable"){
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
    )
    bad.violations should contain theSameElementsAs Seq(
      "Invalid Version",
      "Invalid Name"
    )
  }
}
