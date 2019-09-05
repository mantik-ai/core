package ai.mantik.planner

import ai.mantik.ds.DataType
import ai.mantik.ds.element.Bundle
import ai.mantik.elements.{ ItemId, MantikDefinition, Mantikfile, NamedMantikId }
import ai.mantik.executor.model.Graph
import ai.mantik.executor.model.docker.Container

/**
 * A plan is something which can be executed. They are created by the [[Planner]]
 * and are executed by the [[PlanExecutor]].
 *
 * They should be serializable in future (however this is tricky because of MantikItem references)
 */
case class Plan[T](
    op: PlanOp[T],
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
sealed trait PlanOp[T]

object PlanOp {
  /** PlanOps which do not produce any values. */
  sealed trait ProceduralPlanOp extends PlanOp[Unit]

  /** Nothing to do. */
  case object Empty extends ProceduralPlanOp

  /** Run a job. */
  case class RunGraph(graph: Graph[PlanNodeService]) extends ProceduralPlanOp

  /** Stores a Bundle Content as File. */
  case class StoreBundleToFile(bundle: Bundle, fileReference: PlanFileReference) extends ProceduralPlanOp

  /** Loads a Bundle from a File. */
  case class LoadBundleFromFile(dataType: DataType, fileReference: PlanFileReference) extends PlanOp[Bundle]

  /** Add some mantik item (only the itemId) */
  case class AddMantikItem(item: MantikItem, file: Option[PlanFileReference]) extends ProceduralPlanOp

  /** Tag some Item.  */
  case class TagMantikItem(item: MantikItem, id: NamedMantikId) extends ProceduralPlanOp

  /**
   * Push a Mantik Item to a remote registry.
   * (Must be added first)
   */
  case class PushMantikItem(item: MantikItem) extends ProceduralPlanOp

  /** Deploy an algorithm. */
  case class DeployAlgorithm(
      container: PlanNodeService.DockerContainer,
      serviceId: String,
      serviceNameHint: Option[String],
      item: MantikItem
  ) extends PlanOp[DeploymentState]

  /** Deploy a Pipeline. */
  case class DeployPipeline(
      item: Pipeline,
      serviceId: String,
      serviceNameHint: Option[String],
      ingress: Option[String],
      steps: Seq[Algorithm]
  ) extends PlanOp[DeploymentState]

  /**
   * Evaluate the alternative, if any of the given files do not exist.
   * @param files the files to cache. It's keys form a CacheGroup
   */
  case class CacheOp(files: List[(CacheKey, PlanFileReference)], alternative: PlanOp[_]) extends ProceduralPlanOp {
    /** Returns the cache group for this operation. */
    def cacheGroup: CacheKeyGroup = files.map(_._1)
  }

  /**
   * Run something sequentially, waiting for each other.
   * The result of the last is returned.
   */
  case class Sequential[T](prefix: Seq[PlanOp[_]], last: PlanOp[T]) extends PlanOp[T] {
    def size: Int = prefix.size + 1

    def plans: Seq[PlanOp[_]] = prefix :+ last
  }

  /** Plan Op which just returns a fixed value. */
  case class Const[T](value: T) extends PlanOp[T]

  /**
   * Plan op which stores the result of the last operation into the memory.
   * Also returns the value again to make it transparent
   */
  case class MemoryWriter[T](memoryId: MemoryId) extends PlanOp[T]

  /** Plan op which reads the result of another one from the memory. Must be called later. */
  case class MemoryReader[T](memoryId: MemoryId) extends PlanOp[T]

  /** Convenience method for constructing Sequential Plans. */
  def seq(): Sequential[Unit] = Sequential(Nil, PlanOp.Empty)
  def seq[T](a: PlanOp[T]): Sequential[T] = Sequential(Nil, a)
  def seq[T](a: PlanOp[_], b: PlanOp[T]): Sequential[T] = Sequential(Seq(a), b)
  def seq[T](a: PlanOp[_], b: PlanOp[_], c: PlanOp[T]): Sequential[T] = Sequential(Seq(a, b), c)
  def seq[T](a: PlanOp[_], b: PlanOp[_], c: PlanOp[_], d: PlanOp[T]): Sequential[T] = Sequential(Seq(a, b, c), d)

  /** Combine two plan ops so that they are executed afterwards, compressing on the fly. */
  def combine[T](plan1: PlanOp[_], plan2: PlanOp[T]): PlanOp[T] = {
    plan1 match {
      case PlanOp.Empty => plan2
      case PlanOp.Sequential(elements, last) =>
        plan2 match {
          case PlanOp.Sequential(nextElements, last2) =>
            PlanOp.Sequential((elements :+ last) ++ nextElements, last2)
          case other =>
            PlanOp.Sequential(elements :+ last, other)
        }
      case x: PlanOp.ProceduralPlanOp =>
        plan2 match {
          case PlanOp.Empty =>
            x
          case PlanOp.Sequential(nextElements, last) =>
            PlanOp.Sequential(x +: nextElements, last)
          case other =>
            PlanOp.seq(x, other)
        }
      case x =>
        plan2 match {
          case PlanOp.Sequential(nextElements, last) =>
            PlanOp.Sequential(x +: nextElements, last)
          case other =>
            PlanOp.seq(x, other)
        }
    }
  }

  /** Compress a plan op by removing chains of [[PlanOp.Sequential]]. */
  def compress[T](planOp: PlanOp[T]): PlanOp[T] = {
    subCompress(planOp) match {
      case (elements, last) if elements.isEmpty => last
      case (elements, last)                     => PlanOp.Sequential(elements, last)
    }
  }

  private def subCompress[T](plan: PlanOp[T]): (Seq[PlanOp[_]], PlanOp[T]) = {
    import scala.language.existentials
    plan match {
      case PlanOp.Empty => Nil -> plan
      case PlanOp.Sequential(elements, last) =>
        val (lastCompressed, lastCompressedTail) = subCompress(last)
        val parts = (elements ++ lastCompressed).flatMap { x =>
          val (prefix, tail) = subCompress(x)
          if (tail == PlanOp.Empty) {
            prefix
          } else {
            prefix :+ tail
          }
        }
        parts -> lastCompressedTail
      case c: PlanOp.CacheOp =>
        val c2 = c.copy(alternative = compress(c.alternative))
        Nil -> c2
      case other =>
        Nil -> other
    }
  }
}
