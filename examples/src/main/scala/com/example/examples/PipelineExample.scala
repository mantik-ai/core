package com.example.examples
import java.io.File

import ai.mantik.ds.TabularData
import ai.mantik.ds.sql.Select
import ai.mantik.elements.NamedMantikId
import ai.mantik.planner.{ Pipeline, PlanningContext }

object PipelineExample extends ExampleBase {

  val mnistAnnotated = new File("bridge/tf/saved_model/test/resources/samples/mnist_annotated").toPath

  override protected def run(implicit context: PlanningContext): Unit = {
    context.pushLocalMantikItem(mnistAnnotated, id = Some("mnist_annotated"))

    val mnist = context.loadAlgorithm("mnist_annotated").tag("nob/mnist_annotated") //otherwise it can't be pushed
    val select = Select.parse(mnist.functionType.output.asInstanceOf[TabularData], "SELECT y AS x").right.get

    val pipeline = Pipeline.build(
      Right(mnist),
      Left(select)
    )

    val result = pipeline.deploy(ingressName = Some("mnist")).run()
    println(s"Pipeline deployed at ${result.externalUrl.get}")

    val pushMantikId = NamedMantikId("nob/mnist1")
    val named = pipeline.tag("nob/mnist1")
    named.push().run()

    println(s"Pipeline pushed to ${pushMantikId}")

    // This still fails, see #114
    val pulled = context.pull("nob/mnist1")
    println(s"Pulled pipeline ${pulled.mantikId}")
  }
}
