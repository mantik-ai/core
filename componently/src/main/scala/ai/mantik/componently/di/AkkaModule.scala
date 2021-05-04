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
package ai.mantik.componently.di

import ai.mantik.componently.AkkaRuntime
import com.google.inject.AbstractModule
import com.typesafe.config.Config

/** A module which registers the Akka Runtime for DI. */
class AkkaModule(implicit akkaRuntime: AkkaRuntime) extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[AkkaRuntime]).toInstance(akkaRuntime)
    bind(classOf[Config]).toInstance(akkaRuntime.config)
  }
}
