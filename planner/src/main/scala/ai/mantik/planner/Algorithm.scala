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

import ai.mantik.ds.functional.FunctionType
import ai.mantik.elements.{AlgorithmDefinition, MantikHeader}
import ai.mantik.planner.repository.Bridge

/** Some A => B Transformation Algorithm */
case class Algorithm(
    core: MantikItemCore[AlgorithmDefinition]
) extends ApplicableMantikItem
    with BridgedMantikItem {

  override type DefinitionType = AlgorithmDefinition
  override type OwnType = Algorithm

  override def functionType: FunctionType = mantikHeader.definition.`type`

  override protected def withCore(core: MantikItemCore[AlgorithmDefinition]): Algorithm = {
    copy(core = core)
  }
}

object Algorithm {

  def apply(source: Source, mantikHeader: MantikHeader[AlgorithmDefinition], bridge: Bridge): Algorithm = {
    Algorithm(MantikItemCore(source, mantikHeader, bridge))
  }
}
