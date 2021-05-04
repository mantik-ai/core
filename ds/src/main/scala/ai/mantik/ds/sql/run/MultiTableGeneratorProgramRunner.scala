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
package ai.mantik.ds.sql.run

import ai.mantik.ds.element.{TabularBundle, TabularRow}
import ai.mantik.ds.sql.run.MultiTableGeneratorProgramRunner.MultiQueryRunner
import scala.collection.JavaConverters._

/** A Runner for [[MultiTableGeneratorProgram]] */
class MultiTableGeneratorProgramRunner(multiTableGeneratorProgram: MultiTableGeneratorProgram) {

  /** Id of the maximum input source */
  val maxInputSourceId: Int = multiTableGeneratorProgram.maxInputSource

  val multiQueryRunner = makeMultiQueryRunner(multiTableGeneratorProgram)

  @throws[IllegalArgumentException]("On illegal input size")
  def run(input: Vector[TabularBundle]): Vector[TabularBundle] = {
    require(input.size > maxInputSourceId, s"Expected at least ${maxInputSourceId + 1} elements")
    val rowsVectors = input.map(_.rows)

    val results = multiQueryRunner(rowsVectors)
      .zip(multiTableGeneratorProgram.allResults)
      .map { case (rowIterator, tabularType) =>
        TabularBundle(tabularType, rowIterator.toVector)
      }

    results
  }

  private def makeMultiQueryRunner(multiTableGeneratorProgram: MultiTableGeneratorProgram): MultiQueryRunner = {
    multiTableGeneratorProgram match {
      case s: SplitProgram =>
        makeSplitRunner(s)
    }
  }

  private def makeSplitRunner(s: SplitProgram): MultiQueryRunner = {
    val subRunner = new SingleTableGeneratorProgramRunner(s.input)
    inputs => {
      val rows = subRunner.queryRunner(inputs)

      val collector = scala.collection.mutable.ArrayBuffer[TabularRow]()
      rows.foreach { r =>
        collector += r
      }

      s.shuffleSeed match {
        case Some(seed) =>
          val randomWithSeed = new java.util.Random(seed)
          java.util.Collections.shuffle(collector.asJava, randomWithSeed)
        case None =>
        // nothing
      }

      val elementCount = collector.size
      val borders: Vector[Int] = s.fractions
        .foldLeft(List(0)) { case (current, fraction) =>
          val last = current.head
          (last + (fraction * elementCount).toInt) :: current
        }
        .reverse
        .toVector :+ elementCount

      val iterators = borders.zip(borders.tail).map { case (startIndex, endIndex) =>
        new Iterator[TabularRow] {
          var current = startIndex

          override def hasNext: Boolean = {
            current < endIndex
          }

          override def next(): TabularRow = {
            val result = collector(current)
            current += 1
            result
          }
        }
      }

      iterators
    }
  }
}

object MultiTableGeneratorProgramRunner {
  import SingleTableGeneratorProgramRunner._
  type MultiQueryRunner = InputElements => Vector[RowIterator]
}
