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

import ai.mantik.elements.{BridgeDefinition, ItemId, MantikDefinition, MantikId, MantikHeader, NamedMantikId}
import ai.mantik.planner.repository.{Bridge, ContentTypes}

/** Built in Items in Mantik */
object BuiltInItems {

  /** The protected name for built in Items. */
  val BuiltInAccount = "builtin"

  /** Name of the Natural Format. */
  val NaturalBridgeName = NamedMantikId(account = BuiltInAccount, name = "natural")

  /** Name of the Select Bridge */
  val SelectBridgeName = NamedMantikId(account = BuiltInAccount, name = "select")

  /** Name of the ScalaFn Bridge */
  val ScalaFnBridgeName = NamedMantikId(account = BuiltInAccount, name = "scalafn")

  /** Name of the CSV Bridge */
  val CsvBridgeName = NamedMantikId(account = BuiltInAccount, name = "csv")

  /**
    * Look for a Built In Item
    * @return the item if it was found.
    */
  def readBuiltInItem(mantikId: MantikId): Option[MantikItem] = {
    mantikId match {
      case NaturalBridgeName => Some(NaturalBridge)
      case SelectBridgeName  => Some(SelectBridge)
      case CsvBridgeName     => Some(CsvBridge)
      case _                 => None
    }
  }

  /** The Natural Bridge. */
  val NaturalBridge = Bridge(
    MantikItemCore(
      Source(
        DefinitionSource.Loaded(Some(NaturalBridgeName), ItemId("@1")),
        PayloadSource.Empty
      ),
      MantikHeader.pure(
        BridgeDefinition(
          dockerImage = "",
          suitable = Seq(MantikDefinition.DataSetKind),
          protocol = 0,
          payloadContentType = Some(ContentTypes.MantikBundleContentType)
        )
      )
    )
  )

  /** The Embedded Select Bridge. */
  val SelectBridge = Bridge(
    MantikItemCore(
      Source(
        DefinitionSource.Loaded(Some(SelectBridgeName), ItemId("@2")),
        PayloadSource.Empty
      ),
      MantikHeader.pure(
        BridgeDefinition(
          dockerImage = "mantikai/bridge.select",
          suitable = Seq(MantikDefinition.CombinerKind),
          protocol = 2,
          payloadContentType = None
        )
      )
    )
  )

  val ScalaFnBridge = Bridge(
    MantikItemCore(
      Source(
        DefinitionSource.Loaded(Some(ScalaFnBridgeName), ItemId("@3")),
        PayloadSource.Empty
      ),
      MantikHeader.pure(
        BridgeDefinition(
          dockerImage = "mantikai/bridge.scala-fn",
          suitable = Seq(MantikDefinition.CombinerKind),
          protocol = 2,
          payloadContentType = None
        )
      )
    )
  )

  /** The CSV Bridge */
  val CsvBridge = Bridge(
    MantikItemCore(
      Source(
        DefinitionSource.Loaded(Some(CsvBridgeName), ItemId("@4")),
        PayloadSource.Empty
      ),
      MantikHeader.pure(
        BridgeDefinition(
          dockerImage = "mantikai/bridge.csv",
          suitable = Seq(MantikDefinition.DataSetKind),
          protocol = 2,
          payloadContentType = Some(ContentTypes.Csv)
        )
      )
    )
  )
}
