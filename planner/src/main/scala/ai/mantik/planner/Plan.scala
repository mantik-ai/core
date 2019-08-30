package ai.mantik.planner

import ai.mantik.ds.DataType
import ai.mantik.ds.element.Bundle
import ai.mantik.elements.{ ItemId, MantikDefinition, NamedMantikId, Mantikfile }
import ai.mantik.executor.model.Graph
import ai.mantik.executor.model.docker.Container

/**
 * A plan is something which can be executed. They are created by the [[Planner]]
 * and are executed by the [[PlanExecutor]].
 */
case class Plan[T](
    op: PlanOp,
    files: List[PlanFile],
    cacheGroups: List[CacheKeyGroup]
)

/** An Id for a [[PlanFile]] */
case class PlanFileReference(id: Int) extends AnyVal

object PlanFileReference {
  import scala.language.implicitConversions

  /** Auto convert integer to plan file reference. */
  implicit def fromInt(id: Int): PlanFileReference = PlanFileReference(id)
}

/** Defines a file which will be accessed within the plan. */
case class PlanFile(
    ref: PlanFileReference,
    read: Boolean = false,
    write: Boolean = false,
    fileId: Option[String] = None,
    temporary: Boolean = false,
    cacheKey: Option[CacheKey] = None
) {
  override def toString: String = {
    s"File(ref=${ref},read=${read},write=${write},fileId=${fileId},temp=${temporary},cacheKey=${cacheKey})"
  }
}

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

  /** Stores a Bundle Content as File. */
  case class StoreBundleToFile(bundle: Bundle, fileReference: PlanFileReference) extends PlanOp

  /** Loads a Bundle from a File. */
  case class LoadBundleFromFile(dataType: DataType, fileReference: PlanFileReference) extends PlanOp

  /** Add some mantik item (only the itemId) */
  case class AddMantikItem(item: MantikItem, file: Option[PlanFileReference]) extends PlanOp

  /** Tag some Item.  */
  case class TagMantikItem(item: MantikItem, id: NamedMantikId) extends PlanOp

  /**
   * Push a Mantik Item to a remote registry.
   * (Must be added first)
   */
  case class PushMantikItem(item: MantikItem) extends PlanOp

  /** Deploy an algorithm. */
  case class DeployAlgorithm(
      container: PlanNodeService.DockerContainer,
      serviceId: String,
      serviceNameHint: Option[String],
      item: MantikItem
  ) extends PlanOp

  /** Deploy a Pipeline. */
  case class DeployPipeline(
      item: Pipeline,
      serviceId: String,
      serviceNameHint: Option[String],
      ingress: Option[String],
      steps: Seq[Algorithm]
  ) extends PlanOp

  /**
   * Evaluate the alternative, if any of the given files do not exist.
   * @param files the files to cache. It's keys form a CacheGroup
   */
  case class CacheOp(files: List[(CacheKey, PlanFileReference)], alternative: PlanOp) extends PlanOp {
    /** Returns the cache group for this operation. */
    def cacheGroup: CacheKeyGroup = files.map(_._1)
  }

  /**
   * Run something sequentially, waiting for each other.
   * The result of the last is returned.
   */
  case class Sequential(plans: Seq[PlanOp]) extends PlanOp

  /** Plan Op which just returns a fixed value. */
  case class Const(value: AnyRef) extends PlanOp

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

  /** Compress a plan op by removing chains of [[PlanOp.Sequential]]. */
  def compress(planOp: PlanOp): PlanOp = {
    def subCompress(plan: PlanOp): Seq[PlanOp] = {
      plan match {
        case PlanOp.Empty => Nil
        case PlanOp.Sequential(elements) =>
          elements.flatMap(subCompress)
        case c: PlanOp.CacheOp =>
          Seq(c.copy(alternative = compress(c.alternative)))
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
