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
package ai.mantik.executor.kubernetes.integration

import ai.mantik.executor.Errors
import ai.mantik.executor.model.PublishServiceRequest
import skuber.json.format._
import skuber.{Endpoints, ListResource, Service}

class PublishServiceSpec extends IntegrationTestBase {

  it should "allow publishing ip address services" in new Env {
    val request = PublishServiceRequest(
      "service1",
      4000,
      "192.168.1.1",
      4001
    )
    val response = await(executor.publishService(request))
    response.name shouldBe s"service1.${config.namespace}.svc.cluster.local:4000"
    val service = await(kubernetesClient.getInNamespace[Service]("service1", config.namespace))
    val port = service.spec.get.ports.ensuring(_.size == 1).head
    port.port shouldBe 4000
    service.spec.get.externalName shouldBe ""

    val endpoints = await(kubernetesClient.getInNamespace[Endpoints]("service1", config.namespace))
    val subset = endpoints.subsets.ensuring(_.size == 1).head
    subset.addresses shouldBe List(
      Endpoints.Address("192.168.1.1")
    )
    subset.ports shouldBe List(
      Endpoints.Port(4001, name = Some("port4000"))
    )

    withClue("It should be possible to change the service") {
      val response2 = await(executor.publishService(request))
      response2 shouldBe response
    }
  }

  it should "allow publishing service name services" in new Env {
    val response = await(
      executor.publishService(
        PublishServiceRequest(
          "service1",
          4001,
          "myserver.example.com",
          4001
        )
      )
    )
    response.name shouldBe s"service1.${config.kubernetes.namespacePrefix}${config.common.isolationSpace}.svc.cluster.local:4001"
    val service = await(kubernetesClient.getInNamespace[Service]("service1", config.namespace))
    val port = service.spec.get.ports.ensuring(_.size == 1).head
    port.port shouldBe 4001
    service.spec.get.externalName shouldBe "myserver.example.com"

    val endpoints = await(kubernetesClient.listInNamespace[ListResource[Endpoints]](config.namespace))
    endpoints.items shouldBe empty
  }

  it should "deny service names with different ports" in new Env {
    awaitException[Errors.BadRequestException] {
      executor.publishService(
        PublishServiceRequest(
          "service1",
          4002,
          "myserver.example.com",
          4003
        )
      )
    }
  }
}
