package ai.mantik.engine.server.services

import ai.mantik.engine.protos.graph_executor.{ FetchItemRequest, FetchItemResponse, SaveItemRequest, SaveItemResponse }
import ai.mantik.engine.protos.graph_executor.GraphExecutorServiceGrpc.GraphExecutorService
import ai.mantik.engine.session.{ ItemNotFoundException, ItemWrongTypeException, Session, SessionManager }
import ai.mantik.planner.DataSet
import ai.mantik.repository.MantikId
import akka.stream.Materializer

import scala.concurrent.{ ExecutionContext, Future }

class GraphExecutorServiceImpl(sessionManager: SessionManager[Session])(implicit ec: ExecutionContext, materializer: Materializer) extends GraphExecutorService {

  override def fetchDataSet(request: FetchItemRequest): Future[FetchItemResponse] = {
    for {
      session <- sessionManager.get(request.sessionId)
      dataset = session.getItemAs[DataSet](request.datasetId)
      fetchAction = dataset.fetch
      fetchPlan = session.components.planner.convert(fetchAction)
      result <- session.components.planExecutor.execute(fetchPlan)
      encodedBundle <- Converters.encodeBundle(result, request.encoding)
    } yield {
      FetchItemResponse(
        Some(encodedBundle)
      )
    }
  }

  override def saveItem(request: SaveItemRequest): Future[SaveItemResponse] = {
    for {
      session <- sessionManager.get(request.sessionId)
      item = session.getItem(request.itemId).getOrElse {
        throw new ItemNotFoundException(request.itemId)
      }
      mantikId = MantikId.fromString(request.name)
      saveAction = item.save(mantikId)
      savePlan = session.components.planner.convert(saveAction)
      _ <- session.components.planExecutor.execute(savePlan)
    } yield {
      SaveItemResponse(
        name = mantikId.toString
      )
    }
  }
}
