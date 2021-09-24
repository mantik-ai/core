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
package ai.mantik.ds.helper

import ai.mantik.ds.helper.TableFormatter.Options

/**
  * Tools for rendering ASCII Tables.
  * TODO: Performance could be better, but should be fine for regular Human IO.
  *
  * Copyright 2018 Reactive Core GmbH, licensed as Public Domain.
  */
object TableFormatter {

  /** Renders an ASCII Table. */
  def format(headers: Seq[String], lines: Seq[Seq[String]], options: Options = Options()): String = {
    new TableFormatter(headers, lines, options).format()
  }

  case class Options(
      border: String = "|",
      middleSeparator: String = "|",
      headerSeparator: Char = '-',
      newLine: String = "\n"
  )
}

private class TableFormatter(headers: Seq[String], lines: Seq[Seq[String]], options: Options) {

  val columnCount = headers.length
  require(lines.forall(_.length == columnCount), "Column Count must match")

  lazy val columnLengths: Seq[Int] = lines.foldLeft(headers.map(_.length)) { (sum, line) =>
    sum.zip(line).map { case (a, b) =>
      Math.max(a, b.length)
    }
  }

  def format(): String = {
    if (headers.isEmpty && lines.isEmpty) {
      return ""
    }
    val output = new StringBuilder()

    val headerFormatted = headers
      .zip(columnLengths)
      .map { case (header, length) =>
        blow(header, length)
      }
      .mkString(options.border, options.middleSeparator, options.border)

    output ++= headerFormatted
    output ++= options.newLine

    // header separator
    val headerSeparatorLine = columnLengths
      .map { length =>
        options.headerSeparator.toString * length
      }
      .mkString(options.border, options.middleSeparator, options.border)
    output ++= headerSeparatorLine

    output ++= options.newLine
    lines.foreach { line =>
      val lineFormatted = line.zip(columnLengths).map { case (cell, length) =>
        blow(cell, length)
      }
      output ++= lineFormatted.mkString(options.border, options.middleSeparator, options.border)
      output ++= options.newLine
    }
    output.result()
  }

  private def blow(s: String, length: Int): String = {
    val missing = length - s.length
    if (missing > 0) {
      s + (" " * missing)
    } else {
      s
    }
  }

}
