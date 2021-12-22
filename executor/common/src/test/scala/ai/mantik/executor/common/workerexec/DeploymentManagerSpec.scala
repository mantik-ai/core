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
package ai.mantik.executor.common.workerexec

import ai.mantik.executor.Errors
import ai.mantik.executor.model.{
  EvaluationDeploymentRequest,
  EvaluationWorkload,
  Link,
  WorkloadSession,
  WorkloadSessionPort
}
import ai.mantik.mnp.{MnpSessionPortUrl, MnpSessionUrl}
import ai.mantik.testutils.TestBase
import io.circe.Json

class DeploymentManagerSpec extends TestBase {
  val baseWorkload = EvaluationWorkload(
    id = "id",
    sessions = Vector(
      WorkloadSession(0, "a", Json.Null, None, Vector(), Vector()),
      WorkloadSession(0, "b", Json.Null, None, Vector(), Vector()),
      WorkloadSession(0, "c", Json.Null, None, Vector(), Vector())
    ),
    // Irrelevant for test or updated later
    containers = Vector.empty,
    sources = Vector.empty,
    sinks = Vector.empty,
    links = Vector.empty
  )

  val baseSessions = Vector(
    MnpSessionUrl.build("host1", "session1"),
    MnpSessionUrl.build("host2", "session2"),
    MnpSessionUrl.build("host3", "session3")
  )

  "buildPipelineDefinition" should "work for single case" in {
    val request = EvaluationDeploymentRequest(
      baseWorkload,
      input = WorkloadSessionPort(0, 0),
      output = WorkloadSessionPort(0, 0),
      inputDataType = Json.fromString("int32"),
      outputDataType = Json.fromString("float32"),
      ingressName = Some("foo"),
      nameHint = Some("Nice Pipeline")
    )

    DeploymentManager.buildPipelineDefinition(
      request,
      baseSessions
    ) shouldBe PipelineRuntimeDefinition(
      name = "Nice Pipeline",
      steps = Vector(
        PipelineRuntimeDefinition.Step(
          s"mnp://host1/session1"
        )
      ),
      inputType = Json.fromString("int32"),
      outputType = Json.fromString("float32")
    )
  }

  it should "work for more steps" in {
    val request = EvaluationDeploymentRequest(
      baseWorkload.copy(
        links = Vector(
          Link(Right(WorkloadSessionPort(2, 0)), Right(WorkloadSessionPort(1, 0))),
          Link(Right(WorkloadSessionPort(1, 0)), Right(WorkloadSessionPort(0, 0)))
        )
      ),
      input = WorkloadSessionPort(2, 0),
      output = WorkloadSessionPort(0, 0),
      inputDataType = Json.fromString("int32"),
      outputDataType = Json.fromString("float32"),
      ingressName = Some("foo")
    )

    DeploymentManager.buildPipelineDefinition(
      request,
      baseSessions
    ) shouldBe PipelineRuntimeDefinition(
      name = "id",
      steps = Vector(
        PipelineRuntimeDefinition.Step(s"mnp://host3/session3"),
        PipelineRuntimeDefinition.Step(s"mnp://host2/session2"),
        PipelineRuntimeDefinition.Step(s"mnp://host1/session1")
      ),
      inputType = Json.fromString("int32"),
      outputType = Json.fromString("float32")
    )
  }

  it should "detect loops" in {
    val request = EvaluationDeploymentRequest(
      baseWorkload.copy(
        links = Vector(
          Link(Right(WorkloadSessionPort(0, 0)), Right(WorkloadSessionPort(1, 0))),
          Link(Right(WorkloadSessionPort(1, 0)), Right(WorkloadSessionPort(0, 0)))
        )
      ),
      input = WorkloadSessionPort(0, 0),
      output = WorkloadSessionPort(2, 0),
      inputDataType = Json.fromString("int32"),
      outputDataType = Json.fromString("float32"),
      ingressName = Some("foo")
    )
    intercept[Errors.BadRequestException](
      DeploymentManager.buildPipelineDefinition(
        request,
        baseSessions
      )
    ).getMessage should include("Loop")
  }

  it should "detect unsolveable deployments" in {
    val request = EvaluationDeploymentRequest(
      baseWorkload.copy(
        links = Vector(
          Link(Right(WorkloadSessionPort(0, 0)), Right(WorkloadSessionPort(1, 0)))
        )
      ),
      input = WorkloadSessionPort(0, 0),
      output = WorkloadSessionPort(2, 0),
      inputDataType = Json.fromString("int32"),
      outputDataType = Json.fromString("float32"),
      ingressName = Some("foo")
    )
    intercept[Errors.BadRequestException](
      DeploymentManager.buildPipelineDefinition(
        request,
        baseSessions
      )
    ).getMessage should include("Could not figure out")
  }
}
