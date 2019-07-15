package ai.mantik.executor.kubernetes

import java.util.UUID

import org.slf4j.LoggerFactory

import scala.annotation.tailrec
import scala.collection.mutable

/** Helper for naming kubernetes resources, as kubernetes has tight restrictions */
class KubernetesNamer(id: String, superPrefix: String) {

  private val prefix = s"${superPrefix}${KubernetesNamer.escapeNodeName(id)}-" // Kubernetes prefix must start with alphanumeric

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
    val filtered = nodeName.toLowerCase.filter { c =>
      (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-'
    }
    if (filtered.isEmpty || (filtered.last == '-')) {
      filtered + "N"
    } else {
      filtered
    }
  }

  /** Encode a label value in a way that Kubernetes accepts it. Can be decoded by [[decodeLabelValue]]. */
  def encodeLabelValue(value: String): String = {
    // Valid according to error message '(([A-Za-z0-9][-A-Za-z0-9_.]*)?[A-Za-z0-9])?'

    // Motivation: Most characters should stay be the same, while making it possible to encode all characters.

    // Using '_' as Escape Character
    // If first character is to be escaped, we use Z as start character.
    // The first z is translated into "Z_"

    if (value.isEmpty) {
      return ""
    }
    val resultBuilder = StringBuilder.newBuilder
    val first = value.head

    first match {
      case 'Z'                  => resultBuilder ++= "Z_"
      case x if firstAllowed(x) => resultBuilder += x
      case other =>
        resultBuilder += 'Z'
        resultBuilder ++= charToHex(other)
    }

    value.tail.foreach {
      case x if restAllowed(x) => resultBuilder += x
      case other =>
        resultBuilder += '_'
        resultBuilder ++= charToHex(other)
    }

    resultBuilder.result()
  }

  private def firstAllowed(b: Char): Boolean = {
    (b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z') || (b >= '0' && b <= '9')
  }

  private def restAllowed(b: Char): Boolean = {
    firstAllowed(b) || (b == '.') || (b == '-')
  }

  /**
   * Decodes label values from [[encodeLabelValue]].
   * Note: it can fail on invalid values.
   */
  def decodeLabelValue(value: String): String = {
    if (value.isEmpty) {
      return ""
    }
    val resultBuilder = StringBuilder.newBuilder
    val afterFirst = if (value.startsWith("Z_")) {
      resultBuilder += 'Z'
      value.drop(2)
    } else if (value.startsWith("Z")) {
      val hex = value.drop(1).take(4)
      resultBuilder += charFromHex(hex)
      value.drop(5)
    } else {
      resultBuilder += value.head
      value.drop(1)
    }

    var i = 0
    while (i < afterFirst.length) {
      val c = afterFirst(i)
      c match {
        case '_' =>
          // decode
          val hex = afterFirst.substring(i + 1, i + 5)
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
   *
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
