/*
 * This file is part of the Mantik Project.
 * Copyright (c) 2020-2021 Mantik UG (Haftungsbeschränkt)
 * Authors: See AUTHORS file
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.
 *
 * Additionally, the following linking exception is granted:
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with other code, such other code is not for that reason
 * alone subject to any of the requirements of the GNU Affero GPL
 * version 3.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license.
 */
package ai.mantik.engine.server.services

import ai.mantik.ds.element.Bundle
import ai.mantik.ds.functional.FunctionType
import ai.mantik.ds.helper.circe.CirceJson
import ai.mantik.ds.sql.Split
import ai.mantik.ds.{DataType, FundamentalType, TabularData}
import ai.mantik.elements.errors.{ErrorCodes, MantikException, MantikRemoteException}
import ai.mantik.{elements, planner}
import ai.mantik.elements.{
  AlgorithmDefinition,
  BridgeDefinition,
  DataSetDefinition,
  ItemId,
  MantikHeader,
  NamedMantikId,
  PipelineDefinition,
  PipelineStep,
  TrainableAlgorithmDefinition
}
import ai.mantik.engine.protos.ds.BundleEncoding
import ai.mantik.engine.protos.graph_builder.BuildPipelineStep.Step
import ai.mantik.engine.protos.graph_builder.{
  ApplyRequest,
  AutoUnionRequest,
  BuildPipelineRequest,
  BuildPipelineStep,
  CacheRequest,
  GetRequest,
  LiteralRequest,
  MetaVariableValue,
  QueryRequest,
  SelectRequest,
  SetMetaVariableRequest,
  SplitRequest,
  TagRequest,
  TrainRequest
}
import ai.mantik.engine.protos.items.ObjectKind
import ai.mantik.engine.session.{EngineErrors, Session, SessionManager}
import ai.mantik.engine.testutil.{DummyComponents, TestArtifacts, TestBaseWithSessions}
import ai.mantik.planner.impl.MantikItemStateManager
import ai.mantik.planner.repository.MantikArtifact
import ai.mantik.planner.{Algorithm, BuiltInItems, DataSet, MantikItem, Operation, PayloadSource, Pipeline}
import io.grpc.StatusRuntimeException

class GraphBuilderServiceImplSpec extends TestBaseWithSessions {

  trait PlainEnv {
    val stateManager = new MantikItemStateManager()
    val graphBuilder = new GraphBuilderServiceImpl(sessionManager, stateManager)

    val session1 = await(sessionManager.create())
  }

  trait Env extends PlainEnv {
    val session2 = await(sessionManager.create())

