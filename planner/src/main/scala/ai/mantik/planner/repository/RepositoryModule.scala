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
package ai.mantik.planner.repository

import ai.mantik.componently.AkkaRuntime
import ai.mantik.componently.di.ConfigurableDependencies
import ai.mantik.planner.repository.impl.{LocalFileRepository, LocalRepository, TempFileRepository, TempRepository}

class RepositoryModule(implicit akkaRuntime: AkkaRuntime) extends ConfigurableDependencies {

  override protected val configKey: String = "mantik.repository.type"

  val TempVariant = "temp"
  val LocalVariant = "local"

  override protected def variants: Seq[Classes] = Seq(
    variation[Repository](
      TempVariant -> classOf[TempRepository],
      LocalVariant -> classOf[LocalRepository]
    ),
    variation[FileRepository](
      TempVariant -> classOf[TempFileRepository],
      LocalVariant -> classOf[LocalFileRepository]
    )
  )
}
