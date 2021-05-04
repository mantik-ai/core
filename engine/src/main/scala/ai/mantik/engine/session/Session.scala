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
package ai.mantik.engine.session

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

import ai.mantik.planner.{CoreComponents, MantikItem, PlanExecutor, Planner}
import javax.net.ssl.SSLEngineResult
import org.slf4j.LoggerFactory

import scala.reflect.ClassTag

/**
  * A Single Mantik Engine Session.
  * @param id id of the session
  * @param components the core components as being viewed from the session.
  *        Note: it's possible that each session gets different instances to implement
  *        access controls.
  */
class Session(
    val id: String,
    val components: CoreComponents
) extends SessionBase {
  import Session.logger

  private var alive = true

  private val nextItemId = new AtomicInteger()
  private val items = new ConcurrentHashMap[String, MantikItem]()

  private def getNextItemId(): String = {
    val id = nextItemId.incrementAndGet()
    id.toString
  }

  def isAlive: Boolean = {
    this.synchronized {
      alive
    }
  }

  def quitSession(): Unit = {
    this.synchronized {
      alive = false
    }
    // More can be done in subclasses.
  }

  /** Add an item to the session, returns the id. */
  def addItem(item: MantikItem): String = {
    val itemId = getNextItemId()
    logger.debug(s"Session ${id} storing ${itemId}:${item.getClass.getSimpleName}")
    items.put(itemId, item)
    itemId
  }

  /** Returns a mantik item. */
  def getItem(id: String): Option[MantikItem] = {
    Option(items.get(id))
  }

  /** Returns a mantik item of given type or fail. */
  def getItemAs[T <: MantikItem: ClassTag](id: String): T = {
    getItem(id) match {
      case Some(item: T) => item
      case Some(otherItem) =>
        EngineErrors.ItemUnexpectedType.throwIt(
          s"Expected ${implicitly[ClassTag[T]].getClass.getSimpleName}, got ${otherItem.getClass.getSimpleName}"
        )
      case None => EngineErrors.ItemNotFoundInSession.throwIt("Item not found ${id}")
    }
  }

  def isEmpty: Boolean = items.isEmpty
}

object Session {
  private val logger = LoggerFactory.getLogger(classOf[Session])
}