    val dataset1 = MantikArtifact(
      MantikHeader
        .pure(DataSetDefinition(bridge = BuiltInItems.NaturalBridgeName, `type` = FundamentalType.Int32))
        .toJson,
      fileId = Some("1234"),
      namedId = Some(NamedMantikId("Dataset1")),
      itemId = ItemId.generate()
    )
    val dataset2 = MantikArtifact(
      MantikHeader
        .pure(
          elements.DataSetDefinition(
            bridge = BuiltInItems.NaturalBridgeName,
            `type` = TabularData(
              "x" -> FundamentalType.Int32
            )
          )
        )
        .toJson,
      fileId = Some("1234"),
      namedId = Some(NamedMantikId("Dataset2")),
      itemId = ItemId.generate()
    )
    val dataset3 = MantikArtifact(
      MantikHeader
        .pure(
          elements.DataSetDefinition(
            bridge = BuiltInItems.NaturalBridgeName,
            `type` = TabularData(
              "y" -> FundamentalType.Int32
            )
          )
        )
        .toJson,
      fileId = Some("1234"),
      namedId = Some(NamedMantikId("Dataset3")),
      itemId = ItemId.generate()
    )
    val algorithm1 = MantikArtifact(
      MantikHeader
        .pure(
          AlgorithmDefinition(
            bridge = TestArtifacts.algoBridge1.namedId.get,
            `type` = FunctionType(FundamentalType.Int32, FundamentalType.StringType)
          )
        )
        .toJson,
      fileId = Some("1236"),
      namedId = Some(NamedMantikId("Algorithm1")),
      itemId = ItemId.generate()
    )
    val trainable1 = MantikArtifact(
      MantikHeader
        .pure(
          TrainableAlgorithmDefinition(
            bridge = TestArtifacts.trainingBridge1.namedId.get,
            trainedBridge = Some(TestArtifacts.trainedBridge1.namedId.get),
            trainingType = FundamentalType.Int32,
            statType = FundamentalType.Float32,
            `type` = FunctionType(
              input = FundamentalType.Int32,
              output = FundamentalType.StringType
            )
          )
        )
        .toJson,
      fileId = Some("5000"),
      namedId = Some(NamedMantikId("Trainable1")),
      itemId = ItemId.generate()
    )
    val pipeline1 = MantikArtifact(
      MantikHeader
        .pure(
          PipelineDefinition(
            steps = List(
              PipelineStep.AlgorithmStep("Algorithm1")
            )
          )
        )
        .toJson,
      fileId = None,
      namedId = Some(NamedMantikId("Pipeline1")),
      itemId = ItemId.generate()
    )
    await(components.repository.store(TestArtifacts.algoBridge1))
    await(components.repository.store(TestArtifacts.trainingBridge1))
    await(components.repository.store(TestArtifacts.trainedBridge1))
    await(components.repository.store(dataset1))
    await(components.repository.store(dataset2))
    await(components.repository.store(dataset3))
    await(components.repository.store(algorithm1))
    await(components.repository.store(trainable1))
    await(components.repository.store(pipeline1))
  }

  "get" should "place elements from the repo in the graph" in new Env {
    MantikRemoteException
      .fromGrpc(awaitException[StatusRuntimeException] {
        graphBuilder.get(GetRequest("123", "foo"))
      })
      .code
      .isA(EngineErrors.SessionNotFound) shouldBe true
    MantikRemoteException
      .fromGrpc(awaitException[StatusRuntimeException] {
        graphBuilder.get(GetRequest(session1.id, "foo"))
      })
      .code
      .isA(ErrorCodes.MantikItemNotFound) shouldBe true
    val response = await(graphBuilder.get(GetRequest(session1.id, dataset1.mantikId.toString)))
    response.itemId shouldBe "1"
    response.item.get.kind shouldBe ObjectKind.KIND_DATASET
    response.item.get.getDataset.`type`.get.json shouldBe "\"int32\""
    CirceJson.forceParseJson(response.item.get.mantikHeaderJson) shouldBe dataset1.parsedMantikHeader.toJsonValue
    session1.getItem("1").get shouldBe an[DataSet]
    session2 shouldBe empty
  }

  it should "get algorithms" in new Env {
    val algoGet = await(graphBuilder.get(GetRequest(session1.id, algorithm1.mantikId.toString)))
    algoGet.itemId shouldBe "1"
    algoGet.item.get.kind shouldBe ObjectKind.KIND_ALGORITHM

    val algoItem = algoGet.item.get.getAlgorithm
    val ad = algorithm1.parsedMantikHeader.definition.asInstanceOf[AlgorithmDefinition]
    algoItem.inputType.get.json shouldBe ad.`type`.input.toJsonString
    algoItem.outputType.get.json shouldBe ad.`type`.output.toJsonString
  }

  it should "get trainable algorithms" in new Env {
    val trainableGet = await(graphBuilder.get(GetRequest(session1.id, trainable1.mantikId.toString)))
    trainableGet.itemId shouldBe "1"
    trainableGet.item.get.kind shouldBe ObjectKind.KIND_TRAINABLE_ALGORITHM

    val trainableItem = trainableGet.item.get.getTrainableAlgorithm

    val td = trainable1.parsedMantikHeader.definition.asInstanceOf[TrainableAlgorithmDefinition]
    trainableItem.trainingType.get.json shouldBe td.trainingType.toJsonString
    trainableItem.statType.get.json shouldBe td.statType.toJsonString
    trainableItem.inputType.get.json shouldBe td.`type`.input.toJsonString
    trainableItem.outputType.get.json shouldBe td.`type`.output.toJsonString
  }

