package ai.mantik.planner.impl

import ai.mantik.elements.{ ItemId, NamedMantikId }
import ai.mantik.planner.impl.PlanningState.ItemStateOverride
import ai.mantik.planner.repository.{ ContentTypes, DeploymentInfo }
import ai.mantik.planner.{ CacheKey, CacheKeyGroup, DataSet, DeploymentState, MantikItem, MantikItemState, MemoryId, PayloadSource, PlanFile, PlanFileReference }
import cats.data.State

/**
 * State for [[PlannerImpl]].
 * @param nextNodeId the next available node id.
 * @param nextFileReferenceId the next available file reference id
 * @param filesRev requested files (reverse).
 * @param stateOverrides override of Mantik State (where the planner decided a new target state)
 * @param nextMemoryId the next available id for memoized values.
 */
private[impl] case class PlanningState(
    private val nextNodeId: Int = 1,
    private val nextFileReferenceId: Int = 1,
    private val filesRev: List[PlanFile] = Nil, // reverse requested files
    cacheGroups: List[CacheKeyGroup] = Nil,
    private val stateOverrides: Map[ItemId, ItemStateOverride] = Map.empty,
    private val nextMemoryId: Int = 1
) {

  /** Returns files in request order. */
  def files: List[PlanFile] = filesRev.reverse

  /** Fetches a new Node Id and returns the next planning state. */
  def withNextNodeId: (PlanningState, String) = {
    copy(nextNodeId = nextNodeId + 1) -> nextNodeId.toString
  }

  /** Generates a new memorisation id. */
  def withNextMemoryId: (PlanningState, String) = {
    copy(nextMemoryId = nextMemoryId + 1) -> ("var" + nextMemoryId.toString)
  }

  /** Mark files as cache files. */
  def markCached(filesToUpdate: Map[PlanFileReference, CacheKey]): PlanningState = {
    copy(
      filesRev = filesRev.map { file =>
        filesToUpdate.get(file.ref) match {
          case Some(cacheKey) => file.copy(cacheKey = Some(cacheKey))
          case None           => file
        }
      }
    )
  }

  /** Add a new cache group. */
  def withCacheGroup(cacheKeyGroup: CacheKeyGroup): PlanningState = {
    copy(
      cacheGroups = cacheKeyGroup :: cacheGroups
    )
  }

  /** Request writing a file. */
  def writeFile(temporary: Boolean): (PlanningState, PlanFile) = {
    val file = PlanFile(
      ref = PlanFileReference(nextFileReferenceId),
      write = true,
      temporary = temporary,
      fileId = None
    )
    copy(
      nextFileReferenceId = nextFileReferenceId + 1, filesRev = file :: filesRev
    ) -> file
  }

  /** Request piping through a file (a file written and read in the same plan). */
  def pipeFile(temporary: Boolean): (PlanningState, PlanFile) = {
    val file = PlanFile(
      ref = PlanFileReference(nextFileReferenceId),
      read = true,
      write = true,
      temporary = temporary,
      fileId = None
    )
    copy(
      nextFileReferenceId = nextFileReferenceId + 1, filesRev = file :: filesRev
    ) -> file
  }

  /** Request reading a file. */
  def readFile(fileId: String): (PlanningState, PlanFile) = {
    val file = PlanFile(
      ref = PlanFileReference(nextFileReferenceId),
      read = true,
      fileId = Some(fileId)
    )
    copy(
      nextFileReferenceId = nextFileReferenceId + 1, filesRev = file :: filesRev
    ) -> file
  }

  def readFileRefWithContentType(fileId: String, contentType: String): (PlanningState, PlanFileWithContentType) = {
    val (nextState, planFile) = readFile(fileId)
    val fileRef = PlanFileWithContentType(planFile.ref, contentType)
    nextState -> fileRef
  }

  /** Returns overriding state for an item. */
  def overrideState(item: MantikItem): ItemStateOverride = {
    stateOverrides.getOrElse(item.itemId, initialOverrideState(item))
  }

  private def initialOverrideState(item: MantikItem): ItemStateOverride = {
    val itemState = item.state.get
    ItemStateOverride(
      stored = itemState.itemStored,
      storedWithName = itemState.namedMantikItem.filter(_ => itemState.nameStored),
      deployed = itemState.deployment.map { state =>
        Left(state)
      }
    )
  }

  /** Set an item override. */
  def withOverrideState(item: MantikItem, o: ItemStateOverride): PlanningState = {
    copy(
      stateOverrides = stateOverrides + (item.itemId -> o)
    )
  }

  /** Set an item override, modifying the current one. */
  def withOverrideFunc(item: MantikItem, f: ItemStateOverride => ItemStateOverride): PlanningState = {
    val current = overrideState(item)
    val modified = f(current)
    withOverrideState(item, modified)
  }
}

private[impl] object PlanningState {

  /** Overridden information on items. */
  case class ItemStateOverride(
      // If set, the payload is already requested
      payloadAvailable: Option[IndexedSeq[PlanFileWithContentType]] = None,
      // True if the item was saved
      stored: Boolean,
      // True if the item was saved under a mantik id.
      storedWithName: Option[NamedMantikId],
      // if item was deployed, contains either deployment info or a memory id
      // where the deployment info can be picked up.
      deployed: Option[Either[DeploymentState, MemoryId]]
  )

  /**
   * Helper for declaring a state changing function.
   * @param f the method which changes the state and returns the result
   * @param g a method which does generate transforms the result of f
   * @return a State change which applies f and g.
   */
  def stateChange[T, X](f: PlanningState => (PlanningState, T))(g: T => X): State[PlanningState, X] = {
    State { state: PlanningState =>
      val (nextState, element) = f(state)
      nextState -> g(element)
    }
  }

  /** Examines the current state and forward calls to next state change. */
  def flat[T](f: PlanningState => State[PlanningState, T]): State[PlanningState, T] = {
    for {
      state <- State.get[PlanningState]
      next <- f(state)
    } yield next
  }

  /**
   * Wraps a simple state change in State object.
   * @param f method which changes state and returns element.
   * @return simple state change.
   */
  def apply[T](f: PlanningState => (PlanningState, T)): State[PlanningState, T] = {
    State(f)
  }

  def inspect[T](f: PlanningState => T): State[PlanningState, T] = {
    State.inspect(f)
  }

  def modify[T](f: PlanningState => PlanningState): State[PlanningState, Unit] = {
    State.modify(f)
  }

  /** A Non-state-changing function. */
  def pure[T](f: => T): State[PlanningState, T] = {
    State.pure(f)
  }
}
