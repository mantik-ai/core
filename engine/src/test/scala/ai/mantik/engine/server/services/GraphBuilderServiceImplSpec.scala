package ai.mantik.engine.server.services

import ai.mantik.ds.element.Bundle
import ai.mantik.ds.funcational.FunctionType
import ai.mantik.ds.{ DataType, FundamentalType, TabularData }
import ai.mantik.{ elements, planner }
import ai.mantik.elements.{ AlgorithmDefinition, DataSetDefinition, ItemId, Mantikfile, PipelineDefinition, PipelineStep, TrainableAlgorithmDefinition }
import ai.mantik.engine.protos.ds.BundleEncoding
import ai.mantik.engine.protos.graph_builder.BuildPipelineStep.Step
import ai.mantik.engine.protos.graph_builder.{ ApplyRequest, BuildPipelineRequest, BuildPipelineStep, CacheRequest, GetRequest, LiteralRequest, SelectRequest, TrainRequest }
import ai.mantik.engine.protos.items.ObjectKind
import ai.mantik.engine.session.{ ArtefactNotFoundException, Session, SessionManager, SessionNotFoundException }
import ai.mantik.engine.testutil.{ DummyComponents, TestBaseWithSessions }
import ai.mantik.planner.repository.MantikArtifact
import ai.mantik.planner.{ Algorithm, DataSet, PayloadSource, Pipeline }

class GraphBuilderServiceImplSpec extends TestBaseWithSessions {

  trait PlainEnv {
    val graphBuilder = new GraphBuilderServiceImpl(sessionManager)

    val session1 = await(sessionManager.create())
  }

  trait Env extends PlainEnv {
    val session2 = await(sessionManager.create())

    val dataset1 = MantikArtifact(
      Mantikfile.pure(DataSetDefinition(format = DataSet.NaturalFormatName, `type` = FundamentalType.Int32)),
      fileId = Some("1234"),
      id = "Dataset1",
      itemId = ItemId.generate()
    )
    val dataset2 = MantikArtifact(
      Mantikfile.pure(elements.DataSetDefinition(format = DataSet.NaturalFormatName, `type` = TabularData(
        "x" -> FundamentalType.Int32
      ))),
      fileId = Some("1234"),
      id = "Dataset2",
      itemId = ItemId.generate()
    )
    val algorithm1 = MantikArtifact(
      Mantikfile.pure(AlgorithmDefinition(stack = "stack1", `type` = FunctionType(FundamentalType.Int32, FundamentalType.StringType))),
      fileId = Some("1236"),
      id = "Algorithm1",
      itemId = ItemId.generate()
    )
    val trainable1 = MantikArtifact(
      Mantikfile.pure(
        TrainableAlgorithmDefinition(
          stack = "stack2",
          trainedStack = Some("stack2_trained"),
          trainingType = FundamentalType.Int32,
          statType = FundamentalType.Float32,
          `type` = FunctionType(
            input = FundamentalType.Int32,
            output = FundamentalType.StringType
          )
        )
      ),
      fileId = Some("5000"),
      id = "Trainable1",
      itemId = ItemId.generate()
    )
    val pipeline1 = MantikArtifact(
      Mantikfile.pure(
        PipelineDefinition(
          steps = List(
            PipelineStep.AlgorithmStep("Algorithm1")
          )
        )
      ),
      fileId = None,
      id = "Pipeline1",
      itemId = ItemId.generate()
    )
    await(components.repository.store(dataset1))
    await(components.repository.store(dataset2))
    await(components.repository.store(algorithm1))
    await(components.repository.store(trainable1))
    await(components.repository.store(pipeline1))
  }

  "get" should "place elements from the repo in the graph" in new Env {
    intercept[SessionNotFoundException] {
      await(graphBuilder.get(GetRequest("123", "foo")))
    }
    intercept[ArtefactNotFoundException] {
      await(graphBuilder.get(GetRequest(session1.id, "foo")))
    }
    val response = await(graphBuilder.get(GetRequest(session1.id, dataset1.id.toString)))
    response.itemId shouldBe "1"
    response.item.get.kind shouldBe ObjectKind.KIND_DATASET
    response.item.get.getDataset.`type`.get.json shouldBe "\"int32\""
    session1.getItem("1").get shouldBe an[DataSet]
    session2 shouldBe empty
  }

