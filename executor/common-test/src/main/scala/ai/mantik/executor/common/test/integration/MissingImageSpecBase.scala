package ai.mantik.executor.common.test.integration

import ai.mantik.executor.model.docker.Container
import ai.mantik.executor.model.{ ContainerService, ExecutorModelDefaults, Graph, Job, JobState, Link, Node, NodeResourceRef }
import ai.mantik.testutils.TestBase

trait MissingImageSpecBase {
  self: IntegrationBase with TestBase =>

  it should "fail correctly for missing images." in withExecutor { executor =>
    val job = Job(
      "missing1",
      graph = Graph(
        nodes = Map(
          "A" -> Node.source(
            ContainerService(
              main = Container(
                image = "missing_image1"
              )
            )
          ),
          "B" -> Node.sink(
            ContainerService(
              main = Container(
                image = "missing_image2"
              )
            )
          )
        ),
        links = Link.links(
          NodeResourceRef("A", ExecutorModelDefaults.SourceResource) -> NodeResourceRef("B", ExecutorModelDefaults.SinkResource)
        )
      )
    )
    val jobId = await(executor.schedule(job))
    eventually {
      await(executor.status(job.isolationSpace, jobId)).state shouldBe JobState.Failed
    }
    eventually {
      val log = await(executor.logs(job.isolationSpace, jobId)).toLowerCase()
      val ok = log.contains("could not find image") || log.contains("no such image")
      withClue(s"Log ${log} should indicate image error") {
        ok shouldBe true
      }
    }
  }
}
