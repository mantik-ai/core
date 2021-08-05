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
package ai.mantik.executor.kubernetes

import ai.mantik.executor.common.CommonConfig
import ai.mantik.executor.model.docker.DockerConfig
import com.typesafe.config.{Config => TypesafeConfig}

/**
  * Configuration for the execution.
  *
  * @param common common executor settings.
  * @param kubernetes kubernetes specific settings
  */
case class Config(
    common: CommonConfig,
    kubernetes: KubernetesConfig
) {
  def dockerConfig: DockerConfig = common.dockerConfig

  /** Returns the namespace to use. */
  def namespace: String = kubernetes.namespacePrefix + common.isolationSpace
}

object Config {

  /** Load settings from Config. */
  def fromTypesafeConfig(c: TypesafeConfig): Config = {
    val root = c.getConfig("mantik.executor")
    val commonConfig = CommonConfig.fromTypesafeConfig(c)
    Config(
      common = commonConfig,
      kubernetes = KubernetesConfig.fromTypesafe(root.getConfig("kubernetes"))
    )
  }
}
