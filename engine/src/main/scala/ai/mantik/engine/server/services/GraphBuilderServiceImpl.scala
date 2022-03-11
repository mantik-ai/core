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

import ai.mantik.componently.rpc.{RpcConversions, StreamConversions}
import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.ds.element.{Bundle, SingleElementBundle}
import ai.mantik.ds.formats.json.JsonFormat
import ai.mantik.ds.helper.circe.CirceJson
import ai.mantik.elements.errors.ErrorCodes
import ai.mantik.elements.{
  AlgorithmDefinition,
  DataSetDefinition,
  ItemId,
  MantikDefinition,
  MantikDefinitionWithBridge,
  MantikDefinitionWithoutBridge,
  MantikHeader,
  MantikId,
  NamedMantikId
}
import ai.mantik.engine.protos.graph_builder.BuildPipelineStep.Step
import ai.mantik.engine.protos.graph_builder.{
  ApplyRequest,
  AutoUnionRequest,
  BuildPipelineRequest,
  CacheRequest,
  ConstructRequest,
  GetRequest,
  LiteralRequest,
  MetaVariableValue,
  MultiNodeResponse,
  NodeResponse,
  QueryRequest,
  SelectRequest,
  SetMetaVariableRequest,
  SplitRequest,
  TagRequest,
  TrainRequest,
  TrainResponse
}
import ai.mantik.engine.protos.graph_builder.GraphBuilderServiceGrpc.GraphBuilderService
import ai.mantik.engine.session.{Session, SessionManager}
import ai.mantik.planner.impl.MantikItemStateManager
import ai.mantik.planner.repository.{Bridge, ContentTypes, MantikArtifact}
import ai.mantik.planner.{
  Algorithm,
  ApplicableMantikItem,
  BuiltInItems,
  DataSet,
  MantikItem,
  MantikItemCore,
  MantikItemState,
  PayloadSource,
  Pipeline,
  Source,
  TrainableAlgorithm
}
import akka.NotUsed
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.{Source => AkkaSource}
import io.grpc.stub.StreamObserver

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class GraphBuilderServiceImpl @Inject() (sessionManager: SessionManager, stateManager: MantikItemStateManager)(
    implicit akkaRuntime: AkkaRuntime
) extends ComponentBase
    with GraphBuilderService
    with RpcServiceBase {

  override def get(request: GetRequest): Future[NodeResponse] = handleErrors {
    for {
      session <- sessionManager.get(request.sessionId)
      mantikItem <- retrieve(session, request.name)
    } yield {
      placeInGraph(session, mantikItem)
    }
  }

  private def retrieve(session: Session, mantikId: MantikId): Future[MantikItem] = {
    BuiltInItems.readBuiltInItem(mantikId) match {
      case Some(builtIn) => Future.successful(builtIn)
      case None =>
        session.components.retriever.get(mantikId).map { case (artifact, hull) =>
          MantikItem.fromMantikArtifact(artifact, stateManager, hull)
        }
    }
  }

  private def placeInGraph(session: Session, item: MantikItem): NodeResponse = {
    val id = session.addItem(item)
    NodeResponse(
      itemId = id,
      item = Some(Converters.encodeMantikItem(item))
    )
  }

  override def algorithmApply(request: ApplyRequest): Future[NodeResponse] = handleErrors {
    for {
      session <- sessionManager.get(request.sessionId)
      algorithm = session.getItemAs[ApplicableMantikItem](request.algorithmId)
      dataset = session.getItemAs[DataSet](request.datasetId)
    } yield {
      val result = algorithm.apply(dataset)
      placeInGraph(session, result)
    }
  }

  override def literal(request: LiteralRequest): Future[NodeResponse] = handleErrors {
    for {
      session <- sessionManager.get(request.sessionId)
      bundle = Converters.decodeBundle(
        request.bundle.getOrElse(
          throw new IllegalArgumentException("Missing Bundle")
        )
      )
    } yield {
      val dataset = DataSet.literal(bundle)
      placeInGraph(session, dataset)
    }
  }

  override def cached(request: CacheRequest): Future[NodeResponse] = handleErrors {
    for {
      session <- sessionManager.get(request.sessionId)
      dataset = session.getItemAs[DataSet](request.itemId)
    } yield {
      val cachedDataset = dataset.cached
      placeInGraph(session, cachedDataset)
    }
  }

  override def train(request: TrainRequest): Future[TrainResponse] = handleErrors {
    for {
      session <- sessionManager.get(request.sessionId)
      trainable = session.getItemAs[TrainableAlgorithm](request.trainableId)
      trainDataset = session.getItemAs[DataSet](request.trainingDatasetId)
    } yield {
      val (trained, stats) = trainable.train(trainDataset, cached = !request.noCaching)
      val trainedNode = placeInGraph(session, trained)
      val statsNode = placeInGraph(session, stats)
      TrainResponse(
        trainedAlgorithm = Some(trainedNode),
        statDataset = Some(statsNode)
      )
    }
  }

  override def select(request: SelectRequest): Future[NodeResponse] = handleErrors {
    for {
      session <- sessionManager.get(request.sessionId)
      dataset = session.getItemAs[DataSet](request.datasetId)
    } yield {
      val selected = dataset.select(request.selectQuery)
      placeInGraph(session, selected)
    }
  }

  override def autoUnion(request: AutoUnionRequest): Future[NodeResponse] = handleErrors {
    for {
      session <- sessionManager.get(request.sessionId)
      dataset1 = session.getItemAs[DataSet](request.datasetId1)
      dataset2 = session.getItemAs[DataSet](request.datasetId2)
    } yield {
      val unionized = dataset1.autoUnion(dataset2, all = request.all)
      placeInGraph(session, unionized)
    }
  }

  override def sqlQuery(request: QueryRequest): Future[NodeResponse] = handleErrors {
    for {
      session <- sessionManager.get(request.sessionId)
      datasets = request.datasetIds.map { datasetId =>
        session.getItemAs[DataSet](datasetId)
      }
    } yield {
      val result = DataSet.query(request.statement, datasets: _*)
      placeInGraph(session, result)
    }
  }

  override def split(request: SplitRequest): Future[MultiNodeResponse] = handleErrors {
    sessionManager.get(request.sessionId).map { session =>
      val dataset = session.getItemAs[DataSet](request.datasetId)
      val splitted = dataset.split(
        fractions = request.fractions,
        shuffleSeed = if (request.shuffle) {
          Some(request.shuffleSeed)
        } else {
          None
        },
        cached = !request.noCaching
      )
      val nodes = splitted.map { dataSet =>
        placeInGraph(session, dataSet)
      }
      MultiNodeResponse(nodes)
    }
  }

  override def buildPipeline(request: BuildPipelineRequest): Future[NodeResponse] = handleErrors {
    for {
      session <- sessionManager.get(request.sessionId)
    } yield {
      val steps: Seq[Pipeline.PipelineBuildStep] = request.steps.map { step =>
        step.step match {
          case Step.AlgorithmId(algorithmId) =>
            Pipeline.PipelineBuildStep.AlgorithmBuildStep(session.getItemAs[Algorithm](algorithmId))
          case Step.Select(statement) => Pipeline.PipelineBuildStep.SelectBuildStep(statement)
          case other                  => throw new IllegalArgumentException(s"Unexpected step ${other.getClass.getSimpleName}")
        }
      }
      val inputType = request.inputType.map(Converters.decodeDataType)
      val pipeline = Pipeline.buildFromSteps(steps, inputType)
      placeInGraph(session, pipeline)
    }
  }

  override def tag(request: TagRequest): Future[NodeResponse] = handleErrors {
    for {
      session <- sessionManager.get(request.sessionId)
      item = session.getItemAs[MantikItem](request.itemId)
      mantikId = NamedMantikId.fromString(request.namedMantikId)
      tagged = item.tag(mantikId)
    } yield {
      placeInGraph(session, tagged)
    }
  }

  override def setMetaVariables(request: SetMetaVariableRequest): Future[NodeResponse] = handleErrors {
    for {
      session <- sessionManager.get(request.sessionId)
      item = session.getItemAs[MantikItem](request.itemId)
      values = decodeMetaVariableBundles(item, request)
      applied = item.withMetaValues(values: _*)
    } yield {
      placeInGraph(session, applied)
    }
  }

  private def decodeMetaVariableBundles(
      item: MantikItem,
      request: SetMetaVariableRequest
  ): Seq[(String, SingleElementBundle)] = {
    def toSingle(bundle: Bundle): SingleElementBundle = {
      bundle match {
        case s: SingleElementBundle => s
        case other                  => throw new IllegalArgumentException(s"Expected single element bundle, got ${other}")
      }
    }

    val decodeRequests: Seq[(String, SingleElementBundle)] = request.values.map { value =>
      val metaVariable = item.core.mantikHeader.metaJson.metaVariable(value.name).getOrElse {
        throw new IllegalArgumentException(s"Meta variable ${value.name} not found")
      }
      val decodedValue = value.value match {
        case MetaVariableValue.Value.Json(json) =>
          val parsedJson = CirceJson.forceParseJson(json)
          val parsedMetaValue = JsonFormat
            .deserializeBundleValue(metaVariable.value.model, parsedJson)
            .fold(error => { throw new IllegalArgumentException("Could not parse value", error) }, { x => x })
          toSingle(parsedMetaValue)
        case b: MetaVariableValue.Value.Bundle =>
          toSingle(Converters.decodeBundle(b.value))
        case _ => throw new IllegalArgumentException(s"Missing value for ${value.name}")
      }
      metaVariable.name -> decodedValue
    }
    decodeRequests
  }

  override def construct(responseObserver: StreamObserver[NodeResponse]): StreamObserver[ConstructRequest] = {
    StreamConversions.respondMultiInSingleOutWithHeader[ConstructRequest, NodeResponse](
      translateError,
      responseObserver
    ) { case (header, source) =>
      for {
        session <- sessionManager.get(header.sessionId)
        parsedHeader <- MantikHeader.fromYaml(header.mantikHeaderJson).fold(Future.failed, Future.successful)
        hull <- session.components.retriever.getHull(parsedHeader.definition.referencedItems)
        maybeBridge = findBridge(parsedHeader, hull)
        maybeFileAndContentType <- handleUpload(session, header, source, maybeBridge)
      } yield {
        val itemSource = Source.constructed(
          maybeFileAndContentType
            .map { case (fileId, contentType) =>
              PayloadSource.Loaded(fileId, contentType)
            }
            .getOrElse(PayloadSource.Empty)
        )
        val mantikItem = MantikItem.construct(itemSource, parsedHeader, stateManager, hull)
        placeInGraph(session, mantikItem)
      }
    }
  }

  /** Handle the payload part of an contruct request.
    * @return file id and content type
    */
  private def handleUpload(
      session: Session,
      header: ConstructRequest,
      source: AkkaSource[ConstructRequest, NotUsed],
      maybeBridge: Option[Bridge]
  ): Future[Option[(String, String)]] = {
    if (header.payloadPresent) {
      val contentType = maybeBridge.map(_.mantikHeader.definition.assumedContentType).getOrElse {
        logger.error(s"There is payload, but we do not expect one, assuming zip?!") // can this happen?
        ContentTypes.ZipFileContentType
      }
      val byteSource = source.map(s => RpcConversions.decodeByteString(s.payload))
      session.components.fileRepository
        .uploadNewFile(
          contentType,
          byteSource,
          temporary = true
        )
        .map { case (fileId, _) =>
          Some(fileId -> contentType)
        }
    } else {
      // We still consume the source, some sources do not like to be not consumed.
      source.runWith(Sink.ignore).map { _ =>
        None
      }
    }
  }

  private def findBridge(
      header: MantikHeader[_ <: MantikDefinition],
      hull: Seq[MantikArtifact]
  ): Option[Bridge] = {
    header.definition match {
      case defWithBridge: MantikDefinitionWithBridge =>
        val bridgeId = defWithBridge.bridge
        Some(Bridge.fromMantikArtifacts(bridgeId, hull, header.definition.kind))
      case _ => None
    }
  }
}
