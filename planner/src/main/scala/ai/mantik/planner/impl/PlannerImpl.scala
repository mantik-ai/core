package ai.mantik.planner.impl

import ai.mantik.elements.{ MantikId, PipelineStep }
import ai.mantik.planner.Planner.InconsistencyException
import ai.mantik.planner._
import ai.mantik.planner.bridge.Bridges
import ai.mantik.planner.pipelines.ResolvedPipelineStep
import ai.mantik.planner.repository.ContentTypes
import cats.data.State
import cats.implicits._

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
        storeDependentItems(s.item).flatMap { dependentStoreAction =>
          storeSingleItem(s.item, s.id).map { filesPlan =>
            PlanOp.seq(dependentStoreAction, filesPlan.preOp)
          }
        }
      case p: Action.FetchAction =>
        manifestDataSetAsFile(p.dataSet, canBeTemporary = true).map { filesPlan =>
          val fileRef = filesPlan.fileRefs.head
          PlanOp.seq(
            filesPlan.preOp,
            PlanOp.PullBundle(p.dataSet.dataType, fileRef)
          )
        }
      case d: Action.Deploy =>
        d.item match {
          case a: Algorithm =>
            deployAlgorithm(a, d.name)
          case other =>
            throw new InconsistencyException(s"Can only deploy algorithms, got ${other.getClass.getSimpleName}")
        }
    }
  }

  /** Store an item and also returns the file referencing to it. */
  def storeSingleItem(item: MantikItem, id: MantikId): State[PlanningState, FilesPlan] = {
    translateItemPayloadSourceAsFiles(item.payloadSource, canBeTemporary = false).flatMap { filesPlan =>
      val file = filesPlan.files.headOption
      val fileRef = file.map(_.ref)
      val itemStored = PlanningState.ItemStored(payload = file)
      PlanningState.apply { state =>
        val updatedState = state.withItemStored(item.itemId, itemStored)
        val combinedOps = PlanOp.seq(
          filesPlan.preOp,
          PlanOp.AddMantikItem(id, item, fileRef)
        )
        updatedState -> FilesPlan(
          combinedOps,
          file.toIndexedSeq
        )
      }
    }
  }

  private def storeDependentItems(item: MantikItem): State[PlanningState, PlanOp] = {
    dependentItems(item).map {
      case (mantikId, item) =>
        if (item.state.get.isStored && item.mantikId == mantikId) {
          // nothing to save
          // already loaded under this name
          PlanningState.pure(PlanOp.Empty: PlanOp)
        } else {
          convertSingleAction(Action.SaveAction(item, mantikId))
        }
    }.sequence.map { changes =>
      PlanOp.seq(changes: _*)
    }
  }

  /** Returns dependent referenced items. */
  private def dependentItems(item: MantikItem): List[(MantikId, MantikItem)] = {
    item match {
      case p: Pipeline =>
        // do not collect select steps, they are stored as plain SQL elements.
        p.resolved.steps.collect {
          case ResolvedPipelineStep(as: PipelineStep.AlgorithmStep, algorithm) =>
            as.algorithm -> algorithm
        }
      case _ => Nil
    }
  }

  private def deployAlgorithm(algorithm: Algorithm, nameHint: Option[String]): State[PlanningState, PlanOp] = {
    // already deployed?
    algorithm.state.get.deployment match {
      case Some(existing) => return PlanningState.pure(PlanOp.Const(existing))
      case None           => // continue
    }

    // Item must be present inside the database
    // Then it can be deployed directly.

    ensureItemStored(algorithm).map { filePlan =>
      val file = filePlan.files.headOption
      val container = elements.algorithmContainer(algorithm.mantikfile, file.map(_.ref))
      val serviceId = algorithm.itemId.toString
      val op = PlanOp.DeployAlgorithm(
        container,
        serviceId,
        nameHint,
        algorithm
      )
      PlanOp.seq(
        filePlan.preOp,
        op
      )
    }
  }

  /** Ensure that the item is stored inside the database. */
  def ensureItemStored(item: MantikItem): State[PlanningState, FilesPlan] = {
    PlanningState(_.itemStorage(item)).flatMap {
      case None =>
        // not yet stored
        storeSingleItem(item, item.mantikId)
      case Some(stored) =>
        // already stored
        PlanningState.pure {
          FilesPlan(
            PlanOp.Empty,
            stored.payload.toIndexedSeq
          )
        }
    }
  }

  /**
   * Generate the node, which provides a the data payload of a mantik item.
   * Note: due to a flaw this will most likely lead to a fragmented PlanOp
   * as we can only load files directly into new nodes.
   */
  def translateItemPayloadSource(source: PayloadSource): State[PlanningState, ResourcePlan] = {
    source match {
      case PayloadSource.Empty =>
        // No Support yet.
        throw new Planner.NotAvailableException("Empty Source")
      case loaded: PayloadSource.Loaded =>
        PlanningState(_.readFile(loaded.fileId)).flatMap { file =>
          elements.loadFileNode(PlanFileWithContentType(file.ref, loaded.contentType))
        }
      case l: PayloadSource.Literal =>
        // Ugly this leads to fragmented plan.
        for {
          fileReference <- PlanningState(_.pipeFile(temporary = true))
          loader <- elements.loadFileNode(PlanFileWithContentType(fileReference.ref, ContentTypes.MantikBundleContentType))
        } yield {
          val pusher = elements.literalToPushBundle(l, fileReference)
          loader.prependOp(pusher)
        }
      case a: PayloadSource.OperationResult =>
        translateOperationResult(a.op)
      case p: PayloadSource.Projection =>
        translateItemPayloadSource(p.source).map(_.projectOutput(p.projection))
      case c: PayloadSource.Cached =>
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
  def translateItemPayloadSourceAsFiles(source: PayloadSource, canBeTemporary: Boolean): State[PlanningState, FilesPlan] = {
    source match {
      case PayloadSource.Empty =>
        PlanningState { s =>
          s -> FilesPlan()
        }
      case PayloadSource.Loaded(fileId, contentType) =>
        // already available as file
        PlanningState(_.readFile(fileId)).map { fileGet =>
          FilesPlan(files = IndexedSeq(PlanFileWithContentType(fileGet.ref, contentType)))
        }
      case l: PayloadSource.Literal =>
        // We have to go via temporary file
        PlanningState(_.pipeFile(temporary = canBeTemporary)).map { reference =>
          val pushing = elements.literalToPushBundle(l, reference)
          FilesPlan(pushing, IndexedSeq(PlanFileWithContentType(reference.ref, ContentTypes.MantikBundleContentType)))
        }
      case c: PayloadSource.Cached =>
        cachedSourceFiles(c, canBeTemporary)
      case other =>
        translateItemPayloadSource(other).flatMap { operationResult =>
          resourcePlanToFiles(operationResult, canBeTemporary)
        }
    }
  }

  private def cachedSourceFiles(cachedSource: PayloadSource.Cached, canBeTemporary: Boolean): State[PlanningState, FilesPlan] = {
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
          algorithmSource <- manifestApplicable(algorithm)
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

  /** Manifest something applicable, will have one input and one output. */
  def manifestApplicable(applicableMantikItem: ApplicableMantikItem): State[PlanningState, ResourcePlan] = {
    applicableMantikItem match {
      case a: Algorithm => manifestAlgorithm(a)
      case p: Pipeline  => manifestPipeline(p)
      case other =>
        throw new InconsistencyException(s"Unknown applicable type ${other.getClass}")
    }
  }

  /** Manifests an algorithm as a graph, will have one input and one output. */
  def manifestAlgorithm(algorithm: Algorithm): State[PlanningState, ResourcePlan] = {
    translateItemPayloadSourceAsFiles(algorithm.payloadSource, canBeTemporary = true).flatMap { files =>
      val algorithmFile = files.fileRefs.headOption
      elements.algorithm(algorithm.mantikfile, algorithmFile)
        .map(_.prependOp(files.preOp))
    }
  }

  /** Manifest a pipeline as a graph, will have on input and one output. */
  def manifestPipeline(pipeline: Pipeline): State[PlanningState, ResourcePlan] = {
    val steps = pipeline.resolved.steps
    require(steps.nonEmpty, "Pipelines may not be empty")
    steps.map { step =>
      manifestAlgorithm(step.algorithm)
    }.sequence.map { plans: List[ResourcePlan] =>
      val resultPlan = plans.reduce[ResourcePlan] { (c, n) =>
        n.application(c)
      }
      resultPlan
    }
  }

  /** Manifest a trainable algorithm as a graph, will have one input and two outputs. */
  def manifestTrainableAlgorithm(trainableAlgorithm: TrainableAlgorithm): State[PlanningState, ResourcePlan] = {
    translateItemPayloadSourceAsFiles(trainableAlgorithm.payloadSource, canBeTemporary = true).flatMap { files =>
      val algorithmFile = files.fileRefs.headOption
      elements.trainableAlgorithm(trainableAlgorithm.mantikfile, algorithmFile)
        .map(_.prependOp(files.preOp))
    }
  }

  /** Manifest a data set as a graph with one output. */
  def manifestDataSet(dataSet: DataSet): State[PlanningState, ResourcePlan] = {
    if (dataSet.mantikfile.definition.format == DataSet.NaturalFormatName) {
      translateItemPayloadSource(dataSet.payloadSource)
    } else {
      translateItemPayloadSourceAsFiles(dataSet.payloadSource, canBeTemporary = true).flatMap { files =>
        val dataSetFile = files.fileRefs.headOption
        elements.dataSet(dataSet.mantikfile, dataSetFile).map(_.prependOp(files.preOp))
      }
    }
  }

  /** Manifest a data set as (natural encoded) file. */
  def manifestDataSetAsFile(dataSet: DataSet, canBeTemporary: Boolean): State[PlanningState, FilesPlan] = {
    if (dataSet.mantikfile.definition.format == DataSet.NaturalFormatName) {
      // We can directly use it's file
      translateItemPayloadSourceAsFiles(dataSet.payloadSource, canBeTemporary)
    } else {
      manifestDataSet(dataSet).flatMap { resourcePlan =>
        resourcePlanToFiles(resourcePlan, canBeTemporary)
      }
    }
  }
}
