package ai.mantik.engine.server.services

import ai.mantik.ds.element.Bundle
import ai.mantik.ds.funcational.FunctionType
import ai.mantik.ds.{ DataType, FundamentalType, TabularData }
import ai.mantik.engine.protos.ds.BundleEncoding
import ai.mantik.engine.protos.graph_builder.{ ApplyRequest, CacheRequest, GetRequest, LiteralRequest, SelectRequest, TrainRequest }
import ai.mantik.engine.protos.items.ObjectKind
import ai.mantik.engine.session.{ ArtefactNotFoundException, Session, SessionManager, SessionNotFoundException }
import ai.mantik.engine.testutil.{ DummyComponents, TestBaseWithSessions }
import ai.mantik.planner.{ Algorithm, DataSet, Source }
import ai.mantik.repository.{ AlgorithmDefinition, DataSetDefinition, MantikArtifact, Mantikfile, TrainableAlgorithmDefinition }

class GraphBuilderServiceImplSpec extends TestBaseWithSessions {

  trait Env {
    val graphBuilder = new GraphBuilderServiceImpl(sessionManager)

    val session1 = await(sessionManager.create())
    val session2 = await(sessionManager.create())

    val dataset1 = MantikArtifact(
      Mantikfile.pure(DataSetDefinition(format = DataSet.NaturalFormatName, `type` = FundamentalType.Int32)),
      fileId = Some("1234"),
      id = "Dataset1"
    )
    val dataset2 = MantikArtifact(
      Mantikfile.pure(DataSetDefinition(format = DataSet.NaturalFormatName, `type` = TabularData(
        "x" -> FundamentalType.Int32
      ))),
      fileId = Some("1234"),
      id = "Dataset2"
    )
    val algorithm1 = MantikArtifact(
      Mantikfile.pure(AlgorithmDefinition(stack = "stack1", `type` = FunctionType(FundamentalType.Int32, FundamentalType.StringType))),
      fileId = Some("1236"),
      id = "Algorithm1"
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
      id = "Trainable1"
    )
    await(components.repository.store(dataset1))
    await(components.repository.store(dataset2))
    await(components.repository.store(algorithm1))
    await(components.repository.store(trainable1))
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
        underlyingStat.source.asInstanceOf[Source.Projection].source shouldBe an[Source.Cached]
        underlyingTrainable.source.asInstanceOf[Source.Projection].source shouldBe an[Source.Cached]
      } else {
        underlyingStat.source.asInstanceOf[Source.Projection].source shouldBe an[Source.OperationResult]
        underlyingTrainable.source.asInstanceOf[Source.Projection].source shouldBe an[Source.OperationResult]
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
}
