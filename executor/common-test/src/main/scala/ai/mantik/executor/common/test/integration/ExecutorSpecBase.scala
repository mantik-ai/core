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
package ai.mantik.executor.common.test.integration

import ai.mantik.executor.{Errors, model}
import ai.mantik.executor.model.docker.Container
import ai.mantik.executor.model._
import ai.mantik.testutils.{AkkaSupport, HttpSupport, TestBase}
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import io.circe.Json
import io.circe.parser

import scala.concurrent.Future

trait ExecutorSpecBase extends HttpSupport {
  self: IntegrationBase with TestBase with AkkaSupport =>

  val simpleSelect = {
    io.circe.yaml.parser
      .parse(
        """# Let everything through, SELECT *
          |# Can still rename out columns
          |bridge: builtin/select
          |kind: combiner
          |input:
          |    - columns:
          |        "x": int32
          |        "y": string
          |output:
          |    - columns:
          |        a: int32
          |        b: string
          |program:
          |    type: "select"
          |    result:
          |      columns:
          |        a: int32
          |        b: string
          |    selector: null
          |    projector: null
          |
          |""".stripMargin
      )
      .forceRight
  }

  val sampleData: Json = io.circe.parser
    .parse("""[[1, "Hello World"],[2, "How are you"]]""".stripMargin)
    .forceRight

  def simpleEvaluation(id: String, sink: Sink[ByteString, Future[Long]]) = EvaluationWorkload(
    id = id,
    containers = Vector(
      WorkloadContainer(
        container = Container(
          image = "mantikai/bridge.select"
        )
      )
    ),
    sessions = Vector(
      WorkloadSession(
        containerId = 0,
        mnpSessionId = "select",
        mantikHeader = simpleSelect,
        payload = None,
        inputContentTypes = Vector("application/json"),
        outputContentTypes = Vector("application/json")
      )
    ),
    sources = Vector(
      DataSource.constant(
        ByteString.fromString(
          sampleData.toString()
        ),
        "application/json"
      )
    ),
    sinks = Vector(
      DataSink(
        sink
      )
    ),
    links = Vector(
      Link(Left(0), Right(WorkloadSessionPort(0, 0))),
      Link(Right(WorkloadSessionPort(0, 0)), Left(0))
    )
  )

  it should "be possible to execute a simple evaluation" in {
    withExecutor { executor =>
      val collector = new ByteCollectingSink()
      val workload = simpleEvaluation("simple", collector.sink)
      await(executor.startEvaluation(EvaluationWorkloadRequest(workload)))

      eventually {
        await(executor.getEvaluation(workload.id)).status shouldBe WorkloadStatus.Done
      }

      val collected = await(collector.collectedBytes)
      io.circe.parser.parse(collected.utf8String).forceRight shouldBe sampleData

      val elements = await(executor.list())
      val element = elements.elements.find(_.id == "simple").get
      element.status shouldBe WorkloadStatus.Done
    }
  }

  it should "it should be possible to cancel" in {
    withExecutor { executor =>
      val collector = new ByteCollectingSink()
      val workload = simpleEvaluation("simple2", collector.sink)
      await(executor.startEvaluation(EvaluationWorkloadRequest(workload)))

      withClue("It should detect same ids") {
        awaitException[Errors.ConflictException](executor.startEvaluation(EvaluationWorkloadRequest(workload)))
      }

      val all = await(executor.list())
      val element = all.elements.find(_.id == "simple2").get
      element.kind shouldBe ListElementKind.Evaluation
      element.status shouldNot be(WorkloadStatus.Done) // should not be that fast

      await(executor.cancelEvaluation(workload.id))

      eventually {
        val state = await(executor.getEvaluation(workload.id))
        state.canceled shouldBe true
        state.status shouldBe an[WorkloadStatus.Failed]
      }
    }
  }

  it should "be possible to track executions" in {
    withExecutor { executor =>
      val collector = new ByteCollectingSink()
      val workload = simpleEvaluation("simple3", collector.sink)
      await(executor.startEvaluation(EvaluationWorkloadRequest(workload)))

      val stateSink = Sink.seq[EvaluationWorkloadState]
      val stateSource = executor.trackEvaluation(workload.id)
      val stateChangesFuture = stateSource.runWith(stateSink)
      val stateChanges = await(stateChangesFuture)
      stateChanges.size shouldBe >=(2)
      stateChanges.last.status shouldBe WorkloadStatus.Done
    }
  }

  it should "fail correctly when the container doesn't initialize" in {
    withExecutor { executor =>
      val collector = new ByteCollectingSink()
      val ok = simpleEvaluation("simple4", collector.sink)
      val bad = ok.copy(
        containers = ok.containers.updated(
          0,
          ok.containers(0)
            .copy(
              container = ok
                .containers(0)
                .container
                .copy(
                  image = "mantikai/bad_container"
                )
            )
        )
      )

      await(executor.startEvaluation(EvaluationWorkloadRequest(bad)))

      eventually {
        val state = await(executor.getEvaluation(bad.id))
        state.status shouldBe an[WorkloadStatus.Failed]
      }
    }
  }

  def simplePipeline(id: String) = EvaluationDeploymentRequest(
    workload = EvaluationWorkload(
      id = id,
      containers = Vector(
        WorkloadContainer(
          container = Container(
            image = "mantikai/bridge.select"
          )
        )
      ),
      sessions = Vector(
        WorkloadSession(
          containerId = 0,
          mnpSessionId = "select",
          mantikHeader = simpleSelect,
          payload = None,
          // Important: The pipeline controller is using Mantik Bundles
          inputContentTypes = Vector("application/x-mantik-bundle"),
          outputContentTypes = Vector("application/x-mantik-bundle")
        )
      ),
      sources = Vector.empty,
      sinks = Vector.empty,
      links = Vector()
    ),
    input = WorkloadSessionPort(0, 0),
    output = WorkloadSessionPort(0, 0),
    inputDataType = Json
      .obj(
        "columns" -> Json.obj(
          "x" -> Json.fromString("int32"),
          "y" -> Json.fromString("string")
        )
      ),
    outputDataType = Json
      .obj(
        "columns" -> Json.obj(
          "a" -> Json.fromString("int32"),
          "b" -> Json.fromString("string")
        )
      ),
    ingressName = Some("select1"),
    nameHint = Some("select1")
  )

  "simple pipeline deployments" should "work" in withExecutor { executor =>
    val pipe1 = simplePipeline("pipeline1")
    val response = await(executor.startDeployment(pipe1))

    eventually {
      val applyUrl = response.externalUrl.get + "/apply"
      val httpPostResponse = httpPost(applyUrl, "application/json", ByteString.fromString(sampleData.toString()))
      parser.parse(httpPostResponse.utf8String).forceRight shouldBe sampleData
    }

    val typeUrl = response.externalUrl.get + "/type"

    httpGet(typeUrl)._1.status.intValue() shouldBe 200

    val elements = await(executor.list())
    val element = elements.elements.filter(_.id == "pipeline1")
    element.size shouldBe 1
    // The element may be gone
    element.foreach(_.status shouldBe WorkloadStatus.Running)

    withClue("It should be possible to remove") {
      await(executor.removeDeployment(pipe1.workload.id))
      Thread.sleep(2000) // Some times the request time out if the service is killed while being deleted
      eventually {
        val response = httpGet(typeUrl)._1.status.intValue()
        logger.info(s"Going down: ${response}")
        response shouldBe 404
      }
    }
  }
}
