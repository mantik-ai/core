package ai.mantik.planner.repository.impl

import java.io.File
import java.nio.file.{ Files, Path }
import java.sql.Timestamp

import ai.mantik.componently.{ AkkaRuntime, ComponentBase }
import ai.mantik.elements.{ ItemId, MantikId, Mantikfile, NamedMantikId }
import ai.mantik.planner.repository.impl.LocalRepository.DirectoryConfigKey
import ai.mantik.planner.repository.{ DeploymentInfo, Errors, MantikArtifact, Repository }
import ai.mantik.planner.repository.impl.LocalRepositoryDb._

import scala.concurrent.{ ExecutionContext, Future }
import io.circe.parser
import javax.inject.{ Inject, Singleton }
import org.apache.commons.io.FileUtils
import org.sqlite.{ SQLiteErrorCode, SQLiteException }

/** A local repository for artifacts based upon Sqlite. */
@Singleton
class LocalRepository(val directory: Path)(implicit akkaRuntime: AkkaRuntime) extends ComponentBase with Repository {

  @Inject
  def this()(implicit akkaRuntime: AkkaRuntime) {
    this(new File(
      akkaRuntime.config.getString(LocalRepository.DirectoryConfigKey)
    ).toPath)
  }

  // Note: in general we are using the plain ExecutionContext here
  // Which should be ok for local usage (but not servers).
  // On servers it's not advisable to use Sqlite anyway.

  val dbFile = directory.resolve("items.db")
  logger.info(s"Initializing in ${dbFile}")

  val db = new LocalRepositoryDb(dbFile)

  import db.quill.context._

  case class DbArtifact(
      name: Option[DbMantikName],
      item: DbMantikItem,
      depl: Option[DbDeploymentInfo]
  )

  override def get(id: MantikId): Future[MantikArtifact] = {
    Future {
      val maybeItem = id match {
        case i: ItemId        => getByItemId(i)
        case n: NamedMantikId => getByName(n)
      }

      maybeItem match {
        case None => throw new Errors.NotFoundException(s"Artifact with id ${id} not found")
        case Some(item) =>
          decodeDbArtifact(item)
      }
    }
  }

  private def getByName(id: NamedMantikId): Option[DbArtifact] = {
    run {
      for {
        name <- db.names.filter { n => n.name == lift(id.name) && n.version == lift(id.version) }
        item <- db.items.join(_.itemId == name.currentItemId)
        depl <- db.deployments.leftJoin(_.itemId == item.itemId)
      } yield (name, item, depl)
    }.headOption.map {
      case (name, item, depl) =>
        DbArtifact(Some(name), item, depl)
    }
  }

  private def getByItemId(id: ItemId): Option[DbArtifact] = {
    run {
      for {
        item <- db.items.filter(_.itemId == lift(id.toString))
        depl <- db.deployments.leftJoin(_.itemId == item.itemId)
      } yield (item, depl)
    }.headOption.map {
      case (item, depl) =>
        DbArtifact(None, item, depl)
    }
  }

  override def store(mantikArtifact: MantikArtifact): Future[Unit] = {
    Future {
      val converted = encodeDbArtifact(mantikArtifact)
      val namedId = mantikArtifact.namedId

      // This could be improved by using an upsert.
      transaction {

        // Let it crash if already existant.
        try {
          run(
            db.items.insert(lift(converted))
          )
        } catch {
          case s: SQLiteException if s.getResultCode == SQLiteErrorCode.SQLITE_CONSTRAINT_PRIMARYKEY =>
            throw new Errors.ConflictException("Items may not be overwritten with the same itemId")
        }

        namedId.foreach { mantikId =>
          tagItemExec(mantikArtifact.itemId, mantikId)
        }

        updateDeploymentStateOp(mantikArtifact.itemId, mantikArtifact.deploymentInfo)
      }
    }
  }

