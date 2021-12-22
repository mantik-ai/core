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

import io.circe.Json

/**
  * Request for a permanent deployment of an evaluation.
  *
  * This is made for deploying inferences via HTTP and not supported on all Executors.
  * (Executors may have further restrictions)
  *
  * This evaluations may have no Sinks and one unbound input and unbound output.
  *
  * @param workload the workload definition
  * @param input where input of one task flows
  * @param output where the output is taken from
  * @param inputDataType Mantik DS Input Datatype
  * @param outputDataType Mantik DS Output Datatype
  * @param ingressName hint for an ingress name
  * @param nameHint name hint for the pipeline
  */
case class EvaluationDeploymentRequest(
    workload: EvaluationWorkload,
    input: WorkloadSessionPort,
    output: WorkloadSessionPort,
    inputDataType: Json,
    outputDataType: Json,
    ingressName: Option[String] = None,
    nameHint: Option[String] = None
) {

  /** Returns validation errors. */
  def violations: Seq[String] = {
    val sinkViolations: Seq[String] = if (workload.sinks.nonEmpty) {
      Seq("A Deployment workload may have no sinks")
    } else {
      Nil
    }
    val wrongInputViolations = if (workload.unconnectedInputs != Vector(input)) {
      Seq(s"Unconnected inputs ${workload.unconnectedInputs} do not reflect given input ${input}")
    } else { Nil }
    val wrongOutputViolations = if (workload.unconnectedOutputs != Vector(output)) {
      Seq(s"Unconnected outputs ${workload.unconnectedOutputs} do not reflect given input ${output}")
    } else { Nil }
    sinkViolations ++ wrongInputViolations ++ wrongOutputViolations
  }
}

/**
  * Response for evaluation deployments.
  * @param internalUrl Executor-Internal URL under which the deployment is reachable from inside Executor Space.
  * @param externalUrl URL under which the deployment is reachable from the outside.
  */
case class EvaluationDeploymentResponse(
    internalUrl: String,
    externalUrl: Option[String] = None
)
