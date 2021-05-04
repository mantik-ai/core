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
package ai.mantik.executor.docker

case class IngressConverter(
    config: DockerExecutorConfig,
    dockerHost: String,
    ingressName: String
) {

  /** Creates the container definition with activated ingress. */
  def containerDefinitionWithIngress(containerDefinition: ContainerDefinition): ContainerDefinition = {
    containerDefinition.addLabels(
      ingressLabels(containerDefinition.mainPort.getOrElse(0))
    )
  }

  /** Creates necessary Labels to make ingress working */
  private def ingressLabels(containerPort: Int): Map[String, String] = {
    config.ingress.labels.mapValues { labelValue =>
      interpolateIngressString(labelValue, containerPort)
    } + (DockerConstants.IngressLabelName -> ingressName)
  }

  /** Returns the ingress URL */
  def ingressUrl: String = {
    interpolateIngressString(config.ingress.remoteUrl, containerPort = 0)
  }

  private def interpolateIngressString(in: String, containerPort: Int): String = {
    in
      .replace("${name}", ingressName)
      .replace("${dockerHost}", dockerHost)
      .replace("${traefikPort}", config.ingress.traefikPort.toString)
      .replace("${port}", containerPort.toString)
  }

}
