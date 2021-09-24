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

import ai.mantik.ds.Errors.FeatureNotSupported
import ai.mantik.ds.{DataType, Nullable, TabularData}

import scala.collection.{BitSet, mutable}

/**
  * Resulting type of a query.
  * Similar to [[ai.mantik.ds.TabularData]] but with support for alias and duplicates
  */
case class QueryTabularType(
    columns: Vector[QueryColumn]
) {

  /**
    * Lookup a column.
    * @param name name may use column alias with dot.
    * @return index and column instance.
    */
  def lookupColumn(
      name: String,
      caseSensitive: Boolean = false,
      fromLeft: Boolean = true
  ): Either[String, (Int, QueryColumn)] = {
    val (alias, plainName) = name.indexOf('.') match {
      case -1 => (None, name)
      case n  => (Some(name.take(n)), name.drop(n + 1))
    }
    val plainNameLc = if (!caseSensitive) {
      plainName.toLowerCase(Locale.ENGLISH)
    } else {
      plainName
    }
    val zipped = columns.view.zipWithIndex.toIndexedSeq
    val collector: PartialFunction[(QueryColumn, Int), (Int, QueryColumn)] = {
      case (column, id) if column.matches(alias, plainNameLc, !caseSensitive) => (id -> column)
    }
    val maybeResult = if (fromLeft) {
      zipped.collectFirst(collector)
    } else {
      zipped.reverse.collectFirst(collector)
    }
    maybeResult match {
      case Some(ok) => Right(ok)
      case None     => Left(s"Column ${name} not found (caseSensitive=${caseSensitive})")
    }
  }

  /** Returns the number of columns. */
  def size: Int = columns.size

  /** Append new columns */
  def ++(queryTabularType: QueryTabularType): QueryTabularType = {
    QueryTabularType(columns ++ queryTabularType.columns)
  }

  def dropByIds(ids: Int*): QueryTabularType = {
    val idSet = BitSet(ids: _*)
    val result = columns.view.zipWithIndex.collect {
      case (column, id) if !idSet.contains(id) => column
    }.toVector
    QueryTabularType(result)
  }

  /** Shadow name/alias duplicates */
  @throws[FeatureNotSupported]("If it can't find a new shadowed name")
  def shadow(fromLeft: Boolean = true, ignoreAlias: Boolean = false): QueryTabularType = {
    if (!fromLeft) {
      return QueryTabularType(
        QueryTabularType(columns.reverse)
          .shadow(true)
          .columns
          .reverse
      )
    }

    val builder = Vector.newBuilder[QueryColumn]
    val existingKeys = mutable.Set.empty[(String, Option[String])]

    val MaxTrials = 100

    def key(column: QueryColumn): (String, Option[String]) = {
      if (ignoreAlias) {
        (column.name, None)
      } else {
        (column.name, column.alias)
      }
    }

    columns.foreach { column =>
      val newName = if (existingKeys.contains(key(column))) {
        (0 until MaxTrials).map(column.name + _).find { x =>
          !existingKeys.contains(key(column.copy(name = x)))
        } match {
          case None =>
            throw new FeatureNotSupported(s"Could not find a shadow name for ${column.name}, tried ${MaxTrials}")
          case Some(ok) => ok
        }
      } else {
        column.name
      }
      val using = column.copy(name = newName)
      existingKeys += key(using)
      builder += using
    }
    QueryTabularType(builder.result())
  }

  /** Make all columns nullable, if they aren't yet */
  def makeNullable: QueryTabularType = {
    QueryTabularType(columns.map(c => c.copy(dataType = Nullable.makeNullable(c.dataType))))
  }

  /** Returns a copy with a new alias set. */
  def withAlias(alias: String): QueryTabularType = {
    QueryTabularType(columns.map { c => c.copy(alias = Some(alias)) })
  }

  /** Convert to a tabular data. This can fail on column duplicates */
  def toTabularType: Either[String, TabularData] = {
    val withoutAlias = columns.map { c => c.name -> c.dataType }.distinct
    if (withoutAlias.size < columns.size) {
      Left("Name duplicates found, cannot convert to tabular data")
    } else {
      Right(TabularData(withoutAlias: _*))
    }
  }

  /** Force conversion to tabular type, columns may be renamed. */
  def forceToTabularType: TabularData = {
    shadow(ignoreAlias = true).toTabularType match {
      case Left(error) =>
        // This should not happen
        throw new IllegalStateException(s"Could not create tabular type, even after shadowing: ${error}")
      case Right(ok) => ok
    }
  }

}

object QueryTabularType {

  /** Initialize from TabularData */
  def fromTabularData(tabularData: TabularData, alias: Option[String] = None): QueryTabularType = {
    val columns = tabularData.columns.map { case (name, dataType) =>
      QueryColumn(name = name, alias = alias, dataType = dataType)
    }.toVector
    QueryTabularType(columns)
  }

  /**
    * Convenience Constructor from a list of columns.
    * the alias is automatically deduced if there is a dot in the column name.
    */
  def apply(columns: (String, DataType)*): QueryTabularType = {
    val transformed = columns.map { case (fullName, dataType) =>
      QueryColumn(fullName, dataType)
    }.toVector
    QueryTabularType(transformed)
  }
}
