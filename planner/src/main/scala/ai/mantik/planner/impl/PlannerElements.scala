package ai.mantik.planner.impl

import ai.mantik.executor.model.docker.{ Container, DockerConfig }
import ai.mantik.planner
import ai.mantik.planner._
import ai.mantik.planner.graph._
import ai.mantik.planner.repository.{ Bridge, ContentTypes }
import cats.data.State
import com.typesafe.config.Config

/**
 * Raw Elements in Plan Construction.
 */
class PlannerElements(config: Config) {

  private val dockerConfig = DockerConfig.parseFromConfig(config.getConfig("mantik.bridge.docker"))

  import ai.mantik.planner.repository.ContentTypes._

  /** Converts a plan to a job. */
  def sourcePlanToJob(sourcePlan: ResourcePlan): PlanOp[Unit] = {
    PlanOp.combine(
      sourcePlan.pre,
      PlanOp.RunGraph(sourcePlan.graph)
    )
  }

  /** Converts a Literal into a push plan. */
  def literalToPushBundle(literal: PayloadSource.Literal, fileReference: PlanFile): PlanOp[Unit] = {
    literal match {
      case PayloadSource.BundleLiteral(content) =>
        PlanOp.StoreBundleToFile(content, fileReference.ref)
    }
  }

  /** Creates a [[ResourcePlan]] which saves data from it's sink to a file. */
  def createStoreFileNode(fileReference: PlanFile, contentType: Option[String]): State[PlanningState, ResourcePlan] = {
    val node = Node.sink(PlanNodeService.File(fileReference.ref), contentType)
    PlanningState.stateChange(_.withNextNodeId) { nodeId =>
      ResourcePlan(
        pre = PlanOp.Empty,
        graph = Graph(
          Map(
            nodeId -> node
          )
        ),
        inputs = Seq(NodePortRef(nodeId, 0))
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
        pre = PlanOp.Empty,
        graph = graph,
        outputs = Seq(NodePortRef(nodeId, 0))
      )
    }
  }

  /**
   * Generates the plan for a loaded Mantik DataSet.
   * @param dataSet the dataSet
   * @param file the file, if one is present.
   */
  def dataSet(dataSet: DataSet, file: Option[PlanFileReference]): State[PlanningState, ResourcePlan] = {
    val bridge = dataSet.bridge
    val dockerImage = bridge.mantikHeader.definition.dockerImage
    // If there is no docker image, then directly pipe through the dataset
    // TODO this is a hack to get natural DataSets working.
    if (dockerImage == "") {
      val fileToUse = file.getOrElse(throw new planner.Planner.NotAvailableException("No file given for natural file format"))
      loadFileNode(PlanFileWithContentType(fileToUse, ContentTypes.MantikBundleContentType))
    } else {
      val container = resolveBridge(bridge)

      val node = Node.source(
        PlanNodeService.DockerContainer(
          container, data = file, dataSet.mantikHeader
        ),
        Some(MantikBundleContentType)
      )
      makeSingleNodeResourcePlan(node)
    }
  }

  private def makeSingleNodeResourcePlan(node: Node[PlanNodeService]): State[PlanningState, ResourcePlan] = {
    PlanningState.stateChange(_.withNextNodeId) { nodeId =>
      ResourcePlan.singleNode(nodeId, node)
    }
  }

  /** Generates the plan for an algorithm which runtime data may come from a file. */
  def algorithm(algorithm: Algorithm, file: Option[PlanFileReference]): State[PlanningState, ResourcePlan] = {
    val container = algorithmContainer(algorithm, file)
    val node = Node(
      container,
      inputs = Vector(NodePort(Some(MantikBundleContentType))),
      outputs = Vector(NodePort(Some(MantikBundleContentType)))
    )
    makeSingleNodeResourcePlan(node)
  }

  /** Generates the algorithm container for an Algorithm. */
  def algorithmContainer(algorithm: Algorithm, file: Option[PlanFileReference]): PlanNodeService.DockerContainer = {
    val container = resolveBridge(algorithm.bridge)
    PlanNodeService.DockerContainer(
      container, data = file, algorithm.mantikHeader
    )
  }

  /** Generates the plan for a trainable algorithm. */
  def trainableAlgorithm(trainableAlgorithm: TrainableAlgorithm, file: Option[PlanFileReference]): State[PlanningState, ResourcePlan] = {
    val container = resolveBridge(trainableAlgorithm.bridge)

    val node = Node(
      PlanNodeService.DockerContainer(
        container, data = file, mantikHeader = trainableAlgorithm.mantikHeader
      ),
      inputs = Vector(
        NodePort(Some(MantikBundleContentType))
      ),
      outputs = Vector(
        // result
        NodePort(Some(ZipFileContentType)),
        // stats
        NodePort(Some(MantikBundleContentType))
      )
    )
    makeSingleNodeResourcePlan(node)
  }

  /** Construct a container for a Bridge. */
  def resolveBridge(bridge: Bridge): Container = {
    dockerConfig.resolveContainer(
      Container(
        image = bridge.mantikHeader.definition.dockerImage
      )
    )
  }
}