  it should "get pipelines" in new Env {
    val pipelineGet = await(graphBuilder.get(GetRequest(session1.id, pipeline1.mantikId.toString)))
    pipelineGet.itemId shouldBe "1"
    pipelineGet.item.get.kind shouldBe ObjectKind.KIND_PIPELINE

    val pipelineItem = pipelineGet.item.get.getPipeline
    val ad = algorithm1.parsedMantikHeader.definition.asInstanceOf[AlgorithmDefinition]
    // Type is deducted from algorithm
    pipelineItem.inputType.get.json shouldBe ad.`type`.input.toJsonString
    pipelineItem.outputType.get.json shouldBe ad.`type`.output.toJsonString
  }

  it should "get bridges" in new Env {
    val bridgeGet = await(graphBuilder.get(GetRequest(session1.id, TestArtifacts.algoBridge1.mantikId.toString)))
    bridgeGet.itemId shouldBe "1"
    bridgeGet.item.get.kind shouldBe ObjectKind.KIND_BRIDGE
    val bridgeItem = bridgeGet.item.get.getBridge
    val pd = TestArtifacts.algoBridge1.parsedMantikHeader.definition.asInstanceOf[BridgeDefinition]
    bridgeItem.dockerImage shouldBe pd.dockerImage
    bridgeItem.suitable shouldBe pd.suitable
    bridgeItem.protocol shouldBe pd.protocol
    bridgeItem.payloadContentType shouldBe pd.payloadContentType.get
  }

  "algorithmApply" should "apply algorithms upon datasets" in new Env {
    val ds1 = await(graphBuilder.get(GetRequest(session1.id, dataset1.mantikId.toString)))
    val algo1 = await(graphBuilder.get(GetRequest(session1.id, algorithm1.mantikId.toString)))
    val applied = await(
      graphBuilder.algorithmApply(
        ApplyRequest(session1.id, ds1.itemId, algo1.itemId)
      )
    )
    applied.itemId shouldBe "3"
    applied.item.get.kind shouldBe ObjectKind.KIND_DATASET
    applied.item.get.getDataset.`type`.get.json shouldBe "\"string\""
    // TODO: Error scenarios

    session2 shouldBe empty
  }

