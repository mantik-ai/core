package ai.mantik.executor.model

import ai.mantik.testutils.TestBase

class GraphAnalysisSpec extends TestBase {

  it should "be possible to describe a simple copy op" in {
    val job = Job(
      isolationSpace = "user1",
      graph = Graph(
        nodes = Map(
          "loader" -> Node.source(
            ContainerService(
              main = Container(
                image = "mantik:dataset_loader",
                parameters = Seq("mantik-resource-url1")
              )
            )
          ),
          "saver" -> Node.sink(
            ContainerService(
              main = Container(
                image = "mantik:dataset_saver",
                parameters = Seq("mantik-resource-url2")
              )
            )
          )
        ),
        links = Seq(
          Link(NodeResourceRef.source("loader") -> NodeResourceRef.sink("saver"))
        )
      )
    )
    val analysis = new GraphAnalysis(job.graph)
    analysis.flows shouldBe Set(
      GraphAnalysis.Flow(Seq(NodeResourceRef("loader", ExecutorModelDefaults.SourceResource), NodeResourceRef("saver", ExecutorModelDefaults.SinkResource)))
    )
  }

  it should "be possible to describe a simple algorithm" in {
    val job = Job(
      isolationSpace = "user1",
      graph = Graph(
        nodes = Map(
          "loader" -> Node.source(
            ContainerService(
              main = Container(
                image = "mantik:dataset_loader",
                parameters = Seq("mantik-resource-url1")
              ),
              ioAffine = true
            )
          ),
          "algo1" -> Node.transformer(
            ContainerService(
              main = Container(
                image = "mantik:algorithm"
              ),
              dataProvider = Some(Container(
                image = "mantik:algorithm_loader",
                parameters = Seq("mantik-algorithm-id")
              ))
            )
          ),
          "saver" -> Node.sink(
            ContainerService(
              main = Container(
                image = "mantik:dataset_saver",
                parameters = Seq("mantik-resource-url2")
              ),
              ioAffine = true
            )
          )
        ),
        links = Seq(
          Link(NodeResourceRef.source("loader") -> NodeResourceRef.transformation("algo1")),
          Link(NodeResourceRef.transformation("algo1") -> NodeResourceRef.sink("saver"))
        )
      )
    )
    val analysis = new GraphAnalysis(job.graph)
    analysis.flows shouldBe Set(
      GraphAnalysis.Flow.fromRefs(
        NodeResourceRef("loader", ExecutorModelDefaults.SourceResource),
        NodeResourceRef("algo1", ExecutorModelDefaults.TransformationResource),
        NodeResourceRef("saver", ExecutorModelDefaults.SinkResource)
      )
    )
  }

  it should "be possible to describe learning algorithms" in {
    val job = Job(
      isolationSpace = "user1",
      graph = Graph(
        nodes = Map(
          "train_data" -> Node.source(
            ContainerService(
              main = Container(
                image = "mantik:dataset_loader",
                parameters = Seq("mantik-resource-url1")
              )
            )
          ),
          "trainable_algorithm" -> Node(
            ContainerService(
              main = Container(
                image = "mantik:sk_algorithms",
                parameters = Seq("algorithm1")
              )
            ),
            resources = Map(
              "train_in" -> ResourceType.Sink,
              "stats" -> ResourceType.Source,
              "trained_out" -> ResourceType.Source
            )
          ),
          "trained_saver" -> Node.sink(
            ContainerService(
              main = Container(
                image = "mantik:saver"
              )
            )
          ),
          "trained_stat_saver" -> Node.sink(
            ContainerService(
              main = Container(
                image = "mantik:saver"
              )
            )
          )
        ),
        links = Link.links(
          NodeResourceRef.source("train_data") -> NodeResourceRef("trainable_algorithm", "train_in"),
          NodeResourceRef("trainable_algorithm", "stats") -> NodeResourceRef.sink("trained_stat_saver"),
          NodeResourceRef("trainable_algorithm", "trained_out") -> NodeResourceRef.sink("trained_saver")
        )
      )
    )
    val analysis = new GraphAnalysis(job.graph)
    analysis.flows shouldBe Set(
      GraphAnalysis.Flow.fromRefs(
        NodeResourceRef("trainable_algorithm", "trained_out"),
        NodeResourceRef("trained_saver", ExecutorModelDefaults.SinkResource),
      ),
      GraphAnalysis.Flow.fromRefs(
        NodeResourceRef("trainable_algorithm", "stats"),
        NodeResourceRef("trained_stat_saver", ExecutorModelDefaults.SinkResource)
      ),
      GraphAnalysis.Flow.fromRefs(
        NodeResourceRef("train_data", ExecutorModelDefaults.SourceResource),
        NodeResourceRef("trainable_algorithm", "train_in")
      )
    )
  }

