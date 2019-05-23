package com.example.examples

import java.io.File

import ai.mantik.planner.Context

object ShowBinary extends ExampleBase {

  override protected def run(context: Context): Unit = {
    val sampleFile = new File("bridge/binary/test/mnist").toPath
    context.pushLocalMantikFile(sampleFile)

    val dataset = context.loadDataSet("mnist_test")
    val fetched = context.execute(dataset.fetch)

    println(s"Format: ${fetched.model}")
    println(s"Bundle\n${fetched.render()}")
  }
}
