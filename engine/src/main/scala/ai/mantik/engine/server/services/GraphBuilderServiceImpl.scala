package ai.mantik.engine.server.services

import ai.mantik.engine.protos.graph_builder.{ ApplyRequest, CacheRequest, GetRequest, LiteralRequest, NodeResponse, SelectRequest, TrainRequest, TrainResponse }
import ai.mantik.engine.protos.graph_builder.GraphBuilderServiceGrpc.GraphBuilderService
import ai.mantik.engine.session.{ ArtefactNotFoundException, Session, SessionManager }
import ai.mantik.planner.{ Algorithm, DataSet, MantikItem, TrainableAlgorithm }
import ai.mantik.repository.Errors
import akka.stream.Materializer

import scala.concurrent.{ ExecutionContext, Future }

class GraphBuilderServiceImpl(sessionManager: SessionManager[Session])(implicit ec: ExecutionContext, materializer: Materializer) extends GraphBuilderService {

  override def get(request: GetRequest): Future[NodeResponse] = {
    for {
      session <- sessionManager.get(request.sessionId)
      artifact <- session.components.repository.get(request.name).recover {
        case _: Errors.NotFoundException => throw new ArtefactNotFoundException(request.name)
      }
    } yield {
      val mantikItem = MantikItem.fromMantikArtifact(artifact)
      placeInGraph(session, mantikItem)
    }
  }

  private def placeInGraph(session: Session, item: MantikItem): NodeResponse = {
    val id = session.addItem(item)
    NodeResponse(
      itemId = id,
      item = Some(Converters.encodeMantikItem(item))
    )
  }

  override def algorithmApply(request: ApplyRequest): Future[NodeResponse] = {
    for {
      session <- sessionManager.get(request.sessionId)
      algorithm = session.getItemAs[Algorithm](request.algorithmId)
      dataset = session.getItemAs[DataSet](request.datasetId)
    } yield {
      val result = algorithm.apply(dataset) // TODO: This can fail, catch me!
      placeInGraph(session, result)
    }
  }

  override def literal(request: LiteralRequest): Future[NodeResponse] = {
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

  override def cached(request: CacheRequest): Future[NodeResponse] = {
    for {
      session <- sessionManager.get(request.sessionId)
      dataset = session.getItemAs[DataSet](request.itemId)
    } yield {
      val cachedDataset = dataset.cached
      placeInGraph(session, cachedDataset)
    }
  }

  override def train(request: TrainRequest): Future[TrainResponse] = {
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

  override def select(request: SelectRequest): Future[NodeResponse] = {
    for {
      session <- sessionManager.get(request.sessionId)
      dataset = session.getItemAs[DataSet](request.datasetId)
    } yield {
      val selected = dataset.select(request.selectQuery)
      placeInGraph(session, selected)
    }
  }
}
