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

import java.time.Clock
import ai.mantik.componently.AkkaRuntime
import ai.mantik.executor.{Executor, ExecutorForIntegrationTest}
import com.typesafe.config.{Config => TypesafeConfig}

/** An embedded executor for integration tests. */
class KubernetesExecutorForIntegrationTests(config: TypesafeConfig)(implicit akkaRuntime: AkkaRuntime)
    extends ExecutorForIntegrationTest {

  val executorConfig = Config.fromTypesafeConfig(config)
  implicit val clock = Clock.systemUTC()
  import ai.mantik.componently.AkkaHelper._
  val kubernetesClient = skuber.k8sInit
  private var _executor: Option[KubernetesExecutor] = None

  override def executor: Executor = {
    _executor.getOrElse {
      throw new IllegalStateException(s"Executor not yet started")
    }
  }

  override def start(): Unit = {
    val k8sOperations = new K8sOperations(executorConfig, kubernetesClient)
    val executor = new KubernetesExecutor(executorConfig, k8sOperations)
    _executor = Some(executor)
  }

  def stop(): Unit = {
    kubernetesClient.close
  }

  override def scrap(): Unit = {
    if (_executor.nonEmpty) {
      throw new IllegalStateException(s"Executor already started")
    }
    val cleaner = new KubernetesCleaner(kubernetesClient, executorConfig)
    cleaner.deleteKubernetesContent()
  }
}
