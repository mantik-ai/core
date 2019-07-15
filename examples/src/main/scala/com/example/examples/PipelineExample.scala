package com.example.examples
import java.io.File

import ai.mantik.planner.{ Context, Pipeline }

object PipelineExample extends ExampleBase {

  val mnistAnnotated = new File("bridge/tf/saved_model/test/resources/samples/mnist_annotated").toPath

  override protected def run(context: Context): Unit = {
    context.pushLocalMantikFile(mnistAnnotated, id = Some("mnist_annotated"))

    val mnist = context.loadAlgorithm("mnist_annotated")

    val pipeline = Pipeline.build(
      mnist
    )

    val result = context.execute(pipeline.deploy(ingressName = Some("mnist")))
    println(s"Pipeline deployed at ${result.externalUrl.get}")

    // We must wait a little bit so that it comes online. See bug #95
    Thread.sleep(10000)
  }
}
