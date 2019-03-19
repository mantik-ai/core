package io.mantik.executor.integration

import io.mantik.executor.impl.KubernetesJobConverter
import io.mantik.executor.model._
import io.mantik.executor.testutils.KubernetesIntegrationTest
import skuber.{ LabelSelector, ListResource, Pod }
import skuber.json.format._

/** Says hello to coordinator, sidecar and the sample docker containers. */
@KubernetesIntegrationTest
class HelloWorldSpec extends IntegrationTestBase {

  it should "work" in new Env {
    val jobId = await(executor.schedule(HelloWorldSpec.job))

    val status = eventually {
      val status = await(executor.status(HelloWorldSpec.job.isolationSpace, jobId))
      status.state shouldBe JobState.Finished
      status
    }

    withClue("All pods should end now") {
      eventually {
        val namespacedClient = kubernetesClient.usingNamespace(config.namespacePrefix + "helloworld")
        val pods = await(namespacedClient.listSelected[ListResource[skuber.Pod]](
          LabelSelector(
            LabelSelector.IsEqualRequirement(KubernetesJobConverter.TrackerIdLabel, config.podTrackerId)
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

object HelloWorldSpec {
  val job = Job(
    "helloworld",
    graph = Graph(
      nodes = Map(
        "A" -> Node.source(
          ContainerService(
            main = Container(
              image = "executor_sample_source"
            )
          )
        ),
        "B" -> Node.sink(
          ContainerService(
            main = Container(
              image = "executor_sample_sink"
            )
          )
        )
      ),
      links = Link.links(
        NodeResourceRef("A", ExecutorModelDefaults.SourceResource) -> NodeResourceRef("B", ExecutorModelDefaults.SinkResource)
      )
    )
  )
}