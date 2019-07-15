package ai.mantik.planner.impl

import ai.mantik.elements.{ MantikId, PipelineStep }
import ai.mantik.planner.Planner.InconsistencyException
import ai.mantik.planner._
import ai.mantik.planner.bridge.Bridges
import ai.mantik.planner.pipelines.ResolvedPipelineStep
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
  val resourcePlanBuilder = new ResourcePlanBuilder(elements)

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
        resourcePlanBuilder.manifestDataSetAsFile(p.dataSet, canBeTemporary = true).map { filesPlan =>
          val fileRef = filesPlan.fileRefs.head
          PlanOp.seq(
            filesPlan.preOp,
            PlanOp.PullBundle(p.dataSet.dataType, fileRef)
          )
        }
      case d: Action.Deploy =>
        d.item match {
          case a: Algorithm =>
            deployAlgorithm(a, d.nameHint)
          case p: Pipeline =>
            deployPipeline(p, d.nameHint, d.ingressName)
          case other =>
            throw new InconsistencyException(s"Can only deploy algorithms, got ${other.getClass.getSimpleName}")
        }
    }
  }

  /** Store an item and also returns the file referencing to it. */
  def storeSingleItem(item: MantikItem, id: MantikId): State[PlanningState, FilesPlan] = {
    resourcePlanBuilder.translateItemPayloadSourceAsFiles(item.payloadSource, canBeTemporary = false).flatMap { filesPlan =>
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

    ensureItemStored(algorithm).flatMap { filePlan =>
      val file = filePlan.files.headOption
      val container = elements.algorithmContainer(algorithm.mantikfile, file.map(_.ref))
      val serviceId = algorithm.itemId.toString
      val op = PlanOp.DeployAlgorithm(
        container,
        serviceId,
        nameHint,
        algorithm
      )
      val combined = PlanOp.seq(
        filePlan.preOp,
        op
      )
      PlanningState { state =>
        val updatedState = state.withItemDeployed(algorithm.itemId, PlanningState.ItemDeployed())
        updatedState -> combined
      }
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

  /** Deploy a pipeline. */
  def deployPipeline(pipeline: Pipeline, nameHint: Option[String], ingress: Option[String]): State[PlanningState, PlanOp] = {
    // already deployed?
    pipeline.state.get.deployment match {
      case Some(existing) => return PlanningState.pure(PlanOp.Const(existing))
      case None           => // continue
    }

    for {
      stepDeployment <- ensureStepsDeployed(pipeline)
      pipelineStorage <- ensureItemStored(pipeline)
      pipeDeployment <- buildPipelineDeployment(pipeline, nameHint, ingress)
    } yield {
      PlanOp.seq(
        stepDeployment,
        pipelineStorage.preOp,
        pipeDeployment
      )
    }
  }

  private def buildPipelineDeployment(pipeline: Pipeline, nameHint: Option[String], ingress: Option[String]): State[PlanningState, PlanOp] = {
    PlanningState { state =>
      val serviceId = pipeline.itemId.toString
      val op = PlanOp.DeployPipeline(
        item = pipeline,
        serviceId = serviceId,
        serviceNameHint = nameHint,
        ingress = ingress,
        steps = pipeline.resolved.steps.map(_.algorithm)
      )
      state.withItemDeployed(pipeline.itemId, PlanningState.ItemDeployed()) -> op
    }
  }

  /** Ensure that steps of a pipeline are deployed. */
  private def ensureStepsDeployed(pipeline: Pipeline): State[PlanningState, PlanOp] = {
    val algorithms = pipeline.resolved.steps.map(_.algorithm)
    algorithms.map { algorithm =>
      ensureAlgorithmDeployed(algorithm)
    }.sequence.map { ops =>
      PlanOp.seq(ops: _*)
    }
  }

  /** Ensure that an algorithm is deployed. */
  def ensureAlgorithmDeployed(algorithm: Algorithm): State[PlanningState, PlanOp] = {
    PlanningState.flat { state =>
      state.itemDeployed(algorithm) match {
        case Some(_) =>
          // already deployed
          PlanningState.pure(PlanOp.Empty)
        case None =>
          deployAlgorithm(algorithm, None)
      }
    }
  }
}
