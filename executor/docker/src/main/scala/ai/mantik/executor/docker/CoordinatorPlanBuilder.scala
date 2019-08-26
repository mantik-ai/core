package ai.mantik.executor.docker

import ai.mantik.executor.common.CoordinatorPlan
import ai.mantik.executor.model.GraphAnalysis.Flow
import ai.mantik.executor.model.{ ContainerService, ExistingService, GraphAnalysis, Job, Node, NodeService }
import cats.data.State
import cats.implicits._

/** Builds the coordinator plan for jobs. */
object CoordinatorPlanBuilder {

  /** Build a coordinator plan for a job.  */
  def generateCoordinatorPlan(job: Job): State[NameGenerator, CoordinatorPlan] = {
    val analysis = new GraphAnalysis(job.graph)

    val nodesState = job.graph.nodes.toVector.map {
      case (name, node) =>
        convertCoordinatorNode(name, node).map { node =>
          name -> node
        }
    }.sequence

    val flows = analysis.flows.toVector.map(
      convertFlow(_, job)
    )

    nodesState.map { nodes =>
      CoordinatorPlan(
        nodes = nodes.toMap,
        flows = flows
      )
    }
  }

  /** Build a single coordinator node. */
  private def convertCoordinatorNode(name: String, node: Node[NodeService]): State[NameGenerator, CoordinatorPlan.Node] = {
    node.service match {
      case c: ContainerService =>
        NameGenerator.stateChange(_.nodeName(name)) { dockerNodeName =>
          val url = s"http://${dockerNodeName.internalHostName}:${c.port}"
          CoordinatorPlan.Node(
            url = Some(url),
            quitAfterwards = Some(true)
          )
        }
      case e: ExistingService =>
        State.pure {
          CoordinatorPlan.Node(
            url = Some(e.url)
          )
        }
    }
  }

  private def convertFlow(flow: Flow, job: Job): Seq[CoordinatorPlan.NodeResourceRef] = {
    flow.nodes.map { element =>
      val (_, nodeResource) = job.graph.resolveReference(element).get
      CoordinatorPlan.NodeResourceRef(element.node, element.resource, nodeResource.contentType)
    }
  }
}
