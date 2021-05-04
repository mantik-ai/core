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

import java.nio.charset.StandardCharsets

import ai.mantik.executor.model.docker.{Container, DockerConfig, DockerLogin}
import ai.mantik.testutils.TestBase
import io.circe.Json

class KubernetesConverterSpec extends TestBase {

  "createImagePullPolicy" should "convert nice pull policies" in {
    KubernetesConverter.createImagePullPolicy(false, Container("foo")) shouldBe skuber.Container.PullPolicy.Always
    KubernetesConverter.createImagePullPolicy(
      false,
      Container("foo:latest")
    ) shouldBe skuber.Container.PullPolicy.Always
    KubernetesConverter.createImagePullPolicy(
      false,
      Container("foo:master")
    ) shouldBe skuber.Container.PullPolicy.IfNotPresent
    KubernetesConverter.createImagePullPolicy(
      false,
      Container("foo:other")
    ) shouldBe skuber.Container.PullPolicy.IfNotPresent
  }

  it should "disable pulling if requested" in {
    KubernetesConverter.createImagePullPolicy(true, Container("foo")) shouldBe skuber.Container.PullPolicy.Never
    KubernetesConverter.createImagePullPolicy(true, Container("foo:latest")) shouldBe skuber.Container.PullPolicy.Never
    KubernetesConverter.createImagePullPolicy(true, Container("foo:other")) shouldBe skuber.Container.PullPolicy.Never
  }

  "pullSecrets" should "convert pull screts" in {
    val oldConfig = Config.fromTypesafeConfig(typesafeConfig)
    val config = oldConfig.copy(
      common = oldConfig.common.copy(
        dockerConfig = DockerConfig(
          defaultImageTag = Some("mytag"),
          defaultImageRepository = Some("my-repo"),
          logins = Seq(
            DockerLogin("repo1", "user1", "password1")
          )
        )
      ),
      kubernetes = oldConfig.kubernetes.copy(
        namespacePrefix = "systemtest-"
      )
    )
    val secrets = KubernetesConverter
      .pullSecret(
        config,
        extraLogins = Seq(
          DockerLogin("repo2", "user2", "password2")
        )
      )
      .get

    val value = secrets.data.ensuring(_.size == 1)(".dockerconfigjson")
    val parsed = io.circe.parser.parse(new String(value, StandardCharsets.UTF_8)).right.get
    parsed shouldBe Json.obj(
      "auths" -> Json.obj(
        "repo1" -> Json.obj(
          "username" -> Json.fromString("user1"),
          "password" -> Json.fromString("password1")
        ),
        "repo2" -> Json.obj(
          "username" -> Json.fromString("user2"),
          "password" -> Json.fromString("password2")
        )
      )
    )
  }

}
