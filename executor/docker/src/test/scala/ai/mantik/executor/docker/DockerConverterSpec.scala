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
import ai.mantik.executor.docker.api.PullPolicy
import ai.mantik.executor.model.docker.Container
import ai.mantik.testutils.TestBase

class DockerConverterSpec extends TestBase {

  trait Env {
    val defaultConfig = DockerExecutorConfig.fromTypesafeConfig(typesafeConfig)
    lazy val commonConfig = defaultConfig.common
    lazy val config = defaultConfig.copy(
      common = commonConfig
    )
    lazy val defaultLabels = Map("foo" -> "bar")
    lazy val converter = new DockerConverter(config, "isolation1", "internalId", "userId")
  }

  "pullPolicy" should "work when pulling is disabled" in new Env {
    override lazy val commonConfig: CommonConfig = defaultConfig.common.copy(
      disablePull = true
    )
    converter.pullPolicy(
      Container("foo")
    ) shouldBe PullPolicy.Never

    converter.pullPolicy(
      Container("foo:latest")
    ) shouldBe PullPolicy.Never
  }

  it should "give out nice defaults" in new Env {
    require(!commonConfig.disablePull)
    converter.pullPolicy(
      Container("foo")
    ) shouldBe PullPolicy.Always

    converter.pullPolicy(
      Container("foo:latest")
    ) shouldBe PullPolicy.Always

    converter.pullPolicy(
      Container("foo:other")
    ) shouldBe PullPolicy.IfNotPresent
  }
}
