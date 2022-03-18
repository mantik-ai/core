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
package ai.mantik.planner

import java.nio.file.Path
import ai.mantik.elements.errors.ErrorCodes
import ai.mantik.elements.{MantikId, NamedMantikId}
import akka.NotUsed
import akka.util.ByteString
import akka.stream.scaladsl.{Source => AkkaSource}
import scala.reflect.ClassTag

/** Main Interface for interacting and evaluating with MantikItems */
trait PlanningContext {

  /** Load a generic Mantik Item. */
  def load(id: MantikId): MantikItem

  private def loadCasted[T <: MantikItem](id: MantikId)(implicit classTag: ClassTag[T#DefinitionType]): T = {
    val item = load(id)
    item.mantikHeader.definitionAs[T#DefinitionType] match {
      case Left(error) => throw ErrorCodes.MantikItemWrongType.toException("Wrong item type", error)
      case _           => // ok
    }
    item.asInstanceOf[T]
  }

  /** Load a dataset from Mantik. */
  def loadDataSet(id: MantikId): DataSet = loadCasted[DataSet](id)

  /** Load a Algorithm from Mantik. */
  def loadAlgorithm(id: MantikId): Algorithm = loadCasted[Algorithm](id)

  /** Load a Trainable Algorithm. */
  def loadTrainableAlgorithm(id: MantikId): TrainableAlgorithm = loadCasted[TrainableAlgorithm](id)

  /** Load a Pipeline. */
  def loadPipeline(id: MantikId): Pipeline = loadCasted[Pipeline](id)

  /** Pulls an item from registry. */
  def pull(id: MantikId): MantikItem

  /** Execute an Action. */
  def execute[T](action: Action[T], meta: ActionMeta = ActionMeta()): T

  /**
    * Push a local mantik item including payload to the repository.
    * There must be a MantikItem present.
    * @return Mantik id which was used in the end.
    */
  def pushLocalMantikItem(dir: Path, id: Option[NamedMantikId] = None): MantikId

  /** Returns the internal state of a MantikItem */
  def state(item: MantikItem): MantikItemState

  /**
    * Store a file on Mantik Site (e.g. to use it an Item.)
    * @param contentType content type
    * @param contentLength content length, if known
    * @param temporary if true, file is marked as being temporary
    * @param source data source
    * @return
    */
  def storeFile(
      contentType: String,
      source: AkkaSource[ByteString, NotUsed],
      temporary: Boolean,
      contentLength: Option[Long] = None
  ): (String, Long)
}
