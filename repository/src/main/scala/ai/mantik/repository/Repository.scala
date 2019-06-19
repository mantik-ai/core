package ai.mantik.repository

import ai.mantik.repository.impl.Factory
import com.typesafe.config.Config

import scala.concurrent.{ ExecutionContext, Future }
import scala.reflect.ClassTag

/** Gives access to Mantik objects. */
trait Repository {

  /** Retrieves a Mantik artefact. */
  def get(id: MantikId): Future[MantikArtifact]

  /** Returns a Mantik artefact and all its referenced items. */
  def getWithHull(id: MantikId)(implicit ec: ExecutionContext): Future[(MantikArtifact, Seq[MantikArtifact])] = {
    get(id).flatMap { artifact =>
      val referenced = artifact.mantikfile.definition.referencedItems
      val othersFuture = Future.sequence(referenced.map { subId =>
        get(subId)
      })
      othersFuture.map { others =>
        artifact -> others
      }
    }
  }

  /** Stores a Mantik artefact. */
  def store(mantikArtefact: MantikArtifact): Future[Unit]

  /** Remove an artifact. Returns true if it was found. */
  def remove(id: MantikId): Future[Boolean]

  /** Shut down the repository. */
  def shutdown(): Unit = {}
}

object Repository {

  /**
   * Create a Repository.
   * In Future this should be done using DI Ticket #86.
   */
  def create(config: Config)(implicit ec: ExecutionContext): Repository = Factory.createRepository(config)
}
