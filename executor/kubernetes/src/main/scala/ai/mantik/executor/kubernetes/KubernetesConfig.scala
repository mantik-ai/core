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
package ai.mantik.executor.kubernetes

import com.typesafe.config.{Config => TypesafeConfig}

import scala.concurrent.duration.{Duration, FiniteDuration}
import ai.mantik.componently.utils.ConfigExtensions._

/**
  * Kubernetes part of [[Config]].
  *
  * @param namespacePrefix prefix to use for namespace creation
  * @param retryTimes how often something is retried if kubernetes asks for.
  * @param podPullImageTimeout timeout, after which pods are killed when they can't find their image.
  * @param checkPodInterval how often the pods are checked.
  * @param defaultTimeout when a regular operation times out
  * @param defaultRetryInterval after which period something is retried again.
  */
case class KubernetesConfig(
    namespacePrefix: String,
    retryTimes: Int,
    podPullImageTimeout: Duration,
    checkPodInterval: Duration,
    defaultTimeout: FiniteDuration,
    defaultRetryInterval: FiniteDuration,
    deletionGracePeriod: FiniteDuration,
    ingressSubPath: Option[String],
    ingressRemoteUrl: String,
    ingressAnnotations: Map[String, String],
    nodeAddress: Option[String]
)

object KubernetesConfig {
  def fromTypesafe(c: TypesafeConfig): KubernetesConfig = {
    KubernetesConfig(
      namespacePrefix = c.getString("behavior.namespacePrefix"),
      retryTimes = c.getInt("behavior.retryTimes"),
      podPullImageTimeout = c.getFiniteDuration("behavior.podPullImageTimeout"),
      checkPodInterval = c.getFiniteDuration("behavior.checkPodInterval"),
      defaultTimeout = c.getFiniteDuration("behavior.defaultTimeout"),
      defaultRetryInterval = c.getFiniteDuration("behavior.retryInterval"),
      deletionGracePeriod = c.getFiniteDuration("behavior.deletionGracePeriod"),
      ingressSubPath = c.getOptionalString("ingress.subPath"),
      ingressRemoteUrl = c.getString("ingress.remoteUrl"),
      ingressAnnotations = c.getKeyValueMap("ingress.annotations"),
      nodeAddress = c.getOptionalString("nodeAddress")
    )
  }
}
