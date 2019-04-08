package ai.mantik.planner.impl

import ai.mantik.planner._
import ai.mantik.planner.plugins.Plugins
import ai.mantik.repository.FileRepository.FileGetResult
import ai.mantik.repository._

import scala.concurrent.{ ExecutionContext, Future }

private[impl] class PlannerImpl(isolationSpace: String, fileRepository: FileRepository, formats: Plugins)(implicit ec: ExecutionContext) extends Planner {

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
  def compress(plan: Plan): Plan = {
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

  def convertSingleAction[T](action: Action[T])(implicit nodeIdGenerator: NodeIdGenerator): Future[Plan] = {
    action match {
      case s: Action.SaveAction =>
        for {
          (preplan, fileStorage) <- translateItemPayloadSourceAsFile(s.item.source, canBeTemporary = false)
        } yield {
          val artefact = MantikArtefact(
            s.item.mantikfile,
            Some(fileStorage.fileId),
            s.id
          )
          Plan.seq(
            preplan,
            Plan.AddMantikItem(artefact)
          )
        }
      case p: Action.FetchAction =>
        for {
          (preplan, fileStorage) <- translateItemPayloadSourceAsFile(p.dataSet.source, canBeTemporary = true)
        } yield {
          Plan.seq(
            preplan,
            Plan.PullBundle(p.dataSet.dataType, fileStorage.fileId)
          )
        }
    }
  }

  /**
   * Generate the node, which provides a the data payload of a mantik item.
   * Note: due to a flaw this will most likely lead to a splitted plan
   * as we can only load files directly into new nodes.
   */
  def translateItemPayloadSource(item: MantikItem)(implicit nodeIdGenerator: NodeIdGenerator): Future[ResourcePlan] = {
    item.source match {
      case Source.Empty =>
        // No Support yet.
        throw new Planner.NotAvailableException("Empty Source")
      case loaded: Source.Loaded =>
        fileRepository.requestFileGet(loaded.fileId).map { fileGet =>
          elements.loadFileNode(fileGet)
        }
      case l: Source.Literal =>
        // Ugly this leads to splitted plan.
        for {
          storage <- fileRepository.requestFileStorage(true)
          fileGet <- fileRepository.requestFileGet(storage.fileId, optimistic = true)
        } yield {
          val pushing = elements.literalToPushBundle(l, storage.fileId)
          elements.loadFileNode(fileGet)
            .prependPlan(
              pushing
            )
        }
      case a: Source.OperationResult =>
        translateOperationResult(a.op).map { operationResult =>
          operationResult.projectOutput(a.projection)
        }
    }
  }

  /**
   * Generates a plan, so that a item payload is available as File.
   * This is necessary if some item is initialized by a file (e.g. algorithms).
   * @param canBeTemporary if true, the result may also be a temporary file.
   */
  def translateItemPayloadSourceAsFile(source: Source, canBeTemporary: Boolean)(implicit nodeIdGenerator: NodeIdGenerator): Future[(Plan, FileGetResult)] = {
    source match {
      case Source.Empty =>
        // use translateItemPayloadSourceAsOptionalFile if you want to support empty files
        throw new Planner.NotAvailableException("Empty source")
      case Source.Loaded(fileId) =>
        // already available as file
        fileRepository.requestFileGet(fileId).map { fileGet =>
          (Plan.Empty, fileGet)
        }
      case l: Source.Literal =>
        // We have to go via temporary file
        for {
          storage <- fileRepository.requestFileStorage(temporary = canBeTemporary)
          fileGet <- fileRepository.requestFileGet(storage.fileId, optimistic = true)
        } yield {
          val pushing = elements.literalToPushBundle(l, storage.fileId)
          (pushing, fileGet)
        }
      case a: Source.OperationResult =>
        // Execute the plan and write the result into a file
        // Which can then be loaded...
        for {
          operationResult <- translateOperationResult(a.op)
          fileStoreRequest <- fileRepository.requestFileStorage(canBeTemporary)
          fileGetRequest <- fileRepository.requestFileGet(fileStoreRequest.fileId, optimistic = true)
        } yield {
          val fileNode = elements.createStoreFileNode(fileStoreRequest)
          val runner = fileNode.application(operationResult.projectOutput(a.projection))
          elements.sourcePlanToJob(runner) -> fileGetRequest
        }
    }
  }

  /** Like [[translateItemPayloadSourceAsFile]] but can also return no file, if we use a Mantikfile without data. */
  def translateItemPayloadSourceAsOptionalFile(source: Source, canBeTemporary: Boolean)(implicit nodeIdGenerator: NodeIdGenerator): Future[(Plan, Option[FileGetResult])] = {
    source match {
      case Source.Empty =>
        Future.successful(Plan.Empty -> None)
      case other =>
        translateItemPayloadSourceAsFile(other, canBeTemporary).map {
          case (preplan, file) =>
            preplan -> Some(file)
        }
    }
  }

  /** Generates the Graph which represents operation results. */
  def translateOperationResult(op: Operation)(implicit nodeIdGenerator: NodeIdGenerator): Future[ResourcePlan] = {
    op match {
      case Operation.Application(algorithm, argument) =>
        for {
          argumentSource <- manifestDataSet(argument)
          algorithmSource <- manifestAlgorithm(algorithm)
        } yield {
          algorithmSource.application(argumentSource)
        }
      case Operation.Training(trainable, learningData) =>
        for {
          argumentSource <- manifestDataSet(learningData)
          algorithmSource <- manifestTrainableAlgorithm(trainable)
        } yield {
          algorithmSource.application(argumentSource)
        }
    }
  }

  /** Manifests an algorithm as a graph, will have one input and one output. */
  def manifestAlgorithm(algorithm: Algorithm)(implicit nodeIdGenerator: NodeIdGenerator): Future[ResourcePlan] = {
    translateItemPayloadSourceAsOptionalFile(algorithm.source, true).map {
      case (preplan, algorithmFile) =>
        elements.algorithm(algorithm.mantikfile, algorithmFile)
          .prependPlan(preplan)
    }
  }

  /** Manifest a trainable algorithm as a graph, will have one input and two outputs. */
  def manifestTrainableAlgorithm(trainableAlgorithm: TrainableAlgorithm)(implicit nodeIdGenerator: NodeIdGenerator): Future[ResourcePlan] = {
    translateItemPayloadSourceAsOptionalFile(trainableAlgorithm.source, true).map {
      case (preplan, algorithmFile) =>
        elements.trainableAlgorithm(trainableAlgorithm.mantikfile, algorithmFile)
          .prependPlan(preplan)
    }
  }

  /** Manifest a data set as a graph with one output. */
  def manifestDataSet(dataSet: DataSet)(implicit nodeIdGenerator: NodeIdGenerator): Future[ResourcePlan] = {
    translateItemPayloadSourceAsOptionalFile(dataSet.source, true).map {
      case (preplan, dataSetFile) =>
        elements.dataSet(dataSet.mantikfile, dataSetFile)
          .prependPlan(preplan)
    }
  }
}
