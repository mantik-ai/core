package ai.mantik.planner.utils.sqlite

import java.nio.file.Files

import ai.mantik.planner.repository.impl.MantikDbEvolutions
import ai.mantik.planner.util.TestBaseWithAkkaRuntime
import ai.mantik.testutils.TempDirSupport

import scala.concurrent.Future

/** Test database evolutions */
abstract class SqliteEvolutionsSpecBase extends TestBaseWithAkkaRuntime with TempDirSupport {

  protected def generateEvolutions(quill: QuillSqlite): SqliteEvolutions

  trait Env {
    val path = tempDirectory.resolve("test1.db")
    val db = new QuillSqlite(path)
    akkaRuntime.lifecycle.addShutdownHook(Future.successful(db.shutdown()))
    val dbEvolutions = generateEvolutions(db)
  }

  "ensureCurrentVersion" should "generate a fresh database if there is nothing" in new Env {
    dbEvolutions.dbVersion() shouldBe 0
    dbEvolutions.ensureCurrentVersion()
    dbEvolutions.dbVersion() shouldBe dbEvolutions.currentVersion
    withClue("this is transitive") {
      dbEvolutions.ensureCurrentVersion()
      dbEvolutions.dbVersion() shouldBe dbEvolutions.currentVersion
    }
  }

  "dbVersion" should "detect current version" in new Env {
    dbEvolutions.dbVersion() shouldBe 0
  }

  it should "work for a whole evolution cycle" in new Env {
    dbEvolutions.dbVersion() shouldBe 0
    dbEvolutions.applyMigrations(0)
    dbEvolutions.dbVersion() shouldBe MantikDbEvolutions.CurrentVersion
    withClue("It should be transitive again") {
      dbEvolutions.ensureCurrentVersion()
      dbEvolutions.dbVersion() shouldBe MantikDbEvolutions.CurrentVersion
    }
  }

  it should "lead to the same database schema" in new Env {
    dbEvolutions.ensureCurrentVersion()
    val directSchemas = extractDatabases(db)
    db.shutdown()
    Files.delete(path)
    val db2 = new QuillSqlite(path)
    val evolutions2 = generateEvolutions(db2)
    evolutions2.applyMigrations(0)
    val evolutionSchemas = extractDatabases(db2)
    db2.shutdown()
    ensureSameElements(
      directSchemas,
      evolutionSchemas
    )
  }

  private def extractDatabases(db: QuillSqlite): List[String] = {
    val values = db.context.executeQuery(
      "SELECT sql FROM sqlite_master WHERE type='table';",
      extractor = _.getString(1)
    )
    values.map(cleanupSchema)
  }

  private def cleanupSchema(schema: String): String = {
    // argh, sqlite formats them different if columns are appended ?!
    // it's also showing comments
    schema.linesIterator
      .map { line =>
        val trimmed = line.trim
        // drop optional comment
        trimmed.indexOf("--") match {
          case -1 => trimmed
          case n  => trimmed.take(n).trim
        }
      }
      .mkString(" ")
      .replace(" ,", ",")
      .replace(", ", ",")
      .replace(" )", ")")
      .replace("( ", "(")
  }
}
