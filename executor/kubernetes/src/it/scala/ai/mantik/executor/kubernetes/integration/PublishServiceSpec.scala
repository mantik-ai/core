package ai.mantik.executor.kubernetes.integration

import ai.mantik.executor.Errors
import ai.mantik.executor.model.PublishServiceRequest
import skuber.json.format._
import skuber.{Endpoints, ListResource, Service}

class PublishServiceSpec extends IntegrationTestBase {

  it should "allow publishing ip address services" in new Env {
    val request = PublishServiceRequest(
      "space",
      "service1",
      4000,
      "192.168.1.1",
      4001
    )
    val response = await(executor.publishService(request))
    response.name shouldBe s"service1.${config.kubernetes.namespacePrefix}space.svc.cluster.local:4000"
    val ns = config.kubernetes.namespacePrefix + "space"
    val service = await(kubernetesClient.getInNamespace[Service]("service1", ns))
    val port = service.spec.get.ports.ensuring(_.size == 1).head
    port.port shouldBe 4000
    service.spec.get.externalName shouldBe ""

    val endpoints = await(kubernetesClient.getInNamespace[Endpoints]("service1", ns))
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
    val response = await(executor.publishService(
      PublishServiceRequest(
        "space2",
        "service1",
        4000,
        "myserver.example.com",
        4000
      )
    ))
    response.name shouldBe s"service1.${config.kubernetes.namespacePrefix}space2.svc.cluster.local:4000"
    val ns = config.kubernetes.namespacePrefix + "space2"
    val service = await(kubernetesClient.getInNamespace[Service]("service1", ns))
    val port = service.spec.get.ports.ensuring(_.size == 1).head
    port.port shouldBe 4000
    service.spec.get.externalName shouldBe "myserver.example.com"

    val endpoints = await(kubernetesClient.listInNamespace[ListResource[Endpoints]](ns))
    endpoints.items shouldBe empty
  }

  it should "deny service names with different ports" in new Env {
    intercept[Errors.BadRequestException] {
      await(executor.publishService(
        PublishServiceRequest(
          "space3",
          "service1",
          4000,
          "myserver.example.com",
          4001
        )
      ))
    }
  }
}
