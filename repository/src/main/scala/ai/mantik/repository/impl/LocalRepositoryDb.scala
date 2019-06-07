package ai.mantik.repository.impl

import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Path }
import java.util.UUID

import org.apache.commons.io.IOUtils

/**
 * Contains the Database Adapter for the local Repository.
 */
private[impl] class LocalRepositoryDb(dbFile: Path) {
  import LocalRepositoryDb.DbMantikArtifact

  val quill = new QuillSqlite(dbFile)

  /** SQL File for creating DB Schema. */
  private val dbSchema = IOUtils.resourceToString("/ai/mantik/repository/local_repo_schema.sql", StandardCharsets.UTF_8)

  /** Directly apply schema. The schema may not change the layout if already present. */
  quill.runSqlInTransaction(dbSchema)

  import quill.context._

  /** Quill Query Schema for accessing artifacts. */
  val artifacts = quote {
    querySchema[DbMantikArtifact](
      "mantik_artifact",
      _.id -> "id",
      _.name -> "name",
      _.version -> "version",
      _.mantikfile -> "mantikfile"
    )
  }

  /** Shutdown DB Access. */
  def shutdown(): Unit = {
    quill.shutdown()
  }
}

private[impl] object LocalRepositoryDb {
  // Item stored in the database
  case class DbMantikArtifact(
      id: UUID = UUID.randomUUID(),
      name: String,
      version: String,
      mantikfile: String,
      fileId: Option[String]
  )

}
