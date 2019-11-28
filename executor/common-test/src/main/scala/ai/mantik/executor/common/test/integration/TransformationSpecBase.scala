package ai.mantik.executor.common.test.integration

import ai.mantik.executor.model.{ ContainerService, ExecutorModelDefaults, Graph, Job, JobState, Link, Node, NodeResourceRef }
import ai.mantik.executor.model.docker.Container
import ai.mantik.testutils.TestBase

trait TransformationSpecBase {
  self: IntegrationBase with TestBase =>

  it should "work" in withExecutor { executor =>
    val job = Job(
      "transformer",
      graph = Graph(
        nodes = Map(
          "A" -> Node.source(
            ContainerService(
              main = Container(
                image = "mantikai/executor.sample_source"
              )
            )
          ),
          "B" -> Node.transformer(
            ContainerService(
              main = Container(
                image = "mantikai/executor.sample_transformer"
              )
            )
          ),
          "C" -> Node.sink(
            ContainerService(
              main = Container(
                image = "mantikai/executor.sample_sink"
              )
            )
          )
        ),
        links = Link.links(
          NodeResourceRef("A", ExecutorModelDefaults.SourceResource) -> NodeResourceRef("B", ExecutorModelDefaults.TransformationResource),
          NodeResourceRef("B", ExecutorModelDefaults.TransformationResource) -> NodeResourceRef("C", ExecutorModelDefaults.SinkResource)
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
