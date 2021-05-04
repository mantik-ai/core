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

import java.util.Locale

import scala.util.Random

object DockerNameGenerator {
  val DefaultPrefix = "mantik"
  val VolumeSuffix = "-data"
  val PayloadProviderSuffix = "-pp"
  val RootNameNodeNameSeparator = "-"

  /**
    * A Named node.
    * @param containerName the name of the container
    * @param internalHostName the host name as seen from coordinator
    */
  case class NodeName(
      containerName: String,
      internalHostName: String
  ) {

    /** Returns the name for the payload provider container */
    def payloadProviderName: String = containerName + PayloadProviderSuffix
  }

  /** Characters used for root name generation. */
  private val RootNameCharacters = (('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9'))
    .diff(List('I', 'l', 'O', '0'))

  /** Characters allowed for node names. */
  private val DockerValidChars = (('a' to 'z') ++ ('0' to '9') :+ '-')

  /**
    * Escape a node name to be used in docker.
    * Note: this is one-way operation, illegal characters will be removed.
    */
  def escapeDockerName(name: String): String = {
    val lc = name.toLowerCase(Locale.US).replace('.', '-')
    lc.filter(DockerValidChars.contains)
  }

  /** Generate a root name. This is not functional as it is using Randomness */
  def generateRootName(length: Int, prefix: String = DefaultPrefix): String = {
    require(length >= 0)
    if (length == 0) {
      return prefix
    }
    val randomPart = for (_ <- 0 until length) yield {
      val id = Random.nextInt(RootNameCharacters.length)
      RootNameCharacters(id)
    }
    escapeDockerName(prefix) + randomPart.mkString
  }

}
