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

import ai.mantik.executor.common.CommonConfig
import com.typesafe.config.Config

/** Configuration for Docker Executor. */
case class DockerExecutorConfig(
    common: CommonConfig,
    ingress: IngressConfig,
    workerNetwork: String
)

object DockerExecutorConfig {
  def fromTypesafeConfig(config: Config): DockerExecutorConfig = {
    DockerExecutorConfig(
      common = CommonConfig.fromTypesafeConfig(config),
      ingress = IngressConfig.fromTypesafeConfig(config),
      workerNetwork = config.getString("mantik.executor.docker.workerNetwork")
    )
  }
}

case class IngressConfig(
    ensureTraefik: Boolean,
    traefikImage: String,
    traefikContainerName: String,
    traefikPort: Int,
    labels: Map[String, String],
    remoteUrl: String
)

object IngressConfig {
  def fromTypesafeConfig(config: Config): IngressConfig = {
    import ai.mantik.componently.utils.ConfigExtensions._
    val path = "mantik.executor.docker.ingress"
    val subConfig = config.getConfig(path)
    IngressConfig(
      ensureTraefik = subConfig.getBoolean("ensureTraefik"),
      traefikImage = subConfig.getString("traefikImage"),
      traefikContainerName = subConfig.getString("traefikContainerName"),
      traefikPort = subConfig.getInt("traefikPort"),
      labels = subConfig.getKeyValueMap("labels"),
      remoteUrl = subConfig.getString("remoteUrl")
    )
  }
}
