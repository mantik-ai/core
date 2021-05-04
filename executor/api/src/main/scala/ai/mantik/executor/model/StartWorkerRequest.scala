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

import ai.mantik.executor.model.docker.{Container, DockerLogin}
import akka.util.ByteString
import io.circe.generic.JsonCodec
import ByteStringCodec._
import io.circe.Json

/**
  * Request for starting a MNP worker
  *
  * @param isolationSpace isolation space where to start the worker
  * @param id user specified id to give to the worker
  * @param definition definition of the worker
  * @param keepRunning if true, keep the worker running
  * @param nameHint if given, try to give the worker a name similar to this given name
  * @param ingressName if given, make the worker accessible from the outside
  */
@JsonCodec
case class StartWorkerRequest(
    isolationSpace: String,
    id: String,
    definition: WorkerDefinition,
    keepRunning: Boolean = false,
    nameHint: Option[String] = None,
    ingressName: Option[String] = None
)

@JsonCodec
sealed trait WorkerDefinition {}

/**
  * An MNP Worker
  * @param initializer initializing code for ready to use Nodes.
  */
case class MnpWorkerDefinition(
    container: Container,
    extraLogins: Seq[DockerLogin] = Nil,
    initializer: Option[ByteString] = None
) extends WorkerDefinition

/**
  * An MNP Pipeline
  * @param definition JSON definition of Pipeline Helper
  *                   (Must match Golang Definition)
  */
case class MnpPipelineDefinition(
    definition: Json
) extends WorkerDefinition

/**
  * Response for [[StartWorkerRequest]]
  *
  * @param nodeName name of the Node (usually container or service name)
  * @param externalUrl an URL under which the Node is reachable from the outside.
  */
@JsonCodec
case class StartWorkerResponse(
    nodeName: String,
    externalUrl: Option[String] = None
)
