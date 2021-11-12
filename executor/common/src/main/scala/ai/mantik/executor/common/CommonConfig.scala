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
package ai.mantik.executor.common

import ai.mantik.executor.model.docker.Container
import com.typesafe.config.{Config => TypesafeConfig}

/** Common settings for various executors. */
case class CommonConfig(
    isolationSpace: String,
    mnpPreparer: Container,
    mnpPipelineController: Container,
    grpcProxyContainer: Container,
    dockerConfig: DockerConfig,
    disablePull: Boolean,
    grpcProxy: GrpcProxyConfig,
    mnpDefaultPort: Int,
    pipelineDefaultPort: Int
)

object CommonConfig {

  def fromTypesafeConfig(c: TypesafeConfig): CommonConfig = {
    val root = c.getConfig("mantik.executor")
    val dockerPath = root.getConfig("docker")
    val containersConfig = root.getConfig("containers")
    val dockerConfig = DockerConfig.parseFromConfig(dockerPath)
    def rc(name: String): Container = {
      dockerConfig.resolveContainer(Container.parseFromTypesafeConfig(containersConfig.getConfig(name)))
    }
    CommonConfig(
      isolationSpace = root.getString("isolationSpace"),
      mnpPreparer = rc("mnpPreparer"),
      mnpPipelineController = rc("mnpPipelineController"),
      grpcProxyContainer = rc("grpcProxy"),
      dockerConfig = dockerConfig,
      disablePull = root.getBoolean("behaviour.disablePull"),
      grpcProxy = GrpcProxyConfig.fromTypesafeConfig(c),
      mnpDefaultPort = root.getInt("mnpDefaultPort"),
      pipelineDefaultPort = root.getInt("pipelineDefaultPort")
    )
  }
}
