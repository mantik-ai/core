package ai.mantik.executor.impl

import java.util.UUID

import scala.annotation.tailrec
import scala.collection.mutable

/** Helper for naming kubernetes resources, as kubernetes has tight restrictions */
class KubernetesNamer(id: String, superPrefix: String = "job-") {

  private val prefix = s"${superPrefix}${escapeNodeName(id)}-" // Kubernetes prefix must start with alphanumeric

  val jobName = prefix + "job"

  val configName = prefix + "config"

  private val podNames = mutable.Map.empty[String, String]
  private val usedNames = mutable.Set.empty[String]
  private object lock

  usedNames.add(jobName)
  usedNames.add(configName)

  def podName(nodeName: String): String = {
    lock.synchronized {
      podNames.get(nodeName) match {
        case Some(found) => return found
        case None =>
          val escaped = escapeNodeName(nodeName)
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

  private def escapeNodeName(nodeName: String): String = {
    val filtered = nodeName.toLowerCase.filter { c =>
      (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-'
    }
    if (filtered.isEmpty || (filtered.last == '-')) {
      filtered + "N"
    } else {
      filtered
    }
  }
}
