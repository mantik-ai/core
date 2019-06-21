package ai.mantik.planner.impl

import ai.mantik
import ai.mantik.elements.{AlgorithmDefinition, DataSetDefinition, Mantikfile, TrainableAlgorithmDefinition}
import ai.mantik.executor.model._
import ai.mantik.planner
import ai.mantik.planner._
import ai.mantik.planner.bridge.Bridges
import ai.mantik.planner.repository.ContentTypes
import cats.data.State

/**
 * Raw Elements in Plan Construction.
 * Class should have no side effects (except nodeIdGenerator).
 */
class PlannerElements(bridges: Bridges) {

  import ai.mantik.planner.repository.ContentTypes._

  /** Converts a plan to a job. */
  def sourcePlanToJob(sourcePlan: ResourcePlan): PlanOp = {
    PlanOp.combine(
      sourcePlan.pre,
      PlanOp.RunGraph(sourcePlan.graph)
    )
  }

  /** Converts a Literal into a push plan. */
  def literalToPushBundle(literal: PayloadSource.Literal, fileReference: PlanFile): PlanOp = {
    literal match {
      case PayloadSource.BundleLiteral(content) =>
        PlanOp.PushBundle(content, fileReference.ref)
    }
  }

  /** Creates a [[ResourcePlan]] which saves data from it's sink to a file. */
  def createStoreFileNode(fileReference: PlanFile, contentType: Option[String]): State[PlanningState, ResourcePlan] = {
    val node = Node.sink(PlanNodeService.File(fileReference.ref), contentType)
    PlanningState.stateChange(_.withNextNodeId) { nodeId =>
      ResourcePlan(
        graph = Graph(
          Map(
            nodeId -> node
          )
        ),
        inputs = Seq(NodeResourceRef(nodeId, ExecutorModelDefaults.SinkResource))
      )
    }
  }

  /** Creates a [[ResourcePlan]] which loads a file and represents it as output. */
  def loadFileNode(fileReference: PlanFileWithContentType): State[PlanningState, ResourcePlan] = {
    val node = Node.source(PlanNodeService.File(fileReference.ref), Some(fileReference.contentType))
    PlanningState.stateChange(_.withNextNodeId) { nodeId =>
      val graph = Graph(
        nodes = Map(
          nodeId -> node
        ),
        links = Seq.empty
      )
      ResourcePlan(
        graph = graph,
        outputs = Seq(NodeResourceRef(nodeId, ExecutorModelDefaults.SourceResource))
      )
    }
  }

  /**
   * Generates the plan for a loaded Mantik DataSet.
   * @param mantikfile the mantik of the artefact
   * @param file the file, if one is present.
   */
  def dataSet(mantikfile: Mantikfile[DataSetDefinition], file: Option[PlanFileReference]): State[PlanningState, ResourcePlan] = {
    val bridge = bridges.formatBridge(mantikfile.definition.format).getOrElse {
      throw new Planner.FormatNotSupportedException(s"Format ${mantikfile.definition.format} not supported")
    }
    bridge.container match {
      case None =>
        // directly pipe data
        val fileToUse = file.getOrElse(throw new planner.Planner.NotAvailableException("No file given for natural file format"))
        loadFileNode(PlanFileWithContentType(fileToUse, ContentTypes.MantikBundleContentType))
      case Some(container) =>

        val getResource = "get"

        val node = Node (
          PlanNodeService.DockerContainer (
            container, data = file, mantikfile
          ),
          Map (
            getResource -> NodeResource(ResourceType.Source, Some(MantikBundleContentType))
          )
        )

        PlanningState.stateChange(_.withNextNodeId) { nodeId =>
          val graph = Graph (
            nodes = Map (
              nodeId -> node
            )
          )
          ResourcePlan (
            graph = graph,
            inputs = Nil,
            outputs = Seq(NodeResourceRef(nodeId, getResource))
          )
        }
    }
  }

  /** Generates the plan for an algorithm which runtime data may come from a file. */
  def algorithm(mantikfile: Mantikfile[AlgorithmDefinition], file: Option[PlanFileReference]): State[PlanningState, ResourcePlan] = {
    val bridge = bridges.algorithmBridge(mantikfile.definition.stack).getOrElse {
      throw new Planner.AlgorithmStackNotSupportedException(s"Stack ${mantikfile.definition.stack} not supported")
    }
    val applyResource = "apply"

    val node = Node(
      PlanNodeService.DockerContainer(
        bridge.container, data = file, mantikfile
      ),
      Map(
        applyResource -> NodeResource(ResourceType.Transformer, Some(MantikBundleContentType)),
      )
    )

    PlanningState.stateChange(_.withNextNodeId) { nodeId =>
      val graph = Graph(
        nodes = Map(
          nodeId -> node
        )
      )
      ResourcePlan(
        graph = graph,
        inputs = Seq(NodeResourceRef(nodeId, applyResource)),
        outputs = Seq(NodeResourceRef(nodeId, applyResource))
      )
    }
  }

  /** Generates the plan for a trainable algorithm. */
  def trainableAlgorithm(mantikfile: Mantikfile[TrainableAlgorithmDefinition], file: Option[PlanFileReference]): State[PlanningState, ResourcePlan] = {
    val bridge = bridges.trainableAlgorithmBridge(mantikfile.definition.stack).getOrElse {
      throw new Planner.AlgorithmStackNotSupportedException(s"Stack ${mantikfile.definition.stack} not supported")
    }

    val trainResource = "train"
    val statsResource = "stats"
    val resultResource = "result"

    val node = Node(
      PlanNodeService.DockerContainer(
        bridge.container, data = file, mantikfile = mantikfile
      ),
      Map(
        trainResource -> NodeResource(ResourceType.Sink, Some(MantikBundleContentType)),
        statsResource -> NodeResource(ResourceType.Source, Some(MantikBundleContentType)),
        resultResource -> NodeResource(ResourceType.Source, Some(ZipFileContentType))
      )
    )

    PlanningState.stateChange(_.withNextNodeId) { nodeId =>
      val graph = Graph(
        nodes = Map(
          nodeId -> node
        )
      )
      ResourcePlan(
        graph = graph,
        inputs = Seq(NodeResourceRef(nodeId, trainResource)),
        outputs = Seq(
          NodeResourceRef(nodeId, resultResource),
          NodeResourceRef(nodeId, statsResource)
        )
      )
    }
  }
}
