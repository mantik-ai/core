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

import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.executor.common.LabelConstants
import ai.mantik.executor.docker.api.{DockerClient, DockerOperations}
import ai.mantik.executor.docker.api.structures.{
  CreateContainerHostConfig,
  CreateContainerNetworkSpecificConfig,
  CreateContainerNetworkingConfig,
  CreateContainerRequest,
  CreateNetworkRequest,
  PortBindingHost,
  RestartPolicy
}

import scala.concurrent.Future

/** Handles initalization of extra Services (Traefik etc.) */
class ExtraServices(
    executorConfig: DockerExecutorConfig,
    dockerOperations: DockerOperations
)(
    implicit akkaRuntime: AkkaRuntime
) extends ComponentBase {

  /** Id of the worker network. */
  val workerNetworkId: Future[String] = dockerOperations.ensureNetwork(
    executorConfig.workerNetwork,
    CreateNetworkRequest(
      Name = executorConfig.workerNetwork
    )
  )

  private val traefikStartRequest = CreateContainerRequest(
    Image = executorConfig.ingress.traefikImage,
    Cmd = Vector("--docker"),
    Labels = Map(
      LabelConstants.ManagedByLabelName -> LabelConstants.ManagedByLabelValue,
      LabelConstants.RoleLabelName -> LabelConstants.role.traefik
    ),
    HostConfig = CreateContainerHostConfig(
      PortBindings = Map(
        "80/tcp" -> Vector(
          PortBindingHost(
            HostPort = s"${executorConfig.ingress.traefikPort}"
          )
        )
      ),
      Binds = Some(
        Vector(
          "/var/run/docker.sock:/var/run/docker.sock"
        )
      ),
      RestartPolicy = Some(
        RestartPolicy(
          Name = "unless-stopped"
        )
      )
    )
  )

  /** Traefik. */
  val traefikContainerId: Future[Option[String]] = workerNetworkId.flatMap { networkId =>
    if (executorConfig.ingress.ensureTraefik) {
      val fullContainer = traefikStartRequest.withNetwork(
        executorConfig.workerNetwork,
        CreateContainerNetworkSpecificConfig(
          NetworkID = Some(networkId)
        )
      )
      dockerOperations
        .ensureContainer(executorConfig.ingress.traefikContainerName, fullContainer)
        .map(Some(_))
    } else {
      Future.successful(None)
    }
  }

  private val grpcStartRequest = CreateContainerRequest(
    Image = executorConfig.common.grpcProxy.container.image,
    Cmd = executorConfig.common.grpcProxy.container.parameters.toVector,
    Labels = Map(
      LabelConstants.ManagedByLabelName -> LabelConstants.ManagedByLabelValue,
      LabelConstants.RoleLabelName -> LabelConstants.role.traefik
    ),
    HostConfig = CreateContainerHostConfig(
      PortBindings = Map(
        s"${executorConfig.common.grpcProxy.port}/tcp" -> Vector(
          PortBindingHost(
            HostPort = s"${executorConfig.common.grpcProxy.externalPort}"
          )
        )
      ),
      RestartPolicy = Some(
        RestartPolicy(
          Name = "unless-stopped"
        )
      )
    )
  )

  /** Id of gRpc Proxy. */
  val grpcProxy: Future[Option[String]] = workerNetworkId.flatMap { networkId =>
    if (executorConfig.common.grpcProxy.enabled) {
      val fullContainer = grpcStartRequest.withNetwork(
        executorConfig.workerNetwork,
        CreateContainerNetworkSpecificConfig(
          NetworkID = Some(networkId)
        )
      )
      dockerOperations.ensureContainer(executorConfig.common.grpcProxy.containerName, fullContainer).map(Some(_))
    } else {
      Future.successful(None)
    }
  }
}
