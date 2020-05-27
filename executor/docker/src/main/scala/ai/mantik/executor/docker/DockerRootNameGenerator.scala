package ai.mantik.executor.docker

import ai.mantik.executor.docker.api.DockerClient
import com.typesafe.scalalogging.Logger

import scala.collection.immutable.Queue
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.control.NonFatal

/** Helper for generating root names. Genrates many names at once to not talk to docker too often. */
class DockerRootNameGenerator(dockerClient: DockerClient)(implicit ec: ExecutionContext) {
  private val logger = Logger(getClass)

  private val QueueLength: Int = 10
  private var nextNames: Queue[String] = Queue.empty
  private var nextNameUpdate: Option[Future[Unit]] = None
  private var reserved: Set[String] = Set.empty
  private object lock

  /** Generate a new root name. */
  def generateRootName(): Future[String] = {
    reserve { name =>
      Future.successful(name)
    }
  }

  /** Reserve a root name, and reserve it for the time of the user defined function. */
  def reserve[T](user: String => Future[T]): Future[T] = {
    val got: String = lock.synchronized {
      if (nextNames.nonEmpty) {
        val nextName = nextNames.head
        nextNames = nextNames.tail

        reserved += nextName
        logger.info(s"New name: ${nextName}")
        nextName
      } else {
        val updateFunction = nextNameUpdate match {
          case Some(defined) =>
            defined
          case None =>
            val value = generateNextNames()
            nextNameUpdate = Some(value)
            value
        }
        return updateFunction.flatMap { _ =>
          reserve(user)
        }
      }
    }
    runReserved(got, user)
  }

  private def runReserved[T](name: String, user: String => Future[T]): Future[T] = {
    try {
      user(name).andThen {
        case _ =>
          lock.synchronized {
            reserved -= name
          }
      }
    } catch {
      case NonFatal(e) =>
        lock.synchronized {
          reserved -= name
        }
        throw e
    }
  }

  private def generateNextNames(): Future[Unit] = {
    dockerClient.listContainers(true).map { containers =>
      val names = containers.flatMap(_.Names).toSet
      updateNext(names)
    }
  }

  private def updateNext(dockerUsed: Set[String]): Unit = {
    for (_ <- 0 until QueueLength) {
      addSingeName(dockerUsed)
    }
  }

  private def addSingeName(dockerUsed: Set[String]): Unit = {
    val allUsed = lock.synchronized {
      dockerUsed ++ nextNames ++ reserved
    }
    val nextName = generateSingleName(allUsed)
    lock.synchronized {
      nextNames = nextNames :+ nextName
    }
  }

  private def generateSingleName(usedNames: Set[String]): String = {
    val maxLength = 5
    var i = 1
    while (i < maxLength) {
      val candidate = NameGenerator.generateRootName(i)
      if (!usedNames.exists { usedName =>
        usedName.startsWith(candidate) && usedName.startsWith("/" + candidate)
      }) {
        return candidate
      }
      i += 1
    }
    throw new IllegalStateException("Could not generate a name")
  }
}
