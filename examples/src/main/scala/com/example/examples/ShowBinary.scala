package com.example.examples

import java.nio.file.Paths

import ai.mantik.planner.PlanningContext

object ShowBinary extends ExampleBase {

  override protected def run(implicit context: PlanningContext): Unit = {
    val binaryBridge = Paths.get("bridge/binary")
    val sampleFile = Paths.get("bridge/binary/test/mnist")
    context.pushLocalMantikItem(binaryBridge)
    context.pushLocalMantikItem(sampleFile)

    val dataset = context.loadDataSet("mnist_test")
    val fetched = dataset.fetch.run()

    println(s"Format: ${fetched.model}")
    println(s"Bundle\n${fetched.render()}")
  }
}
