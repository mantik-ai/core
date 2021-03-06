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

import java.util.concurrent.atomic.AtomicInteger

import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

/** Base trait for sessions as being managed by the [[SessionManager]]. */
trait SessionBase {

  /** Returns the id of the session. */
  def id: String

  /** Shutdown the session. */
  private[session] def quitSession(): Unit
}

/**
  * Manages lifecycle of [[Session]].
  * @param sessionFactory method for creating new sessions with a given name.
  *
  * TODO: Session Timeout.
  */
class SessionManagerBase[S <: SessionBase](sessionFactory: String => S)(implicit ec: ExecutionContext) {

  private val logger = LoggerFactory.getLogger(getClass)
  private val sessions = new java.util.concurrent.ConcurrentHashMap[String, S]
  private val nextId = new AtomicInteger()

  /** Create a new session. */
  def create(): Future[S] = {
    val id = s"s${nextId.incrementAndGet()}"
    val session = sessionFactory(id)
    val previous = Option(sessions.put(id, session))
    if (previous.isDefined) {
      // Should not happen, as ids are increasing
      throw new IllegalStateException("Session already exists?!")
    }
    logger.info(s"Created session ${id}")
    Future.successful(session)
  }

  /** Explicitly close a session. */
  def close(sessionId: String): Future[Unit] = {
    val session = Option(sessions.remove(sessionId))
    if (session.isEmpty) {
      logger.info(s"Session ${sessionId} not found")
      return Future.successful(())
    }
    Future {
      logger.info(s"Shutting down ${sessionId}")
      session.foreach(_.quitSession())
    }
  }

  /** Returns a session. */
  def get(id: String): Future[S] = {
    Option(sessions.get(id)) match {
      case Some(session) =>
        Future.successful(session)
      case None =>
        Future.failed(EngineErrors.SessionNotFound.toException(id))
    }

  }

}

class SessionManager(sessionFactory: String => Session)(implicit ec: ExecutionContext)
    extends SessionManagerBase[Session](sessionFactory)