  it should "detect cycles" in {
    val graph = Graph(
      nodes = Map(
        "A" -> Node.sink(ExistingService("url1")),
        "B" -> Node.transformer(ExistingService("url2")),
        "C" -> Node.transformer(ExistingService("url3")),
        "D" -> Node.source(ExistingService("url4"))
      ),
      links = Link.links(
        NodeResourceRef("B", ExecutorModelDefaults.TransformationResource) -> NodeResourceRef("A", ExecutorModelDefaults.SinkResource),
        NodeResourceRef("C", ExecutorModelDefaults.TransformationResource) -> NodeResourceRef("B", ExecutorModelDefaults.TransformationResource),
        NodeResourceRef("B", ExecutorModelDefaults.TransformationResource) -> NodeResourceRef("C", ExecutorModelDefaults.TransformationResource)
      )
    )
    intercept[GraphAnalysis.CycleDetectedException] {
      new GraphAnalysis(graph)
    }
  }

  it should "detect unreachable nodes" in {
    val graph = Graph(
      nodes = Map(
        "A" -> Node.sink(ExistingService("url1")),
        "B" -> Node.source(ExistingService("url2"))
      ),
      links = Seq.empty
    )
    intercept[GraphAnalysis.UnreachableNodeDetected] {
      new GraphAnalysis(graph)
    }
  }

  it should "detect multi edges" in {
    val graph = Graph(
      nodes = Map(
        "A" -> Node.sink(ExistingService("url1")),
        "B" -> Node.source(ExistingService("url2")),
        "C" -> Node.source(ExistingService("url3"))
      ),
      links = Link.links(
        NodeResourceRef("B", ExecutorModelDefaults.SourceResource) -> NodeResourceRef("A", ExecutorModelDefaults.SinkResource),
        NodeResourceRef("C", ExecutorModelDefaults.SourceResource) -> NodeResourceRef("A", ExecutorModelDefaults.SinkResource)
      )
    )
    intercept[GraphAnalysis.MultiTargetDetected] {
      new GraphAnalysis(graph)
    }
  }

  it should "detect flow from sinks" in {
    val graph = Graph(
      nodes = Map(
        "A" -> Node.sink(ExistingService("url1")),
        "B" -> Node.sink(ExistingService("url2")),
        "C" -> Node.source(ExistingService("url3"))
      ),
      links = Link.links(
        NodeResourceRef("A", ExecutorModelDefaults.SinkResource) -> NodeResourceRef("B", ExecutorModelDefaults.SinkResource),
        NodeResourceRef("C", ExecutorModelDefaults.SourceResource) -> NodeResourceRef("A", ExecutorModelDefaults.SinkResource)
      )
    )
    intercept[GraphAnalysis.FlowFromSinkException] {
      new GraphAnalysis(graph)
    }
  }

  it should "detect not existing resources" in {
    val graph = Graph(
      nodes = Map(
        "A" -> Node.sink(ExistingService("url1")),
        "B" -> Node.source(ExistingService("url2"))
      ),
      links = Link.links(
        NodeResourceRef("B", ExecutorModelDefaults.SourceResource) -> NodeResourceRef("A", "not_existing")
      )
    )
    intercept[GraphAnalysis.ResourceNotFoundException] {
      new GraphAnalysis(graph)
    }
  }

  it should "detect not existing nodes" in {
    val graph = Graph(
      nodes = Map(
        "A" -> Node.sink(ExistingService("url1")),
        "B" -> Node.source(ExistingService("url2"))
      ),
      links = Link.links(
        NodeResourceRef("C", ExecutorModelDefaults.SourceResource) -> NodeResourceRef("A", ExecutorModelDefaults.SinkResource)
      )
    )
    intercept[GraphAnalysis.ResourceNotFoundException] {
      new GraphAnalysis(graph)
    }
  }

  it should "detect not existing nodes II" in {
    val graph = Graph(
      nodes = Map(
        "A" -> Node.sink(ExistingService("url1")),
        "B" -> Node.source(ExistingService("url2"))
      ),
      links = Link.links(
        NodeResourceRef("B", ExecutorModelDefaults.SourceResource) -> NodeResourceRef("A", ExecutorModelDefaults.SinkResource),
        NodeResourceRef("C", ExecutorModelDefaults.SourceResource) -> NodeResourceRef("D", ExecutorModelDefaults.SinkResource)
      )
    )
    intercept[GraphAnalysis.ResourceNotFoundException] {
      new GraphAnalysis(graph)
    }
  }
}
