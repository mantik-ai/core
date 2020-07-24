package ai.mantik.planner.impl.exec

import ai.mantik.bridge.protocol.bridge.MantikInitConfiguration
import ai.mantik.executor.model.{ Graph, Node, NodeResourceRef, ResourceType }
import ai.mantik.mnp.protocol.mnp.{ ConfigureInputPort, ConfigureOutputPort }
import ai.mantik.planner.PlanNodeService.DockerContainer
import ai.mantik.planner.Planner.InconsistencyException
import ai.mantik.planner.repository.FileRepository.{ FileGetResult, FileStorageResult }
import ai.mantik.planner.{ PlanNodeService, PlanOp }
import akka.http.scaladsl.model.Uri
import MnpExecutionPreparation._
import ai.mantik.planner.repository.ContentTypes

/** Information for execution of a [[ai.mantik.planner.PlanOp.RunGraph]] */
case class MnpExecutionPreparation(
    sessionInitializers: Map[String, SessionInitializer],
    inputPushs: Vector[InputPush],
    outputPulls: Vector[OutputPull],
    taskQueries: Vector[TaskQuery]
)

object MnpExecutionPreparation {
  /** A prepared session initializer */
  case class SessionInitializer(
      sessionId: String,
      config: MantikInitConfiguration,
      inputPorts: Vector[ConfigureInputPort],
      outputPorts: Vector[ConfigureOutputPort]
  )

  /** A Prepared input push */
  case class InputPush(
      nodeId: String,
      portId: Int,
      fileGetResult: FileGetResult
  )

  /** A prepared output pull */
  case class OutputPull(
      nodeId: String,
      portId: Int,
      contentType: String,
      fileStorageResult: FileStorageResult
  )

  /** A prepared task query. */
  case class TaskQuery(
      nodeId: String
  )
}

