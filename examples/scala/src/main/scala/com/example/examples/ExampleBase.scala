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
package com.example.examples

import java.nio.file.Paths

import ai.mantik.componently.AkkaRuntime
import ai.mantik.engine.EngineClient
import ai.mantik.planner.PlanningContext

/** Implements boiler plate for local mantik sample applications. */
abstract class ExampleBase {

  private val BinaryBridge = Paths.get("bridge/binary")
  private val TfTrainBridge = Paths.get("bridge/tf/train")
  private val TfSavedModelBridge = Paths.get("bridge/tf/saved_model")
  private val SkLearnBridge = Paths.get("bridge/sklearn/simple_learn")

  def main(args: Array[String]): Unit = {
    implicit val akkaRuntime = AkkaRuntime.createNew()
    try {
      val engineClient = EngineClient.create()
      val context = engineClient.planningContext

      println("Adding Sample Bridges")
      context.pushLocalMantikItem(BinaryBridge)
      context.pushLocalMantikItem(TfTrainBridge)
      context.pushLocalMantikItem(TfSavedModelBridge)
      context.pushLocalMantikItem(SkLearnBridge)
      println("Done Adding Sample Bridges")

      run(context)
    } finally {
      akkaRuntime.shutdown()
    }
  }

  protected def run(implicit context: PlanningContext): Unit
}