  /**
   * Forces mantikId to point to itemId.
   * @return true if something changed.
   */
  private def tagItemExec(itemId: ItemId, mantikId: NamedMantikId): Boolean = {
    val nameElement = DbMantikName(
      account = mantikId.account,
      name = mantikId.name,
      version = mantikId.version,
      currentItemId = itemId.toString
    )

    val exists = run {
      db.names.filter { e =>
        e.account == lift(mantikId.account) &&
          e.name == lift(mantikId.name) &&
          e.version == lift(mantikId.version)
      }.map(x => (x.id, x.currentItemId))
    }.headOption

    exists match {
      case Some((_, itemId)) if itemId == nameElement.currentItemId =>
        // No change
        false
      case Some((id, _)) =>
        // Current Item points to another item
        // drop it and rewrite it
        run(db.names.filter(_.id == lift(id)).delete)
        run(db.names.insert(lift(nameElement)))
        true
      case None =>
        run {
          db.names.insert(lift(nameElement))
        }
        true
    }
  }

  override def ensureMantikId(id: ItemId, mantikId: NamedMantikId): Future[Boolean] = {
    Future {
      transaction {
        tagItemExec(id, mantikId)
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
          internalUrl = info.internalUrl,
          externalUrl = info.externalUrl,
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
    }
  }

  override def remove(id: MantikId): Future[Boolean] = {
    Future {
      id match {
        case named: NamedMantikId =>
          // note: the item itself is not deleted, but dereferenced.
          // it can still be referenced by other things (e.g. deployments)
          run {
            db.names.filter { n =>
              n.account == lift(named.account) && n.name == lift(named.name) && n.version == lift(named.version)
            }.delete
          } > 0
        case i: ItemId =>
          // note: this will fail, if it's still referenced by names / deployments
          transaction {
            val existingNames = run {
              db.names.filter(_.currentItemId == lift(i.toString))
            }
            if (existingNames.nonEmpty) {
              throw new Errors.ConflictException("There are existing names for this item")
            }
            val existingDeployments = run {
              db.deployments.filter(_.itemId == lift(i.toString))
            }
            if (existingDeployments.nonEmpty) {
              throw new Errors.ConflictException("There are existing deployments for this item")
            }
            run {
              db.items.filter { n =>
                n.itemId == lift(i.toString)
              }.delete
            } > 0
          }
      }
    }
  }

  addShutdownHook {
    db.shutdown()
    Future.successful(())
  }

  /**
   * Convert a DB Artifact to a artifact
   * @throws Errors.RepositoryError on illegal mantikfiles.
   */
  private def decodeDbArtifact(dbArtifact: DbArtifact): MantikArtifact = {
    val mantikfile = (for {
      json <- parser.parse(dbArtifact.item.mantikfile)
      mf <- Mantikfile.parseSingleDefinition(json)
    } yield {
      mf
    }) match {
      case Left(error) =>
        logger.error(s"Could not parse stored mantikfile of ${dbArtifact.item.itemId}, code: ${dbArtifact.item.mantikfile}", error)
        throw new Errors.RepositoryError("Could not parse Mantikfile", error)
      case Right(ok) => ok
    }
    MantikArtifact(
      mantikfile = mantikfile,
      namedId = dbArtifact.name.map { name =>
        NamedMantikId(
          account = name.account,
          name = name.name,
          version = name.version
        )
      },
      fileId = dbArtifact.item.fileId,
      itemId = ItemId.fromString(dbArtifact.item.itemId),
      deploymentInfo = dbArtifact.depl.map { depl =>
        DeploymentInfo(
          name = depl.name,
          internalUrl = depl.internalUrl,
          externalUrl = depl.externalUrl,
          timestamp = depl.timestamp.toInstant
        )
      }
    )
  }

  /**
   * Encode a DB Artefact.
   * Each Db Artefacts have different Ids, so it can come to collisions.
   */
  private def encodeDbArtifact(a: MantikArtifact): DbMantikItem = {
    DbMantikItem(
      mantikfile = a.mantikfile.toJson,
      fileId = a.fileId,
      itemId = a.itemId.toString
    )
  }
}

@Singleton
class TempRepository @Inject() (implicit akkaRuntime: AkkaRuntime) extends LocalRepository(
  Files.createTempDirectory("mantik_db")
) {

  addShutdownHook {
    logger.debug(s"Deleting temp directory ${directory}")
    FileUtils.deleteDirectory(directory.toFile)
    Future.successful(())
  }
}

object LocalRepository {

  val DirectoryConfigKey = "mantik.repository.artifactRepository.local.directory"

}