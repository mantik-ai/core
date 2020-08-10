package ai.mantik.executor.kubernetes.integration

import java.time.Clock
import java.util.UUID

import ai.mantik.executor.kubernetes.{K8sOperations, KubernetesConstants}
import ai.mantik.testutils.tags.IntegrationTest
import play.api.libs.json.Json
import skuber.Pod.Phase
import skuber.api.client.Status
import skuber.apps.Deployment
import skuber.batch.Job
import skuber.json.batch.format._
import skuber.json.format._
import skuber.{Container, K8SException, ObjectMeta, Pod, RestartPolicy}

import scala.concurrent.Future

class K8sOperationsSpec extends KubernetesTestBase {

  trait Env extends super.Env {
    implicit val clock = Clock.systemUTC()
    val k8sOperations = new K8sOperations(config, kubernetesClient)
  }

  // A pod which will hang around
  val longRunningPod = Pod.Spec(
    containers = List(
      Container(
        name = KubernetesConstants.SidecarContainerName,
        image = config.common.sideCar.image,
        args = config.common.sideCar.parameters.toList ++ List("--url", "http://localhost:8042")
      )
    ),
    restartPolicy = RestartPolicy.Never
  )

  val shortRunningPod = Pod.Spec(
    containers = List(
      Container(
        name = "main",
        image = "hello-world"
      )
    ),
    restartPolicy = RestartPolicy.Never
  )

  "ensureNamespace" should "work" in new Env {
    val myNamespace = config.kubernetes.namespacePrefix + "-ensure"
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

  "getNamespace" should "work" in new Env {
    val myNamespace = config.kubernetes.namespacePrefix + "-get"
    await(k8sOperations.getNamespace(myNamespace)) shouldBe empty
    await(k8sOperations.ensureNamespace(myNamespace))
    val trial2 = await(k8sOperations.getNamespace(myNamespace))
    trial2.map(_.name) shouldBe Some(myNamespace)
  }

  "startPodAndGetIpAddress" should "work" in new Env {
    val pod = Pod.apply(
      "pod1", Pod.Spec(
        containers = List(
          Container(
            name = "sidecar1",
            image = config.common.sideCar.image,
            args = config.common.sideCar.parameters.toList ++ List("--url", "http://localhost:8042")
          )
        ),
        restartPolicy = RestartPolicy.Never
      )
    )
    val pod2 = pod.copy(metadata = pod.metadata.copy(name = "pod2"))
    val ns = config.kubernetes.namespacePrefix + "startpod"
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
            name = KubernetesConstants.SidecarContainerName,
            image = config.common.sideCar.image,
            args = config.common.sideCar.parameters.toList ++ List("--url", "http://localhost:8042")
          )
        ),
        restartPolicy = RestartPolicy.Never
      )
    )
    val ns = config.kubernetes.namespacePrefix + "cancel"
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

  "getManagedNonFinishedJobs" should "return managed jobs" in new Env {
    val ns = config.kubernetes.namespacePrefix + "managed"
    await(k8sOperations.ensureNamespace(ns))
    await(k8sOperations.getManagedNonFinishedJobs(Some(ns))) shouldBe empty

    val job1 = Job(
      metadata = ObjectMeta(
        name = "job1",
        labels = Map(
          KubernetesConstants.TrackerIdLabel -> config.podTrackerId,
          KubernetesConstants.ManagedLabel -> KubernetesConstants.ManagedValue
        )
      ),
      spec = Some(Job.Spec(backoffLimit = Some(1)))
    ).withTemplate(
        Pod.Template.Spec(
          spec = Some(longRunningPod)
        )
      )
    val job2 = Job(
      metadata = ObjectMeta(
        name = "job2",
        labels = Map(
          KubernetesConstants.TrackerIdLabel -> "other_id",
          KubernetesConstants.ManagedLabel -> KubernetesConstants.ManagedValue
        )
      ),
      spec = Some(Job.Spec(backoffLimit = Some(1)))
    ).withTemplate(
        Pod.Template.Spec(
          spec = Some(longRunningPod)
        )
      )
    await(k8sOperations.create(Some(ns), job2))
    await(k8sOperations.create(Some(ns), job1))
    eventually {
      await(k8sOperations.getManagedNonFinishedJobs(Some(ns))).map(_.name) shouldBe List("job1")
    }
    eventually {
      await(k8sOperations.getManagedNonFinishedJobsForAllNamespaces()).filter(_.namespace == ns).map(_.name) shouldBe List("job1")
    }
  }

  it should "not return finished jobs" in new Env {
    val ns = config.kubernetes.namespacePrefix + "managed2"
    await(k8sOperations.ensureNamespace(ns))
    await(k8sOperations.getManagedNonFinishedJobs(Some(ns))) shouldBe empty

    val job1 = Job(
      metadata = ObjectMeta(
        name = "job1",
        labels = Map(
          KubernetesConstants.JobIdLabel -> "job1",
          KubernetesConstants.TrackerIdLabel -> config.podTrackerId,
          KubernetesConstants.ManagedLabel -> KubernetesConstants.ManagedValue
        )
      ),
      spec = Some(Job.Spec(backoffLimit = Some(1)))
    ).withTemplate(
        Pod.Template.Spec(
          spec = Some(shortRunningPod)
        )
      )
    await(k8sOperations.create(Some(ns), job1))
    eventually {
      await(k8sOperations.getJobById(Some(ns), "job1")).get.status.get.succeeded.get shouldBe >(0)
    }
    await(k8sOperations.getManagedNonFinishedJobs(Some(ns))) shouldBe empty
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

  "deploymentSetReplicas" should "work" in new Env {
    import skuber.json.apps.format.depFormat
    val ns = config.kubernetes.namespacePrefix + "scaletest"
    await(k8sOperations.ensureNamespace(ns))

    val labels = Map(
      KubernetesConstants.InternalId -> UUID.randomUUID().toString
    )

    val deployment = Deployment(
      metadata = ObjectMeta(
        generateName = "deplscaletest1",
        labels = labels
      ),
      spec = Some(
        Deployment.Spec(
          template = Some(
            Pod.Template.Spec(
              metadata = ObjectMeta(
                labels = labels
              ),
              spec = Some(longRunningPod.withRestartPolicy(RestartPolicy.Always))
            )
          )
        )
      )
    )
    deployment.spec.get.replicas.get shouldBe 1
    val deployed = await(
      k8sOperations.create(Some(ns), deployment)
    )
    deployed.spec.get.replicas.get shouldBe 1
    val updated = await(
      k8sOperations.deploymentSetReplicas(Some(ns), deployed.name, 2)
    )
    updated.spec.get.replicas.get shouldBe 2
    val getAgain = await(
      k8sOperations.byName[Deployment](Some(ns), deployed.name)
    )
    getAgain.get.spec.get.replicas.get shouldBe 2
  }
}
