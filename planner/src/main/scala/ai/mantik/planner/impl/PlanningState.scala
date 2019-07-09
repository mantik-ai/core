package ai.mantik.planner.impl

import ai.mantik.elements.ItemId
import ai.mantik.planner.impl.PlanningState.ItemStored
import ai.mantik.planner.repository.ContentTypes
import ai.mantik.planner.{ CacheKey, CacheKeyGroup, DataSet, MantikItem, PayloadSource, PlanFile, PlanFileReference }
import cats.data.State

/**
 * State for [[PlannerImpl]].
 * @param nextNodeId the next available node id.
 * @param nextFileReferenceId the next available file reference id
 * @param filesRev requested files (reverse).
 * @param stored items which were stored within the plan.
 */
private[impl] case class PlanningState(
    private val nextNodeId: Int = 1,
    private val nextFileReferenceId: Int = 1,
    private val filesRev: List[PlanFile] = Nil, // reverse requested files
    cacheGroups: List[CacheKeyGroup] = Nil,
    private val stored: Map[ItemId, PlanningState.ItemStored] = Map.empty
) {

  /** Returns files in request order. */
  def files: List[PlanFile] = filesRev.reverse

  /** Fetches a new Node Id and returns the next planning state. */
  def withNextNodeId: (PlanningState, String) = {
    copy(nextNodeId = nextNodeId + 1) -> nextNodeId.toString
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

  /** Add an item to the stored values. */
  def withItemStored(itemId: ItemId, storage: PlanningState.ItemStored): PlanningState = {
    copy(
      stored = stored + (itemId -> storage)
    )
  }

  /**
   * Figures out the stored payload of an item.
   * This can either be already persistent storage, or it was
   * added in an earlier stage.
   */
  def itemStorage(item: MantikItem): (PlanningState, Option[ItemStored]) = {
    stored.get(item.itemId) match {
      case Some(already) =>
        // is stored inside the state
        this -> Some(already)
      case None =>
        // maybe it's already stored in the item?
        val state = item.state.get
        if (state.isStored) {
          // it is stored, access the payoad, if it has some...
          state.payloadFile match {
            case Some(payloadFile) =>
              val (nextState, file) = readFile(payloadFile)
              val contentType = itemPayloadContentType(item)
              val fileWithContentType = PlanFileWithContentType(file.ref, contentType)
              nextState -> Some(ItemStored(payload = Some(fileWithContentType)))
            case None =>
              // item is stored, but has no payload file
              this -> Some(ItemStored(payload = None))
          }
        } else {
          // not yet stored
          this -> None
        }
    }
  }

  private def itemPayloadContentType(item: MantikItem): String = {
    // TODO: We need a better place for this distinguishment
    // Also the format content type may be runtime specific.
    item match {
      case ds: DataSet if ds.stack == DataSet.NaturalFormatName =>
        ContentTypes.MantikBundleContentType
      case _ => ContentTypes.ZipFileContentType
    }
  }
}

private[impl] object PlanningState {

  /** Information about an item which was stored within the plan. */
  case class ItemStored(
      payload: Option[PlanFileWithContentType]
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

  /**
   * Wraps a simple state change in State object.
   * @param f method which changes state and returns element.
   * @return simple state change.
   */
  def apply[T](f: PlanningState => (PlanningState, T)): State[PlanningState, T] = {
    State(f)
  }

  /** A Non-state-changing function. */
  def pure[T](f: => T): State[PlanningState, T] = {
    State.pure(f)
  }
}
