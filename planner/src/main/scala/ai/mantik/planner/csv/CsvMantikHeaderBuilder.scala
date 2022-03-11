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
package ai.mantik.planner.csv

import ai.mantik.ds.TabularData
import ai.mantik.elements.meta.MetaJson
import ai.mantik.elements.{DataSetDefinition, MantikDefinition, MantikHeader, MantikHeaderMeta}
import ai.mantik.planner.BuiltInItems
import io.circe.syntax._

/** Responsible for building MantikHeaders for CSV Bridge */
case class CsvMantikHeaderBuilder(url: Option[String], dataType: TabularData, options: CsvOptions = CsvOptions()) {

  private def definition: DataSetDefinition = {
    DataSetDefinition(
      bridge = BuiltInItems.CsvBridgeName,
      `type` = dataType
    )
  }

  /** Convert to Header Format of CSV Bridge */
  def convert: MantikHeader[DataSetDefinition] = {
    val asJson = (definition: MantikDefinition).asJsonObject
      .add(
        "options",
        options.asJson
      )

    val maybeWithUrl = url
      .map { url =>
        asJson.add("url", url.asJson)
      }
      .getOrElse(asJson)

    MantikHeader(definition, MetaJson.withoutMetaVariables(maybeWithUrl), MantikHeaderMeta())
  }
}