/** Builds [[MnpExecutionPreparation]] */
class MnpExecutionPreparer(
    graphId: String,
    graph: Graph[PlanNodeService],
    containerAddresses: Map[String, String], // maps nodeId to running name + port
    files: ExecutionOpenFiles,
    remoteFileRepositoryAddress: Uri
) {

  def build(): MnpExecutionPreparation = {
    val initializers = graph.nodes.collect {
      case (nodeId, Node(d: DockerContainer, _)) => nodeId -> buildSessionCall(nodeId, d)
    }

    val inputPushes = collectInputPushes()
    val outputPulls = collectOutputPulls()
    val taskQueries = collectTaskQueries()
    MnpExecutionPreparation(
      initializers,
      inputPushes,
      outputPulls,
      taskQueries
    )
  }

  private def buildSessionCall(nodeId: String, dockerContainer: DockerContainer): SessionInitializer = {
    val sessionId = sessionIdForNode(nodeId)

    val inputData = dockerContainer.data.map { data =>
      files.resolveFileRead(data.id)
    }

    // TODO: Input/Output Ports should be reflected inside the graph, this is a buggy workaround.
    val graphNode = graph.nodes(nodeId)

    val inputPorts: Vector[ConfigureInputPort] = graphNode.resources.collect {
      case (resourceName, resource) if resource.resourceType == ResourceType.Sink || resource.resourceType == ResourceType.Transformer =>
        portForResource(resourceName) -> ConfigureInputPort(resource.contentType.getOrElse(""))
    }.toVector.sortBy(_._1).map(_._2)

    val outputPorts: Vector[ConfigureOutputPort] = graphNode.resources.collect {
      case (resourceName, resource) if resource.resourceType == ResourceType.Source || resource.resourceType == ResourceType.Transformer =>
        val forwarding = graph.links.collect {
          case link if link.from.node == nodeId && graph.nodes(link.to.node).service.isInstanceOf[DockerContainer] =>
            mnpUrlForResource(link.to)
        }
        val singleForwarding = forwarding match {
          case x if x.isEmpty => None
          case Seq(single)    => Some(single)
          case multiple =>
            throw new InconsistencyException(s"Only a single forwarding is allowed, broken plan? found ${multiple} as goal of ${nodeId}")
        }

        portForResource(resourceName) -> ConfigureOutputPort(
          contentType = resource.contentType.getOrElse(""),
          destinationUrl = singleForwarding.getOrElse("")
        )
    }.toVector.sortBy(_._1).map(_._2)

    val initConfiguration = MantikInitConfiguration(
      header = dockerContainer.mantikHeader.toJson,
      payloadContentType = inputData match {
        case None => "" // encoding for no content type
        case Some(present) =>
          // TODO: Content Type should be always present, see #180
          present.contentType.getOrElse(ContentTypes.ZipFileContentType)
      },
      payload = inputData.map { data =>
        val fullUrl = Uri(data.path).resolvedAgainst(remoteFileRepositoryAddress).toString()
        MantikInitConfiguration.Payload.Url(fullUrl)
      }.getOrElse(
        MantikInitConfiguration.Payload.Empty
      )
    )

    MnpExecutionPreparation.SessionInitializer(sessionId, initConfiguration, inputPorts, outputPorts)
  }

  private def collectInputPushes(): Vector[InputPush] = {
    graph.links.flatMap { link =>
      val to = graph.nodes(link.to.node)
      val from = graph.nodes(link.from.node)
      from.service match {
        case fromFile: PlanNodeService.File =>
          to.service match {
            case toFile: PlanNodeService.File =>
              throw new InconsistencyException("Links from file to file should not happen inside the graph")
            case toNode: PlanNodeService.DockerContainer =>
              Some(
                InputPush(
                  nodeId = link.to.node,
                  portId = portForResource(link.to.resource),
                  fileGetResult = files.resolveFileRead(fromFile.fileReference)
                )
              )
          }
        case _ => None
      }
    }.toVector
  }

  private def collectOutputPulls(): Vector[OutputPull] = {
    graph.links.flatMap { link =>
      val to = graph.nodes(link.to.node)
      val (from, fromResource) = graph.resolveReference(link.from).getOrElse {
        throw new InconsistencyException(s"Could not resolve reference ${link.from}")
      }
      from.service match {
        case container: PlanNodeService.DockerContainer =>
          to.service match {
            case file: PlanNodeService.File =>
              val contentType = fromResource.contentType.getOrElse {
                throw new InconsistencyException(s"No content type for reference ${link.from}")
              }
              Some(
                OutputPull(
                  nodeId = link.from.node,
                  portId = portForResource(link.from.resource),
                  contentType = contentType,
                  fileStorageResult = files.resolveFileWrite(file.fileReference)
                )
              )
            case _ =>
              None
          }
        case _ =>
          None
      }
    }.toVector
  }

  private def collectTaskQueries(): Vector[TaskQuery] = {
    // Task Queries are necessary if a node has no inputs but outputs (DataSets) because it may be forwarded
    graph.nodes.collect {
      case (nodeId, Node(_: PlanNodeService.DockerContainer, _)) if !graph.links.exists(_.to.node == nodeId) =>
        TaskQuery(
          nodeId = nodeId
        )
    }.toVector
  }

  /**
   * Resolve a port number for a resource name
   * Workaround as long this is not reflected in the Plan itself.
   */
  private def portForResource(resourceName: String): Int = {
    MnpExecutionPreparer.ResourceMapping.getOrElse(resourceName, throw new InconsistencyException(s"No port mapping for resource ${resourceName}"))
  }

  private def mnpUrlForResource(nodeResourceRef: NodeResourceRef): String = {
    val port = portForResource(nodeResourceRef.resource)
    val sessionId = sessionIdForNode(nodeResourceRef.node)
    val address = containerAddresses.getOrElse(
      nodeResourceRef.node,
      throw new InconsistencyException(s"Container ${nodeResourceRef.node} not prepared?")
    )
    s"mnp://$address/$sessionId/$port"
  }

  private def sessionIdForNode(nodeId: String): String = {
    graphId + "_" + nodeId
  }

}

private object MnpExecutionPreparer {

  // TODO: Reflect that in the graph
  private val ResourceMapping = Map(
    "get" -> 0,
    "apply" -> 0,
    "train" -> 0,
    "result" -> 0,
    "stats" -> 1
  )
}
