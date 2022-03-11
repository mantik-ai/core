/*
 * This file is part of the Mantik Project.
 * Copyright (c) 2020-2021 Mantik UG (Haftungsbeschränkt)
 * Authors: See AUTHORS file
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.
 *
 * Additionally, the following linking exception is granted:
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with other code, such other code is not for that reason
 * alone subject to any of the requirements of the GNU Affero GPL
 * version 3.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license.
 */
package ai.mantik.planner.impl

import scala.concurrent.{ExecutionContext, Future}

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

  /** Load the hull of ids. */
  def loadHull(ids: Seq[I]): Future[Seq[T]] = {
    iterative(ids, Set.empty, Vector.empty)
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