  it should "apply pipelines upon datasets" in new Env {
    val ds1 = await(graphBuilder.get(GetRequest(session1.id, dataset1.mantikId.toString)))
    val pipe1 = await(graphBuilder.get(GetRequest(session1.id, pipeline1.mantikId.toString)))
    val applied = await(
      graphBuilder.algorithmApply(
        ApplyRequest(session1.id, ds1.itemId, pipe1.itemId)
      )
    )
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
      val encoded = Converters.encodeBundle(lit, encoding)
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
      pds.bridge shouldBe BuiltInItems.NaturalBridgeName.toString

      // TODO: Error scenarios

      val item = session1.getItem("1").get.asInstanceOf[DataSet]

      def withItemId(item: DataSet, itemId: ItemId): MantikItem = {
        item.copy(
          core = item.core.copy(
            itemId = itemId
          )
        )
      }

      item shouldBe withItemId(DataSet.literal(lit), item.itemId)
      session2 shouldBe empty
    }
  }

  "cached" should "place a cached literal in the graph" in new Env {
    val element1 = await(graphBuilder.get(GetRequest(session1.id, dataset1.mantikId.toString)))
    val element2 = await(graphBuilder.cached(CacheRequest(session1.id, element1.itemId)))
    element2.itemId shouldBe "2"
    element2.item.get.kind shouldBe ObjectKind.KIND_DATASET

    val original1 = session1.getItem("1").get.asInstanceOf[DataSet]
    val original2 = session1.getItem("2").get.asInstanceOf[DataSet]
    original1.mantikHeader shouldBe original2.mantikHeader
    original2.source.payload shouldBe an[PayloadSource.Cached]
  }

  for {
    withCaching <- Seq(false, true)
  } {
    "train" should s"place a trained nodes in the graph (caching=${withCaching})" in new Env {
      val dataset = await(graphBuilder.get(GetRequest(session1.id, dataset1.mantikId.toString)))
      val trainable = await(graphBuilder.get(GetRequest(session1.id, trainable1.mantikId.toString)))

      val result = await(
        graphBuilder.train(
          TrainRequest(
            sessionId = session1.id,
            trainableId = trainable.itemId,
            trainingDatasetId = dataset.itemId,
            noCaching = !withCaching
          )
        )
      )
      val underlyingStat = session1.getItemAs[DataSet](result.statDataset.get.itemId)
      underlyingStat.dataType shouldBe FundamentalType.Float32
      val underlyingTrainable = session1.getItemAs[Algorithm](result.trainedAlgorithm.get.itemId)
      underlyingTrainable.bridgeMantikId shouldBe TestArtifacts.trainedBridge1.namedId.get
      if (withCaching) {
        underlyingStat.payloadSource.asInstanceOf[PayloadSource.Projection].source shouldBe an[PayloadSource.Cached]
        underlyingTrainable.payloadSource
          .asInstanceOf[PayloadSource.Projection]
          .source shouldBe an[PayloadSource.Cached]
      } else {
        underlyingStat.payloadSource
          .asInstanceOf[PayloadSource.Projection]
          .source shouldBe an[PayloadSource.OperationResult]
        underlyingTrainable.payloadSource
          .asInstanceOf[PayloadSource.Projection]
          .source shouldBe an[PayloadSource.OperationResult]
      }
    }
  }

  "select" should "create select statements" in new Env {
    val dataset = await(graphBuilder.get(GetRequest(session1.id, dataset2.mantikId.toString)))
    val selected = await(
      graphBuilder.select(
        SelectRequest(
          sessionId = session1.id,
          dataset.itemId,
          selectQuery = "select CAST(x as INT64) as y"
        )
      )
    )
    selected.itemId shouldBe "2"
    selected.item.get.kind shouldBe ObjectKind.KIND_DATASET
    selected.item.get.getDataset.`type`.get.json shouldBe TabularData(
      "y" -> FundamentalType.Int64
    ).toJsonString
  }

  "autoUnion" should "create auto union statements" in new Env {
    val ds2 = await(graphBuilder.get(GetRequest(session1.id, dataset2.mantikId.toString)))
    val underlyingDs2 = session1.getItemAs[DataSet](ds2.itemId)
    val ds3 = await(graphBuilder.get(GetRequest(session1.id, dataset3.mantikId.toString)))
    val underlyingDs3 = session1.getItemAs[DataSet](ds3.itemId)
    val union1 = await(
      graphBuilder.autoUnion(
        AutoUnionRequest(
          sessionId = session1.id,
          ds2.itemId,
          ds3.itemId,
          all = false
        )
      )
    )
    val underlying1 = session1.getItemAs[DataSet](union1.itemId)
    underlying1 shouldBe underlyingDs2.autoUnion(underlyingDs3, all = false).withItemId(underlying1.itemId)

    val union2 = await(
      graphBuilder.autoUnion(
        AutoUnionRequest(
          sessionId = session1.id,
          ds2.itemId,
          ds3.itemId,
          all = true
        )
      )
    )
    val underlying2 = session1.getItemAs[DataSet](union2.itemId)
    underlying2 shouldBe underlyingDs2.autoUnion(underlyingDs3, all = true).withItemId(underlying2.itemId)
  }

  "sqlQuery" should "work" in new Env {
    val ds2 = await(graphBuilder.get(GetRequest(session1.id, dataset2.mantikId.toString)))
    val underlyingDs2 = session1.getItemAs[DataSet](ds2.itemId)

    val ds3 = await(graphBuilder.get(GetRequest(session1.id, dataset3.mantikId.toString)))
    val underlyingDs3 = session1.getItemAs[DataSet](ds3.itemId)

    // Note: this statement doesn't make much sense
    val statement = "SELECT l.x, r.y FROM $0 AS l LEFT JOIN $1 AS r ON l.x = r.y"
    val joined = await(
      graphBuilder.sqlQuery(
        QueryRequest(
          sessionId = session1.id,
          statement = statement,
          datasetIds = Seq(ds2.itemId, ds3.itemId)
        )
      )
    )
    val underlying = session1.getItemAs[DataSet](joined.itemId)
    underlying shouldBe DataSet.query(statement, underlyingDs2, underlyingDs3).withItemId(underlying.itemId)
  }

  "split" should "work" in new Env {
    val ds2 = await(graphBuilder.get(GetRequest(session1.id, dataset2.mantikId.toString)))
    val underlyingDs2 = session1.getItemAs[DataSet](ds2.itemId)

    val splitted = await(
      graphBuilder.split(
        SplitRequest(
          sessionId = session1.id,
          datasetId = ds2.itemId,
          fractions = Seq(0.5, 0.3)
        )
      )
    )
    val underlying = splitted.nodes.map { node =>
      session1.getItemAs[DataSet](node.itemId)
    }
    val expected = underlyingDs2.split(Seq(0.5, 0.3))
    underlying.size shouldBe expected.size
    // We can't check for item equivalence here as itemIds are deep buried inside caching
    // however we can check that caching is activated by default
    underlying.foreach { u =>
      val query = u.payloadSource
        .asInstanceOf[PayloadSource.Projection]
        .source
        .asInstanceOf[PayloadSource.Cached]
        .source
        .asInstanceOf[PayloadSource.OperationResult]
        .op
        .asInstanceOf[Operation.SqlQueryOperation]
      val split = query.query.asInstanceOf[Split]
      split.fractions shouldBe Seq(0.5, 0.3)
      split.shuffleSeed shouldBe None
    }
  }

  it should "accept shuffling and caching requests" in new Env {
    val ds2 = await(graphBuilder.get(GetRequest(session1.id, dataset2.mantikId.toString)))
    val underlyingDs2 = session1.getItemAs[DataSet](ds2.itemId)

    val splitted = await(
      graphBuilder.split(
        SplitRequest(
          sessionId = session1.id,
          datasetId = ds2.itemId,
          fractions = Seq(0.5, 0.3),
          shuffle = true,
          shuffleSeed = 14,
          noCaching = true
        )
      )
    )
    val underlying = splitted.nodes.map { node =>
      session1.getItemAs[DataSet](node.itemId)
    }
    val expected = underlyingDs2.split(Seq(0.5, 0.3), shuffleSeed = Some(14), cached = false)
    underlying.size shouldBe expected.size
    underlying.zip(expected).foreach { case (u, e) =>
      u shouldBe e.withItemId(u.itemId)
    }
  }

  trait EnvForPipeline extends PlainEnv {
    val algorithm1 = MantikArtifact(
      MantikHeader
        .pure(
          elements.AlgorithmDefinition(
            bridge = TestArtifacts.algoBridge1.namedId.get,
            `type` = FunctionType(
              input = TabularData(
                "x" -> FundamentalType.Int32
              ),
              output = TabularData(
                "y" -> FundamentalType.Float64
              )
            )
          )
        )
        .toJson,
      fileId = Some("1236"),
      namedId = Some(NamedMantikId("Algorithm1")),
      itemId = ItemId.generate()
    )
    await(components.repository.store(TestArtifacts.algoBridge1))
    await(components.repository.store(algorithm1))
    val algorithm1Item = await(graphBuilder.get(GetRequest(session1.id, algorithm1.mantikId.toString)))
  }

  "buildPipeline" should "work" in new EnvForPipeline {
    val pipeItem = await(
      graphBuilder.buildPipeline(
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
      )
    )
    pipeItem.item.get.kind shouldBe ObjectKind.KIND_PIPELINE
    val realPipeline = session1.getItemAs[Pipeline](pipeItem.itemId)
    realPipeline.stepCount shouldBe 2
  }

  it should "work with starting type" in new EnvForPipeline {
    val pipeItem = await(
      graphBuilder.buildPipeline(
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
      )
    )
    pipeItem.item.get.kind shouldBe ObjectKind.KIND_PIPELINE
    val realPipeline = session1.getItemAs[Pipeline](pipeItem.itemId)
    realPipeline.stepCount shouldBe 2
  }

  "tag" should "work" in new Env {
    val response = await(graphBuilder.get(GetRequest(session1.id, dataset1.mantikId.toString)))
    val response2 = await(graphBuilder.tag(TagRequest(session1.id, response.itemId, "new_name")))
    val original = session1.getItem(response.itemId).get
    val modified = session1.getItem(response2.itemId).get
    modified shouldBe original.tag("new_name")
  }

  "setMetaVariables" should "work" in new PlainEnv {
    val algorithm1 = MantikArtifact(
      s"""kind: algorithm
         |bridge: ${TestArtifacts.algoBridge1.namedId.get}
         |type:
         |  input: int32
         |  output: int32
         |metaVariables:
         |  - name: a
         |    type: string
         |    value: "hello"
         |  - name: b
         |    type: int32
         |    value: 42
         |""".stripMargin,
      fileId = Some("1236"),
      namedId = Some(NamedMantikId("Algorithm1")),
      itemId = ItemId.generate()
    )
    await(components.repository.store(TestArtifacts.algoBridge1))
    await(components.repository.store(algorithm1))
    val algorithm = await(graphBuilder.get(GetRequest(session1.id, algorithm1.mantikId.toString)))
    val nameChanged = await(
      graphBuilder.setMetaVariables(
        SetMetaVariableRequest(
          sessionId = session1.id,
          itemId = algorithm.itemId,
          values = Seq(
            // type1: directly use JSON
            MetaVariableValue("a", MetaVariableValue.Value.Json("\"boom\""))
          )
        )
      )
    )
    val nameChangedBack = session1.getItemAs[Algorithm](nameChanged.itemId)
    nameChangedBack.mantikHeader.metaJson.metaVariable("a").get.value shouldBe Bundle.fundamental("boom")
    val intChanged = await(
      graphBuilder.setMetaVariables(
        SetMetaVariableRequest(
          sessionId = session1.id,
          itemId = algorithm.itemId,
          values = Seq(
            // type1: directly use JSON
            MetaVariableValue(
              "b",
              MetaVariableValue.Value.Bundle(
                Converters.encodeBundle(Bundle.fundamental(100), BundleEncoding.ENCODING_MSG_PACK)
              )
            )
          )
        )
      )
    )
    val intChangedBack = session1.getItemAs[Algorithm](intChanged.itemId)
    intChangedBack.mantikHeader.metaJson.metaVariable("b").get.value shouldBe Bundle.fundamental(100)

    val bothChanged = await(
      graphBuilder.setMetaVariables(
        SetMetaVariableRequest(
          sessionId = session1.id,
          itemId = algorithm.itemId,
          values = Seq(
            // type1: directly use JSON
            MetaVariableValue(
              "b",
              MetaVariableValue.Value.Bundle(
                Converters.encodeBundle(Bundle.fundamental(123), BundleEncoding.ENCODING_MSG_PACK)
              )
            ),
            MetaVariableValue(
              "a",
              MetaVariableValue.Value.Bundle(
                Converters.encodeBundle(Bundle.fundamental("Bam"), BundleEncoding.ENCODING_JSON)
              )
            )
          )
        )
      )
    )
    val bothChangedBack = session1.getItemAs[Algorithm](bothChanged.itemId)
    bothChangedBack.mantikHeader.metaJson.metaVariables.map { x => x.name -> x.value }.toMap shouldBe Map(
      "a" -> Bundle.fundamental("Bam"),
      "b" -> Bundle.fundamental(123)
    )

    // TODO: Various error scenarios (not existing Meta Variable etc?!)

    withClue("the response contains the serialized MantikHeader, so that gRpc users can evaluate Meta variables") {
      CirceJson.forceParseJson(
        bothChanged.item.get.mantikHeaderJson
      ) shouldBe bothChangedBack.core.mantikHeader.toJsonValue
    }
  }
}
