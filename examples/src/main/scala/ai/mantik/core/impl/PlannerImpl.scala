package ai.mantik.core.impl

import ai.mantik.core._
import ai.mantik.core.impl.PlannerElements.SourcePlan
import ai.mantik.core.impl.PlannerImpl.NodeIdGenerator
import ai.mantik.core.plugins.Plugins
import ai.mantik.executor.model._
import ai.mantik.repository.FileRepository.FileGetResult
import ai.mantik.repository._

import scala.concurrent.{ ExecutionContext, Future }

class PlannerImpl(isolationSpace: String, fileRepository: FileRepository, formats: Plugins)(implicit ec: ExecutionContext) extends Planner {

  // We are using MsgPack serialized Mantik Bundles.
  val ContentType = "application/x-mantik-bundle"
  val elements = new PlannerElements(formats, isolationSpace = isolationSpace, contentType = ContentType)

  def convert[T](action: Action[T]): Future[Plan] = {
    implicit val idGenerator = new NodeIdGenerator()
    convertSingleAction(action).map { plan =>
      compress(plan)
    }
  }

  /** Compress a plan by removing Sequantials of sequentials. */
  private def compress(plan: Plan): Plan = {
    def subCompress(plan: Plan): Seq[Plan] = {
      plan match {
        case Plan.Empty => Nil
        case Plan.Sequential(elements) =>
          elements.flatMap(subCompress)
        case other => Seq(other)
      }
    }
    subCompress(plan) match {
      case s if s.isEmpty => Plan.Empty
      case Seq(single)    => single
      case multiple       => Plan.Sequential(multiple)
    }
  }

  private def convertSingleAction[T](action: Action[T])(implicit nodeIdGenerator: NodeIdGenerator): Future[Plan] = {
    action match {
      case s: Action.SaveAction =>
        val fileStorageFuture = fileRepository.requestFileStorage(temporary = false)
        val dataSourceFuture = convertDataSource(s.item)
        for {
          storage <- fileStorageFuture
          sourcePlan <- dataSourceFuture
        } yield {

          elements.saveAction(
            s.item,
            s.location,
            storage,
            sourcePlan
          )

        }
      case p: Action.FetchAction =>
        val fileStorageFuture = fileRepository.requestFileStorage(temporary = true)
        val dataSourceFuture = convertDataSource(p.dataSet)
        for {
          storage <- fileStorageFuture
          sourcePlan <- dataSourceFuture
        } yield {

          elements.fetchAction(
            p.dataSet, sourcePlan, storage
          )

        }
      case other =>
        // TODO
        ???
    }
  }

  /** Converts an item into a graph resource, may generate a pre-plan. */
  private[impl] def convertDataSource(item: MantikItem)(implicit nodeIdGenerator: NodeIdGenerator): Future[SourcePlan] = {
    // Here we need the plugin system in place...
    item match {
      case ds: DataSet =>
        convertDataSetSource(ds)
      case tf: Transformation =>
        convertTransformationSource(tf)
      case other =>
        // TODO
        ???
    }
  }

  private def convertDataSetSource(ds: DataSet)(implicit nodeIdGenerator: NodeIdGenerator): Future[SourcePlan] = {
    ds.source match {
      case l: Source.Loaded =>
        val fileFuture: Future[Option[FileGetResult]] = l.artefact.fileId match {
          case Some(fileId) => fileRepository.requestFileGet(fileId).map(Some(_))
          case None         => Future.successful(None)
        }

        fileFuture.map { file =>
          elements.loadedDataSet(l.artefact, file)
        }
      case l: Source.Literal =>
        fileRepository.requestFileStorage(temporary = true).map { storage =>
          elements.literalDataSet(l.bundle, storage)
        }
      case applied: Source.AppliedTransformation =>
        val sourceFuture = convertDataSetSource(applied.dataSet)
        val transformationSourceFuture = convertTransformationSource(applied.transformation)
        for {
          sourcePlan <- sourceFuture
          algorithmPlan <- transformationSourceFuture
        } yield {
          elements.appliedTransformation(sourcePlan, algorithmPlan)
        }
      case other =>
        // TODO
        ???
    }
  }

  private def convertTransformationSource(tf: Transformation)(implicit nodeIdGenerator: NodeIdGenerator): Future[SourcePlan] = {
    tf.source match {
      case l: Source.Loaded =>
        val fileFuture = l.artefact.fileId.map { fileId =>
          fileRepository.requestFileGet(fileId).map(Some(_))
        }.getOrElse(Future.successful(None))

        fileFuture.map { file =>
          elements.loadedAlgorithm(l.artefact, file)
        }
      case other =>
        // TODO
        ???
    }
  }
}

object PlannerImpl {

  /** Simple generator for generating Ids in a Graph. */
  class NodeIdGenerator {
    object lock
    private var nextId = 1
    def makeId(): String = {
      val id = lock.synchronized {
        val id = nextId
        nextId += 1
        id
      }
      id.toString
    }
  }
}