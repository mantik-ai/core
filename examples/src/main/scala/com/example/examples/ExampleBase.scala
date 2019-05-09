package com.example.examples

import ai.mantik.planner.Context

/** Implements boiler plate for local mantik sample applications. */
abstract class ExampleBase {

  def main(args: Array[String]): Unit = {
    val localContext = Context.local()
    try {
      run(localContext)
    } finally {
      localContext.shutdown()
    }
  }

  protected def run(context: Context): Unit
}
