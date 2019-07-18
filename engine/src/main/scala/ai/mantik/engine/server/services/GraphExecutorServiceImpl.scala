package ai.mantik.engine.server.services

import ai.mantik.componently.{ AkkaRuntime, ComponentBase }
import ai.mantik.elements.MantikId
import ai.mantik.engine.protos.graph_executor.{ DeployItemRequest, DeployItemResponse, FetchItemRequest, FetchItemResponse, SaveItemRequest, SaveItemResponse }
import ai.mantik.engine.protos.graph_executor.GraphExecutorServiceGrpc.GraphExecutorService
import ai.mantik.engine.session.{ ItemNotFoundException, ItemWrongTypeException, Session, SessionManager }
import ai.mantik.planner.{ ApplicableMantikItem, DataSet }
import akka.stream.Materializer

import scala.concurrent.{ ExecutionContext, Future }

class GraphExecutorServiceImpl(sessionManager: SessionManager[Session])(implicit akkaRuntime: AkkaRuntime) extends ComponentBase with GraphExecutorService {

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

  override def deployItem(request: DeployItemRequest): Future[DeployItemResponse] = {
    for {
      session <- sessionManager.get(request.sessionId)
      item = session.getItemAs[ApplicableMantikItem](request.itemId)
      action = item.deploy(
        ingressName = optionalString(request.ingressName),
        nameHint = optionalString(request.nameHint)
      )
      plan = session.components.planner.convert(action)
      result <- session.components.planExecutor.execute(plan)
    } yield {
      DeployItemResponse(
        name = result.name,
        internalUrl = result.internalUrl,
        externalUrl = result.externalUrl.getOrElse("")
      )
    }
  }

  private def optionalString(s: String): Option[String] = {
    if (s.isEmpty) {
      None
    } else {
      Some(s)
    }
  }
}
