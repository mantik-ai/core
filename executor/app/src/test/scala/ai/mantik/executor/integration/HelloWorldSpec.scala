package ai.mantik.executor.integration

import ai.mantik.executor.impl.KubernetesJobConverter
import ai.mantik.executor.model._
import ai.mantik.executor.model.docker.{ Container, DockerConfig }
import ai.mantik.executor.testutils.KubernetesIntegrationTest
import com.typesafe.config.ConfigFactory
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
  val dockerConfig = DockerConfig.parseFromConfig(ConfigFactory.load().getConfig("docker"))

  val job = Job(
    "helloworld",
    graph = Graph(
      nodes = Map(
        "A" -> Node.source(
          ContainerService(
            main = dockerConfig.resolveContainer(Container(
              image = "executor_sample_source"
            ))
          )
        ),
        "B" -> Node.sink(
          ContainerService(
            main = dockerConfig.resolveContainer(Container(
              image = "executor_sample_sink"
            ))
          )
        )
      ),
      links = Link.links(
        NodeResourceRef("A", ExecutorModelDefaults.SourceResource) -> NodeResourceRef("B", ExecutorModelDefaults.SinkResource)
      )
    )
  )
}