package ai.mantik.repository

import ai.mantik.repository.impl.Factory
import com.typesafe.config.Config

import scala.concurrent.{ ExecutionContext, Future }
import scala.reflect.ClassTag

/** Gives access to Mantik objects. */
trait Repository {

  /** Retrieves a Mantik artefact. */
  def get(id: MantikId): Future[MantikArtifact]

  /** Retriebes a Mantik artefact and checks the type. */
  def getAs[T <: MantikDefinition: ClassTag](id: MantikId)(implicit ex: ExecutionContext): Future[(MantikArtifact, Mantikfile[T])] = {
    get(id).flatMap { artefact =>
      artefact.mantikfile.cast[T] match {
        case Left(error) => Future.failed(new Errors.WrongTypeException(error.getMessage))
        case Right(ok)   => Future.successful(artefact -> ok)
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
