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
package ai.mantik.ui.model

import ai.mantik.ds.helper.circe.DiscriminatorDependentCodec
import ai.mantik.elements.{ItemId, MantikId, NamedMantikId}

/** Defines the content of the different operations.
  * Roughly similar to PlanOp in PlanExecution (but not all operations present)
  */
sealed trait OperationDefinition

object OperationDefinition {

  /** Prepare containers */
  case class PrepareContainers() extends OperationDefinition

  /** Prepare IDs for files. */
  case class PrepareFiles(count: Int) extends OperationDefinition

  /** Evaluating a graph. */
  case class RunGraph() extends OperationDefinition

  /** Upload a file to the executor storage */
  case class UploadFile(fileRef: String, contentType: String, contentSize: Option[Long]) extends OperationDefinition

  /** Download a file from executor storage */
  case class DownloadFile(fileRef: String, contentType: String) extends OperationDefinition

  /** Do something with an item */
  case class ItemOp(op: String, id: MantikId, name: Option[NamedMantikId] = None) extends OperationDefinition

  case class UpdateDeployState(id: MantikId, deploy: Boolean) extends OperationDefinition

  /** Some other operation */
  case class Other(name: String) extends OperationDefinition

  implicit object operationCodec extends DiscriminatorDependentCodec[OperationDefinition]("type") {
    override val subTypes = Seq(
      makeSubType[PrepareContainers]("prepare_containers"),
      makeSubType[PrepareFiles]("prepare_files"),
      makeSubType[RunGraph]("run_graph"),
      makeSubType[UploadFile]("upload_file"),
      makeSubType[DownloadFile]("download_file"),
      makeSubType[ItemOp]("item_op"),
      makeSubType[UpdateDeployState]("deploy_state"),
      makeSubType[Other]("other")
    )
  }
}
