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
package ai.mantik.executor.common.workerexec

import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.componently.utils.{Tracked, TrackingContext}
import ai.mantik.executor.Errors
import ai.mantik.executor.model.{ContainerState, EvaluationWorkloadState, LinkState, SessionState, WorkloadStatus}
import akka.actor.Cancellable
import akka.stream.scaladsl.Source

import java.util.concurrent.ConcurrentHashMap
import cats.implicits._
import scala.jdk.CollectionConverters._

import scala.concurrent.duration._

/** Implements state tracking for evaluations. */
class StateTracker(implicit akkaRuntime: AkkaRuntime) extends ComponentBase {

  private implicit val trackingContext = new TrackingContext(10.seconds)

  /** Contains current evaluations. */
  private val evaluations: ConcurrentHashMap[String, Tracked[Evaluation]] = new ConcurrentHashMap()

  /**
    * Add an evaluation
    * @return None if added, Some(existing) if there was already one with the given id.
    */
  def add(evaluation: Evaluation): Option[Evaluation] = {
    Option(evaluations.putIfAbsent(evaluation.workload.id, new Tracked(evaluation))).map(_.value)
  }

  /** Evaluation lookup */
  def get(id: String): Option[Evaluation] = {
    Option(evaluations.get(id)).map(_.value)
  }

  /** Return all evaluations */
  def all(): Seq[Evaluation] = {
    val result = Seq.newBuilder[Evaluation]
    evaluations.elements().asScala.foreach { tracked =>
      result += tracked.get()._1
    }
    result.result()
  }

  /** Update the whole state. */
  def updateState(id: String)(f: Evaluation => Evaluation): Unit = {
    val existingEvaluation = get(id)
    val updatedEvalution = Option {
      evaluations.computeIfPresent(
        id,
        { (_, value) =>
          value.update(f)
          value
        }
      )
    }

    for {
      e <- existingEvaluation
      u <- updatedEvalution
      if (e.state.status != u.value.state.status)
    } {
      logger.info(s"Evaluation state of ${id}: ${e.state.status} -> ${u.value.state.status}")
    }
  }

  /** Update the main workload state. */
  def updateWorkloadState(id: String, workloadState: WorkloadStatus): Unit = {
    updateState(id)(_.withWorkloadState(workloadState))
  }

  /** Update the evaluation state. */
  def updateEvaluationState(id: String)(f: EvaluationWorkloadState => EvaluationWorkloadState): Unit = {
    updateState(id)(_.stateUpdate(f))
  }

  /** Update the state of a single container. */
  def updateContainerState(id: String, containerIdx: Int)(f: ContainerState => ContainerState): Unit = {
    updateEvaluationState(id) { es =>
      es.copy(
        containers = updateMaybeExisting(es.containers, containerIdx, f)
      )
    }
  }

  /** Update the state of a single session. */
  def updateSessionState(id: String, sessionId: Int)(f: SessionState => SessionState): Unit = {
    updateEvaluationState(id) { es =>
      es.copy(
        sessions = updateMaybeExisting(es.sessions, sessionId, f)
      )
    }
  }

  /** Update the state of a single link */
  def updateLinkState(id: String, linkId: Int)(f: LinkState => LinkState): Unit = {
    updateEvaluationState(id) { es =>
      es.copy(
        links = updateMaybeExisting(es.links, linkId, f)
      )
    }
  }

  /** Track an evaluation */
  def track(id: String): Source[Evaluation, Cancellable] = {
    Option(evaluations.get(id)) match {
      case None => throw new Errors.NotFoundException(s"Evaluation with id ${id} not found")
      case Some(tracked) =>
        tracked.trackAsSource()
    }
  }

  /** Mark  an evaluation as being end of live, no more changes on it */
  def finishEvaluation(id: String): Unit = {
    evaluations.computeIfPresent(
      id,
      (_, p) => {
        p.markEndOfLive()
        p
      }
    )
  }

  /** Update the indexed element if it exists. */
  private def updateMaybeExisting[T](in: Option[Vector[T]], idx: Int, f: T => T): Option[Vector[T]] = {
    in.map { existing =>
      existing.get(idx) match {
        case None => existing
        case Some(old) =>
          val updated = f(old)
          existing.updated(idx, updated)
      }
    }
  }
}
