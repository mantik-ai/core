package com.example.examples

import java.io.File

import ai.mantik.planner.Context

object ShowBinary {

  def main(args: Array[String]): Unit = {
    val context = Context.local()
    try {
      val sampleFile = new File("bridge/binary/test/mnist").toPath
      context.pushLocalMantikFile(sampleFile)

      val dataset = context.loadDataSet("mnist_test")
      val fetched = context.execute(dataset.fetch)

      println(s"Format: ${fetched.model}")
      println(s"Bundle\n${fetched.render()}")

    } finally {
      context.shutdown()
    }
  }
}
