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
package ai.mantik.planner.repository.impl

import java.sql.SQLException

import ai.mantik.ds.helper.circe.CirceJson
import ai.mantik.elements.MantikHeader
import ai.mantik.planner.utils.sqlite.{QuillSqlite, SqliteEvolutions}
import io.getquill.Escape

/** Constants for Mantik Database Evolutions */
object MantikDbEvolutions {
  val CompleteResource = "/ai.mantik.planner.repository/local_repo_schema.sql"
  val EvolutionResources = "/ai.mantik.planner.repository/evolution"
  val CurrentVersion = 6
}

/** Mantiks Database Evolution. */
class MantikDbEvolutions(
    quillSqlite: QuillSqlite
) extends SqliteEvolutions(quillSqlite) {
  override protected val completeResource: String = MantikDbEvolutions.CompleteResource
  override protected val evolutionResources: String = MantikDbEvolutions.EvolutionResources

  override val currentVersion: Int = MantikDbEvolutions.CurrentVersion

  override protected def freshDetector(): Int = {
    // migrations are introduced after version1
    try {
      quillSqlite.context.executeQuery("SELECT COUNT(*) FROM mantik_item")
      // query suceeded, current version is 1
      1
    } catch {
      case _: SQLException => 0 // no database available yet
    }
  }

  override protected def postMigration(version: Int): Unit = {
    version match {
      case 2 => migration2()
      case _ => // nothing to do
    }
  }

  private def migration2(): Unit = {
    // items now have a kind field, which is calculated from the mantikFile (later renamed to mantikFile)
    import quillSqlite.context._

    // infix doesn't doesn't seem to work with two results
    val result = executeQuery(
      "SELECT item_id, mantikfile FROM mantik_item",
      extractor = row => (row.getString(1), row.getString(2))
    ).toIndexedSeq

    val itemIdWithKind: IndexedSeq[(String, String)] = result.map { case (itemId, mantikHeaderJson) =>
      val json = CirceJson.forceParseJson(mantikHeaderJson)
      val mantikHeader = MantikHeader.parseSingleDefinition(json).right.getOrElse {
        throw new IllegalStateException(s"Could not parse json ${json}")
      }
      itemId -> mantikHeader.definition.kind
    }

    itemIdWithKind.foreach { case (itemId, kind) =>
      quillSqlite.context.executeAction(
        "UPDATE mantik_item SET kind=? WHERE item_id=?",
        prepare = { row =>
          row.setString(1, kind)
          row.setString(2, itemId)
          List(kind, itemId) -> row
        }
      )
    }
  }
}
