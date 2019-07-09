package ai.mantik.planner.repository.impl

import java.io.File
import java.nio.file.Files
import java.sql.Timestamp

import ai.mantik.elements.{ ItemId, MantikId, Mantikfile }
import ai.mantik.planner.repository.{ DeploymentInfo, Errors, MantikArtifact, Repository }
import ai.mantik.planner.repository.impl.LocalRepositoryDb._
import ai.mantik.planner.utils.{ AkkaRuntime, ComponentBase }

import scala.concurrent.{ ExecutionContext, Future }
import io.circe.parser
import org.apache.commons.io.FileUtils
import org.slf4j.LoggerFactory
import org.sqlite.{ SQLiteErrorCode, SQLiteException }

/** A local repository for artifacts based upon Sqlite. */
class LocalRepository(implicit akkaRuntime: AkkaRuntime) extends ComponentBase with Repository {

  private val logger = LoggerFactory.getLogger(getClass)

  // Note: in general we are using the plain ExecutionContext here
  // Which should be ok for local usage (but not servers).
  // On servers it's not advisable to use Sqlite anyway.

  val directory = new File(
    config.getString(LocalRepository.DirectoryConfigKey)
  ).toPath
  val dbFile = directory.resolve("items.db")
  logger.info(s"Initializing in ${dbFile}")

  val db = new LocalRepositoryDb(dbFile)

  import db.quill.context._

  override def get(id: MantikId): Future[MantikArtifact] = {
    Future {
      val items = run(getByIdQuery(id))

      items.headOption match {
        case None => throw new Errors.NotFoundException(s"Artifact with id ${id} not found")
        case Some((name, item, depl)) =>
          decodeDbArtifact(name, item, depl)
      }
    }
  }

  private def getByIdQuery(id: MantikId) = {
    quote {
      for {
        name <- db.names.filter { n => n.name == lift(id.name) && n.version == lift(id.version) }
        item <- db.items.join(_.itemId == name.currentItemId)
        depl <- db.deployments.leftJoin(_.itemId == item.itemId)
      } yield (name, item, depl)
    }
  }

  override def store(mantikArtefact: MantikArtifact): Future[Unit] = {
    Future {
      val converted = encodeDbArtefact(mantikArtefact)
      val mantikId = mantikArtefact.id

      // This could be improved by using an upsert.
      transaction {

        // Let it crash if already existant.
        try {
          run(
            db.items.insert(lift(converted))
          )
        } catch {
          case s: SQLiteException if s.getResultCode == SQLiteErrorCode.SQLITE_CONSTRAINT_PRIMARYKEY =>
            throw new Errors.OverwriteNotAllowedException("Items may not be overwritten with the same itemId")
        }

        val exists = run {
          db.names.filter { e => e.name == lift(mantikId.name) && e.version == lift(mantikId.version) }.map(_.id)
        }.headOption

        exists match {
          case Some(id) =>
            // already exists, overwrite current item id.
            run {
              db.names.filter(_.id == lift(id)).update(_.currentItemId -> lift(converted.itemId))
            }
          case None =>
            // doesn't exit, insert
            val convertedName = DbMantikName(
              name = mantikId.name,
              version = mantikId.version,
              currentItemId = converted.itemId
            )
            run {
              db.names.insert(lift(convertedName))
            }
        }
        updateDeploymentStateOp(mantikArtefact.itemId, mantikArtefact.deploymentInfo)
      }
    }
  }

  override def setDeploymentInfo(itemId: ItemId, info: Option[DeploymentInfo]): Future[Boolean] = {
    Future {
      transaction(updateDeploymentStateOp(itemId, info))
    }
  }

  private def updateDeploymentStateOp(itemId: ItemId, maybeDeployed: Option[DeploymentInfo]): Boolean = {
    maybeDeployed match {
      case None => run {
        db.deployments.filter(_.itemId == lift(itemId.toString)).delete
      } > 0
      case Some(info) =>
        val converted = DbDeploymentInfo(
          itemId = itemId.toString,
          name = info.name,
          url = info.url,
          timestamp = new Timestamp(info.timestamp.toEpochMilli)
        )

        // upsert hasn't worked (error [SQLITE_ERROR] SQL error or missing database (near "AS": syntax error) )

        val updateTrial = run(db.deployments.filter(_.itemId == lift(converted.itemId)).update(lift(converted))) > 0
        if (updateTrial) {
          // ok
          updateTrial
        } else {
          try {
            run(db.deployments.insert(lift(converted))) > 0
          } catch {
            case e: SQLiteException if e.getResultCode == SQLiteErrorCode.SQLITE_CONSTRAINT_FOREIGNKEY =>
              // entry not present.
              false
          }

        }

      /*
        // upsert hasn't worked (error [SQLITE_ERROR] SQL error or missing database (near "AS": syntax error) )
        run {
          db.deployments.insert(lift(converted)).onConflictUpdate(_.itemId)((e,t) => e -> t)
        }.toInt
         */
    }
  }

  override def remove(id: MantikId): Future[Boolean] = {
    Future {
      run {
        // note: the item itself is not deleted, but dereferenced.
        // it can still be referenced by other things (e.g. deployments)
        db.names.filter { n =>
          n.name == lift(id.name) && n.version == lift(id.version)
        }.delete
      } > 0
    }
  }

  override def shutdown(): Unit = {
    super.shutdown()
    db.shutdown()
  }

  /**
   * Convert a DB Artifact to a artifact
   * @throws Errors.RepositoryError on illegal mantikfiles.
   */
  private def decodeDbArtifact(name: DbMantikName, item: DbMantikItem, maybeDeployed: Option[DbDeploymentInfo]): MantikArtifact = {
    val mantikfile = (for {
      json <- parser.parse(item.mantikfile)
      mf <- Mantikfile.parseSingleDefinition(json)
    } yield {
      mf
    }) match {
      case Left(error) =>
        logger.error(s"Could not parse stored mantikfile of ${name.id}, code: ${item.mantikfile}", error)
        throw new Errors.RepositoryError("Could not parse Mantikfile", error)
      case Right(ok) => ok
    }
    MantikArtifact(
      mantikfile = mantikfile,
      id = MantikId(
        name = name.name,
        version = name.version
      ),
      fileId = item.fileId,
      itemId = ItemId(item.itemId),
      deploymentInfo = maybeDeployed.map { depl =>
        DeploymentInfo(
          name = depl.name,
          url = depl.url,
          timestamp = depl.timestamp.toInstant
        )
      }
    )
  }

  /**
   * Encode a DB Artefact.
   * Each Db Artefacts have different Ids, so it can come to collisions.
   */
  private def encodeDbArtefact(a: MantikArtifact): DbMantikItem = {
    DbMantikItem(
      mantikfile = a.mantikfile.toJson,
      fileId = a.fileId,
      itemId = a.itemId.toString
    )
  }
}

object LocalRepository {

  val DirectoryConfigKey = "mantik.repository.artifactRepository.local.directory"

  def createTemporary()(implicit akkaRuntime: AkkaRuntime): LocalRepository = {
    val tempDir = Files.createTempDirectory("mantik_db")
    val derivedRuntime: AkkaRuntime = akkaRuntime.withConfigOverrides(
      DirectoryConfigKey -> tempDir.toString
    )
    new LocalRepository()(derivedRuntime) {
      override def shutdown(): Unit = {
        super.shutdown()
        FileUtils.deleteDirectory(directory.toFile)
      }
    }
  }
}