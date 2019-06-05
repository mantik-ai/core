package ai.mantik.planner.impl

import ai.mantik.planner._
import ai.mantik.planner.bridge.Bridges
import ai.mantik.repository.ContentTypes
import cats.data.State

/**
 * Implementation of [[Planner]].
 *
 * Note: the implementation is using the [[State]] Monad for saving
 * the list of opened files (state class is [[PlanningState]].
 *
 * This way it's pure functional and easier to test.
 */
private[planner] class PlannerImpl(bridges: Bridges) extends Planner {

  val elements = new PlannerElements(bridges)

  override def convert[T](action: Action[T]): Plan[T] = {
    val (planningState, planOp) = convertSingleAction(action).run(PlanningState()).value
    val compressed = PlanOp.compress(planOp)
    Plan(compressed, planningState.files, planningState.cacheGroups)
  }

  def convertSingleAction[T](action: Action[T]): State[PlanningState, PlanOp] = {
    action match {
      case s: Action.SaveAction =>
        translateItemPayloadSourceAsFiles(s.item.source, canBeTemporary = false).map { filesPlan =>
          val fileRef = filesPlan.fileRefs.headOption
          PlanOp.seq(
            filesPlan.preOp,
            PlanOp.AddMantikItem(s.id, fileRef, s.item.mantikfile)
          )
        }
      case p: Action.FetchAction =>
        manifestDataSetAsFile(p.dataSet, canBeTemporary = true).map { filesPlan =>
          val fileRef = filesPlan.fileRefs.head
          PlanOp.seq(
            filesPlan.preOp,
            PlanOp.PullBundle(p.dataSet.dataType, fileRef)
          )
        }
    }
  }

  /**
   * Generate the node, which provides a the data payload of a mantik item.
   * Note: due to a flaw this will most likely lead to a fragmented PlanOp
   * as we can only load files directly into new nodes.
   */
  def translateItemPayloadSource(source: Source): State[PlanningState, ResourcePlan] = {
    source match {
      case Source.Empty =>
        // No Support yet.
        throw new Planner.NotAvailableException("Empty Source")
      case loaded: Source.Loaded =>
        PlanningState(_.readFile(loaded.fileId)).flatMap { file =>
          elements.loadFileNode(PlanFileWithContentType(file.ref, loaded.contentType))
        }
      case l: Source.Literal =>
        // Ugly this leads to fragmented plan.
        for {
          fileReference <- PlanningState(_.pipeFile(temporary = true))
          loader <- elements.loadFileNode(PlanFileWithContentType(fileReference.ref, ContentTypes.MantikBundleContentType))
        } yield {
          val pusher = elements.literalToPushBundle(l, fileReference)
          loader.prependOp(pusher)
        }
      case a: Source.OperationResult =>
        translateOperationResult(a.op)
      case p: Source.Projection =>
        translateItemPayloadSource(p.source).map(_.projectOutput(p.projection))
      case c: Source.Cached =>
        for {
          filesPlan <- cachedSourceFiles(c, canBeTemporary = true)
          loader <- filesPlanToResourcePlan(filesPlan)
        } yield loader
    }
  }

  /**
   * Generates a plan, so that a item payload is available as File.
   * This is necessary if some item is initialized by a file (e.g. algorithms).
   * @param canBeTemporary if true, the result may also be a temporary file.
   */
  def translateItemPayloadSourceAsFiles(source: Source, canBeTemporary: Boolean): State[PlanningState, FilesPlan] = {
    source match {
      case Source.Empty =>
        PlanningState { s =>
          s -> FilesPlan()
        }
      case Source.Loaded(fileId, contentType) =>
        // already available as file
        PlanningState(_.readFile(fileId)).map { fileGet =>
          FilesPlan(files = IndexedSeq(PlanFileWithContentType(fileGet.ref, contentType)))
        }
      case l: Source.Literal =>
        // We have to go via temporary file
        PlanningState(_.pipeFile(temporary = canBeTemporary)).map { reference =>
          val pushing = elements.literalToPushBundle(l, reference)
          FilesPlan(pushing, IndexedSeq(PlanFileWithContentType(reference.ref, ContentTypes.MantikBundleContentType)))
        }
      case c: Source.Cached =>
        cachedSourceFiles(c, canBeTemporary)
      case other =>
        translateItemPayloadSource(other).flatMap { operationResult =>
          resourcePlanToFiles(operationResult, canBeTemporary)
        }
    }
  }

  private def cachedSourceFiles(cachedSource: Source.Cached, canBeTemporary: Boolean): State[PlanningState, FilesPlan] = {
    for {
      opFiles <- translateItemPayloadSourceAsFiles(cachedSource.source, canBeTemporary)
      _ <- markFileAsCachedFile(opFiles.fileRefs, cachedSource.cacheGroup)
    } yield {
      val cacheFiles = cachedSource.cacheGroup.zip(opFiles.fileRefs)
      FilesPlan(
        PlanOp.CacheOp(
          cacheFiles, opFiles.preOp
        ), opFiles.files
      )
    }
  }

  private def markFileAsCachedFile(files: IndexedSeq[PlanFileReference], cacheKeys: CacheKeyGroup): State[PlanningState, Unit] = {
    val toCached = files.zip(cacheKeys).toMap
    PlanningState { state =>
      val updatedState = state
        .markCached(toCached)
        .withCacheGroup(cacheKeys)
      updatedState -> (())
    }
  }

  /**
   * Converts a [[ResourcePlan]] into a [[FilesPlan]].
   * Note: this leads to a splitted plan, usually.
   */
  private def resourcePlanToFiles(resourcePlan: ResourcePlan, canBeTemporary: Boolean): State[PlanningState, FilesPlan] = {
    import cats.implicits._

    resourcePlan.outputs.toList.map { output =>
      val outputResource = resourcePlan.outputResource(output)
      for {
        file <- PlanningState(_.pipeFile(temporary = canBeTemporary))
        fileNode <- elements.createStoreFileNode(file, outputResource.contentType)
      } yield {
        fileNode -> PlanFileWithContentType(file.ref, outputResource.contentType.getOrElse {
          ContentTypes.MantikBundleContentType // Does this happen?
        })
      }
    }.sequence.map { fileNodeWithFile: List[(ResourcePlan, PlanFileWithContentType)] =>
      val consumers = fileNodeWithFile.foldLeft(ResourcePlan()) {
        case (p, (storeFile, _)) =>
          p.merge(storeFile)
      }
      val combinedResourcePlan = consumers.application(resourcePlan)
      val preOp = elements.sourcePlanToJob(combinedResourcePlan)
      val filesPlan = FilesPlan(
        preOp,
        files = fileNodeWithFile.map(_._2).toIndexedSeq
      )
      filesPlan
    }
  }

  /** Convert a [[FilesPlan]] to a [[ResourcePlan]]. */
  private def filesPlanToResourcePlan(filesPlan: FilesPlan): State[PlanningState, ResourcePlan] = {
    import cats.implicits._
    filesPlan.files.toList.map { file =>
      elements.loadFileNode(file)
    }.sequence.map { fileLoaders =>
      val fullPlan = fileLoaders.foldLeft(ResourcePlan())(_.merge(_))
        .prependOp(filesPlan.preOp)
      fullPlan
    }
  }

  /** Generates the Graph which represents operation results. */
  def translateOperationResult(op: Operation): State[PlanningState, ResourcePlan] = {
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
  def manifestAlgorithm(algorithm: Algorithm): State[PlanningState, ResourcePlan] = {
    translateItemPayloadSourceAsFiles(algorithm.source, canBeTemporary = true).flatMap { files =>
      val algorithmFile = files.fileRefs.headOption
      elements.algorithm(algorithm.mantikfile, algorithmFile)
        .map(_.prependOp(files.preOp))
    }
  }

  /** Manifest a trainable algorithm as a graph, will have one input and two outputs. */
  def manifestTrainableAlgorithm(trainableAlgorithm: TrainableAlgorithm): State[PlanningState, ResourcePlan] = {
    translateItemPayloadSourceAsFiles(trainableAlgorithm.source, canBeTemporary = true).flatMap { files =>
      val algorithmFile = files.fileRefs.headOption
      elements.trainableAlgorithm(trainableAlgorithm.mantikfile, algorithmFile)
        .map(_.prependOp(files.preOp))
    }
  }

  /** Manifest a data set as a graph with one output. */
  def manifestDataSet(dataSet: DataSet): State[PlanningState, ResourcePlan] = {
    if (dataSet.mantikfile.definition.format == DataSet.NaturalFormatName) {
      translateItemPayloadSource(dataSet.source)
    } else {
      translateItemPayloadSourceAsFiles(dataSet.source, canBeTemporary = true).flatMap { files =>
        val dataSetFile = files.fileRefs.headOption
        elements.dataSet(dataSet.mantikfile, dataSetFile).map(_.prependOp(files.preOp))
      }
    }
  }

  /** Manifest a data set as (natural encoded) file. */
  def manifestDataSetAsFile(dataSet: DataSet, canBeTemporary: Boolean): State[PlanningState, FilesPlan] = {
    if (dataSet.mantikfile.definition.format == DataSet.NaturalFormatName) {
      // We can directly use it's file
      translateItemPayloadSourceAsFiles(dataSet.source, canBeTemporary)
    } else {
      manifestDataSet(dataSet).flatMap { resourcePlan =>
        resourcePlanToFiles(resourcePlan, canBeTemporary)
      }
    }
  }
}
