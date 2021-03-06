/*
 * This file is part of the Mantik Project.
 * Copyright (c) 2020-2021 Mantik UG (Haftungsbeschränkt)
 * Authors: See AUTHORS file
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.
 *
 * Additionally, the following linking exception is granted:
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with other code, such other code is not for that reason
 * alone subject to any of the requirements of the GNU Affero GPL
 * version 3.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license.
 */
package ai.mantik.planner

import ai.mantik.componently.utils.Renderable
import ai.mantik.ds.DataType
import ai.mantik.ds.element.Bundle
import ai.mantik.elements.{ItemId, MantikDefinition, MantikHeader, NamedMantikId}
import ai.mantik.executor.model.docker.Container
import ai.mantik.planner.graph.{Graph, Node, NodePortRef}
import akka.NotUsed
import akka.util.ByteString

import scala.annotation.tailrec

/**
  * A plan is something which can be executed. They are created by the [[Planner]]
  * and are executed by the [[PlanExecutor]].
  *
  * They should be serializable in future (however this is tricky because of MantikItem references)
  */
case class Plan[T](
    op: PlanOp[T],
    files: List[PlanFile],
    cachedItems: Set[Vector[ItemId]],
    name: Option[String] = None
) {

  /** Returns operations as flat list with their coordinates. */
  def flatWithCoordinates: Seq[(List[Int], PlanOp[_])] = PlanOp.flatWithCoordinates(op)

  /** Files by file reference. */
  lazy val fileMap: Map[PlanFileReference, PlanFile] = {
    files.map { file =>
      file.ref -> file
    }.toMap
  }
}

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
    contentType: String,
    read: Boolean = false,
    write: Boolean = false,
    fileId: Option[String] = None,
    temporary: Boolean = false,
    cacheItemId: Option[ItemId] = None
) {
  override def toString: String = {
    s"File(ref=${ref},contentType=${contentType},read=${read},write=${write},fileId=${fileId},temp=${temporary},cacheItemId=${cacheItemId})"
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

  /** Represents a docker container in the graph.
    * @param container container to spin up
    * @param data data as file upload
    * @param mantikHeader Mantik header to initialize
    * @param embeddedData embedded data (with Content Type)
    */
  case class DockerContainer(
      container: Container,
      data: Option[PlanFileReference] = None,
      mantikHeader: MantikHeader[_ <: MantikDefinition],
      embeddedData: Option[(String, ByteString)] = None
  ) extends PlanNodeService

  implicit val renderable = new Renderable[PlanNodeService] {
    override def buildRenderTree(value: PlanNodeService): Renderable.RenderTree = {
      value match {
        case x: File =>
          Renderable.keyValueList("File", "file" -> x.fileReference.id.toString)
        case d: DockerContainer =>
          Renderable.keyValueList(
            "Docker",
            "container" -> d.container,
            "data" -> d.data.map(_.id.toString),
            "embeddedData" -> d.embeddedData.map { e => s"Embedded ${e._2.size} bytes" },
            "mantikHeader" -> d.mantikHeader.toJsonValue.noSpaces
          )
      }
    }
  }
}

/** An operation inside a plan. */
sealed trait PlanOp[T] {
  def foldLeftDown[S](s0: S)(f: (S, PlanOp[_]) => S): S = {
    f(s0, this)
  }
}

object PlanOp {

  /** PlanOps which do not produce any values. */
  sealed trait ProceduralPlanOp extends PlanOp[Unit]

  /** Basic operation which doesn't involve the Executor. */
  sealed trait BasicOp[T] extends PlanOp[T]

  /** Nothing to do. */
  case object Empty extends ProceduralPlanOp with BasicOp[Unit]

  /** Run a job. */
  case class RunGraph(graph: Graph[PlanNodeService]) extends ProceduralPlanOp

  /** Stores a Bundle Content as File. */
  case class StoreBundleToFile(bundle: Bundle, fileReference: PlanFileReference)
      extends ProceduralPlanOp
      with BasicOp[Unit]

  /** Loads a Bundle from a File. */
  case class LoadBundleFromFile(dataType: DataType, fileReference: PlanFileReference)
      extends PlanOp[Bundle]
      with BasicOp[Bundle]

  /** Uploads generic data */
  case class UploadFile(data: ByteString, fileReference: PlanFileReference) extends ProceduralPlanOp with BasicOp[Unit]

  /** Add some mantik item (only the itemId) */
  case class AddMantikItem(item: MantikItem, file: Option[PlanFileReference])
      extends ProceduralPlanOp
      with BasicOp[Unit]

  /** Tag some Item. */
  case class TagMantikItem(item: MantikItem, id: NamedMantikId) extends ProceduralPlanOp with BasicOp[Unit]

  /**
    * Push a Mantik Item to a remote registry.
    * (Must be added first)
    */
  case class PushMantikItem(item: MantikItem) extends ProceduralPlanOp with BasicOp[Unit]

  /**
    * Deploys a pipeline.
    * @param itemId the item id
    * @param graph the evaluation graph, must have one open in- and output
    * @param input the node/port where data flows in
    * @param inputDataType input data type
    * @param output the node/port where dat flows out
    * @param outputDataType output data type
    * @param serviceId the service id (evaluation.id) to deploy it
    * @param serviceNameHint name hint for the service
    * @param ingress name for the ingress
    */
  case class DeployPipeline(
      itemId: ItemId,
      graph: Graph[PlanNodeService],
      input: NodePortRef,
      inputDataType: DataType,
      output: NodePortRef,
      outputDataType: DataType,
      serviceId: String,
      serviceNameHint: Option[String],
      ingress: Option[String]
  ) extends PlanOp[DeploymentState]

  /** Mark files as being cached. */
  case class MarkCached(files: Vector[(ItemId, PlanFileReference)]) extends ProceduralPlanOp with BasicOp[Unit] {
    def siblingIds: Vector[ItemId] = files.map(_._1)
  }

  /**
    * Run something sequentially, waiting for each other.
    * The result of the last is returned.
    */
  case class Sequential[T](prefix: Seq[PlanOp[_]], last: PlanOp[T]) extends PlanOp[T] {
    def size: Int = prefix.size + 1

    def plans: Seq[PlanOp[_]] = prefix :+ last

    override def foldLeftDown[S](s0: S)(f: (S, PlanOp[_]) => S): S = {
      val s1 = super.foldLeftDown(s0)(f)
      plans.foldLeft(s1)(f)
    }
  }

  /** Plan Op which just returns a fixed value. */
  case class Const[T](value: T) extends PlanOp[T] with BasicOp[T]

  /** Copy a file. */
  case class CopyFile(from: PlanFileReference, to: PlanFileReference) extends ProceduralPlanOp with BasicOp[Unit]

  /**
    * Plan op which stores the result of the last operation into the memory.
    * Also returns the value again to make it transparent
    */
  case class MemoryWriter[T](memoryId: MemoryId) extends PlanOp[T] with BasicOp[T]

  /** Plan op which reads the result of another one from the memory. Must be called later. */
  case class MemoryReader[T](memoryId: MemoryId) extends PlanOp[T] with BasicOp[T]

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
      case other =>
        Nil -> other
    }
  }

  /** Generates a flat list with coordinates for all PlanOps. */
  def flatWithCoordinates(op: PlanOp[_]): Seq[(List[Int], PlanOp[_])] = {
    def build(path: List[Int], current: PlanOp[_]): Seq[(List[Int], PlanOp[_])] = {
      current match {
        case s: Sequential[_] =>
          s.plans.zipWithIndex.flatMap { case (op, idx) =>
            build(idx :: path, op)
          }
        case other => Seq(path -> other)
      }
    }
    val results = build(Nil, op)
    // Note: results have reversed order in path
    results.map { case (reversedPath, op) =>
      reversedPath.reverse -> op
    }
  }

  implicit def renderable[T]: Renderable[PlanOp[T]] = new Renderable[PlanOp[T]] {
    override def buildRenderTree(value: PlanOp[T]): Renderable.RenderTree = {
      value match {
        case Empty => Renderable.buildRenderTree("empty")
        case g: RunGraph =>
          Renderable.keyValueList(
            "RunGraph",
            "graph" -> g.graph
          )
        case Sequential(prefix, last) =>
          Renderable.SubTree(
            title = Some("Sequential"),
            items = (prefix :+ last).map { op =>
              Renderable.buildRenderTree(op)
            }.toIndexedSeq
          )
        case other =>
          Renderable.Leaf(other.toString)
      }
    }
  }
}
