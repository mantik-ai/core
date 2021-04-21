package ai.mantik.planner.repository

import ai.mantik.planner.repository.impl.LocalMantikRegistryImpl
import com.google.inject.ImplementedBy

import scala.concurrent.Future

/** The local Mantik Registry. */
@ImplementedBy(classOf[LocalMantikRegistryImpl])
trait LocalMantikRegistry extends MantikRegistry {

  /**
    * List Mantik Artifacts.
    * @param alsoAnonymous if true, also return anonymous artifacts who are not named
    * @param deployedOnly if true, only return deployed artifacts
    * @param kindFilter if set, filter for a specific kind.
    */
  def list(
      alsoAnonymous: Boolean = false,
      deployedOnly: Boolean = false,
      kindFilter: Option[String] = None
  ): Future[IndexedSeq[MantikArtifact]]
}
