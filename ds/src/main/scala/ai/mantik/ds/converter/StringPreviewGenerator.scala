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
package ai.mantik.ds.converter

import ai.mantik.ds._
import ai.mantik.ds.element._
import ai.mantik.ds.helper.TableFormatter

/**
  * Responsible for rendering a string preview of a value.
  * @param maxCellLength maximum length of cells.
  * @param maxRows maximum number of rows.
  */
case class StringPreviewGenerator(maxCellLength: Int = 64, maxRows: Int = 20) {

  /**
    * Render a Bundle.
    * @throws IllegalArgumentException if the bundle is not consistent.
    */
  def render(bundle: Bundle): String = {
    bundle.model match {
      case table: TabularData =>
        val rows = bundle.rows.collect { case r: TabularRow =>
          r
        }
        renderTable(table, rows)
      case dataType =>
        val singleElement = bundle.rows match {
          case IndexedSeq(SingleElement(element)) => element
          case other =>
            throw new IllegalArgumentException(s"Expected single element, got ${other.size} different elements")
        }
        renderSingleElement(dataType, singleElement)
    }
  }

  def renderSingleLine(bundle: Bundle): String = {
    bundle.model match {
      case table: TabularData =>
        // wrap in an embedded table
        val renderFunction = renderEmbeddedTable(table)
        val rows = bundle.rows.collect { case r: TabularRow => r }
        renderFunction(EmbeddedTabularElement(rows))
      case otherwise =>
        render(bundle) // already single line
    }
  }

  private def renderTable(table: TabularData, rows: Vector[TabularRow]): String = {
    val cellRenderers = table.columns.map { case (_, dataType) => locateCellRenderer(dataType) }
    val columnNames = table.columns.keys
    val renderedCells = rows.take(maxRows).map { row =>
      row.columns.zip(cellRenderers).map { case (cell, renderer) =>
        renderer(cell)
      }
    }
    val result = TableFormatter.format(columnNames.toSeq, renderedCells)
    if (rows.length > maxRows) {
      result + s" (${maxRows} of ${rows.length} Rows)\n"
    } else {
      result
    }
  }

  private def renderSingleElement(dataType: DataType, element: Element): String = {
    val renderer = locateCellRenderer(dataType)
    renderer(element)
  }

  private type Renderer = Element => String

  /** Returns a renderer for a cell */
  private def locateCellRenderer(dataType: DataType): Renderer = {
    dataType match {
      case f: FundamentalType => {
        case p: Primitive[_] => limitToCellLength(p.x.toString)
        case x               => throw new IllegalArgumentException(s"Expected primitive, got ${x.getClass.getSimpleName}")
      }
      case i: Image => {
        case _: ImageElement => limitToCellLength(i.toString)
        case x               => throw new IllegalArgumentException(s"Expected Image Element, got ${x.getClass.getSimpleName}")
      }
      case t: Tensor => {
        case e: TensorElement[_] => renderTensor(t, e) // limits by itself.
        case x                   => throw new IllegalArgumentException(s"Expected Tensor, got ${x.getClass.getSimpleName}")
      }
      case t: TabularData => {
        renderEmbeddedTable(t)
      }
      case n: Nullable => {
        renderNullable(n)
      }
      case a: ArrayT =>
        renderList(a)
      case s: Struct =>
        renderStruct(s)

    }
  }

  private def renderTensor(tensor: Tensor, tensorElement: TensorElement[_]): String = {
    val elementIterator = tensorElement.elements.view.map(_.toString).iterator
    limitToCellLength(renderTensorLike(tensor.shape.toList, elementIterator, maxCellLength))
  }

  /** Render a tensor, may render slightly to many elements. */
  private def renderTensorLike(shape: List[Int], elementIterator: Iterator[String], pendingLength: Int): String = {
    val result = StringBuilder.newBuilder
    var pending = pendingLength

    // Note: not tail recursive
    if (shape.length > 10) {
      // Avoid crash
      return s"Complex Tensor ${shape.mkString("[", ",", "]")}"
    }

    def continue(shape: List[Int]): Unit = {
      shape match {
        case List(n) =>
          var i = 0
          result.append("[")
          pending -= 1
          while (i < n) {
            if (pending <= 0) {
              return
            }
            val e = elementIterator.next()
            if (i > 0) {
              result.append(",")
              pending -= 1
            }
            result.append(e)
            pending -= e.size
            i += 1
          }
          result.append("]")
          pending -= 1
          return
        case Nil =>
          // 0-tensor
          val e = elementIterator.next()
          result.append(e)
        case head :: rest =>
          var i = 0
          result.append("[")
          pending -= 1
          if (pending < 0) {
            return
          }
          while (i < head) {
            if (i > 0) {
              result.append(",")
              pending -= 1
            }
            continue(rest)
            i += 1
          }
          result.append("]")
          pending -= 1
      }
    }
    continue(shape)
    result.toString()
  }

  private def renderEmbeddedTable(t: TabularData): Element => String = {
    val columnRenderers = t.columns.map { case (_, dataType) =>
      locateCellRenderer(dataType)
    }.toSeq

    {
      case t: EmbeddedTabularElement =>
        val shape = List(t.rows.length, columnRenderers.size)
        val values = t.rows.view.flatMap { row =>
          row.columns.view.zip(columnRenderers).map { case (cell, cellRenderer) =>
            cellRenderer(cell)
          }
        }.iterator
        limitToCellLength(renderTensorLike(shape, values, maxCellLength))
      case x =>
        throw new IllegalArgumentException(s"Expected embedded tabular element, got ${x.getClass.getSimpleName}")
    }
  }

  private def renderNullable(nullable: Nullable): Element => String = {
    val subRenderer = locateCellRenderer(nullable.`underlying`)

    {
      case NullElement    => "null"
      case SomeElement(e) => subRenderer(e)
      case x              => throw new IllegalArgumentException(s"Expected nullable, got ${x.getClass.getSimpleName}")
    }
  }

  private def renderList(list: ArrayT): Element => String = {
    val subRenderer = locateCellRenderer(list.underlying)

    {
      case ArrayElement(elements) =>
        elements.map(subRenderer).mkString("[", ",", "]")
      case x =>
        throw new IllegalArgumentException(s"Expected ListElement, got ${x.getClass.getSimpleName}")
    }
  }

  private def renderStruct(tuple: Struct): Element => String = {
    val columnRenderers = tuple.fields.map { case (_, dataType) =>
      locateCellRenderer(dataType)
    }.toSeq

    {
      case t: StructElement =>
        val result = t.elements
          .zip(tuple.fields)
          .zip(columnRenderers)
          .map { case ((value, field), subRenderer) =>
            field._1 + ":" + subRenderer(value)
          }
          .mkString(",")
        limitToCellLength(result)
      case x =>
        throw new IllegalArgumentException(s"Expected embedded tabular element, got ${x.getClass.getSimpleName}")
    }
  }

  private def limitToCellLength(s: String): String = {
    if (s.length > maxCellLength) {
      s.take(maxCellLength - 3) + "..."
    } else {
      s
    }
  }

}
