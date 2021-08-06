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
package ai.mantik.executor.docker.integration

import ai.mantik.executor.common.LabelConstants
import ai.mantik.executor.docker.api.DockerOperations
import ai.mantik.executor.docker.api.structures.CreateContainerRequest

class DockerOperationsSpec extends IntegrationTestBase {

  val createRequest = CreateContainerRequest(
    Image = "mantikai/bridge.binary",
    Labels = Map(
      LabelConstants.ManagedByLabelName -> LabelConstants.ManagedByLabelValue
    )
  )

  trait Env {
    val operations = new DockerOperations(dockerClient)
  }

  "ensureContainer" should "create a container if missing" in new Env {
    val id = await(operations.ensureContainer("dummy1", createRequest))
    val all = await(dockerClient.listContainers(false))
    val existing = all.find(_.Id == id).get
    existing.State shouldBe "running"
    existing.Image should startWith(createRequest.Image)
  }

  it should "do nothing if the container exists with running" in new Env {
    val id1 = await(operations.ensureContainer("dummy2", createRequest))
    val id2 = await(operations.ensureContainer("dummy2", createRequest))
    id2 shouldBe id1
  }

  it should "recreate the container if already present" in new Env {
    val id = await(operations.ensureContainer("dummy3", createRequest))
    await(dockerClient.killContainer(id))
    val id2 = await(operations.ensureContainer("dummy3", createRequest))
    id2 shouldNot be(id)
  }
}
