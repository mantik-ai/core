package ai.mantik.executor.kubernetes.integration

import ai.mantik.executor.common.test.SampleJobs
import ai.mantik.executor.kubernetes.KubernetesConstants
import ai.mantik.executor.model._
import ai.mantik.executor.model.docker.{ Container, DockerConfig }
import ai.mantik.testutils.tags.IntegrationTest
import com.typesafe.config.ConfigFactory
import skuber.json.format._
import skuber.{ LabelSelector, ListResource, Pod }

/** Says hello to coordinator, sidecar and the sample docker containers. */
@IntegrationTest
class HelloWorldSpec extends IntegrationTestBase {

  val job = SampleJobs.job

  it should "work" in new Env {
    val jobId = await(executor.schedule(job))

    val status = eventually {
      val status = await(executor.status(job.isolationSpace, jobId))
      status.state shouldBe JobState.Finished
      status
    }

    withClue("All pods should end now") {
      eventually {
        val namespacedClient = kubernetesClient.usingNamespace(config.kubernetes.namespacePrefix + "helloworld")
        val pods = await(namespacedClient.listSelected[ListResource[skuber.Pod]](
          LabelSelector(
            LabelSelector.IsEqualRequirement(KubernetesConstants.TrackerIdLabel, config.podTrackerId)
          )
        ))
        pods.size shouldBe 3 // A, B and Coordinator
        pods.foreach { pod =>
          pod.status.get.phase.get shouldBe Pod.Phase.Succeeded
        }
      }
    }
  }
}
