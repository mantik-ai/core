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

import ai.mantik.ds.Errors.FeatureNotSupported
import ai.mantik.ds.sql.{MultiQuery, Query, Select, SingleQuery}
import ai.mantik.elements.ItemId
import ai.mantik.planner.Planner.InconsistencyException
import ai.mantik.planner.pipelines.ResolvedPipelineStep
import ai.mantik.planner.{
  Algorithm,
  ApplicableMantikItem,
  DataSet,
  Operation,
  PayloadSource,
  Pipeline,
  PlanFileReference,
  PlanOp,
  Planner,
  TrainableAlgorithm
}
import ai.mantik.planner.repository.{Bridge, ContentTypes}
import ai.mantik.planner.select.SelectMantikHeaderBuilder
import cats.data.State
import cats.implicits._

/**
  * Responsible for building [[ResourcePlan]] and [[FilesPlan]] for evaluating Mantik Items in Graphs.
  * Part of [[PlannerImpl]]
  */
private[impl] class ResourcePlanBuilder(elements: PlannerElements, mantikItemStateManager: MantikItemStateManager) {

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
        PlanningState(_.readFile(loaded.fileId, loaded.contentType)).flatMap { file =>
          elements.loadFileNode(PlanFileWithContentType(file.ref, loaded.contentType))
        }
      case l: PayloadSource.Literal =>
        // Ugly this leads to fragmented plan.
        for {
          fileReference <- PlanningState(_.pipeFile(ContentTypes.MantikBundleContentType, temporary = true))
          loader <- elements.loadFileNode(
            PlanFileWithContentType(fileReference.ref, ContentTypes.MantikBundleContentType)
          )
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
  def translateItemPayloadSourceAsFiles(
      source: PayloadSource,
      canBeTemporary: Boolean
  ): State[PlanningState, FilesPlan] = {
    source match {
      case PayloadSource.Empty =>
        PlanningState { s =>
          s -> FilesPlan()
        }
      case PayloadSource.Loaded(fileId, contentType) =>
        // already available as file
        PlanningState(_.readFile(fileId, contentType)).map { fileGet =>
          FilesPlan(files = IndexedSeq(PlanFileWithContentType(fileGet.ref, contentType)))
        }
      case l: PayloadSource.Literal =>
        // We have to go via temporary file
        PlanningState(_.pipeFile(ContentTypes.MantikBundleContentType, temporary = canBeTemporary)).map { reference =>
          val pushing = elements.literalToPushBundle(l, reference)
          FilesPlan(pushing, IndexedSeq(PlanFileWithContentType(reference.ref, ContentTypes.MantikBundleContentType)))
        }
      case c: PayloadSource.Cached =>
        cachedSourceFiles(c, canBeTemporary)
      case p: PayloadSource.Projection =>
        translateItemPayloadSourceAsFiles(p.source, canBeTemporary).map { filesPlan =>
          filesPlan.copy(
            files = IndexedSeq(filesPlan.files(p.projection))
          )
        }
      case other =>
        translateItemPayloadSource(other).flatMap { operationResult =>
          resourcePlanToFiles(operationResult, canBeTemporary)
        }
    }
  }

  /** Assembles a cached plan (temporary or persistent). */
  private def cachedSourceFiles(
      cachedSource: PayloadSource.Cached,
      canBeTemporary: Boolean
  ): State[PlanningState, FilesPlan] = {
    if (canBeTemporary) {
      cachedTemporarySource(cachedSource)
    } else {
      // Generate a cached view and then copy that to real files
      for {
        filesPlan <- cachedTemporarySource(cachedSource)
        nonTemporaries <- filesPlan.files
          .map { planFile =>
            PlanningState(_.pipeFile(planFile.contentType, temporary = false))
          }
          .toList
          .sequence
        copyOperations = filesPlan.files.zip(nonTemporaries).map { case (temporaryFile, nontemporaryFile) =>
          PlanOp.CopyFile(from = temporaryFile.ref, to = nontemporaryFile.ref)
        }
        newFiles = filesPlan.files.zip(nonTemporaries).map { case (temporaryFile, nonTemporaryFile) =>
          PlanFileWithContentType(nonTemporaryFile.ref, temporaryFile.contentType)
        }
      } yield {
        FilesPlan(
          preOp = PlanOp.combine(filesPlan.preOp, PlanOp.Sequential(copyOperations, PlanOp.Empty)),
          files = newFiles
        )
      }
    }
  }

  /** Assembles a plan for having a cached item present (temporary). */
  private def cachedTemporarySource(cachedSource: PayloadSource.Cached): State[PlanningState, FilesPlan] = {
    PlanningState.flat { planningState =>
      planningState.evaluatedCache(cachedSource.siblings) match {
        case Some(files) =>
          // Node was already evalauted
          PlanningState.pure(FilesPlan(files = files))
        case None =>
          // Node need to be re-evaluated
          for {
            filesPlan <- reevaluateCachedSource(cachedSource)
            _ <- PlanningState.modify(_.withEvaluatedCache(cachedSource.siblings, filesPlan.files))
          } yield {
            filesPlan
          }
      }
    }
  }

  /** Forces reassembly of a cached item. */
  private def reevaluateCachedSource(cachedSource: PayloadSource.Cached): State[PlanningState, FilesPlan] = {
    cacheState(cachedSource.siblings) match {
      case Some(files) =>
        for {
          contentTypes <- fileContentTypes(cachedSource.source)
          fileReads <- files
            .zip(contentTypes)
            .map { case (fileId, contentType) =>
              PlanningState(_.readFile(fileId, contentType)).map { planFile =>
                PlanFileWithContentType(planFile.ref, contentType)
              }
            }
            .sequence
        } yield {
          FilesPlan(files = fileReads)
        }
      case None =>
        for {
          opFiles <- translateItemPayloadSourceAsFiles(cachedSource.source, canBeTemporary = true)
          _ <- markFileAsCachedFile(opFiles.fileRefs, cachedSource.siblings)
        } yield {
          val cacheFiles = cachedSource.siblings.zip(opFiles.fileRefs)
          FilesPlan(
            PlanOp.combine(
              opFiles.preOp,
              PlanOp.MarkCached(cacheFiles)
            ),
            files = opFiles.files
          )
        }
    }
  }

  /** Returns the FileIds of a cached sibling set, if and only if all of them are cached available. */
  private def cacheState(siblings: Vector[ItemId]): Option[Vector[String]] = {
    val cacheFiles = for {
      itemId <- siblings
      state <- mantikItemStateManager.get(itemId)
      cacheFile <- state.cacheFile
    } yield cacheFile
    if (cacheFiles.size == siblings.size) {
      Some(cacheFiles)
    } else {
      None
    }
  }

  private def fileContentTypes(payloadSource: PayloadSource): State[PlanningState, IndexedSeq[String]] = {
    // Note: this is a bit hacky as we do a optimistic payload conversions and throw away the result
    // and all state changes
    translateItemPayloadSourceAsFiles(payloadSource, canBeTemporary = true).flatMap { filesPlan =>
      val contentTypes = filesPlan.files.map(_.contentType)
      // this throws away state and just returns the content types.
      PlanningState.pure(contentTypes)
    }
  }

  private def markFileAsCachedFile(
      files: IndexedSeq[PlanFileReference],
      items: Vector[ItemId]
  ): State[PlanningState, Unit] = {
    val toCached = files.zip(items).toMap
    PlanningState { state =>
      val updatedState = state
        .markCached(toCached)
      updatedState -> (())
    }
  }

  /**
    * Converts a [[ResourcePlan]] into a [[FilesPlan]].
    * Note: this leads to a splitted plan, usually.
    */
  private def resourcePlanToFiles(
      resourcePlan: ResourcePlan,
      canBeTemporary: Boolean
  ): State[PlanningState, FilesPlan] = {
    resourcePlan.outputs.toList
      .map { output =>
        val outputResource = resourcePlan.outputResource(output)
        for {
          file <- PlanningState(_.pipeFile(outputResource.contentType, temporary = canBeTemporary))
          fileNode <- elements.createStoreFileNode(file, outputResource.contentType)
        } yield {
          fileNode -> PlanFileWithContentType(file.ref, outputResource.contentType)
        }
      }
      .sequence
      .map { fileNodeWithFile: List[(ResourcePlan, PlanFileWithContentType)] =>
        val consumers = fileNodeWithFile.foldLeft(ResourcePlan()) { case (p, (storeFile, _)) =>
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
    filesPlan.files.toList
      .map { file =>
        elements.loadFileNode(file)
      }
      .sequence
      .map { fileLoaders =>
        val fullPlan = fileLoaders
          .foldLeft(ResourcePlan())(_.merge(_))
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
      case Operation.SqlQueryOperation(query, arguments) =>
        for {
          argumentSource <- arguments.map(manifestDataSet).sequence
          selectSource <- manifestQuery(query)
        } yield {
          val mergedArguments = argumentSource.reduceLeft(_.merge(_))
          selectSource.application(mergedArguments)
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
      elements
        .algorithm(algorithm, algorithmFile)
        .map(_.prependOp(files.preOp))
    }
  }

  /** Manifest a pipeline as a graph, will have on input and one output. */
  def manifestPipeline(pipeline: Pipeline): State[PlanningState, ResourcePlan] = {
    val steps = pipeline.resolved.steps
    require(steps.nonEmpty, "Pipelines may not be empty")
    steps
      .map { step =>
        manifestPipelineStep(step)
      }
      .sequence
      .map { plans: List[ResourcePlan] =>
        val resultPlan = plans.reduce[ResourcePlan] { (c, n) =>
          n.application(c)
        }
        resultPlan
      }
  }

  def manifestPipelineStep(resolvedPipelineStep: ResolvedPipelineStep): State[PlanningState, ResourcePlan] = {
    resolvedPipelineStep match {
      case ResolvedPipelineStep.AlgorithmStep(algorithm) => manifestAlgorithm(algorithm)
      case ResolvedPipelineStep.SelectStep(select)       => manifestQuery(SingleQuery(select))
    }
  }

  /** Manifest an SQL Query */
  def manifestQuery(query: MultiQuery): State[PlanningState, ResourcePlan] = {
    elements.query(query)
  }

  /** Manifest a trainable algorithm as a graph, will have one input and two outputs. */
  def manifestTrainableAlgorithm(trainableAlgorithm: TrainableAlgorithm): State[PlanningState, ResourcePlan] = {
    translateItemPayloadSourceAsFiles(trainableAlgorithm.payloadSource, canBeTemporary = true).flatMap { files =>
      val algorithmFile = files.fileRefs.headOption
      elements
        .trainableAlgorithm(trainableAlgorithm, algorithmFile)
        .map(_.prependOp(files.preOp))
    }
  }

  /** Manifest a data set as a graph with one output. */
  def manifestDataSet(dataSet: DataSet): State[PlanningState, ResourcePlan] = {
    if (dataSet.bridgeMantikId == Bridge.naturalBridge.mantikId) {
      translateItemPayloadSource(dataSet.payloadSource)
    } else {
      translateItemPayloadSourceAsFiles(dataSet.payloadSource, canBeTemporary = true).flatMap { files =>
        val dataSetFile = files.fileRefs.headOption
        elements.dataSet(dataSet, dataSetFile).map(_.prependOp(files.preOp))
      }
    }
  }

  /** Manifest a data set as (natural encoded) file. */
  def manifestDataSetAsFile(dataSet: DataSet, canBeTemporary: Boolean): State[PlanningState, FilesPlan] = {
    if (dataSet.bridgeMantikId == Bridge.naturalBridge.mantikId) {
      // We can directly use it's file
      translateItemPayloadSourceAsFiles(dataSet.payloadSource, canBeTemporary)
    } else {
      manifestDataSet(dataSet).flatMap { resourcePlan =>
        resourcePlanToFiles(resourcePlan, canBeTemporary)
      }
    }
  }

}
