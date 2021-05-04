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
package ai.mantik.ds.sql

import java.util.Locale

import ai.mantik.ds.DataType

/** A Single column inside a query. */
case class QueryColumn(
    name: String,
    alias: Option[String],
    dataType: DataType
) {
  private[sql] val lowerCaseName: String = name.toLowerCase(Locale.ENGLISH)

  /**
    * Check if this column matches a given lookup.
    * @param lookupAlias the alias to look, if empty any alias is taken
    * @param plainName the non-aliased name to look for. if lookupLowercases is true it must be lowercased to
    * @param lookupLowercased if true, lookup for a lowercased name
    */
  private[sql] def matches(lookupAlias: Option[String], plainName: String, lookupLowercased: Boolean): Boolean = {
    val nameMatch = if (lookupLowercased) {
      lowerCaseName == plainName
    } else {
      name == plainName
    }
    val aliasMatch = lookupAlias.isEmpty || (alias == lookupAlias)
    nameMatch && aliasMatch
  }
}

object QueryColumn {

  /** Creates a QueryColumn from a fullname (which can contain an alias). */
  def apply(fullName: String, dataType: DataType): QueryColumn = {
    val (alias, baseName) = fullName.indexOf(".") match {
      case -1 => (None, fullName)
      case n  => (Some(fullName.take(n)), fullName.drop(n + 1))
    }
    QueryColumn(baseName, alias, dataType)
  }
}
