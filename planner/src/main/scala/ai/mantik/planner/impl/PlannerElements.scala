package ai.mantik.planner.impl

import ai.mantik
import ai.mantik.executor.model._
import ai.mantik.planner
import ai.mantik.planner._
import ai.mantik.planner.bridge.Bridges
import ai.mantik.repository._
import cats.data.State

/**
 * Raw Elements in Plan Construction.
 * Class should have no side effects (except nodeIdGenerator).
 */
class PlannerElements(bridges: Bridges) {

  /** Converts a plan to a job. */
  def sourcePlanToJob(sourcePlan: ResourcePlan): PlanOp = {
    PlanOp.combine(
      sourcePlan.pre,
      PlanOp.RunGraph(sourcePlan.graph)
    )
  }

  /** Converts a Literal into a push plan. */
  def literalToPushBundle(literal: Source.Literal, fileReference: PlanFile): PlanOp = {
    literal match {
      case Source.BundleLiteral(content) =>
        PlanOp.PushBundle(content, fileReference.id)
    }
  }

  /** Creates a [[ResourcePlan]] which saves data from it's sink to a file. */
  def createStoreFileNode(fileReference: PlanFile): State[PlanningState, ResourcePlan] = {
    val node = Node.sink(PlanNodeService.File(fileReference.id))
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
  def loadFileNode(fileReference: PlanFileReference): State[PlanningState, ResourcePlan] = {
    val node = Node.source(PlanNodeService.File(fileReference))
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
      throw new Planner.FormatNotSupportedException(mantikfile.definition.format)
    }
    bridge.container match {
      case None =>
        // directly pipe data
        val fileToUse = file.getOrElse(throw new planner.Planner.NotAvailableException("No file given for natural file format"))
        loadFileNode(fileToUse)
      case Some(containerName) =>
        throw new mantik.planner.Planner.NotAvailableException(s"No support yet for file format plugins ($containerName")
    }
  }

  /** Generates the plan for an algorithm which runtime data may come from a file. */
  def algorithm(mantikfile: Mantikfile[AlgorithmDefinition], file: Option[PlanFileReference]): State[PlanningState, ResourcePlan] = {
    val bridge = bridges.algorithmBridge(mantikfile.definition.stack).getOrElse {
      throw new Planner.AlgorithmStackNotSupportedException(mantikfile.definition.stack)
    }
    val applyResource = "apply"

    val node = Node(
      PlanNodeService.DockerContainer(
        bridge.container, data = file, mantikfile
      ),
      Map(
        applyResource -> ResourceType.Transformer
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
      throw new Planner.AlgorithmStackNotSupportedException(mantikfile.definition.stack)
    }

    val trainResource = "train"
    val statsResource = "stats"
    val resultResource = "result"

    val node = Node(
      PlanNodeService.DockerContainer(
        bridge.container, data = file, mantikfile = mantikfile
      ),
      Map(
        trainResource -> ResourceType.Sink,
        statsResource -> ResourceType.Source,
        resultResource -> ResourceType.Source
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
