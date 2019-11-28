package ai.mantik.executor.kubernetes.integration

import ai.mantik.executor.common.test.integration.DeployServiceSpecBase
import ai.mantik.executor.kubernetes.KubernetesConstants
import ai.mantik.executor.model.docker.Container
import ai.mantik.executor.model._
import ai.mantik.testutils.tags.IntegrationTest
import skuber.apps.v1.ReplicaSet
import skuber.json.format._
import skuber.{ ListResource, Secret }

@IntegrationTest
class DeployServiceSpec extends IntegrationTestBase with DeployServiceSpecBase {

  // this seems to be missing in skuber
  implicit val rsListFormat = skuber.json.format.ListResourceFormat[ReplicaSet]

  it should "should delete all kubernetes artifacts upon deletion" in new Env {
    val isolationSpace = "deploy-spec3"
    val ns = config.kubernetes.namespacePrefix + isolationSpace
    val nsClient = kubernetesClient.usingNamespace(ns)

    def replicaSetCount(): Int = {
      await(nsClient.list[ListResource[ReplicaSet]]()).size
    }

    def secretCount(): Int = {
      // there is also one default token secret inside minikube.
      await(nsClient.list[ListResource[Secret]]()).count(_.metadata.labels.contains(KubernetesConstants.TrackerIdLabel))
    }

    val deployRequest = DeployServiceRequest(
      "service1",
      isolationSpace = isolationSpace,
      service = ContainerService(
        main = Container(
          image = "mantikai/executor.sample_transformer"
        )
      )
    )

    val deployRequest2 = DeployServiceRequest(
      "service2",
      isolationSpace = isolationSpace,
      service = ContainerService(
        main = Container(
          image = "mantikai/executor.sample_transformer"
        )
      )
    )

    val response = await(executor.deployService(deployRequest))
    response.serviceName shouldNot be(empty)
    response.url shouldNot be(empty)

    val response2 = await(executor.deployService(deployRequest2))
    response2.serviceName shouldNot be(empty)
    response2.url shouldNot be(empty)

    replicaSetCount() shouldBe 2
    secretCount() shouldBe 2

    // correct id
    val deleteResponse4 = await(executor.deleteDeployedServices(
      DeployedServicesQuery(
        isolationSpace,
        serviceId = Some("service2")
      )
    ))
    deleteResponse4 shouldBe 1

    replicaSetCount() shouldBe 1
    secretCount() shouldBe 1

    // all
    val deleteResponse5 = await(executor.deleteDeployedServices(
      DeployedServicesQuery(
        isolationSpace
      )
    ))
    deleteResponse5 shouldBe 1

    await(executor.queryDeployedServices(DeployedServicesQuery(isolationSpace))).services shouldBe empty
    replicaSetCount() shouldBe 0
    secretCount() shouldBe 0
  }
}
