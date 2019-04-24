package ai.mantik.planner.impl

import ai.mantik.planner._
import ai.mantik.planner.bridge.Bridges
import cats.data.State

/**
 * Implementation of [[Planner]].
 *
 * Note: the implementation is using the [[State]] Monad for saving
 * the list of opened files (state class is [[PlanningState]].
 *
 * This way it's pure functional and easier to test.
 */
private[impl] class PlannerImpl(bridges: Bridges) extends Planner {

  val elements = new PlannerElements(bridges)

  override def convert[T](action: Action[T]): Plan = {
    val (planningState, planOp) = convertSingleAction(action).run(PlanningState()).value
    val compressed = PlanOp.compress(planOp)
    Plan(compressed, planningState.files)
  }

  def convertSingleAction[T](action: Action[T]): State[PlanningState, PlanOp] = {
    action match {
      case s: Action.SaveAction =>
        translateItemPayloadSourceAsOptionalFile(s.item.source, canBeTemporary = false).map {
          case (preplan, fileStorage) =>
            PlanOp.seq(
              preplan,
              PlanOp.AddMantikItem(s.id, fileStorage, s.item.mantikfile)
            )
        }
      case p: Action.FetchAction =>
        manifestDataSetAsFile(p.dataSet, canBeTemporary = true).map {
          case (preplan, fileId) =>
            PlanOp.seq(
              preplan,
              PlanOp.PullBundle(p.dataSet.dataType, fileId)
            )
        }
    }
  }

  /**
   * Generate the node, which provides a the data payload of a mantik item.
   * Note: due to a flaw this will most likely lead to a fragmented PlanOp
   * as we can only load files directly into new nodes.
   */
  def translateItemPayloadSource(source: Source, expectedContentType: Option[String] = None): State[PlanningState, ResourcePlan] = {
    source match {
      case Source.Empty =>
        // No Support yet.
        throw new Planner.NotAvailableException("Empty Source")
      case loaded: Source.Loaded =>
        PlanningState(_.readFile(loaded.fileId)).flatMap { file =>
          elements.loadFileNode(file.id, expectedContentType)
        }
      case l: Source.Literal =>
        // Ugly this leads to fragmented plan.
        for {
          fileReference <- PlanningState(_.pipeFile(temporary = true))
          loader <- elements.loadFileNode(fileReference.id, expectedContentType)
        } yield {
          val pusher = elements.literalToPushBundle(l, fileReference)
          loader.prependOp(pusher)
        }
      case a: Source.OperationResult =>
        translateOperationResult(a.op).map(_.projectOutput(a.projection))
    }
  }

  /**
   * Generates a plan, so that a item payload is available as File.
   * This is necessary if some item is initialized by a file (e.g. algorithms).
   * @param canBeTemporary if true, the result may also be a temporary file.
   */
  def translateItemPayloadSourceAsFile(source: Source, canBeTemporary: Boolean): State[PlanningState, (PlanOp, PlanFileReference)] = {
    source match {
      case Source.Loaded(fileId) =>
        // already available as file
        PlanningState(_.readFile(fileId)).map { fileGet =>
          (PlanOp.Empty, fileGet.id)
        }
      case l: Source.Literal =>
        // We have to go via temporary file
        PlanningState(_.pipeFile(temporary = canBeTemporary)).map { reference =>
          val pushing = elements.literalToPushBundle(l, reference)
          (pushing, reference.id)
        }
      case other =>
        translateItemPayloadSource(other).flatMap { operationResult =>
          resourcePlanToFile(operationResult, canBeTemporary)
        }
    }
  }

  /** Converts a resource plan into a possible temporary file. Note: this leads to a splitted plan, usually. */
  private def resourcePlanToFile(resourcePlan: ResourcePlan, canBeTemporary: Boolean): State[PlanningState, (PlanOp, PlanFileReference)] = {
    // Execute the plan and write the result into a file
    // Which can then be loaded...
    for {
      file <- PlanningState(_.pipeFile(temporary = canBeTemporary))
      fileNode <- elements.createStoreFileNode(file, resourcePlan.outputResource(0).contentType)
    } yield {
      val runner = fileNode.application(resourcePlan)
      elements.sourcePlanToJob(runner) -> file.id
    }
  }

  /** Like [[translateItemPayloadSourceAsFile]] but can also return no file, if we use a Mantikfile without data. */
  def translateItemPayloadSourceAsOptionalFile(source: Source, canBeTemporary: Boolean): State[PlanningState, (PlanOp, Option[PlanFileReference])] = {
    source match {
      case Source.Empty =>
        State.pure(PlanOp.Empty -> None)
      case other =>
        translateItemPayloadSourceAsFile(other, canBeTemporary).map {
          case (preplan, file) =>
            preplan -> Some(file)
        }
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
    translateItemPayloadSourceAsOptionalFile(algorithm.source, canBeTemporary = true).flatMap {
      case (preplan, algorithmFile) =>
        elements.algorithm(algorithm.mantikfile, algorithmFile)
          .map(_.prependOp(preplan))
    }
  }

  /** Manifest a trainable algorithm as a graph, will have one input and two outputs. */
  def manifestTrainableAlgorithm(trainableAlgorithm: TrainableAlgorithm): State[PlanningState, ResourcePlan] = {
    translateItemPayloadSourceAsOptionalFile(trainableAlgorithm.source, canBeTemporary = true).flatMap {
      case (preplan, algorithmFile) =>
        elements.trainableAlgorithm(trainableAlgorithm.mantikfile, algorithmFile).map(_.prependOp(preplan))
    }
  }

  /** Manifest a data set as a graph with one output. */
  def manifestDataSet(dataSet: DataSet): State[PlanningState, ResourcePlan] = {
    translateItemPayloadSourceAsOptionalFile(dataSet.source, canBeTemporary = true).flatMap {
      case (preplan, dataSetFile) =>
        elements.dataSet(dataSet.mantikfile, dataSetFile).map(_.prependOp(preplan))
    }
  }

  /** Manifest a data set as (natural encoded) file. */
  def manifestDataSetAsFile(dataSet: DataSet, canBeTemporary: Boolean): State[PlanningState, (PlanOp, PlanFileReference)] = {
    if (dataSet.mantikfile.definition.format == DataSet.NaturalFormatName) {
      // We can directly use it's file
      translateItemPayloadSourceAsFile(dataSet.source, canBeTemporary)
    } else {
      manifestDataSet(dataSet).flatMap { resourcePlan =>
        resourcePlanToFile(resourcePlan, canBeTemporary)
      }
    }
  }
}
