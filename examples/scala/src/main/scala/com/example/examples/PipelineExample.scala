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
import java.io.File

import ai.mantik.ds.TabularData
import ai.mantik.ds.sql.Select
import ai.mantik.elements.NamedMantikId
import ai.mantik.planner.{Pipeline, PlanningContext}

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
