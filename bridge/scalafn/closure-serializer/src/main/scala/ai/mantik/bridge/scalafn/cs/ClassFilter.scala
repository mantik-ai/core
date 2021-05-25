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
package ai.mantik.bridge.scalafn.cs

/** Filter for classes to be serialized. */
trait ClassFilter {
  def include(className: String): Boolean
}

object ClassFilter {

  def all: ClassFilter = new ClassFilter {
    override def include(className: String): Boolean = true
  }

  def default: ClassFilter = IgnorePrefixClassFilter(DefaultIgnorePrefixes)

  /** Default Prefixes to ignore. */
  val DefaultIgnorePrefixes = Seq(
    "java.",
    "jdk.",
    "sun.",
    "scala."
  )

  /** Simple Filter which just matches a list of ignored prefixes. */
  case class IgnorePrefixClassFilter(prefixes: Seq[String]) extends ClassFilter {
    def include(canonicalName: String): Boolean = {
      !prefixes.exists(p => canonicalName.startsWith(p))
    }
  }
}
