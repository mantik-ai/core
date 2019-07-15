package ai.mantik.executor.kubernetes.integration

import ai.mantik.executor.kubernetes.Config
import ai.mantik.executor.model.docker.Container
import ai.mantik.executor.model.{ ContainerService, ExecutorModelDefaults, Graph, Job, Link, Node, NodeResourceRef }

class SampleJobs(config: Config) {
  val job = Job(
    "helloworld",
    graph = Graph(
      nodes = Map(
        "A" -> Node.source(
          ContainerService(
            main = config.dockerConfig.resolveContainer(Container(
              image = "executor_sample_source"
            ))
          )
        ),
        "B" -> Node.sink(
          ContainerService(
            main = config.dockerConfig.resolveContainer(Container(
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
