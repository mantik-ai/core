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
package ai.mantik.executor.model

import ai.mantik.testutils.TestBase
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import io.circe.Json

import scala.concurrent.Future

class EvaluationWorkloadSpec extends TestBase {
  val workload = EvaluationWorkload(
    id = "id",
    sessions = Vector(
      WorkloadSession(0, "a", Json.Null, None, Vector(), Vector("a")),
      WorkloadSession(0, "b", Json.Null, None, Vector("b", "c"), Vector("c")),
      WorkloadSession(0, "c", Json.Null, None, Vector("d"), Vector("e", "f"))
    ),
    // Irrelevant for test or updated later
    containers = Vector.empty,
    sources = Vector(
      DataSource.constant(ByteString(), "")
    ),
    sinks = Vector(
      DataSink(Sink.seq[ByteString].mapMaterializedValue(_ => Future.successful(100L)))
    ),
    links = Vector(
      Link(Left(0), Right(WorkloadSessionPort(1, 0))),
      Link(Right(WorkloadSessionPort(0, 0)), Right(WorkloadSessionPort(1, 0))),
      Link(Right(WorkloadSessionPort(1, 0)), Right(WorkloadSessionPort(2, 0))),
      Link(Right(WorkloadSessionPort(2, 1)), Left(0))
    )
  )

  "unconnectedInputs" should "work" in {
    workload.unconnectedInputs shouldBe Vector(WorkloadSessionPort(1, 1))
  }

  "unconnectedOutputs" should "work" in {
    workload.unconnectedOutputs shouldBe Vector(WorkloadSessionPort(2, 0))
  }
}
