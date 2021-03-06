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

import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiFunction

import ai.mantik.elements.ItemId
import ai.mantik.planner.{MantikItem, MantikItemState}
import javax.inject.Singleton

/** Holds the [[MantikItemState]] for [[MantikItem]] */
@Singleton
private[mantik] final class MantikItemStateManager {

  private val stateMap = new ConcurrentHashMap[ItemId, MantikItemState]

  /**
    * Updates the mantik item state
    * @param itemId the item id
    * @param f the updating function
    * @return returns the current state, if exists.
    */
  def update(itemId: ItemId, f: MantikItemState => MantikItemState): Option[MantikItemState] = {
    Option {
      stateMap.computeIfPresent(
        itemId,
        new BiFunction[ItemId, MantikItemState, MantikItemState] {
          override def apply(t: ItemId, u: MantikItemState): MantikItemState = {
            f(u)
          }
        }
      )
    }
  }

  /**
    * Like update, but creates a fresh state if not existant, usable for constructed Items.
    */
  def updateOrFresh(itemId: ItemId, f: MantikItemState => MantikItemState): MantikItemState = {
    stateMap.compute(
      itemId,
      new BiFunction[ItemId, MantikItemState, MantikItemState] {
        override def apply(t: ItemId, u: MantikItemState): MantikItemState = {
          val existing = Option(u).getOrElse(MantikItemState())
          f(existing)
        }
      }
    )
  }

  /**
    * Updates an existing mantik state, or generates a new one and applies f to it.
    * @return the updated item state
    */
  def upsert(item: MantikItem, f: MantikItemState => MantikItemState): MantikItemState = {
    stateMap.compute(
      item.itemId,
      new BiFunction[ItemId, MantikItemState, MantikItemState] {
        override def apply(t: ItemId, u: MantikItemState): MantikItemState = {
          val existing = Option(u).getOrElse(
            MantikItemState.initializeFromSource(item.source)
          )
          f(existing)
        }
      }
    )
  }

  /** Returns the [[MantikItemState]]. */
  def get(itemId: ItemId): Option[MantikItemState] = {
    Option(stateMap.get(itemId))
  }

  /** Returns the [[MantikItemState]] or if no specific is saved, the default state from the source. */
  def getOrDefault(item: MantikItem): MantikItemState = {
    Option(stateMap.get(item.itemId)).getOrElse(MantikItemState.initializeFromSource(item.source))
  }

  /** Retuens the [[MantikItemState]] or initializes a fresh new one */
  def getOrInit(item: MantikItem): MantikItemState = {
    upsert(item, identity)
  }

  /** Set an MantikItemState. */
  def set(itemId: ItemId, state: MantikItemState): Unit = {
    stateMap.put(itemId, state)
  }
}
