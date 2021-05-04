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
package ai.mantik.ds.sql.parser

import ai.mantik.ds.testutil.TestBase
import org.parboiled2.{Parser, Rule1}

abstract class ParserTestBase extends TestBase {
  type ParserImpl <: Parser
  protected def makeParser(s: String): ParserImpl

  def testEquality[T](rule: ParserImpl => Rule1[T], text: String, expected: T): Unit = {
    import Parser.DeliveryScheme.Either
    val parser = makeParser(text)
    // use __run instead of rule(parser).run()
    // see https://groups.google.com/forum/#!topic/parboiled-user/uwcy6MVZV5s
    val result = parser.__run(rule(parser))
    result match {
      case Left(error) =>
        fail(error.format(parser))
      case Right(value) =>
        if (value != expected) {
          fail(s"When parsing ${text}: ${value} is not equal to ${expected}")
        }
    }
  }
}
