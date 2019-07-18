package ai.mantik.planner.repository

import ai.mantik.componently.{ AkkaRuntime, Component }
import ai.mantik.elements.{ ItemId, MantikId }
import ai.mantik.planner.repository.impl.Factory

import scala.concurrent.Future

/** Gives access to Mantik objects. */
trait Repository extends Component {
  import ai.mantik.componently.AkkaHelper._

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

  /**
   * Update the deployment state.
   * @param itemId item to update
   * @param state new deployment state, can be empty if not deployed
   * @return true if the item was found and updated.
   */
  def setDeploymentInfo(itemId: ItemId, state: Option[DeploymentInfo]): Future[Boolean]

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
