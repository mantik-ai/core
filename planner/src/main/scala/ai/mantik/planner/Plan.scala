package ai.mantik.planner

import ai.mantik.ds.DataType
import ai.mantik.ds.element.Bundle
import ai.mantik.executor.model.Graph
import ai.mantik.executor.model.docker.Container
import ai.mantik.repository.{ MantikDefinition, MantikId, Mantikfile }

/**
 * A plan is something which can be executed. They are created by the [[Planner]]
 * and are executed by the [[PlanExecutor]].
 */
case class Plan(
    op: PlanOp,
    files: List[PlanFile]
)

/** An Id for a [[PlanFile]] */
case class PlanFileReference(id: Int) extends AnyVal

/** Defines a file which will be accessed within the plan. */
case class PlanFile(
    id: PlanFileReference,
    read: Boolean = false,
    write: Boolean = false,
    fileId: Option[String] = None,
    temporary: Boolean = false
)

/**
 * A node in a planning graph.
 * Node: this is not yet the "real" node, as resolving by the [[PlanExecutor]] is missing.
 */
sealed trait PlanNodeService

object PlanNodeService {

  /** Represents a pure file in a graph. */
  case class File(fileReference: PlanFileReference) extends PlanNodeService

  /** Represents a docker container in the graph. */
  case class DockerContainer(container: Container, data: Option[PlanFileReference] = None, mantikfile: Mantikfile[_ <: MantikDefinition]) extends PlanNodeService
}

/** An operation inside a plan. */
sealed trait PlanOp

object PlanOp {
  /** Nothing to do. */
  case object Empty extends PlanOp

  /** Run a job. */
  case class RunGraph(graph: Graph[PlanNodeService]) extends PlanOp

  /** Push a bundles content to the file repository. */
  case class PushBundle(bundle: Bundle, fileReference: PlanFileReference) extends PlanOp

  /** Pulls a bundle from the file repository. */
  case class PullBundle(dataType: DataType, fileReference: PlanFileReference) extends PlanOp

  /** Add some mantik item. */
  case class AddMantikItem(id: MantikId, file: Option[PlanFileReference], mantikfile: Mantikfile[_ <: MantikDefinition]) extends PlanOp

  /**
   * Run something sequentially, waiting for each other.
   * The result of the last is returned.
   */
  case class Sequential(plans: Seq[PlanOp]) extends PlanOp

  /** Convenience method for constructing Sequential Plans. */
  def seq(plans: PlanOp*): Sequential = Sequential(plans)

  /** Combine two plan ops so that they are executed afterwards, compressing on the fly. */
  def combine(plan1: PlanOp, plan2: PlanOp): PlanOp = {
    plan1 match {
      case PlanOp.Empty               => plan2
      case x if plan2 == PlanOp.Empty => x
      case PlanOp.Sequential(elements) =>
        plan2 match {
          case PlanOp.Sequential(nextElements) =>
            PlanOp.Sequential(elements ++ nextElements)
          case other =>
            PlanOp.Sequential(elements :+ other)
        }
      case x =>
        plan2 match {
          case PlanOp.Sequential(nextElements) =>
            PlanOp.Sequential(x +: nextElements)
          case other =>
            PlanOp.seq(x, other)
        }
    }
  }

  /** Compress a plan op by removing chaings of [[PlanOp.Sequential]]. */
  def compress(planOp: PlanOp): PlanOp = {
    def subCompress(plan: PlanOp): Seq[PlanOp] = {
      plan match {
        case PlanOp.Empty => Nil
        case PlanOp.Sequential(elements) =>
          elements.flatMap(subCompress)
        case other => Seq(other)
      }
    }
    subCompress(planOp) match {
      case s if s.isEmpty => PlanOp.Empty
      case Seq(single)    => single
      case multiple       => PlanOp.Sequential(multiple)
    }
  }
}
