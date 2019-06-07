package ai.mantik.repository.impl

import java.io.File

import ai.mantik.repository.impl.LocalRepositoryDb.DbMantikArtifact
import ai.mantik.repository.{ Errors, MantikArtifact, MantikId, Mantikfile, Repository }
import com.typesafe.config.Config

import scala.concurrent.{ ExecutionContext, Future }
import io.circe.parser
import org.slf4j.LoggerFactory

/** A local repository for artifacts based upon Sqlite. */
class LocalRepository(config: Config)(implicit ec: ExecutionContext) extends Repository {

  private val logger = LoggerFactory.getLogger(getClass)

  // Note: in general we are using the plain ExecutionContext here
  // Which should be ok for local usage (but not servers).
  // On servers it's not advisable to use Sqlite anyway.

  val directory = new File(
    config.getString("mantik.repository.artifactRepository.local.directory")
  ).toPath
  val dbFile = directory.resolve("items.db")
  logger.info(s"Initializing in ${dbFile}")

  val db = new LocalRepositoryDb(dbFile)

  import db.quill.context._

  override def get(id: MantikId): Future[MantikArtifact] = {
    Future {
      val items = run {
        db.artifacts.filter { a =>
          a.name == lift(id.name) && a.version == lift(id.version)
        }
      }
      items.headOption match {
        case None => throw new Errors.NotFoundException(s"Artifact with id ${id} not found")
        case Some(dbArtifact) =>
          decodeDbArtifact(dbArtifact)
      }
    }
  }

  override def store(mantikArtefact: MantikArtifact): Future[Unit] = {
    Future {
      val converted = encodeDbArtefact(mantikArtefact)
      // This could be improved by using an upsert.
      transaction {
        val exists = run {
          db.artifacts.filter { e => e.name == lift(converted.name) && e.version == lift(converted.version) }.map(_.id)
        }.headOption

        exists match {
          case Some(id) =>
            val updateValue = converted.copy(id = id)
            // already exists, overwrite
            run {
              db.artifacts.filter(_.id == lift(id)).update(lift(updateValue))
            }
          case None =>
            run {
              db.artifacts.insert(lift(converted))
            }
        }
      }
    }
  }

  override def remove(id: MantikId): Future[Boolean] = {
    Future {
      run {
        db.artifacts.filter { a =>
          a.name == lift(id.name) && a.version == lift(id.version)
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
  private def decodeDbArtifact(a: DbMantikArtifact): MantikArtifact = {
    val mantikfile = (for {
      json <- parser.parse(a.mantikfile)
      mf <- Mantikfile.parseSingleDefinition(json)
    } yield {
      mf
    }) match {
      case Left(error) =>
        logger.error(s"Could not parse stored mantikfile of ${a.id}, code: ${a.mantikfile}", error)
        throw new Errors.RepositoryError("Could not parse Mantikfile", error)
      case Right(ok) => ok
    }
    MantikArtifact(
      mantikfile = mantikfile,
      id = MantikId(
        name = a.name,
        version = a.version
      ),
      fileId = a.fileId
    )
  }

  /**
   * Encode a DB Artefact.
   * Each Db Artefacts have different Ids, so it can come to collisions.
   */
  private def encodeDbArtefact(a: MantikArtifact): DbMantikArtifact = {
    DbMantikArtifact(
      mantikfile = a.mantikfile.toJson,
      name = a.id.name,
      version = a.id.version,
      fileId = a.fileId
    )
  }
}
