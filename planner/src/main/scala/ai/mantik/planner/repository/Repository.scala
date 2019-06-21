package ai.mantik.planner.repository

import ai.mantik.elements.MantikId
import ai.mantik.planner.repository.impl.Factory
import ai.mantik.planner.utils.{ AkkaRuntime, Component }
import com.typesafe.config.Config

import scala.concurrent.Future

/** Gives access to Mantik objects. */
trait Repository extends Component {

  /** Retrieves a Mantik artefact. */
  def get(id: MantikId): Future[MantikArtifact]

  /** Returns a Mantik artefact and all its referenced items. */
  def getWithHull(id: MantikId): Future[(MantikArtifact, Seq[MantikArtifact])] = {
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
}

object Repository {

  /**
   * Create a Repository.
   * In Future this should be done using DI Ticket #86.
   */
  def create()(implicit akkaRuntime: AkkaRuntime): Repository = Factory.createRepository()
}
