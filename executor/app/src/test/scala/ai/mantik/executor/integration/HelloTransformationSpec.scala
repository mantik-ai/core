package ai.mantik.executor.integration

import ai.mantik.executor.model._
import ai.mantik.executor.model.docker.Container
import ai.mantik.executor.testutils.KubernetesIntegrationTest

@KubernetesIntegrationTest
class HelloTransformationSpec extends IntegrationTestBase {

  it should "work" in new Env {
    val job = Job(
      "transformer",
      graph = Graph(
        nodes = Map(
          "A" -> Node.source(
            ContainerService(
              main = Container(
                image = "executor_sample_source"
              )
            )
          ),
          "B" -> Node.transformer(
            ContainerService(
              main = Container(
                image = "executor_sample_transformer"
              )
            )
          ),
          "C" -> Node.sink(
            ContainerService(
              main = Container(
                image = "executor_sample_sink"
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

