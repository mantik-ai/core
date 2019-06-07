package ai.mantik.repository.impl

import java.nio.file.{ Files, Path }

import ai.mantik.repository.Errors
import com.typesafe.config.ConfigFactory
import io.getquill.{ JdbcContextConfig, SnakeCase, SqliteJdbcContext }
import scala.collection.JavaConverters._

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
        throw new Errors.RepositoryError("Could not create directory", e)
    }
    val config = ConfigFactory.parseMap(
      Map(
        "driverClassName" -> "org.sqlite.JDBC",
        "jdbcUrl" -> s"jdbc:sqlite:${dbFile.toAbsolutePath.toString}"
      ).asJava
    )
    new SqliteJdbcContext(SnakeCase, new JdbcContextConfig(config))
  }

  /**
   * Run a SQL File in transaction.
   * The file is splitted, see limitations of [[splitSql]].
   */
  def runSqlInTransaction(sql: String): Unit = {
    val lines = splitSql(sql)
    context.transaction {
      lines.foreach { line =>
        context.executeAction(line)
      }
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
