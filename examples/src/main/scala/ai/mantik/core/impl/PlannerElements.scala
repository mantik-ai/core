package ai.mantik.core.impl

import ai.mantik.core.impl.PlannerElements.SourcePlan
import ai.mantik.core.impl.PlannerImpl.NodeIdGenerator
import ai.mantik.core.plugins.Plugins
import ai.mantik.core._
import ai.mantik.ds.element.Bundle
import ai.mantik.executor.model._
import ai.mantik.repository._
import ai.mantik.repository.FileRepository.{ FileGetResult, FileStorageResult }
import PlannerGraphOps._

/**
 * Raw Elements in Plan Construction.
 * Class should have no side effects (except nodeIdGenerator).
 */
class PlannerElements(formats: Plugins, isolationSpace: String, contentType: String) {

  /** Saves a resource to mantik by using a given FileStorage */
  def saveAction(
    item: MantikItem,
    mantikId: MantikId,
    storage: FileStorageResult,
    sourcePlan: SourcePlan
  )(implicit nodeIdGenerator: NodeIdGenerator): Plan = {
    val saver = generateAddMantikItem(item, mantikId, storage.fileId)
    val sinkResource = storage.resource
    val sinkNodeId = nodeIdGenerator.makeId()
    val sinkNode = Node(
      ExistingService(storage.executorClusterUrl),
      resources = Map(
        sinkResource -> ResourceType.Sink
      )
    )
    val fullGraph = sourcePlan.graph.addNodes(
      Map(
        sinkNodeId -> sinkNode
      )
    ).addLinks(
        Link(sourcePlan.output, NodeResourceRef(sinkNodeId, sinkResource))
      )
    Plan.Sequential(
      Seq(
        sourcePlan.preplan,
        Plan.RunJob(Job(isolationSpace, fullGraph, Some(contentType))),
        saver
      )
    )
  }

  private def generateAddMantikItem(item: MantikItem, mantikId: MantikId, fileId: String): Plan.AddMantikItem = {
    item match {
      case d: DataSet =>
        val definition = DataSetDefinition(
          name = mantikId.name,
          version = mantikId.version,
          format = "natural",
          `type` = d.dataType
        )
        val artefact = MantikArtefact(
          Mantikfile.pure(definition),
          Some(fileId)
        )
        Plan.AddMantikItem(artefact)
      case other =>
        // TODO
        ???
    }
  }

  /** Fetches a resource from the cluster. */
  def fetchAction(dataSet: DataSet, sourcePlan: SourcePlan, storage: FileStorageResult)(implicit nodeIdGenerator: NodeIdGenerator): Plan = {
    val pullItem = Plan.PullBundle(dataSet.dataType, storage.fileId)
    val sinkResource = storage.resource
    val sinkNodeId = nodeIdGenerator.makeId()
    val sinkNode = Node(
      ExistingService(storage.executorClusterUrl),
      resources = Map(
        sinkResource -> ResourceType.Sink
      )
    )
    val fullGraph = sourcePlan.graph.addNodes(
      Map(
        sinkNodeId -> sinkNode
      )
    ).addLinks(
        Link(sourcePlan.output, NodeResourceRef(sinkNodeId, sinkResource))
      )
    Plan.Sequential(
      Seq(
        sourcePlan.preplan,
        Plan.RunJob(Job(isolationSpace, fullGraph, Some(contentType))),
        pullItem
      )
    )
  }

  /**
   * Generates the plan for a loaded Mantik DataSet.
   * @param artefact the mantik artefact
   * @param file the file, if one is present.
   */
  def loadedDataSet(artefact: MantikArtefact, file: Option[FileGetResult])(implicit nodeIdGenerator: NodeIdGenerator): SourcePlan = {
    val dsArtefact = artefact.mantikfile.cast[DataSetDefinition].getOrElse {
      throw new Planner.InconsistencyException(s"Expected data set for ${artefact.id}, got ${artefact.mantikfile.definition.kind}")
    }
    val plugin = formats.pluginForFormat(dsArtefact.definition.format).getOrElse {
      throw new Planner.FormatNotSupportedException(dsArtefact.definition.format)
    }
    val nodeId = nodeIdGenerator.makeId()
    val (node, resourceId) = plugin.createClusterReader(dsArtefact, file.map { f => f.executorClusterUrl -> f.resource })
    val graph = Graph(
      nodes = Map(
        nodeId -> node
      )
    )
    SourcePlan(
      graph = graph,
      output = NodeResourceRef(nodeId, resourceId)
    )
  }

  /** Generates the plan for using a data literal. */
  def literalDataSet(bundle: Bundle, fileStorageResult: FileStorageResult)(implicit nodeIdGenerator: NodeIdGenerator): SourcePlan = {
    val push = Plan.PushBundle(bundle, fileStorageResult.fileId)
    val nodeId = nodeIdGenerator.makeId()
    val graph = Graph(
      nodes = Map(
        nodeId -> Node(
          ExistingService(fileStorageResult.executorClusterUrl),
          resources = Map(fileStorageResult.resource -> ResourceType.Source)
        )
      ),
      links = Seq.empty
    )
    SourcePlan(
      push,
      graph,
      NodeResourceRef(nodeId, fileStorageResult.resource)
    )
  }

  /** Generates the plan for transforming a source using an algorithm. */
  def appliedTransformation(sourcePlan: SourcePlan, algorithmPlan: SourcePlan): SourcePlan = {
    val fullGraph = sourcePlan.graph.mergeWith(algorithmPlan.graph)
      .addLinks(
        Link(sourcePlan.output, algorithmPlan.output)
      )
    val resultingPlan = Plan.Sequential(
      Seq(
        sourcePlan.preplan,
        algorithmPlan.preplan
      )
    )
    SourcePlan(
      resultingPlan,
      fullGraph,
      algorithmPlan.output
    )
  }

  /** Generates the plan for loading an algorithm*/
  def loadedAlgorithm(artefact: MantikArtefact, file: Option[FileGetResult])(implicit nodeIdGenerator: NodeIdGenerator): SourcePlan = {
    val dsArtefact = artefact.mantikfile.cast[AlgorithmDefinition].getOrElse {
      throw new Planner.InconsistencyException(s"Expected algorithm for ${artefact.id}, got ${artefact.mantikfile.definition.kind}")
    }
    val plugin = formats.pluginForAlgorithm(dsArtefact.definition.stack).getOrElse {
      throw new Planner.AlgorithmStackNotSupportedException(dsArtefact.definition.stack)
    }
    val nodeId = nodeIdGenerator.makeId()
    val (node, resourceId) = plugin.createClusterTransformation(dsArtefact, file.map { f => f.executorClusterUrl -> f.resource })
    val graph = Graph(
      nodes = Map(
        nodeId -> node
      )
    )
    SourcePlan(
      graph = graph,
      output = NodeResourceRef(
        nodeId, resourceId
      )
    )
  }
}

object PlannerElements {

  /**
   * Describes the way a source is calculated.
   * @param preplan a plan which must be executed before evaluating the graph
   * @param the graph to evaluate
   * @param output the result node resource in the graph
   */
  case class SourcePlan(
      preplan: Plan = Plan.Empty,
      graph: Graph,
      output: NodeResourceRef
  )
}