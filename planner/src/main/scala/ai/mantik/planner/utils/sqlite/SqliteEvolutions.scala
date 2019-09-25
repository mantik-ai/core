package ai.mantik.planner.utils.sqlite

import java.nio.charset.StandardCharsets

import com.typesafe.scalalogging.Logger
import org.apache.commons.io.IOUtils

/**
 * Manages evolutions in sqlite.
 *
 * The current version is stored as "user_version", see https://stackoverflow.com/questions/989558/best-practices-for-in-app-database-migration-for-sqlite
 */
abstract class SqliteEvolutions(
    quillSqlite: QuillSqlite
) {
  /** the resource file which contains the whole database setup */
  protected val completeResource: String

  /** the resource file which contains migrations (01.sql, 02.sql, ...) */
  protected val evolutionResources: String

  /**
   * current DB Layout version
   */
  val currentVersion: Int

  /**
   * a function which detects the current state if it can't be detected
   * (can be used if migrations were introduced later).
   * If it returns <=0 there is no database available and the current layout is instantiated.
   */
  protected def freshDetector(): Int

  /**
   * POST-Steps done after each migration.
   * Write them carefully, they are very hard to unit-test.
   * Test them manually!
   */
  protected def postMigration(version: Int): Unit

  private val logger = Logger(getClass)

  private lazy val completeResourceContent = IOUtils.resourceToString(completeResource, StandardCharsets.UTF_8)

  /** Generate the database schema for the current version */
  def ensureCurrentVersion(): Unit = {
    import quillSqlite.context._
    transaction {
      val detected = dbVersion()
      if (detected <= 0) {
        logger.info("Generating Fresh Database")
        quillSqlite.runSql(completeResourceContent)
        storeVersion(currentVersion)
      } else {
        applyMigrations(detected)
      }
    }
  }

  /** Returns the version currently in the database. */
  def dbVersion(): Int = {
    val stored = detectStoredVersion()
    if (stored > 0) {
      stored
    } else {
      freshDetector()
    }
  }

  private def detectStoredVersion(): Int = {
    quillSqlite.context.executeQuerySingle[Int](
      "pragma user_version", extractor = row => row.getLong(1).toInt
    )
  }

  private def storeVersion(v: Int): Unit = {
    val query = s"PRAGMA user_version = ${v}"
    quillSqlite.runSql(query)
  }

  /** Apply Migrations. */
  private[sqlite] def applyMigrations(detected: Int): Unit = {
    if (detected == currentVersion) {
      logger.debug("Database version seems current version, all fine")
      return
    }
    if (detected > currentVersion) {
      logger.error("Database version is newer than current version")
      throw new IllegalStateException("Database version is newer than shipped version")
    }
    logger.info(s"Applying Migrations: detected: ${detected} current: ${currentVersion}")
    for {
      targetVersion <- detected + 1 to currentVersion
    } {
      logger.debug(s"Applying version ${targetVersion}")
      val migrationFile = loadMigrationFile(targetVersion)
      quillSqlite.runSql(migrationFile)

      postMigration(targetVersion)
      storeVersion(targetVersion)
    }
  }

  private def loadMigrationFile(version: Int): String = {
    // The user may supply 5.sql, 05.sql or 005.sql for the migration '5'
    // let's don't be picky.
    val candidates = Seq(
      version.toString, "%02d".format(version), "%03d".format(version)
    ).map { number =>
        evolutionResources + "/" + number + ".sql"
      }
    val resource = candidates.find { resourceName =>
      getClass.getResource(resourceName) != null
    }.getOrElse {
      throw new IllegalStateException(s"Could not find migration ${version}, tried ${candidates}")
    }
    IOUtils.resourceToString(resource, StandardCharsets.UTF_8)
  }

}
