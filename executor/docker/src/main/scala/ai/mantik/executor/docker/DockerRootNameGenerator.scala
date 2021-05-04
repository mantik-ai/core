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

import ai.mantik.executor.docker
import ai.mantik.executor.docker.api.DockerClient

import scala.concurrent.{ExecutionContext, Future}

/** Helper for generating root names. Genrates many names at once to not talk to docker too often. */
class DockerRootNameGenerator(dockerClient: DockerClient)(implicit ec: ExecutionContext)
    extends ReservedNameGenerator(
      new docker.DockerRootNameGenerator.DockerBackend(dockerClient)
    )

object DockerRootNameGenerator {

  class DockerBackend(dockerClient: DockerClient)(implicit val executionContext: ExecutionContext)
      extends ReservedNameGenerator.Backend {

    override def lookupAlreadyTaken(): Future[Set[String]] = {
      dockerClient.listContainers(true).map { containers =>
        val rawSet = containers.flatMap(_.Names).toSet
        rawSet.map(_.stripPrefix("/"))
      }
    }

    override def generate(prefix: String, taken: Set[String]): String = {
      val maxLength = 5
      var i = 1
      while (i < maxLength) {
        val candidate = DockerNameGenerator.generateRootName(i, prefix)
        if (
          !taken.exists { usedName =>
            usedName.startsWith(candidate)
          }
        ) {
          return candidate
        }
        i += 1
      }
      throw new IllegalStateException("Could not generate a name")
    }
  }

}
