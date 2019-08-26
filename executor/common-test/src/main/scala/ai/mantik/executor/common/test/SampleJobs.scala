package ai.mantik.executor.common.test

import ai.mantik.executor.model.docker.Container
import ai.mantik.executor.model.{ ContainerService, ExecutorModelDefaults, Graph, Job, Link, Node, NodeResourceRef }

object SampleJobs {
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
