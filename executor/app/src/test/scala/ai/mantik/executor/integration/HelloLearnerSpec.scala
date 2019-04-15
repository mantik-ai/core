package ai.mantik.executor.integration

import ai.mantik.executor.model._
import ai.mantik.executor.model.docker.Container
import ai.mantik.executor.testutils.KubernetesIntegrationTest

@KubernetesIntegrationTest
class HelloLearnerSpec extends IntegrationTestBase {

  it should "work" in new Env {
    val job = Job(
      "learner",
      graph = Graph(
        nodes = Map(
          "A" -> Node.source(
            ContainerService(
              main = Container(
                image = "executor_sample_source"
              )
            )
          ),
          "B" -> Node(
            ContainerService(
              main = Container(
                image = "executor_sample_learner"
              )
            ),
            resources = Map(
              "in" -> ResourceType.Sink,
              "status" -> ResourceType.Source,
              "result" -> ResourceType.Source
            )
          ),
          "C" -> Node.sink(
            ContainerService(
              main = Container(
                image = "executor_sample_sink"
              )
            )
          ),
          "D" -> Node.sink(
            ContainerService(
              main = Container(
                image = "executor_sample_sink"
              )
            )
          )
        ),
        links = Link.links(
          NodeResourceRef("A", ExecutorModelDefaults.SourceResource) -> NodeResourceRef("B", "in"),
          NodeResourceRef("B", "status") -> NodeResourceRef("C", ExecutorModelDefaults.SinkResource),
          NodeResourceRef("B", "result") -> NodeResourceRef("D", ExecutorModelDefaults.SinkResource)
        )
      )
    )
    val jobId = await(executor.schedule(job))

    val status = eventually {
      val status = await(executor.status(job.isolationSpace, jobId))
      status.state shouldBe JobState.Finished
      status
    }
  }
}

