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
package ai.mantik.planner.impl

import ai.mantik.elements.{ItemId, NamedMantikId}
import ai.mantik.planner.Planner.{InconsistencyException, PlannerException}
import ai.mantik.planner._
import ai.mantik.planner.pipelines.ResolvedPipelineStep
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
private[mantik] class PlannerImpl @Inject() (config: Config, mantikItemStateManager: MantikItemStateManager)
    extends Planner {

  val elements = new PlannerElements(config)
  val resourcePlanBuilder = new ResourcePlanBuilder(elements, mantikItemStateManager)

  override def convert[T](action: Action[T], meta: ActionMeta): Plan[T] = {
    val (planningState, planOp) = convertSingleAction(action).run(PlanningState()).value
    val compressed = PlanOp.compress(planOp)
    Plan(compressed, planningState.files, planningState.cacheItems, name = meta.name)
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
          case p: Pipeline =>
            deployPipeline(p, d.nameHint, d.ingressName)
          case other =>
            throw new InconsistencyException(s"Can only deploy pipelines, got ${other.getClass.getSimpleName}")
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
                _ <- PlanningState
                  .modify(_.withOverrideFunc(item, mantikItemStateManager, _.copy(storedWithName = Some(n))))
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
    resourcePlanBuilder.translateItemPayloadSourceAsFiles(item.payloadSource, canBeTemporary = false).flatMap {
      filesPlan =>
        val files = filesPlan.files
        PlanningState.apply { state =>
          val updatedState = state.withOverrideFunc(
            item,
            mantikItemStateManager,
            e =>
              e.copy(
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
        p.resolved.steps.collect { case a: ResolvedPipelineStep.AlgorithmStep =>
          a.algorithm
        }
      case _ => Nil
    }
  }

  /** Deploy a pipeline */
  def deployPipeline(
      pipeline: Pipeline,
      nameHint: Option[String],
      ingress: Option[String]
  ): State[PlanningState, PlanOp[DeploymentState]] = {
    /*
    Steps:
    - 0. Check if pipeline is already deployed
    - 1. Pipeline must be in database
    - 2. All Items must be manifested (payload must be available)
     */
    ifNotDeployed(pipeline) {
      for {
        stored <- ensureItemStored(pipeline)
        plan <- resourcePlanBuilder.manifestPipeline(pipeline)
        deploymentOp = PlanOp.DeployPipeline(
          itemId = pipeline.itemId,
          graph = plan.graph,
          input = plan.inputs.head,
          inputDataType = pipeline.functionType.input,
          output = plan.outputs.head,
          outputDataType = pipeline.functionType.output,
          serviceId = pipeline.itemId.toString,
          serviceNameHint = nameHint,
          ingress = ingress
        )
        memoryId <- PlanningState.apply(_.withNextMemoryId)
        _ <- PlanningState.modify(
          _.withOverrideFunc(
            pipeline,
            mantikItemStateManager,
            _.copy(
              deployed = Some(Right(memoryId))
            )
          )
        )
      } yield {
        PlanOp.seq(
          stored.preOp,
          plan.pre,
          deploymentOp,
          PlanOp.MemoryWriter[DeploymentState](memoryId)
        )
      }
    }
  }

  /** Run f if the pipeline is not yet deployed */
  private def ifNotDeployed(pipeline: Pipeline)(
      otherwise: State[PlanningState, PlanOp[DeploymentState]]
  ): State[PlanningState, PlanOp[DeploymentState]] = {
    PlanningState.flat { state =>
      state.overrideState(pipeline, mantikItemStateManager).deployed match {
        case Some(Left(existing)) =>
          // already deployed before the planner ran
          PlanningState.pure(PlanOp.Const(existing))
        case Some(Right(memory)) =>
          // already deployed withing planner
          PlanningState.pure(PlanOp.MemoryReader(memory))
        case None =>
          otherwise
      }
    }
  }
}