  it should "get algorithms" in new Env {
    val algoGet = await(graphBuilder.get(GetRequest(session1.id, algorithm1.id.toString)))
    algoGet.itemId shouldBe "1"
    algoGet.item.getOrElse(fail).kind shouldBe ObjectKind.KIND_ALGORITHM

    val algoItem = algoGet.item.getOrElse(fail).getAlgorithm
    val ad = algorithm1.mantikfile.definition.asInstanceOf[AlgorithmDefinition]
    algoItem.inputType.get.json shouldBe ad.`type`.input.toJsonString
    algoItem.outputType.get.json shouldBe ad.`type`.output.toJsonString
  }

  it should "get trainable algorithms" in new Env {
    val trainableGet = await(graphBuilder.get(GetRequest(session1.id, trainable1.id.toString)))
    trainableGet.itemId shouldBe "1"
    trainableGet.item.getOrElse(fail).kind shouldBe ObjectKind.KIND_TRAINABLE_ALGORITHM

    val trainableItem = trainableGet.item.getOrElse(fail).getTrainableAlgorithm

    val td = trainable1.mantikfile.definition.asInstanceOf[TrainableAlgorithmDefinition]
    trainableItem.trainingType.get.json shouldBe td.trainingType.toJsonString
    trainableItem.statType.get.json shouldBe td.statType.toJsonString
    trainableItem.inputType.get.json shouldBe td.`type`.input.toJsonString
    trainableItem.outputType.get.json shouldBe td.`type`.output.toJsonString
  }

  it should "get pipelines" in new Env {
    val pipelineGet = await(graphBuilder.get(GetRequest(session1.id, pipeline1.id.toString)))
    pipelineGet.itemId shouldBe "1"
    pipelineGet.item.getOrElse(fail).kind shouldBe ObjectKind.KIND_PIPELINE

    val pipelineItem = pipelineGet.item.getOrElse(fail).getPipeline
    val ad = algorithm1.mantikfile.definition.asInstanceOf[AlgorithmDefinition]
    // Type is deducted from algorithm
    pipelineItem.inputType.get.json shouldBe ad.`type`.input.toJsonString
    pipelineItem.outputType.get.json shouldBe ad.`type`.output.toJsonString
  }

  "algorithmApply" should "apply algorithms upon datasets" in new Env {
    val ds1 = await(graphBuilder.get(GetRequest(session1.id, dataset1.id.toString)))
    val algo1 = await(graphBuilder.get(GetRequest(session1.id, algorithm1.id.toString)))
    val applied = await(graphBuilder.algorithmApply(
      ApplyRequest(session1.id, ds1.itemId, algo1.itemId)
    ))
    applied.itemId shouldBe "3"
    applied.item.get.kind shouldBe ObjectKind.KIND_DATASET
    applied.item.get.getDataset.`type`.get.json shouldBe "\"string\""
    // TODO: Error scenarios

    session2 shouldBe empty
  }

  it should "apply pipelines upon datasets" in new Env {
    val ds1 = await(graphBuilder.get(GetRequest(session1.id, dataset1.id.toString)))
    val pipe1 = await(graphBuilder.get(GetRequest(session1.id, pipeline1.id.toString)))
    val applied = await(graphBuilder.algorithmApply(
      ApplyRequest(session1.id, ds1.itemId, pipe1.itemId)
    ))
    applied.itemId shouldBe "3"
    applied.item.get.kind shouldBe ObjectKind.KIND_DATASET
    applied.item.get.getDataset.`type`.get.json shouldBe "\"string\""
    // TODO: Error scenarios

    session2 shouldBe empty
  }

  for {
    encoding <- Seq(BundleEncoding.ENCODING_MSG_PACK, BundleEncoding.ENCODING_JSON)
  } {
    "literal" should s"place a literal in the graph with ${encoding}" in new Env {
      val lit = Bundle.fundamental("Hello World")
      val encoded = await(Converters.encodeBundle(lit, encoding))
      val element = await(
        graphBuilder.literal(
          LiteralRequest(
            session1.id,
            Some(encoded)
          )
        )
      )
      element.itemId shouldBe "1"
      element.item.get.kind shouldBe ObjectKind.KIND_DATASET
      val pds = element.item.get.getDataset
      pds.`type`.get.json shouldBe "\"string\""
      pds.stack shouldBe DataSet.NaturalFormatName

      // TODO: Error scenarios

      val item = session1.getItem("1").get.asInstanceOf[DataSet]
      item shouldBe DataSet.literal(lit)
      session2 shouldBe empty
    }
  }

