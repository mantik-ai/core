package ai.mantik.executor.impl

import java.time.Clock

import ai.mantik.executor.integration.{ IntegrationTestBase, KubernetesTestBase }
import ai.mantik.executor.testutils.KubernetesIntegrationTest
import play.api.libs.json.{ JsObject, Json }
import skuber.Pod.Phase
import skuber.api.client.Status
import skuber.{ Container, K8SException, Pod, RestartPolicy }
import skuber.json.format._

import scala.concurrent.Future

@KubernetesIntegrationTest
class K8sOperationsSpec extends KubernetesTestBase {

  trait Env extends super.Env {
    implicit val clock = Clock.systemUTC()
    val k8sOperations = new K8sOperations(config, kubernetesClient)
  }

  "ensureNamespace" should "work" in new Env {
    val myNamespace = config.namespacePrefix + "-ensure"
    val namespaces = await(kubernetesClient.getNamespaceNames)
    namespaces should not contain myNamespace
    val client1 = await(k8sOperations.ensureNamespace(myNamespace))

    val namespaces2 = await(kubernetesClient.getNamespaceNames)

    namespaces2 should contain(myNamespace)
    val client2 = withClue("It should not throw if the namespace already exists") {
      await(k8sOperations.ensureNamespace(myNamespace))
    }

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
    val ns = config.namespacePrefix + "startpod"
    val client = await(k8sOperations.ensureNamespace(ns))
    val result = await(k8sOperations.startPodsAndGetIpAdresses(Some(ns), Seq(pod, pod2)))
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
    val ns = config.namespacePrefix + "cancel"
    await(k8sOperations.ensureNamespace(ns))
    val pod = await(k8sOperations.create(Some(ns), podSpec))
    val namespaced = kubernetesClient.usingNamespace(ns)
    val podAgain = eventually {
      val podAgain = await(namespaced.getOption[Pod](pod.name))
      podAgain shouldBe defined
      podAgain.get.status.get.containerStatuses.exists(
        _.state.get.id == "running"
      ) shouldBe true
      podAgain.get
    }
    await(k8sOperations.cancelMantikPod(podAgain, "bam"))
    eventually {
      val podAgain = await(namespaced.getOption[Pod](pod.name))
      podAgain.get.status.get.phase.get shouldBe Phase.Failed
    }
  }

  "errorHandling" should "execute a regular function" in new Env {
    await(k8sOperations.errorHandling(Future.successful(5))) shouldBe 5
    val e = new RuntimeException("Boom")
    intercept[RuntimeException] {
      await(k8sOperations.errorHandling(Future.failed(e)))
    } shouldBe e
  }

  val retryException = new K8SException(
    status = Status(
      code = Some(500),
      details = Some(
        Json.obj(
          "retryAfterSeconds" -> 1
        )
      )
    )
  )

  it should "retry if k8s asks so" in new Env {
    var count = 0
    def failTwice(): Future[Int] = {
      count += 1
      if (count < 3) {
        Future.failed(retryException)
      } else {
        Future.successful(5)
      }
    }
    val t0 = System.currentTimeMillis()
    val result = await(k8sOperations.errorHandling(failTwice()))
    val t1 = System.currentTimeMillis()
    result shouldBe 5
    t1 - t0 shouldBe >=(2000L)
    count shouldBe 3
  }

  it should "stop retrying if it's too much" in new Env {
    var count = 0
    def failAlways(): Future[Int] = {
      count += 1
      Future.failed(retryException)
    }
    val t0 = System.currentTimeMillis()
    intercept[K8SException] {
      await(k8sOperations.errorHandling(failAlways()))
    } shouldBe retryException
    val t1 = System.currentTimeMillis()
    (t1 - t0) shouldBe >=(3000L)
    count shouldBe 4 // retried 3 times.
  }
}
