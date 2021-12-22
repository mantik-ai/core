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
package ai.mantik.executor

import ai.mantik.componently.AkkaRuntime
import ai.mantik.componently.di.ConfigurableDependencies
import com.google.inject.AbstractModule

class ExecutorModule(implicit akkaRuntime: AkkaRuntime) extends AbstractModule {
  override def configure(): Unit = {
    install(new ExecutorModule.PayloadProviderModule())
    install(new ExecutorModule.ExecutorFileStorageModule())
    install(new ExecutorModule.ExecutorBackendModule())
  }
}

object ExecutorModule {

  private class ExecutorBackendModule(implicit akkaRuntime: AkkaRuntime) extends ConfigurableDependencies {

    override protected val configKey: String = "mantik.executor.type"

    private val kubernetesType = "kubernetes"
    private val dockerType = "docker"

    override protected def variants: Seq[Classes] = Seq(
      variation[Executor](
        kubernetesType -> provider("ai.mantik.executor.kubernetes.KubernetesExecutorProvider", asSingleton = true),
        dockerType -> provider("ai.mantik.executor.docker.DockerExecutorProvider", asSingleton = true)
      )
    )
  }

  private class PayloadProviderModule(implicit akkaRuntime: AkkaRuntime) extends ConfigurableDependencies {
    override protected val configKey: String = "mantik.executor.payloadProvider"

    val executorVariant = "executor"
    val localVariant = "local"

    override protected def variants: Seq[Classes] = Seq(
      variation[PayloadProvider](
        executorVariant -> "ai.mantik.executor.common.workerexec.ExecutorFileStoragePayloadProvider",
        localVariant -> "ai.mantik.executor.common.workerexec.LocalServerPayloadProvider"
      )
    )
  }

  private class ExecutorFileStorageModule(implicit akkaRuntime: AkkaRuntime) extends ConfigurableDependencies {
    override protected val configKey: String = "mantik.executor.storageType"

    private val s3Type = "s3"

    override protected def variants: Seq[Classes] = Seq(
      variation[ExecutorFileStorage](
        s3Type -> "ai.mantik.executor.s3storage.S3Storage"
      )
    )
  }
}
