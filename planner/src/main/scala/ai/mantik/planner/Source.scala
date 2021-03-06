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

import ai.mantik.bridge.scalafn.{ScalaFnDefinition, ScalaFnHeader}
import ai.mantik.ds.DataType
import ai.mantik.ds.element.Bundle
import ai.mantik.ds.sql.{MultiQuery, Query, Select}
import ai.mantik.elements.{ItemId, NamedMantikId}
import akka.util.ByteString

/** Defines where a MantikItem comes from. */
case class Source(
    definition: DefinitionSource,
    payload: PayloadSource
) {

  /** Creates a derived source with the same payload. */
  def derive: Source = Source(DefinitionSource.Derived(definition), payload)
}

object Source {

  /** Creates a source for a constructed element. */
  def constructed(payloadSource: PayloadSource = PayloadSource.Empty): Source = Source(
    DefinitionSource.Constructed(),
    payloadSource
  )
}

/** Represents the way [[MantikItem]](s) get their Definition data from. */
sealed trait DefinitionSource {

  /** Returns true if the source is indicating that the name is stored. */
  def nameStored: Boolean = false

  /** Returns tre if the source is indicating that the item is stored. */
  def itemStored: Boolean = false

  /** Returns a name if the source indicates that the item has one. */
  def name: Option[NamedMantikId] = None

  /** Returns an item id if the source indicates that the item has one. */
  def storedItemId: Option[ItemId] = None
}

object DefinitionSource {

  /** The item was loaded from the repository. */
  case class Loaded(mantikId: Option[NamedMantikId], itemId: ItemId) extends DefinitionSource {
    override def nameStored: Boolean = mantikId.isDefined

    override def itemStored: Boolean = true

    override def name: Option[NamedMantikId] = mantikId

    override def storedItemId: Option[ItemId] = Some(itemId)
  }

  /** The item was artificially constructed (e.g. literals, calculations, ...) */
  case class Constructed() extends DefinitionSource

  /** The item was somehow derived from another one (e.g. changing MantikHeader) */
  case class Derived(other: DefinitionSource) extends DefinitionSource

  /** The item was tagged with a name. */
  case class Tagged(namedId: NamedMantikId, from: DefinitionSource) extends DefinitionSource {
    override def nameStored: Boolean = false

    override def itemStored: Boolean = from.itemStored

    override def name: Option[NamedMantikId] = Some(namedId)

    override def storedItemId: Option[ItemId] = from.storedItemId
  }
}

/** Defines an operation. */
sealed trait Operation {
  def resultCount: Int = 1
}

case object Operation {

  /** An algorithm was applied. */
  case class Application(algorithm: ApplicableMantikItem, argument: DataSet) extends Operation

  /** A Training Operation. */
  case class Training(algorithm: TrainableAlgorithm, trainingData: DataSet) extends Operation {
    override def resultCount: Int = 2 // stats, trained algorithm
  }

  /** An SQL Query Operation */
  case class SqlQueryOperation(query: MultiQuery, arguments: Vector[DataSet]) extends Operation {
    override val resultCount: Int = query.resultingQueryType.size
  }

  /** An operation on the ScalaFn-Bridge. */
  case class ScalaFnOperation(
      definition: ScalaFnDefinition,
      arguments: Vector[DataSet]
  ) extends Operation {
    override def resultCount: Int = definition.output.size
  }
}

/** Represents the way [[MantikItem]](s) gets their Payload Data from. */
sealed trait PayloadSource {
  def resultCount: Int = 1
}

object PayloadSource {

  /** There is no payload. */
  case object Empty extends PayloadSource {
    override def resultCount = 0
  }

  /** A fixed location in the file repository. */
  case class Loaded(fileId: String, contentType: String) extends PayloadSource

  /** A fixed data block. */
  sealed abstract class Literal extends PayloadSource

  /** A Literal which contains a bundle. */
  case class BundleLiteral(content: Bundle) extends Literal()

  /**
    * It's the result of some operation.
    * If projection is > 0, the non main result is used.
    */
  case class OperationResult(op: Operation) extends PayloadSource {
    override def resultCount: Int = op.resultCount
  }

  /** Projects one of multiple results of the source. */
  case class Projection(source: PayloadSource, projection: Int = 0) extends PayloadSource {
    require(projection >= 0 && projection < source.resultCount)
  }

  /**
    * A Cached source value.
    * @param siblings the parallel elements which are evaluated at the same time.
    */
  case class Cached(
      source: PayloadSource,
      siblings: Vector[ItemId]
  ) extends PayloadSource {
    override def resultCount: Int = source.resultCount

    require(source.resultCount == siblings.size, "Results must match items")
  }
}
