package ai.mantik.executor

import java.time.Clock

import ai.mantik.executor.buildinfo.BuildInfo
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.Logger
import ai.mantik.executor.impl.{ ExecutorImpl, K8sOperations }
import ai.mantik.executor.server.ExecutorServer

object Main extends App {
  val logger = Logger(getClass)
  logger.info(s"Starting Mantik Executor ${BuildInfo.version} (${BuildInfo.gitVersion} ${BuildInfo.buildNum})")

  implicit val actorSystem = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = actorSystem.dispatcher
  implicit val clock = Clock.systemUTC()
  try {
    val config = Config()
    val kubernetesClient = skuber.k8sInit
    val k8sOperations = new K8sOperations(config, kubernetesClient)
    val executor = new ExecutorImpl(config, k8sOperations)
    val server = new ExecutorServer(config, executor)
    server.start()
  } catch {
    case e: Exception =>
      logger.error("Could not start executor", e)
      System.exit(1)
  }
}
