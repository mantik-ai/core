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
package ai.mantik.planner.select

import ai.mantik.ds.TabularData
import ai.mantik.ds.sql.run.{Compiler, TableGeneratorProgram}
import ai.mantik.ds.sql.{AnonymousInput, MultiQuery, Query, Select, SingleQuery, Union}
import ai.mantik.elements.meta.MetaJson
import ai.mantik.elements.{CombinerDefinition, MantikDefinition, MantikHeader, MantikHeaderMeta}
import ai.mantik.planner.BuiltInItems
import io.circe.Json
import io.circe.syntax._

/**
  * Converts a [[ai.mantik.ds.sql.run.TableGeneratorProgram]] to a [[ai.mantik.elements.MantikHeader]] for the select-Bridge.
  * @param program the compiled program
  * @param query human readable query
  */
case class SelectMantikHeaderBuilder(
    program: TableGeneratorProgram,
    inputs: Vector[TabularData],
    query: String
) {

  def definition: CombinerDefinition = {
    CombinerDefinition(
      bridge = BuiltInItems.SelectBridgeName,
      input = inputs,
      output = program.allResults
    )
  }

  def toMantikHeader: MantikHeader[CombinerDefinition] = {
    val defJson = (definition: MantikDefinition).asJsonObject
      .add(
        "program",
        program.asJson
      )
      .add(
        "query",
        Json.fromString(query)
      )
    MantikHeader(definition, MetaJson.withoutMetaVariables(defJson), MantikHeaderMeta())
  }
}

object SelectMantikHeaderBuilder {

  /**
    * Compile Query to a select mantikHeader.
    * @return either an error or a mantikHeader which can execute the selection.
    */
  def compileToMantikHeader(query: Query): Either[String, MantikHeader[CombinerDefinition]] = {
    compileToMantikHeader(SingleQuery(query))
  }

  /** Compile a Muil Query to a select header. */
  def compileToMantikHeader(multiQuery: MultiQuery): Either[String, MantikHeader[CombinerDefinition]] = {
    for {
      program <- Compiler.compile(multiQuery)
      inputs <- multiQuery.figureOutInputPorts
    } yield {
      SelectMantikHeaderBuilder(program, inputs, multiQuery.toStatement).toMantikHeader
    }
  }
}
