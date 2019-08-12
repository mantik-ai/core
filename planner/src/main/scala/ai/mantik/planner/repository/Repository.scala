package ai.mantik.planner.repository

import ai.mantik.componently.Component
import ai.mantik.elements.{ ItemId, MantikId }

import scala.concurrent.Future

/** Gives access to Mantik objects. */
trait Repository extends Component {
  /** Retrieves a Mantik artefact. */
  def get(id: MantikId): Future[MantikArtifact]

  /**
   * Tag an existing item with a new name.
   * @return if the new tag was created or false if it was already existant.
   * Throws if the item was not found.
   */
  def ensureMantikId(id: ItemId, newName: MantikId): Future[Boolean]

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
