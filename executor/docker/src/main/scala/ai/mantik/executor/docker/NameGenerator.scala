package ai.mantik.executor.docker

import java.util.Locale

import ai.mantik.executor.docker.NameGenerator.NodeName
import cats.data.State

import scala.util.Random

/** Functional Name generator. */
case class NameGenerator(
    rootName: String,
    usedNames: Set[String] = Set.empty,
    mapping: Map[String, String] = Map.empty
) {

  /** Generate a node name, returns a new state. */
  def nodeName(graphName: String): (NameGenerator, NodeName) = {
    mapping.get(graphName) match {
      case Some(existing) => this -> makeNodeName(existing)
      case None =>
        val matching = findMatchingName(graphName)
        val newState = copy(
          usedNames = usedNames + matching,
          mapping = mapping + (graphName -> matching)
        )
        newState -> makeNodeName(matching)
    }
  }

  private def makeNodeName(shortName: String): NodeName = {
    NodeName(
      containerName = rootName + NameGenerator.RootNameNodeNameSeparator + shortName,
      internalHostName = shortName
    )
  }

  private def findMatchingName(nodeName: String): String = {
    val escaped = NameGenerator.escapeDockerName(nodeName)
    // we call Nodes via DNS Resolution and pure numbers don't do that well
    val mustStartWithLetter = if (escaped.isEmpty || escaped.head.isDigit) {
      "n" + escaped
    } else {
      escaped
    }
    val candidate1 = mustStartWithLetter
    if (escaped.nonEmpty && !usedNames.contains(candidate1)) {
      return candidate1
    }
    for (i <- 0 until 1000) {
      val candidate = mustStartWithLetter + i.toString
      if (!usedNames.contains(candidate)) {
        return candidate
      }
    }
    throw new IllegalStateException("Could not generate a name")
  }
}

object NameGenerator {
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

  /** Run a method on the name generator using f and execute method g on it. */
  def stateChange[E, X](f: NameGenerator => (NameGenerator, E))(g: E => X): State[NameGenerator, X] = {
    State { generator: NameGenerator =>
      val (newNameGenerator, extracted) = f(generator)
      newNameGenerator -> g(extracted)
    }
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
