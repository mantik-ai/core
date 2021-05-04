/*
 * This file is part of the Mantik Project.
 * Copyright (c) 2020-2021 Mantik UG (Haftungsbeschränkt)
 * Authors: See AUTHORS file
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.
 *
 * Additionally, the following linking exception is granted:
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with other code, such other code is not for that reason
 * alone subject to any of the requirements of the GNU Affero GPL
 * version 3.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license.
 */
package ai.mantik.elements

import ai.mantik.testutils.TestBase
import io.circe.Json
import io.circe.syntax._

class MantikIdSpec extends TestBase {

  it should "have support for anonymous" in {
    val notAnonym = NamedMantikId("foo", "bar")
    MantikId.fromString(notAnonym.toString) shouldBe notAnonym

    val itemId = ItemId.generate()
    itemId.toString should startWith(ItemId.ItemIdPrefix)
    MantikId.fromString(itemId.toString) shouldBe itemId
  }

  val validNames = Seq(
    "abcd",
    "abcd-foo",
    "abcd1234",
    "abcd_hole",
    "foo.bar"
  )

  val invalidNames = Seq(
    "",
    "Invalid",
    "  invalid",
    "invalid ",
    "-invalid",
    "invalid-",
    "abcd/foo",
    "bob:bar"
  )

  "validateName" should "work" in {
    validNames.foreach { validName =>
      withClue(validName + " should be detected as being valid") {
        NamedMantikId.nameViolations(validName) shouldBe empty
      }
    }
    invalidNames.foreach { invalidName =>
      withClue(invalidName + " should be detected as being invalid") {
        NamedMantikId.nameViolations(invalidName) shouldBe Seq("Invalid Name")
      }
    }
  }

  val validVersions = Seq(
    "head",
    "1.2",
    "2",
    "1-foo",
    NamedMantikId.DefaultVersion
  )

  val invalidVersions = Seq(
    "HEAD",
    " 1.2",
    "1.2 ",
    "-2",
    "2-",
    ".",
    ".2",
    "2."
  )

  "validateVersion" should "work" in {
    validVersions.foreach { version =>
      withClue(version + " should be detected as being valid") {
        NamedMantikId.versionViolations(version) shouldBe empty
      }
    }
    invalidVersions.foreach { version =>
      withClue(version + " should be detected as being invalid") {
        NamedMantikId.versionViolations(version) shouldBe Seq("Invalid Version")
      }
    }
  }

  "violations" should "check multiple violations" in {
    val bad = NamedMantikId(
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
    NamedMantikId(name = "foo") -> "foo",
    NamedMantikId(account = "nob", name = "foo") -> "nob/foo",
    NamedMantikId(account = "nob", name = "foo", version = "v1") -> "nob/foo:v1",
    NamedMantikId(name = "foo", version = "v1") -> "foo:v1"
  )

  "toString/fromString" should "decode various elements" in {
    examples.foreach { case (example, serialized) =>
      example.violations shouldBe empty
      example.toString shouldBe serialized
      NamedMantikId.fromString(serialized) shouldBe example
    }
  }

  "JSON Encoding" should "work" in {
    examples.foreach { case (example, serialized) =>
      example.asJson shouldBe Json.fromString(serialized)
      Json.fromString(serialized).as[NamedMantikId] shouldBe Right(example)
    }
  }

  "auto conversion" should "work" in {
    val a: NamedMantikId = "user/foo:bar" // auto converted by implicit
    val b: NamedMantikId = NamedMantikId("user/foo:bar")
    val c: NamedMantikId = NamedMantikId(name = "foo", account = "user", version = "bar")
    a shouldBe c
    b shouldBe c
  }
}
