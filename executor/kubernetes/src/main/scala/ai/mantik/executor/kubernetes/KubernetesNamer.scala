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

import java.util.UUID
import org.slf4j.LoggerFactory

import java.util.regex.Pattern
import scala.annotation.tailrec
import scala.collection.mutable

/** Helper for naming kubernetes resources, as kubernetes has tight restrictions */
class KubernetesNamer(id: String, superPrefix: String) {

  private val prefix =
    s"${superPrefix}${KubernetesNamer.escapeNodeName(id)}-" // Kubernetes prefix must start with alphanumeric

  val jobName = prefix + "job"

  val replicaSetName = prefix + "rs"

  val serviceName = prefix + "service"

  val configName = prefix + "config"

  val pullSecretName = prefix + "pullsecret"

  private val podNames = mutable.Map.empty[String, String]
  private val usedNames = mutable.Set.empty[String]
  private object lock

  usedNames.add(jobName)
  usedNames.add(configName)
  usedNames.add(pullSecretName)
  usedNames.add(replicaSetName)
  usedNames.add(serviceName)

  def podName(nodeName: String): String = {
    lock.synchronized {
      podNames.get(nodeName) match {
        case Some(found) => return found
        case None =>
          val escaped = KubernetesNamer.escapeNodeName(nodeName)
          val candidate = prefix + escaped
          val toUse = if (usedNames.contains(candidate)) {
            findSuitable(escaped, 0)
          } else {
            candidate
          }
          usedNames.add(toUse)
          podNames.put(nodeName, toUse)
          return toUse
      }
    }
  }

  @tailrec
  private def findSuitable(escapedString: String, count: Int): String = {
    val candidate = if (count > 10) {
      // choose arbitrary name
      prefix + UUID.randomUUID().toString
    } else {
      prefix + escapedString + count.toString
    }
    if (usedNames.contains(candidate)) {
      findSuitable(escapedString, count + 1)
    } else {
      candidate
    }
  }
}

object KubernetesNamer {

  private val logger = LoggerFactory.getLogger(getClass)

  def escapeNodeName(nodeName: String): String = {
    val filtered = nodeName.toLowerCase
      .replace('.', '-')
      .filter { c =>
        (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-'
      }
    if (filtered.isEmpty || (filtered.last == '-')) {
      filtered + "N"
    } else {
      filtered
    }
  }

  /** Prefix to be for @ and an already valid label (Mantik's ItemIds) */
  val AtPrefix = "A"

  /** Suffix to be used for @ (Mantik Items can end with slashes) */
  val AtSuffix = "A"

  /** Prefix to be used if the rest of the string is a valid label. */
  val ValidPrefix = "B"

  /** Prefix to be used for custom character encoding. */
  val CustomPrefix = "C"

  /** Suffix fo be used for custom character encoding */
  val CustomSuffix = "C"

  /** Encode a label in a way that kubernetes accepts it.
    * Note: if the label is too long, it's possible that Kubernetes stil won't accept it.
    */
  def encodeLabelValue(value: String): String = {
    if (value.isEmpty) {
      ""
    } else if (isValidLabel(value)) {
      ValidPrefix + value
    } else {
      if (value.startsWith("@")) {
        val candidate = AtPrefix + value.drop(1) + AtSuffix
        if (isValidLabel(candidate)) {
          return candidate
        }
      }
      CustomPrefix + customLabelEncoding(value) + CustomSuffix
    }
  }

  /** Encode a label value in a way that Kubernetes accepts it. Can be decoded by [[decodeLabelValue]]. */
  private def customLabelEncoding(value: String): String = {
    // Start with CustomPrefix
    // Everything which is a Special Character is encoded using _ and code
    // _ is encoded as __
    // If the last char is not valid, use a trailing _

    val resultBuilder = StringBuilder.newBuilder
    value.foreach {
      case c if middleAllowed(c) => resultBuilder += c
      case '_'                   => resultBuilder ++= "__"
      case other                 => resultBuilder ++= "_" + charToHex(other)
    }
    resultBuilder.result
  }

  /** The character is allowed at the start or end in custom encoding */
  private def startOrEndAllowed(b: Char): Boolean = {
    (b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z') || (b >= '0' && b <= '9')
  }

  /** The character is allowed at the middle a label. */
  private def middleAllowed(b: Char): Boolean = {
    startOrEndAllowed(b) || (b == '.') || (b == '-')
  }

  private val validLabelRegex = "(([A-Za-z0-9][-A-Za-z0-9_.]*)?[A-Za-z0-9])?".r

  /** Check if something is a valid label. */
  def isValidLabel(s: String): Boolean = {
    validLabelRegex.pattern.matcher(s).matches() && s.length <= 63
  }

  /**
    * Decodes label values from [[encodeLabelValue]].
    * Note: it can fail on invalid values.
    */
  def decodeLabelValue(value: String): String = {
    if (value.isEmpty) {
      ""
    } else if (value.startsWith(ValidPrefix)) {
      value.stripPrefix(ValidPrefix)
    } else if (value.startsWith(AtPrefix)) {
      "@" + value.stripPrefix(AtPrefix).stripSuffix(AtSuffix)
    } else if (value.startsWith(CustomPrefix)) {
      decodeCustomLabel(value.stripPrefix(CustomPrefix).stripSuffix(CustomSuffix))
    } else {
      throw new IllegalArgumentException(s"Invalid label prefix in ${value}")
    }
  }

  private def decodeCustomLabel(value: String): String = {
    val resultBuilder = StringBuilder.newBuilder
    var i = 0
    while (i < value.length) {
      val c = value(i)
      c match {
        case '_' if i == value.length - 1 =>
        // Trailing underline, not important
        case '_' if value.substring(i + 1, i + 2) == "_" =>
          //  Another underline
          resultBuilder += '_'
          i += 1
        case '_' =>
          // Arbitrary character
          val hex = value.substring(i + 1, i + 5)
          val dec = charFromHex(hex)
          resultBuilder += dec
          i += 4
        case o =>
          resultBuilder += o
      }
      i += 1
    }

    resultBuilder.result()
  }

  /**
    * Like [[decodeLabelValue]] but catches exceptions and returns the original string
    * if it fails. Note: the decoding result can be something else in case of a bug.
    */
  def decodeLabelValueNoCrashing(value: String): String = {
    try {
      decodeLabelValue(value)
    } catch {
      case e: Exception =>
        logger.warn(s"Error on decoding ${value}, returning original value", e)
        value
    }
  }

  private def charToHex(value: Char): String = {
    "%04x".format(value.toInt)
  }

  private def charFromHex(value: String): Char = {
    java.lang.Integer.parseInt(value, 16).toChar
  }

}
