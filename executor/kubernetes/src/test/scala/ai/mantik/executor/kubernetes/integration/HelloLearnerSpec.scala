package ai.mantik.executor.kubernetes.integration

import ai.mantik.executor.model._
import ai.mantik.executor.model.docker.Container
import ai.mantik.testutils.tags.IntegrationTest

@IntegrationTest
class HelloLearnerSpec extends IntegrationTestBase {

  it should "work" in new Env {
    val job = Job(
      "learner",
      graph = Graph(
        nodes = Map(
          "A" -> Node.source(
            ContainerService(
              main = Container(
                image = "mantikai/executor.sample_source"
              )
            )
          ),
          "B" -> Node(
            ContainerService(
              main = Container(
                image = "mantikai/executor.sample_learner"
              )
            ),
            resources = Map(
              "in" -> NodeResource(ResourceType.Sink),
              "status" -> NodeResource(ResourceType.Source),
              "result" -> NodeResource(ResourceType.Source)
            )
          ),
          "C" -> Node.sink(
            ContainerService(
              main = Container(
                image = "mantikai/executor.sample_sink"
              )
            )
          ),
          "D" -> Node.sink(
            ContainerService(
              main = Container(
                image = "mantikai/executor.sample_sink"
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

