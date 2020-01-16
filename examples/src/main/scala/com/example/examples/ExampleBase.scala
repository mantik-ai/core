package com.example.examples

import java.nio.file.Paths

import ai.mantik.componently.AkkaRuntime
import ai.mantik.engine.EngineClient
import ai.mantik.planner.Context

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
      val context = engineClient.createContext()

      println("Adding Sample Bridges")
      context.pushLocalMantikItem(BinaryBridge)
      context.pushLocalMantikItem(TfTrainBridge)
      context.pushLocalMantikItem(TfSavedModelBridge)
      context.pushLocalMantikItem(SkLearnBridge)

      run(context)
    } finally {
      akkaRuntime.shutdown()
    }
  }

  protected def run(implicit context: Context): Unit
}