  "cached" should "place a cached literal in the graph" in new Env {
    val element1 = await(graphBuilder.get(GetRequest(session1.id, dataset1.id.toString)))
    val element2 = await(graphBuilder.cached(CacheRequest(session1.id, element1.itemId)))
    element2.itemId shouldBe "2"
    element2.item.get.kind shouldBe ObjectKind.KIND_DATASET

    val original1 = session1.getItem("1").get.asInstanceOf[DataSet]
    val original2 = session1.getItem("2").get.asInstanceOf[DataSet]
    original2 shouldBe original1.cached
  }

  for {
    withCaching <- Seq(false, true)
  } {
    "train" should s"place a trained nodes in the graph (caching=${withCaching})" in new Env {
      val dataset = await(graphBuilder.get(GetRequest(session1.id, dataset1.id.toString)))
      val trainable = await(graphBuilder.get(GetRequest(session1.id, trainable1.id.toString)))

      val result = await(graphBuilder.train(
        TrainRequest(
          sessionId = session1.id,
          trainableId = trainable.itemId,
          trainingDatasetId = dataset.itemId,
          noCaching = !withCaching
        )
      ))
      val underlyingStat = session1.getItemAs[DataSet](result.statDataset.get.itemId)
      underlyingStat.dataType shouldBe FundamentalType.Float32
      val underlyingTrainable = session1.getItemAs[Algorithm](result.trainedAlgorithm.get.itemId)
      underlyingTrainable.stack shouldBe "stack2_trained"
      if (withCaching) {
        underlyingStat.payloadSource.asInstanceOf[PayloadSource.Projection].source shouldBe an[PayloadSource.Cached]
        underlyingTrainable.payloadSource.asInstanceOf[PayloadSource.Projection].source shouldBe an[PayloadSource.Cached]
      } else {
        underlyingStat.payloadSource.asInstanceOf[PayloadSource.Projection].source shouldBe an[PayloadSource.OperationResult]
        underlyingTrainable.payloadSource.asInstanceOf[PayloadSource.Projection].source shouldBe an[PayloadSource.OperationResult]
      }
    }
  }

  "select" should "create select statements" in new Env {
    val dataset = await(graphBuilder.get(GetRequest(session1.id, dataset2.id.toString)))
    val selected = await(graphBuilder.select(
      SelectRequest(
        sessionId = session1.id,
        dataset.itemId,
        selectQuery = "select CAST(x as INT64) as y"
      )
    ))
    selected.itemId shouldBe "2"
    selected.item.get.kind shouldBe ObjectKind.KIND_DATASET
    selected.item.get.getDataset.`type`.get.json shouldBe TabularData(
      "y" -> FundamentalType.Int64
    ).toJsonString
  }

  trait EnvForPipeline extends PlainEnv {
    val algorithm1 = MantikArtifact(
      Mantikfile.pure(elements.AlgorithmDefinition(
        stack = "stack1",
        `type` = FunctionType(
          input = TabularData(
            "x" -> FundamentalType.Int32
          ),
          output = TabularData(
            "y" -> FundamentalType.Float64
          )
        )
      )),
      fileId = Some("1236"),
      id = "Algorithm1",
      itemId = ItemId.generate()
    )
    await(components.repository.store(algorithm1))
    val algorithm1Item = await(graphBuilder.get(GetRequest(session1.id, algorithm1.id.toString)))
  }

  "buildPipeline" should "work" in new EnvForPipeline {
    val pipeItem = await(graphBuilder.buildPipeline(
      BuildPipelineRequest(
        session1.id,
        Seq(
          BuildPipelineStep(
            Step.AlgorithmId(algorithm1Item.itemId)
          ),
          BuildPipelineStep(
            Step.Select("select y as string")
          )
        )
      )
    ))
    pipeItem.item.get.kind shouldBe ObjectKind.KIND_PIPELINE
    val realPipeline = session1.getItemAs[Pipeline](pipeItem.itemId)
    realPipeline.stepCount shouldBe 2
  }

  it should "work with starting type" in new EnvForPipeline {
    val pipeItem = await(graphBuilder.buildPipeline(
      BuildPipelineRequest(
        session1.id,
        Seq(
          BuildPipelineStep(
            Step.Select("select CAST (x as int32)")
          ),
          BuildPipelineStep(
            Step.AlgorithmId(algorithm1Item.itemId)
          )
        ),
        inputType = Some(
          Converters.encodeDataType(
            TabularData(
              "x" -> FundamentalType.Uint8
            )
          )
        )
      )
    ))
    pipeItem.item.get.kind shouldBe ObjectKind.KIND_PIPELINE
    val realPipeline = session1.getItemAs[Pipeline](pipeItem.itemId)
    realPipeline.stepCount shouldBe 2
  }
}