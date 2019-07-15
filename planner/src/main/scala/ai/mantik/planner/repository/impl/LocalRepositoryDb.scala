package ai.mantik.planner.repository.impl

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.sql.Timestamp
import java.util.UUID

import org.apache.commons.io.IOUtils

/**
 * Contains the Database Adapter for the local Repository.
 */
private[impl] class LocalRepositoryDb(dbFile: Path) {
  import LocalRepositoryDb._

  val quill = new QuillSqlite(dbFile)

  /** SQL File for creating DB Schema. */
  private val dbSchema = IOUtils.resourceToString("/ai.mantik.planner.repository/local_repo_schema.sql", StandardCharsets.UTF_8)

  /** Directly apply schema. The schema may not change the layout if already present. */
  quill.runSqlInTransaction(dbSchema)

  import quill.context._

  val names = quote {
    querySchema[DbMantikName](
      "mantik_name",
      _.id -> "id",
      _.name -> "name",
      _.version -> "version",
      _.currentItemId -> "current_item_id"
    )
  }

  val deployments = quote {
    querySchema[DbDeploymentInfo](
      ("mantik_deployment_info"),
      _.itemId -> "item_id",
      _.name -> "name",
      _.internalUrl -> "internal_url",
      _.externalUrl -> "external_url",
      _.timestamp -> "timestamp"
    )
  }

  /** Quill Query Schema for accessing artifacts. */
  val items = quote {
    querySchema[DbMantikItem](
      "mantik_item",
      _.itemId -> "item_id",
      _.mantikfile -> "mantikfile",
      _.fileId -> "file_id"
    )
  }

  /** Shutdown DB Access. */
  def shutdown(): Unit = {
    quill.shutdown()
  }
}

private[impl] object LocalRepositoryDb {

  case class DbMantikName(
      id: UUID = UUID.randomUUID(),
      name: String,
      version: String,
      currentItemId: String
  )

  // Item stored in the database
  case class DbMantikItem(
      itemId: String,
      mantikfile: String,
      fileId: Option[String]
  )

  case class DbDeploymentInfo(
      itemId: String,
      name: String,
      internalUrl: String,
      externalUrl: Option[String],
      timestamp: java.util.Date
  )
}
