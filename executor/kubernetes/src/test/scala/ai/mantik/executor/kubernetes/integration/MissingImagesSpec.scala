package ai.mantik.executor.kubernetes.integration

import ai.mantik.executor.model._
import ai.mantik.executor.model.docker.Container
import ai.mantik.testutils.tags.IntegrationTest

@IntegrationTest
class MissingImagesSpec extends IntegrationTestBase {

  it should "fail correctly for missing images." in new Env {
    val job = Job (
      "missing1",
      graph = Graph(
        nodes = Map (
          "A" -> Node.source(
            ContainerService(
              main = Container(
                image = "missing_image1"
              )
            ),
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
      await(executor.logs(job.isolationSpace, jobId)) should include ("could not find image")
    }
  }

}
