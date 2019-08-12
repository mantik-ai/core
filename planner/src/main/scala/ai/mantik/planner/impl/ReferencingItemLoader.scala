package ai.mantik.planner.impl

import scala.concurrent.{ ExecutionContext, Future }

/**
 * Helper for loading items which are referenced by each other.
 * In practice this are [[ai.mantik.planner.repository.MantikArtifact]] but for better testability, this class is abstract.
 * An Item T is identified by some Id, and can reference other Ids. This
 * references are not allowed to be cyclic.
 *
 * Loading is done asynchronous by extending a reachability graph in iterations.
 *
 * @tparam I ID type
 * @tparam T type of item.
 * @param loader loads an item with  given id
 * @param dependencyExtractor extracts dependencies of an item.
 */
class ReferencingItemLoader[I, T](
    loader: I => Future[T],
    dependencyExtractor: T => Seq[I]
)(implicit ec: ExecutionContext) {

  /** Load Items with hull. The items are ordered, so that the ones without further dependencies are on the right. */
  def loadWithHull(id: I): Future[Seq[T]] = {
    iterative(Seq(id), Set.empty, Vector.empty)
  }

  /**
   * Iterative resolves a new group of unknown elements.
   * @param border current unknown elements (border of the graph)
   * @param known already loaded element
   * @param travelled current loaded elements (result builder)
   */
  private def iterative(border: Seq[I], known: Set[I], travelled: Vector[T]): Future[Seq[T]] = {
    loadMany(border).flatMap { newItems =>
      val newKnown = known ++ border
      val newTravelled = travelled ++ newItems
      val newBorder = newItems.flatMap(dependencyExtractor).distinct.filterNot(newKnown.contains)
      if (newBorder.isEmpty) {
        Future.successful(newTravelled)
      } else {
        iterative(newBorder, newKnown, newTravelled)
      }
    }
  }

  private def loadMany(items: Seq[I]): Future[Seq[T]] = {
    Future.sequence(items.map(loader))
  }
}
