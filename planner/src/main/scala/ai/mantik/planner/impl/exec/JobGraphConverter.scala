package ai.mantik.planner.impl.exec

import ai.mantik.executor.model.{Container, ContainerService, DataProvider, ExistingService, Graph, Job, Link, Node, NodeResourceRef, NodeService, ResourceType}
import ai.mantik.planner.PlanNodeService
import ai.mantik.planner.PlanNodeService.DockerContainer

/**
  * Helper for converting the graph (in [[ai.mantik.planner.PlanOp.RunGraph]])
  * into one, that the [[PlanExecutorImpl]] can execute.
  *
  * This adapter shouldn't be too complex, otherwise we should think about changing the executor more.
  * */
private [impl] class JobGraphConverter (isolationSpace: String, files: ExecutionOpenFiles, contentType: String) {

  /** Translate a graph like the planner creates to a Executor Job. */
  def translateGraphIntoJob(graph: Graph[PlanNodeService]): Job = {
    val containerNodes = graph.nodes.collect {
      case (name, node@ Node(d: DockerContainer, _)) =>
        name -> node.copy(service = convertDockerContainer(d))
    }

    val dataNodes = graph.nodes.collect {
      case (name, node@ Node(_: PlanNodeService.File, _)) =>
        name -> convertFileNode(node.asInstanceOf[Node[PlanNodeService.File]])
    }

    val updatedLinks = fixLinks(graph, containerNodes, dataNodes)
    val updatedGraph = Graph(containerNodes ++ dataNodes, updatedLinks)
    Job(
      isolationSpace,
      updatedGraph,
      contentType = Some(contentType)
    )
  }

  /** Fix the links which happened when transforming from Planner graph to Executor graph.
    * The Planner graph is using default sink/source for File-Nodes, while the translated
    * "ExistingService" is using resource names. */
  def fixLinks(
    graph: Graph[PlanNodeService],
    containerNodes: Map[String, Node[ContainerService]],
    dataNodes: Map[String, Node[ExistingService]]
  ): Seq[Link] = {

    def updateResource(nodeResourceRef: NodeResourceRef): NodeResourceRef = {
      if (containerNodes.contains(nodeResourceRef.node)) {
        nodeResourceRef
      } else {
        dataNodes.get(nodeResourceRef.node) match {
          case Some(node) =>
            // The resource has changed, see method convertFileNode(..)
            val oldResourceType = graph.nodes.get(nodeResourceRef.node).flatMap(_.resources.get(nodeResourceRef.resource)).getOrElse {
              throw new IllegalStateException(s"Could not locate resource $nodeResourceRef")
            }
            val newResourceName = node.resources.filter(_._2 == oldResourceType) match {
              case candidates if candidates.isEmpty =>
                throw new IllegalStateException(s"Could not find resource of type $oldResourceType in node $node")
              case candidates if candidates.size > 1 =>
                throw new IllegalStateException(s"Ambiguous resources for type $oldResourceType in node $node")
              case candidate =>
                candidate.head._1
            }
            nodeResourceRef.copy(resource = newResourceName)
          case None =>
            throw new IllegalStateException(s"Could not locate container ${nodeResourceRef.node}")
        }
      }
    }

    graph.links.map { link =>
      link.copy(from = updateResource(link.from), to = updateResource(link.to))
    }
  }

  /** Convert a docker node to that way the executor expects. */
  def convertDockerContainer(d: PlanNodeService.DockerContainer): ContainerService = {
    val dataUrl = d.data.map { dataFile =>
      val fileGet = files.resolveFileRead(dataFile)
      fileGet.executorClusterUrl + fileGet.resource
    }
    val dataProvider = DataProvider(
      url = dataUrl,
      mantikfile = Some(d.mantikfile.toJson),
      directory = d.mantikfile.definition.directory
    )
    val containerService = ContainerService(
      main = Container(d.image),
      dataProvider = Some(dataProvider),
    )
    containerService
  }

  /** Converts a file node to that way the executor expects. Note: the resource name changes and links must be updated. */
  def convertFileNode(node: Node[PlanNodeService.File]): Node[ExistingService] = {
    require(node.resources.size == 1, "This only works for single-resource nodes yet")
    val (repoUrl, resourceName, resourceType) = node.resources.head._2 match {
      case rt@ (ResourceType.Sink | ResourceType.Transformer) =>
        val writeFileInstance = files.resolveFileWrite(node.service.fileReference)
        (writeFileInstance.executorClusterUrl, writeFileInstance.resource, rt)
      case ResourceType.Source =>
        val readFileInstance = files.resolveFileRead(node.service.fileReference)
        (readFileInstance.executorClusterUrl, readFileInstance.resource, ResourceType.Source)
    }
    Node(
      ExistingService(repoUrl),
      Map(
        resourceName -> resourceType
      )
    )
  }
}
