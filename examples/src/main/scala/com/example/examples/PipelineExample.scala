package com.example.examples
import java.io.File

import ai.mantik.elements.NamedMantikId
import ai.mantik.planner.{ Context, Pipeline }

object PipelineExample extends ExampleBase {

  val mnistAnnotated = new File("bridge/tf/saved_model/test/resources/samples/mnist_annotated").toPath

  override protected def run(context: Context): Unit = {
    context.pushLocalMantikFile(mnistAnnotated, id = Some("mnist_annotated"))

    val mnist = context.loadAlgorithm("mnist_annotated").tag("nob/mnist_annotated") //otherwise it can't be pushed

    val pipeline = Pipeline.build(
      mnist
    )

    val result = context.execute(pipeline.deploy(ingressName = Some("mnist")))
    println(s"Pipeline deployed at ${result.externalUrl.get}")

    val pushMantikId = NamedMantikId("nob/mnist1")
    val named = pipeline.tag("nob/mnist1")
    context.execute(named.push())
    println(s"Pipeline pushed to ${pushMantikId}")

    // This still fails, see #114
    val pulled = context.pull("nob/mnist1")
    println(s"Pulled pipeline ${pulled.mantikId}")
  }
}
