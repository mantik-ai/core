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
package ai.mantik.executor.model

import ai.mantik.executor.model.EvaluationWorkloadBuilder.KeyedVectorBuilder
import ai.mantik.executor.model.docker.Container

/**
  * Functional Builder for EvaluationWorkloads.
  * You can use your own Key 'K' for accessing objects.
  * They will be compiled into a flat structure by calling [[result]].
  */
case class EvaluationWorkloadBuilder[K](
    id: String,
    sources: KeyedVectorBuilder[K, DataSource] = KeyedVectorBuilder[K, DataSource](),
    sinks: KeyedVectorBuilder[K, DataSink] = KeyedVectorBuilder[K, DataSink](),
    sessions: KeyedVectorBuilder[K, WorkloadSession] = KeyedVectorBuilder[K, WorkloadSession](),
    containers: KeyedVectorBuilder[Container, WorkloadContainer] = KeyedVectorBuilder[Container, WorkloadContainer](),
    reverseLinksBuilder: List[Link] = Nil
) {

  def addDataSource(key: K, dataSource: DataSource): EvaluationWorkloadBuilder[K] = {
    copy(
      sources = sources.add(key, dataSource)
    )
  }

  def ensureDataSource(key: K, dataSource: DataSource): (EvaluationWorkloadBuilder[K], Int) = {
    val (updatedSources, id) = sources.ensure(key, dataSource)
    copy(
      sources = updatedSources
    ) -> id
  }

  def ensureDataSink(key: K, dataSink: DataSink): (EvaluationWorkloadBuilder[K], Int) = {
    val (updatedSinks, id) = sinks.ensure(key, dataSink)
    copy(
      sinks = updatedSinks
    ) -> id
  }

  def getDataSource(key: K): Option[Int] = {
    sources.get(key)
  }

  def addDataSink(key: K, dataSink: DataSink): EvaluationWorkloadBuilder[K] = {
    copy(
      sinks = sinks.add(key, dataSink)
    )
  }

  def getDataSink(key: K): Option[Int] = {
    sinks.get(key)
  }

  /**
    * Ensure the existance of a container.
    * @return the updated builder and the id of the container.
    */
  def ensureContainer(container: Container, creator: => WorkloadContainer): (EvaluationWorkloadBuilder[K], Int) = {
    containers.get(container) match {
      case Some(id) => (this, id)
      case None =>
        val (updatedContainers, id) = containers.addAndReturnId(container, creator)
        copy(
          containers = updatedContainers
        ) -> id
    }
  }

  def addSession(key: K, workloadSession: WorkloadSession): EvaluationWorkloadBuilder[K] = {
    copy(
      sessions = sessions.add(key, workloadSession)
    )
  }

  def addLink(link: Link): EvaluationWorkloadBuilder[K] = {
    copy(
      reverseLinksBuilder = link :: reverseLinksBuilder
    )
  }

  def result: EvaluationWorkload = EvaluationWorkload(
    id = id,
    containers = containers.result,
    sessions = sessions.result,
    sources = sources.result,
    sinks = sinks.result,
    links = reverseLinksBuilder.reverse.toVector
  )
}

object EvaluationWorkloadBuilder {

  /** Helper for building result vectors which have Keys during construction. */
  case class KeyedVectorBuilder[K, T](
      private val reverseBuilder: List[T] = Nil,
      private val mapping: Map[K, Int] = Map.empty[K, Int],
      private val nextId: Int = 0
  ) {

    def add(key: K, value: T): KeyedVectorBuilder[K, T] = {
      addAndReturnId(key, value)._1
    }

    def addAndReturnId(key: K, value: T): (KeyedVectorBuilder[K, T], Int) = {
      copy(
        reverseBuilder = value :: reverseBuilder,
        mapping = mapping + (key -> nextId),
        nextId = nextId + 1
      ) -> nextId
    }

    def ensure(key: K, value: T): (KeyedVectorBuilder[K, T], Int) = {
      mapping.get(key) match {
        case Some(id) => (this, id)
        case None =>
          addAndReturnId(key, value)
      }
    }

    def get(key: K): Option[Int] = {
      mapping.get(key)
    }

    def result: Vector[T] = {
      reverseBuilder.toVector.reverse
    }
  }
}
