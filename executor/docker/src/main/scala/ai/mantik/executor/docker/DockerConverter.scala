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
package ai.mantik.executor.docker

import java.util.Base64

import ai.mantik.executor.common.LabelConstants
import ai.mantik.executor.docker.api.PullPolicy
import ai.mantik.executor.docker.api.structures.CreateContainerRequest
import ai.mantik.executor.model.docker.Container
import akka.util.ByteString
import io.circe.Json

class DockerConverter(
    config: DockerExecutorConfig,
    internalId: String,
    userId: String
) {

  val defaultLabels = Map(
    LabelConstants.ManagedByLabelName -> LabelConstants.ManagedByLabelValue,
    DockerConstants.IsolationSpaceLabelName -> config.common.isolationSpace,
    LabelConstants.InternalIdLabelName -> internalId,
    LabelConstants.UserIdLabelName -> userId
  )

  /** Generate a pull policy for a container. */
  def pullPolicy(container: Container): PullPolicy = {
    // Should be similar to kubernetes
    if (config.common.disablePull) {
      PullPolicy.Never
    } else {
      container.imageTag match {
        case None           => PullPolicy.Always
        case Some("latest") => PullPolicy.Always
        case Some(other)    => PullPolicy.IfNotPresent
      }
    }
  }

  /** Generate a worker container */
  def generateWorkerContainer(
      containerName: String,
      container: Container,
      workerNetworkId: Option[String]
  ): ContainerDefinition = {
    val resolved = config.common.dockerConfig.resolveContainer(container)

    val request = CreateContainerRequest(
      Image = resolved.image,
      Cmd = resolved.parameters.toVector,
      Labels = mnpWorkerLabels()
    )

    val requestWithNetwork = workerNetworkId
      .map { networkId =>
        request.withNetworkId(config.workerNetwork, networkId)
      }
      .getOrElse(
        request
      )

    ContainerDefinition(
      containerName,
      mainPort = Some(8502),
      pullPolicy = pullPolicy(resolved),
      requestWithNetwork
    )
  }

  private def mnpWorkerLabels(): Map[String, String] = {
    defaultLabels + (
      LabelConstants.RoleLabelName -> LabelConstants.role.worker,
      LabelConstants.WorkerTypeLabelName -> LabelConstants.workerType.mnpWorker,
    )
  }

  /** Generate an MNP preparer for a node. */
  def generateMnpPreparer(
      mainContainerName: String,
      initRequest: ByteString,
      workerNetworkId: Option[String]
  ): ContainerDefinition = {
    val preparerName = mainContainerName + "_init"
    val mainAddress = s"${mainContainerName}:8502"
    val parameters = Seq(
      "--address",
      mainAddress
    )
    val allParameters = config.common.mnpPreparer.parameters ++ parameters
    val encodedInitRequest = Base64.getEncoder.encodeToString(initRequest.toArray[Byte])

    val envValue = s"MNP_INIT=${encodedInitRequest}"

    val request = CreateContainerRequest(
      Image = config.common.mnpPreparer.image,
      Cmd = allParameters.toVector,
      Labels = mnpWorkerLabels(),
      Env = Vector(envValue)
    )

    val requestWithNetwork = workerNetworkId
      .map { networkId =>
        request.withNetworkId(config.workerNetwork, networkId)
      }
      .getOrElse(
        request
      )

    ContainerDefinition(
      preparerName,
      None,
      pullPolicy = pullPolicy(config.common.mnpPreparer),
      requestWithNetwork
    )
  }

  def generatePipelineContainer(
      containerName: String,
      pipelineDefinition: Json,
      workerNetworkId: Option[String]
  ): ContainerDefinition = {
    val container = config.common.mnpPipelineController
    val pipelineEnv = "PIPELINE=" + pipelineDefinition.noSpaces
    val extraArgs = Vector("-port", "8502")
    val allParameters = container.parameters ++ extraArgs
    val request = CreateContainerRequest(
      Image = container.image,
      Cmd = allParameters.toVector,
      Labels = defaultLabels + (
        LabelConstants.RoleLabelName -> LabelConstants.role.worker,
        LabelConstants.WorkerTypeLabelName -> LabelConstants.workerType.mnpPipeline
      ),
      Env = Vector(pipelineEnv)
    )

    val requestWithNetwork = workerNetworkId
      .map { networkId =>
        request.withNetworkId(config.workerNetwork, networkId)
      }
      .getOrElse(
        request
      )

    ContainerDefinition(
      containerName,
      Some(8502),
      pullPolicy = pullPolicy(config.common.mnpPreparer),
      requestWithNetwork
    )
  }
}
