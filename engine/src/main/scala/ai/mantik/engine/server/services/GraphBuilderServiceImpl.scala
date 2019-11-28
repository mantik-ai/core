package ai.mantik.engine.server.services

import ai.mantik.componently.{ AkkaRuntime, ComponentBase }
import ai.mantik.ds.element.{ Bundle, SingleElementBundle }
import ai.mantik.ds.formats.json.JsonFormat
import ai.mantik.ds.helper.circe.CirceJson
import ai.mantik.elements.NamedMantikId
import ai.mantik.engine.protos.graph_builder.BuildPipelineStep.Step
import ai.mantik.engine.protos.graph_builder.{ ApplyRequest, BuildPipelineRequest, CacheRequest, GetRequest, LiteralRequest, MetaVariableValue, NodeResponse, SelectRequest, SetMetaVariableRequest, TagRequest, TrainRequest, TrainResponse }
import ai.mantik.engine.protos.graph_builder.GraphBuilderServiceGrpc.GraphBuilderService
import ai.mantik.engine.session.{ Session, SessionManager }
import ai.mantik.planner.{ Algorithm, ApplicableMantikItem, BuiltInItems, DataSet, MantikItem, Pipeline, TrainableAlgorithm }
import akka.http.scaladsl.util.FastFuture
import akka.stream.Materializer
import javax.inject.Inject

import scala.concurrent.{ ExecutionContext, Future }

class GraphBuilderServiceImpl @Inject() (sessionManager: SessionManager)(implicit akkaRuntime: AkkaRuntime) extends ComponentBase with GraphBuilderService with RpcServiceBase {

  override def get(request: GetRequest): Future[NodeResponse] = handleErrors {
    for {
      session <- sessionManager.get(request.sessionId)
      mantikItem <- retrieve(session, request.name)
    } yield {
      placeInGraph(session, mantikItem)
    }
  }

  private def retrieve(session: Session, name: String): Future[MantikItem] = {
    BuiltInItems.readBuiltInItem(name) match {
      case Some(builtIn) => Future.successful(builtIn)
      case None => session.components.retriever.get(name).map {
        case (artifact, hull) =>
          MantikItem.fromMantikArtifact(artifact, hull)
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
      val result = algorithm.apply(dataset) // TODO: This can fail, catch me!
      placeInGraph(session, result)
    }
  }

  override def literal(request: LiteralRequest): Future[NodeResponse] = handleErrors {
    for {
      session <- sessionManager.get(request.sessionId)
      bundle <- Converters.decodeBundle(request.bundle.getOrElse(
        throw new IllegalArgumentException("Missing Bundle")
      ))
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

  override def buildPipeline(request: BuildPipelineRequest): Future[NodeResponse] = handleErrors {
    for {
      session <- sessionManager.get(request.sessionId)
    } yield {
      val steps: Seq[Pipeline.PipelineBuildStep] = request.steps.map { step =>
        step.step match {
          case Step.AlgorithmId(algorithmId) => Pipeline.PipelineBuildStep.AlgorithmBuildStep(session.getItemAs[Algorithm](algorithmId))
          case Step.Select(statement)        => Pipeline.PipelineBuildStep.SelectBuildStep(statement)
          case other                         => throw new IllegalArgumentException(s"Unexpected step ${other.getClass.getSimpleName}")
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

  override def setMetaVariables(request: SetMetaVariableRequest): Future[NodeResponse] = {
    for {
      session <- sessionManager.get(request.sessionId)
      item = session.getItemAs[MantikItem](request.itemId)
      values <- decodeMetaVariableBundles(item, request)
      applied = item.withMetaValues(values: _*)
    } yield {
      placeInGraph(session, applied)
    }
  }

  private def decodeMetaVariableBundles(item: MantikItem, request: SetMetaVariableRequest): Future[Seq[(String, SingleElementBundle)]] = {
    import FastFuture._

    def toSingle(bundle: Bundle): SingleElementBundle = {
      bundle match {
        case s: SingleElementBundle => s
        case other                  => throw new IllegalArgumentException(s"Expected single element bundle, got ${other}")
      }
    }

    val decodeRequests: Seq[Future[(String, SingleElementBundle)]] = request.values.map { value =>
      val metaVariable = item.core.mantikfile.metaJson.metaVariable(value.name).getOrElse {
        throw new IllegalArgumentException(s"Meta variable ${value.name} not found")
      }
      val decodedValue = value.value match {
        case MetaVariableValue.Value.Json(json) =>
          val parsedJson = CirceJson.forceParseJson(json)
          val parsedMetaValue = JsonFormat.deserializeBundleValue(metaVariable.value.model, parsedJson).fold(error =>
            { throw new IllegalArgumentException("Could not parse value", error) }, { x => x }
          )
          FastFuture.successful(toSingle(parsedMetaValue))
        case b: MetaVariableValue.Value.Bundle =>
          Converters.decodeBundle(b.value).fast.map(toSingle)
        case _ => throw new IllegalArgumentException(s"Missing value for ${value.name}")
      }
      decodedValue.fast.map { value =>
        metaVariable.name -> value
      }
    }
    Future.sequence(decodeRequests)
  }
}
