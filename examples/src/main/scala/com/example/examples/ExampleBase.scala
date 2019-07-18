package com.example.examples

import ai.mantik.componently.AkkaRuntime
import ai.mantik.engine.EngineClient
import ai.mantik.planner.Context

/** Implements boiler plate for local mantik sample applications. */
abstract class ExampleBase {

  def main(args: Array[String]): Unit = {
    implicit val akkaRuntime = AkkaRuntime.createNew()
    try {
      val engineClient = EngineClient.create()
      try {
        val context = engineClient.createContext()
        run(context)
      } finally {
        engineClient.shutdown()
      }
    } finally {
      akkaRuntime.shutdown()
    }
  }

  protected def run(context: Context): Unit
}
