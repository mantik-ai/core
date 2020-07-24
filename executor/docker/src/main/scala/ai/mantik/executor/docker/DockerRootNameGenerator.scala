package ai.mantik.executor.docker

import ai.mantik.executor.docker.api.DockerClient

import scala.concurrent.{ ExecutionContext, Future }

/** Helper for generating root names. Genrates many names at once to not talk to docker too often. */
class DockerRootNameGenerator(dockerClient: DockerClient)(implicit ec: ExecutionContext) extends ReservedNameGenerator(
  new DockerRootNameGenerator.DockerBunchGenerator(dockerClient)
)

object DockerRootNameGenerator {

  class DockerBunchGenerator(dockerClient: DockerClient)(implicit val executionContext: ExecutionContext) extends ReservedNameGenerator.PrelistedBunchGenerator {

    override protected def lookupAlreadyTaken(): Future[Set[String]] = {
      dockerClient.listContainers(true).map { containers =>
        containers.flatMap(_.Names).toSet
      }
    }

    override protected def generateSingle(taken: Set[String]): String = {
      val maxLength = 5
      var i = 1
      while (i < maxLength) {
        val candidate = NameGenerator.generateRootName(i)
        if (!taken.exists { usedName =>
          usedName.startsWith(candidate) || usedName.startsWith("/" + candidate)
        }) {
          return candidate
        }
        i += 1
      }
      throw new IllegalStateException("Could not generate a name")
    }
  }

}
