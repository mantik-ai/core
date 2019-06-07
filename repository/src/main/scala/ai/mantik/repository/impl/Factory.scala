package ai.mantik.repository.impl

import java.time.Clock

import ai.mantik.repository.{ Errors, FileRepository, Repository }
import akka.actor.ActorSystem
import akka.stream.Materializer
import com.typesafe.config.Config
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext

/**
 * Factory for [[Repository]] and [[FileRepository]].
 *
 * In Future this should be done using DI Ticket #86.
 */
private[repository] object Factory {

  private val logger = LoggerFactory.getLogger(getClass)

  /** Type of repository to initialize. */
  sealed abstract class RepoType(name: String)
  case object TempType extends RepoType("temp")
  case object LocalType extends RepoType("local")
  val validTypes = Seq(TempType, LocalType)

  /** Configuration Key for Repository Type. */
  val RepoTypeConfigKey = "mantik.repository.type"

  /** Create the FileRepository which is referenced in the config. */
  def createFileRepository(config: Config)(implicit actorySystem: ActorSystem, materializer: Materializer, ec: ExecutionContext): FileRepository = {
    getRepoType(config) match {
      case TempType =>
        logger.info("Creating SimpleTempFileRepository")
        new SimpleTempFileRepository(config)
      case LocalType =>
        val clock = Clock.systemUTC()
        logger.info("Creating LocalFileRepository")
        new LocalFileRepository(config, clock)
    }
  }

  /** Create the repository which is referenced in the config. */
  def createRepository(config: Config)(implicit ec: ExecutionContext): Repository = {
    getRepoType(config) match {
      case TempType =>
        logger.info("Creating SimpleInMemoryRepository")
        new SimpleInMemoryRepository()
      case LocalType =>
        logger.info("Creating LocalRepository")
        new LocalRepository(config)
    }
  }

  /** Figures out expected repo type. */
  private def getRepoType(config: Config): RepoType = {
    val repoType = config.getString(RepoTypeConfigKey)
    repoType match {
      case "temp"  => TempType
      case "local" => LocalType
      case oher =>
        throw new Errors.ConfigException(s"Invalid type in config ${repoType}")
    }
  }
}
