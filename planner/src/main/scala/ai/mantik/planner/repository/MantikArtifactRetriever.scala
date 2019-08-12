package ai.mantik.planner.repository

import java.nio.file.Path

import ai.mantik.componently.Component
import ai.mantik.elements.MantikId
import ai.mantik.planner.repository.impl.MantikArtifactRetrieverImpl
import com.google.inject.ImplementedBy

import scala.concurrent.Future

/** Responsible for retrieving [[MantikArtifact]] from local repository and remote registry. */
@ImplementedBy(classOf[MantikArtifactRetrieverImpl])
trait MantikArtifactRetriever extends Component {

  /** Pull an Item from external Registry and put it into the local repository. */
  def pull(id: MantikId): Future[MantikArtifactWithHull]

  /** Tries to load an item from local repository, and if not available from a remote repository. */
  def get(id: MantikId): Future[MantikArtifactWithHull]

  /** Loads an item locally. */
  def getLocal(id: MantikId): Future[MantikArtifactWithHull]

  /** Pushes an Item from the local repository to the remote registry. */
  def push(id: MantikId): Future[MantikArtifactWithHull]

  /** Add a local directory to the local repository. */
  def addLocalDirectoryToRepository(dir: Path, id: Option[MantikId] = None): Future[MantikArtifact]
}
