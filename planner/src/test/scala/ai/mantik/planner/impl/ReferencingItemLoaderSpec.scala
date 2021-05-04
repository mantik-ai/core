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
package ai.mantik.planner.impl

import ai.mantik.elements.errors.{ErrorCodes, MantikException}
import ai.mantik.testutils.{AkkaSupport, TestBase}

import scala.concurrent.Future

class ReferencingItemLoaderSpec extends TestBase with AkkaSupport {

  case class Item(name: String)

  def makeNet(x: (String, Seq[String])*): Map[String, Seq[String]] = x.toMap

  class SimpleLoader(map: Map[String, Seq[String]])
      extends ReferencingItemLoader[String, Item](
        loader = id =>
          map.get(id) match {
            case Some(i) => Future.successful(Item(id))
            case None    => Future.failed(ErrorCodes.MantikItemNotFound.toException(""))
          },
        dependencyExtractor = x => map.getOrElse(x.name, Nil)
      )

  it should "work for a empty example" in {
    val dependencies = makeNet(
      "a" -> Nil
    )
    val loader = new SimpleLoader(dependencies)
    await(loader.loadWithHull("a")) shouldBe Seq(Item("a"))
    awaitException[MantikException] {
      loader.loadWithHull("b")
    }.code.isA(ErrorCodes.MantikItemNotFound) shouldBe true
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
