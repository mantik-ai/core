package ai.mantik.planner.utils.sqlite

import java.nio.file.{ Files, Path }
import java.sql.Connection
import java.util.Properties

import ai.mantik.elements.errors.ErrorCodes
import com.zaxxer.hikari.{ HikariConfig, HikariDataSource }
import io.getquill.{ SnakeCase, SqliteJdbcContext }

/**
 * Prepares Sqlite Access via Quill.
 * For documentation see https://getquill.io/
 * Naming Strategy is fixed to [[io.getquill.CamelCase]]
 */
class QuillSqlite(dbFile: Path) {
  import QuillSqlite.QuillContext

  /** The Database Context. */
  val context: QuillContext = createContext()

  /** Closes the context, releasing resources. */
  def shutdown(): Unit = {
    context.close()
  }

  private def createContext(): QuillContext = {
    val parentDirectory = Option(dbFile.getParent)
    try {
      parentDirectory.foreach(Files.createDirectories(_))
    } catch {
      case e: Exception =>
        ErrorCodes.InternalError.throwIt("Could not create directory", e)
    }
    val dataSource = createDataSource()
    new SqliteJdbcContext(SnakeCase, dataSource)
  }

  private def createDataSource(): HikariDataSource = {
    // Building Data source by Hand, because [[JdbcContextConfig]]
    // has no way of enabling foreign_keys.
    val defaults = new Properties()
    defaults.setProperty("driverClassName", "org.sqlite.JDBC")
    defaults.setProperty("jdbcUrl", s"jdbc:sqlite:${dbFile.toAbsolutePath.toString}")
    val hikariConfig = new HikariConfig(defaults)

    hikariConfig.addDataSourceProperty(
      "foreign_keys", "true"
    )
    val hikariDataSource = new HikariDataSource(hikariConfig)
    hikariDataSource
  }

  /**
   * Run a SQL File in transaction.
   * The file is splitted, see limitations of [[splitSql]].
   */
  def runSqlInTransaction(sql: String): Unit = {
    context.transaction {
      runSql(sql)
    }
  }

  def runSql(sql: String): Unit = {
    val lines = splitSql(sql)
    lines.foreach { line =>
      // despite it's name this executes this SQL line
      context.probe(line)
    }
  }

  /**
   * Split a SQL File into single actions. Not very robust.
   * The file may not contain ";" in text fields.
   */
  def splitSql(sql: String): Seq[String] = {

    val withoutComments = sql
      .split("\n")
      .filterNot(_.trim.startsWith("--"))
      .filterNot(_.trim.isEmpty)
      .mkString("\n")

    withoutComments.split(";")
      .map(_.trim)
      .filterNot(_.isEmpty) // no empty lines
      .map(_.stripSuffix(";")) // strip trailing ;
  }
}

object QuillSqlite {
  /** Quill Context Type. */
  type QuillContext = SqliteJdbcContext[SnakeCase.type]
}
