package ai.mantik.engine.server.services

import ai.mantik.componently.rpc.RpcConversions
import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.elements.NamedMantikId
import ai.mantik.engine.protos.graph_executor.{
  DeployItemRequest,
  DeployItemResponse,
  FetchItemRequest,
  FetchItemResponse,
  SaveItemRequest,
  SaveItemResponse
}
import ai.mantik.engine.protos.graph_executor.GraphExecutorServiceGrpc.GraphExecutorService
import ai.mantik.engine.session.{EngineErrors, Session, SessionManager}
import ai.mantik.planner.{ApplicableMantikItem, DataSet}
import akka.stream.Materializer
import javax.inject.Inject

import scala.concurrent.{ExecutionContext, Future}

class GraphExecutorServiceImpl @Inject() (sessionManager: SessionManager)(implicit akkaRuntime: AkkaRuntime)
    extends ComponentBase
    with GraphExecutorService
    with RpcServiceBase {

  override def fetchDataSet(request: FetchItemRequest): Future[FetchItemResponse] = handleErrors {
    for {
      session <- sessionManager.get(request.sessionId)
      dataset = session.getItemAs[DataSet](request.datasetId)
      fetchAction = dataset.fetch
      fetchPlan = session.components.planner.convert(fetchAction)
      result <- session.components.planExecutor.execute(fetchPlan)
      encodedBundle = Converters.encodeBundle(result, request.encoding)
    } yield {
      FetchItemResponse(
        Some(encodedBundle)
      )
    }
  }

  override def saveItem(request: SaveItemRequest): Future[SaveItemResponse] = handleErrors {
    for {
      session <- sessionManager.get(request.sessionId)
      item = session.getItem(request.itemId).getOrElse {
        EngineErrors.ItemNotFoundInSession.throwIt(request.itemId)
      }
      mantikId = RpcConversions.decodeOptionalString(request.name).map(NamedMantikId.fromString)
      perhapsTagged = mantikId.map(item.tag).getOrElse(item)
      saveAction = perhapsTagged.save()
      savePlan = session.components.planner.convert(saveAction)
      _ <- session.components.planExecutor.execute(savePlan)
    } yield {
      SaveItemResponse(
        name = RpcConversions.encodeOptionalString(mantikId.map(_.toString)),
        mantikItemId = item.itemId.toString
      )
    }
  }

  override def deployItem(request: DeployItemRequest): Future[DeployItemResponse] = handleErrors {
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
