package io.mantik.executor.impl

import io.mantik.executor.Config
import io.mantik.executor.model._
import io.mantik.executor.testutils.TestBase
import skuber.RestartPolicy

import scala.concurrent.duration._

class KubernetesJobConverterSpec extends TestBase {

  val config = Config().copy(
    sideCar = Container("my_sidecar", Seq("sidecar_arg")),
    coordinator = Container("my_coordinator", Seq("coordinator_arg")),
    namespacePrefix = "systemtest-",
    podTrackerId = "mantik-executor"
  )

  val simpleAbJob = Job(
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

  trait SimpleAbEnv {
    val converter = new KubernetesJobConverter(config, simpleAbJob, "job1")
  }

  it should "create nice pods" in new SimpleAbEnv {
    val pods = converter.pods
    pods.size shouldBe 2
    withClue("It should have disabled restart policy") {
      pods.foreach { pod =>
        pod.spec.get.restartPolicy shouldBe RestartPolicy.Never
      }
    }
    withClue("It should all have the job embedded") {
      pods.foreach { pod =>
        val labels = pod.metadata.labels
        labels shouldBe Map(
          "jobId" -> "job1",
          "trackerId" -> config.podTrackerId,
          "role" -> KubernetesJobConverter.WorkerRole
        )
      }
    }
    withClue("It should embed a sidecar for every one") {
      pods.foreach { pod =>
        val spec = pod.spec.get
        spec.containers.size shouldBe 2
        val sidecar = spec.containers.find(_.name == "sidecar").get
        sidecar.image shouldBe config.sideCar.image
        sidecar.args shouldBe Seq("sidecar_arg", "-url", "http://localhost:8502", "-shutdown")
      }
    }
  }

  it should "create a nice config ConfigMap" in new SimpleAbEnv {
    pending
  }

  it should "create a nice job" in new SimpleAbEnv {
    pending
  }
}
