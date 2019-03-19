package io.mantik.executor

import java.time.Clock

import ai.mantik.executor.buildinfo.BuildInfo
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.Logger
import io.mantik.executor.impl.ExecutorImpl
import io.mantik.executor.server.ExecutorServer

object Main extends App {
  val logger = Logger(getClass)
  logger.info(s"Starting Mantik Executor ${BuildInfo.version} (${BuildInfo.gitVersion} ${BuildInfo.buildNum})")

  implicit val actorSystem = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = actorSystem.dispatcher
  implicit val clock = Clock.systemUTC()
  val config = Config()
  val kubernetesClient = skuber.k8sInit

  val executor = new ExecutorImpl(config, kubernetesClient)
  val server = new ExecutorServer(config, executor)
  server.start()
}
