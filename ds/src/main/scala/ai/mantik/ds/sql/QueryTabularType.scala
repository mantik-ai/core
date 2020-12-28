package ai.mantik.ds.sql

import java.util.Locale

import ai.mantik.ds.Errors.FeatureNotSupported
import ai.mantik.ds.{ DataType, Nullable, TabularData }

import scala.collection.{ BitSet, mutable }

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
  def lookupColumn(name: String, caseSensitive: Boolean = false, fromLeft: Boolean = true): Either[String, (Int, QueryColumn)] = {
    val (alias, plainName) = name.indexOf('.') match {
      case -1 => (None, name)
      case n  => (Some(name.take(n)), name.drop(n + 1))
    }
    val plainNameLc = if (!caseSensitive) {
      plainName.toLowerCase(Locale.ENGLISH)
    } else {
      plainName
    }
    val zipped = columns.view.zipWithIndex
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
  def shadow(fromLeft: Boolean = true): QueryTabularType = {
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

    columns.foreach { column =>
      val newName = if (existingKeys.contains((column.name, column.alias))) {
        (0 until MaxTrials).map(column.name + _).find { x => !existingKeys.contains((x, column.alias)) } match {
          case None     => throw new FeatureNotSupported(s"Could not find a shadow name for ${column.name}, tried ${MaxTrials}")
          case Some(ok) => ok
        }
      } else {
        column.name
      }
      existingKeys += ((newName, column.alias))
      builder += column.copy(name = newName)
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
    shadow().toTabularType match {
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
    val columns = tabularData.columns.map {
      case (name, dataType) =>
        QueryColumn(name = name, alias = alias, dataType = dataType)
    }.toVector
    QueryTabularType(columns)
  }

  /**
   * Convenience Constructor from a list of columns.
   * the alias is automatically deduced if there is a dot in the column name.
   */
  def apply(columns: (String, DataType)*): QueryTabularType = {
    val transformed = columns.map {
      case (fullName, dataType) =>
        QueryColumn(fullName, dataType)
    }.toVector
    QueryTabularType(transformed)
  }
}
