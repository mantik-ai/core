package ai.mantik.planner.impl

import ai.mantik.ds.sql.SingleQuery
import ai.mantik.elements.{ ItemId, MantikId, NamedMantikId, PipelineStep }
import ai.mantik.planner.PlanOp.DeployPipelineSubItem
import ai.mantik.planner.Planner.{ InconsistencyException, PlannerException }
import ai.mantik.planner._
import ai.mantik.planner.pipelines.ResolvedPipelineStep
import ai.mantik.planner.repository.ContentTypes
import cats.data.State
import cats.implicits._
import com.typesafe.config.Config

import javax.inject.Inject

/**
 * Implementation of [[Planner]].
 *
 * Note: the implementation is using the [[State]] Monad for saving
 * the list of opened files (state class is [[PlanningState]].
 *
 * This way it's pure functional and easier to test.
 */
private[mantik] class PlannerImpl @Inject() (config: Config, mantikItemStateManager: MantikItemStateManager) extends Planner {

  val elements = new PlannerElements(config)
  val resourcePlanBuilder = new ResourcePlanBuilder(elements, mantikItemStateManager)

  override def convert[T](action: Action[T]): Plan[T] = {
    val (planningState, planOp) = convertSingleAction(action).run(PlanningState()).value
    val compressed = PlanOp.compress(planOp)
    Plan(compressed, planningState.files, planningState.cacheItems)
  }

  def convertSingleAction[T](action: Action[T]): State[PlanningState, PlanOp[T]] = {
    // Note: IntelliJ likes to mark the return code red, however it's correct checked by scala compiler
    action match {
      case s: Action.SaveAction =>
        ensureItemWithDependenciesStored(s.item)
      case p: Action.FetchAction =>
        resourcePlanBuilder.manifestDataSetAsFile(p.dataSet, canBeTemporary = true).map { filesPlan =>
          val fileRef = filesPlan.fileRefs.head
          PlanOp.seq(
            filesPlan.preOp,
            PlanOp.LoadBundleFromFile(p.dataSet.dataType, fileRef)
          )
        }
      case p: Action.PushAction =>
        ensureItemWithDependenciesStored(p.item).map { op =>
          PlanOp.seq(op, PlanOp.PushMantikItem(p.item))
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

  /** Ensure that the item is stored inside the database. */
  def ensureItemStored(item: MantikItem): State[PlanningState, FilesPlan] = {
    PlanningState.inspect(_.overrideState(item, mantikItemStateManager)).flatMap { itemState =>
      item.mantikId match {
        case i: ItemId =>
          if (itemState.stored) {
            readPayloadFile(item)
          } else {
            storeSingleItem(item)
          }
        case n: NamedMantikId =>
          if (itemState.stored) {
            if (itemState.storedWithName.contains(n)) {
              // already existing under this name
              readPayloadFile(item)
            } else {
              // exists, however needs a tagging
              for {
                plan <- readPayloadFile(item)
                withTag = plan.copy(preOp = PlanOp.combine(plan.preOp, PlanOp.TagMantikItem(item, n)))
                _ <- PlanningState.modify(_.withOverrideFunc(item, mantikItemStateManager, _.copy(storedWithName = Some(n))))
              } yield {
                withTag
              }
            }
          } else {
            // full saving
            storeSingleItem(item)
          }
      }
    }
  }

  /** An empty operation which returns the payload file of an item, with a given itemState. */
  private def readPayloadFile(item: MantikItem): State[PlanningState, FilesPlan] = {
    PlanningState { planningState =>
      val overrideState = planningState.overrideState(item, mantikItemStateManager)
      val itemState = mantikItemStateManager.getOrInit(item)
      overrideState.payloadAvailable match {
        case Some(already) =>
          // Payload already loaded
          val plan = FilesPlan(PlanOp.Empty, already)
          planningState -> plan
        case None =>
          // Load from content
          itemState.payloadFile match {
            case Some(fileId) =>
              // request read of the file
              val contentType = itemPayloadContentType(item)
              val (updatedState, readFile) = planningState.readFile(fileId, contentType)
              val readFileWithContentType = PlanFileWithContentType(readFile.ref, contentType)
              val files = IndexedSeq(readFileWithContentType)
              val updatedState2 = updatedState.withOverrideFunc(
                item,
                mantikItemStateManager,
                _.copy(payloadAvailable = Some(files))
              )
              updatedState2 -> FilesPlan(
                PlanOp.Empty,
                files = files
              )
            case None =>
              // there is nothing to read
              planningState -> FilesPlan(PlanOp.Empty, IndexedSeq.empty)
          }
      }
    }
  }

  private def itemPayloadContentType(item: MantikItem): String = {
    item.core.bridge match {
      case None => throw new PlannerException(s"No bridge defined for ${item}")
      case Some(bridge) =>
        bridge.mantikHeader.definition.payloadContentType match {
          case Some(contentType) => contentType
          case None              => throw new PlannerException(s"Bridge ${bridge.mantikId} doesn't define a content type")
        }
    }
  }

  /** Ensure that an item with it's dependencies is stored. */
  def ensureItemWithDependenciesStored(item: MantikItem): State[PlanningState, PlanOp[Unit]] = {
    val dependent = dependentItemsForSaving(item)

    for {
      dependentOps <- (dependent.map { dependent =>
        ensureItemWithDependenciesStored(dependent)
      }).sequence
      mainOp <- ensureItemStored(item)
    } yield {
      PlanOp.Sequential(dependentOps, mainOp.preOp)
    }
  }

  /** Store an item and also returns the file referencing to it. */
  def storeSingleItem(item: MantikItem): State[PlanningState, FilesPlan] = {
    resourcePlanBuilder.translateItemPayloadSourceAsFiles(item.payloadSource, canBeTemporary = false).flatMap { filesPlan =>
      val files = filesPlan.files
      PlanningState.apply { state =>
        val updatedState = state.withOverrideFunc(item, mantikItemStateManager, e => e.copy(
          payloadAvailable = Some(files),
          stored = true,
          storedWithName = mantikItemStateManager.getOrInit(item).namedMantikItem.orElse(e.storedWithName)
        )
        )
        val fileRef = files.headOption.map(_.ref)
        val combinedOps = PlanOp.seq(
          filesPlan.preOp,
          PlanOp.AddMantikItem(item, fileRef)
        )
        updatedState -> FilesPlan(
          combinedOps,
          files
        )
      }
    }
  }

  /** Returns dependent referenced items. */
  private def dependentItemsForSaving(item: MantikItem): List[MantikItem] = {
    item match {
      case p: Pipeline =>
        p.resolved.steps.collect {
          case a: ResolvedPipelineStep.AlgorithmStep => a.algorithm
        }
      case _ => Nil
    }
  }

  private def deployAlgorithm(algorithm: Algorithm, nameHint: Option[String]): State[PlanningState, PlanOp[DeploymentState]] = {
    PlanningState.flat { state =>
      state.overrideState(algorithm, mantikItemStateManager).deployed match {
        case Some(Left(existing)) =>
          // already deployed before the planner ran
          PlanningState.pure(PlanOp.Const(existing))
        case Some(Right(memory)) =>
          // already deployed withing planner
          PlanningState.pure(PlanOp.MemoryReader[DeploymentState](memory))
        case None =>
          // not yet deployed
          for {
            filePlan <- ensureItemStored(algorithm)
            memoryId <- PlanningState.apply(_.withNextMemoryId)
            _ <- PlanningState.modify { _.withOverrideFunc(algorithm, mantikItemStateManager, _.copy(deployed = Some(Right(memoryId)))) }
          } yield {
            val file = filePlan.files.headOption
            val node = elements.algorithmNode(algorithm, file.map(_.ref))
            val serviceId = algorithm.itemId.toString
            val op = PlanOp.DeployAlgorithm(
              node,
              serviceId,
              nameHint,
              algorithm
            )
            val combined = PlanOp.seq(
              filePlan.preOp,
              op,
              PlanOp.MemoryWriter[DeploymentState](memoryId)
            )
            combined
          }
      }
    }
  }

  /** Deploy a pipeline. */
  def deployPipeline(pipeline: Pipeline, nameHint: Option[String], ingress: Option[String]): State[PlanningState, PlanOp[DeploymentState]] = {
    PlanningState.flat { state =>
      state.overrideState(pipeline, mantikItemStateManager).deployed match {
        case Some(Left(existing)) =>
          // already deployed before the planner ran
          PlanningState.pure(PlanOp.Const(existing))
        case Some(Right(memory)) =>
          // already deployed withing planner
          PlanningState.pure(PlanOp.MemoryReader(memory))
        case None =>
          for {
            stepDeployment <- ensureIndependentStepsDeployed(pipeline)
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
    }
  }

  private def buildPipelineDeployment(pipeline: Pipeline, nameHint: Option[String], ingress: Option[String]): State[PlanningState, PlanOp[DeploymentState]] = {
    for {
      memoryId <- PlanningState.apply(_.withNextMemoryId)
      _ <- PlanningState.modify(_.withOverrideFunc(pipeline, mantikItemStateManager, _.copy(
        deployed = Some(Right(memoryId))
      )))
    } yield {
      val serviceId = pipeline.itemId.toString
      val subDeployments = dependentStepsDeployment(pipeline)
      val op1 = PlanOp.DeployPipeline(
        item = pipeline,
        sub = subDeployments,
        serviceId = serviceId,
        serviceNameHint = nameHint,
        ingress = ingress
      )
      val op2 = PlanOp.MemoryWriter[DeploymentState](memoryId)
      PlanOp.seq(op1, op2)
    }
  }

  /** Ensure that independent steps of a pipeline are deployed. */
  private def ensureIndependentStepsDeployed(pipeline: Pipeline): State[PlanningState, PlanOp[Unit]] = {
    val steps = pipeline.resolved.steps
    steps.collect {
      case ResolvedPipelineStep.AlgorithmStep(algorithm) => ensureAlgorithmDeployed(algorithm)
    }.sequence.map { ops =>
      PlanOp.Sequential(ops, PlanOp.Empty)
    }
  }

  private def dependentStepsDeployment(pipeline: Pipeline): Map[String, DeployPipelineSubItem] = {
    val steps = pipeline.resolved.steps.zipWithIndex
    steps.collect {
      case (ResolvedPipelineStep.SelectStep(select), idx) =>
        val node = elements.queryNode(SingleQuery(select))
        idx.toString -> DeployPipelineSubItem(node)
    }.toMap
  }

  /** Ensure that an algorithm is deployed. */
  def ensureAlgorithmDeployed(algorithm: Algorithm): State[PlanningState, PlanOp[DeploymentState]] = {
    PlanningState.flat { state =>
      state.overrideState(algorithm, mantikItemStateManager).deployed match {
        case Some(Left(deploymentState)) =>
          // already deployed
          PlanningState.pure(PlanOp.Const(deploymentState))
        case Some(Right(memoryId)) =>
          PlanningState.pure(PlanOp.MemoryReader[DeploymentState](memoryId))
        case None =>
          deployAlgorithm(algorithm, None)
      }
    }
  }
}
