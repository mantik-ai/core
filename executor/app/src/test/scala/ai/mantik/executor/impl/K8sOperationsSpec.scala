package ai.mantik.executor.impl

import java.time.Clock

import ai.mantik.executor.integration.{ IntegrationTestBase, KubernetesTestBase }
import ai.mantik.executor.testutils.KubernetesIntegrationTest
import skuber.Pod.Phase
import skuber.{ Container, Pod, RestartPolicy }
import skuber.json.format._

@KubernetesIntegrationTest
class K8sOperationsSpec extends KubernetesTestBase {

  trait Env extends super.Env {
    val k8sOperations = new K8sOperations(Clock.systemUTC(), config)
  }

  "ensureNamespace" should "work" in new Env {
    val myNamespace = config.namespacePrefix + "-ensure"
    val namespaces = await(kubernetesClient.getNamespaceNames)
    namespaces should not contain myNamespace
    val client1 = await(k8sOperations.ensureNamespace(kubernetesClient, myNamespace))

    client1.namespaceName shouldBe myNamespace
    val namespaces2 = await(kubernetesClient.getNamespaceNames)

    namespaces2 should contain(myNamespace)
    val client2 = withClue("It should not throw if the namespace already exists") {
      await(k8sOperations.ensureNamespace(kubernetesClient, myNamespace))
    }

    client2.namespaceName shouldBe myNamespace
    val namespaces3 = await(kubernetesClient.getNamespaceNames)
    namespaces3 should contain(myNamespace)
  }

  "startPodAndGetIpAddress" should "work" in new Env {
    val pod = Pod.apply(
      "pod1", Pod.Spec(
        containers = List(
          Container(
            name = "sidecar1",
            image = config.sideCar.image,
            args = config.sideCar.parameters.toList ++ List("--url", "http://localhost:8042")
          )
        ),
        restartPolicy = RestartPolicy.Never
      )
    )
    val pod2 = pod.copy(metadata = pod.metadata.copy(name = "pod2"))
    val client = await(k8sOperations.ensureNamespace(kubernetesClient, config.namespacePrefix + "startpod"))
    val result = await(k8sOperations.startPodsAndGetIpAdresses(client, Seq(pod, pod2)))
    result.size shouldBe 2
    result.keys.toSet shouldBe Set("pod1", "pod2")
    result.values.toSeq.distinct.size shouldBe 2
  }

  "cancelMantikPod" should "work" in new Env {
    val podSpec = Pod.apply(
      "pod1", Pod.Spec(
        containers = List(
          Container(
            name = KubernetesJobConverter.SidecarContainerName,
            image = config.sideCar.image,
            args = config.sideCar.parameters.toList ++ List("--url", "http://localhost:8042")
          )
        ),
        restartPolicy = RestartPolicy.Never
      )
    )
    val namespaced = await(k8sOperations.ensureNamespace(kubernetesClient, config.namespacePrefix + "cancel"))
    val pod = await(namespaced.create(podSpec))
    val podAgain = eventually {
      val podAgain = await(namespaced.getOption[Pod](pod.name))
      podAgain shouldBe defined
      podAgain.get.status.get.containerStatuses.exists(
        _.state.get.id == "running"
      ) shouldBe true
      podAgain.get
    }
    await(k8sOperations.cancelMantikPod(kubernetesClient, podAgain, "bam"))
    eventually {
      val podAgain = await(namespaced.getOption[Pod](pod.name))
      podAgain.get.status.get.phase.get shouldBe Phase.Failed
    }
  }
}
