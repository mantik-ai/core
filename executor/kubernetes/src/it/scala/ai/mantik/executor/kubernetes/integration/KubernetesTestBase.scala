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
package ai.mantik.executor.kubernetes.integration

import java.time.Clock

import ai.mantik.componently.AkkaRuntime
import ai.mantik.executor.kubernetes.{Config, KubernetesCleaner}
import ai.mantik.testutils.{AkkaSupport, TestBase}
import com.typesafe.config.ConfigFactory
import org.scalatest.time.{Millis, Span}
import skuber.api.client.KubernetesClient

import scala.concurrent.duration._

abstract class KubernetesTestBase extends TestBase with AkkaSupport {

  override protected val timeout: FiniteDuration = 30.seconds

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(
    timeout = scaled(Span(30000, Millis)),
    interval = scaled(Span(500, Millis))
  )

  override lazy val typesafeConfig = ConfigFactory.load("systemtest.conf")
  val config = Config.fromTypesafeConfig(typesafeConfig)

  protected var _kubernetesClient: KubernetesClient = _

  protected implicit lazy val akkaRuntime = AkkaRuntime.fromRunning(typesafeConfig)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    _kubernetesClient = skuber.k8sInit(typesafeConfig)(actorSystem)
    implicit val clock = Clock.systemUTC()
    val cleaner = new KubernetesCleaner(_kubernetesClient, config)
    cleaner.deleteKubernetesContent()
  }

  override protected def afterAll(): Unit = {
    _kubernetesClient.close
    super.afterAll()
  }

  protected trait Env {
    val kubernetesClient = _kubernetesClient
  }
}
