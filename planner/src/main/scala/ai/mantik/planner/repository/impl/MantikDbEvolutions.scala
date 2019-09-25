package ai.mantik.planner.repository.impl

import java.sql.SQLException

import ai.mantik.ds.helper.circe.CirceJson
import ai.mantik.elements.Mantikfile
import ai.mantik.planner.utils.sqlite.{ QuillSqlite, SqliteEvolutions }
import io.getquill.Escape

class MantikDbEvolutions(
    quillSqlite: QuillSqlite
) extends SqliteEvolutions(quillSqlite) {
  override protected val completeResource: String = "/ai.mantik.planner.repository/local_repo_schema.sql"
  override protected val evolutionResources: String = "/ai.mantik.planner.repository/evolution"

  override val currentVersion: Int = 2

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
    // items now have a kind field, which is calculated from the mantikfile
    import quillSqlite.context._

    // infix doesn't doesn't seem to work with two results
    val result = executeQuery(
      "SELECT item_id, mantikfile FROM mantik_item", extractor = row =>
        (row.getString(1), row.getString(2))
    ).toIndexedSeq

    val itemIdWithKind: IndexedSeq[(String, String)] = result.map {
      case (itemId, mantikfileJson) =>
        val json = CirceJson.forceParseJson(mantikfileJson)
        val mantikfile = Mantikfile.parseSingleDefinition(json).right.getOrElse {
          throw new IllegalStateException(s"Could not patse json ${json}")
        }
        itemId -> mantikfile.definition.kind
    }

    itemIdWithKind.foreach {
      case (itemId, kind) =>
        quillSqlite.context.executeAction("UPDATE mantik_item SET kind=? WHERE item_id=?", prepare = { row =>
          row.setString(1, kind)
          row.setString(2, itemId)
          List(kind, itemId) -> row
        })
    }
  }
}
