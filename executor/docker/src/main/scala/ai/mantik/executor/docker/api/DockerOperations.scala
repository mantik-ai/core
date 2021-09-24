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
package ai.mantik.executor.docker.api

import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeoutException
import ai.mantik.componently.AkkaRuntime
import ai.mantik.executor.Errors.{ExecutorException, InternalException}
import ai.mantik.executor.docker.ContainerDefinition
import ai.mantik.executor.docker.api.DockerClient.WrappedErrorResponse
import ai.mantik.executor.docker.api.structures.{
  ContainerWaitResponse,
  CreateContainerRequest,
  CreateContainerResponse,
  CreateNetworkRequest,
  InspectContainerResponse,
  ListContainerRequestFilter,
  ListContainerResponseRow,
  ListNetworkRequestFilter
}
import akka.stream.scaladsl.{JsonFraming, Sink}
import com.typesafe.scalalogging.Logger
import io.circe.generic.semiauto
import io.circe.{Decoder, Json}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

/** High level docker operations, consisting of multiple sub operations. */
class DockerOperations(dockerClient: DockerClient)(implicit akkaRuntime: AkkaRuntime) {
  import ai.mantik.componently.AkkaHelper._
  val logger = Logger(getClass)

  /** Execute a Pull policy for an image. */
  def executePullPolicy(pullPolicy: PullPolicy, image: String): Future[Unit] = {
    pullPolicy match {
      case PullPolicy.Never => Future.successful(())
      case PullPolicy.IfNotPresent =>
        pullImageIfNotPresent(image)
      case PullPolicy.Always =>
        pullImage(image)
    }
  }

  /** Pull the image if not present. */
  def pullImageIfNotPresent(image: String): Future[Unit] = {
    dockerClient.inspectImage(image).transformWith {
      case Success(_) =>
        logger.debug(s"Image ${image} present, no pulling")
        Future.successful(())
      case Failure(e: WrappedErrorResponse) if e.code == 404 =>
        logger.debug(s"Image ${image} not present, pulling")
        pullImage(image)
      case Failure(e) =>
        Future.failed(e)
    }
  }

  /** Pull an image. */
  def pullImage(image: String): Future[Unit] = {
    // The docker API doc is not very clear what is the correct format of the Response
    // however status and error seem to be present.
    case class PullResponseLine(
        error: Option[String],
        status: Option[String]
    )
    implicit val decoder: Decoder[PullResponseLine] = semiauto.deriveDecoder[PullResponseLine]

    val t0 = System.currentTimeMillis()
    logger.debug(s"Starting pulling of image ${image}")
    val result = for {
      response <- dockerClient.pullImage(image)
      // This is tricky, the pull is silently aborted if we do not completely consume the response
      responseLines <- response._2
        .via(
          JsonFraming
            .objectScanner(1024)
        )
        .map { bytes =>
          val string = bytes.utf8String
          logger.debug(s"Pulling Response ${string}")
          io.circe.parser.parse(string).flatMap(_.as[PullResponseLine]) match {
            case Left(value) => throw new InternalException(s"Docker responds with invalid JSON", value)
            case Right(ok)   => ok
          }
        }
        .runWith(Sink.seq)
      _ <- {
        responseLines.flatMap(_.error).lastOption match {
          case Some(err) =>
            Future.failed(new InternalException(s"Could not pull image ${image}: ${err}"))
          case None => Future.successful(())
        }

      }
    } yield {
      val t1 = System.currentTimeMillis()
      logger.debug(s"Pulled image ${image} in ${t1 - t0}ms")
      ()
    }
    result.failed.foreach { case NonFatal(e) =>
      val t1 = System.currentTimeMillis()
      logger.error(s"Pulling image ${image} failed within ${t1 - t0}ms", e)
    }
    result
  }

  /** Creates a container, pulling if necessary. */
  def createContainer(containerDefinition: ContainerDefinition): Future[CreateContainerResponse] = {
    for {
      _ <- executePullPolicy(containerDefinition.pullPolicy, containerDefinition.createRequest.Image)
      response <- dockerClient.createContainer(containerDefinition.name, containerDefinition.createRequest)
    } yield response
  }

  /** Creates a container, pulling if necessary and runs it. */
  def createAndRunContainer(containerDefinition: ContainerDefinition): Future[CreateContainerResponse] = {
    for {
      createResponse <- createContainer(containerDefinition)
      _ = logger.debug(s"Created ${containerDefinition.name} (${containerDefinition.createRequest.Image})")
      _ <- dockerClient.startContainer(createResponse.Id)
      _ = logger.debug(s"Started ${containerDefinition.name} (${containerDefinition.createRequest.Image})")
    } yield createResponse
  }

  /**
    * Wait for a container.
    * This works around TimeoutExceptions from Akka Http.
    */
  def waitContainer(container: String, timeout: FiniteDuration): Future[ContainerWaitResponse] = {
    val finalTimeout = akkaRuntime.clock.instant().plus(timeout.toSeconds, ChronoUnit.SECONDS)

    def continueWait(): Future[ContainerWaitResponse] = {
      val ct = akkaRuntime.clock.instant()
      if (ct.isAfter(finalTimeout)) {
        Future.failed(new TimeoutException(s"Timeout waiting for container ${container}"))
      }

      dockerClient.containerWait(container).recoverWith { case _: TimeoutException =>
        logger.debug("Container wait failed with timeout, trying again.")
        continueWait()
      }
    }

    continueWait()
  }

  /**
    * List containers.
    * @param all if true, only running containers are returned.
    */
  def listContainers(all: Boolean, labelFilters: Seq[(String, String)]): Future[Vector[ListContainerResponseRow]] = {
    dockerClient.listContainersFiltered(
      all,
      ListContainerRequestFilter.forLabelKeyValue(labelFilters: _*)
    )
  }

  /** Ensures the existance of a running container, returns the ID if it's already running. */
  def ensureContainer(name: String, createContainerRequest: CreateContainerRequest): Future[String] = {
    checkExistance(name).flatMap {
      case Some(existing) if existing.State.Running =>
        logger.info(s"Container ${name} already exists with Id ${existing.Id}")
        Future.successful(existing.Id)
      case otherwise =>
        val preparation: Future[Unit] = otherwise match {
          case None =>
            logger.info(s"Container ${name} doesn't exist")
            Future.successful(())
          case Some(value) =>
            logger.info(s"Container ${name} does exist, but doesn't appear running, recreating...")
            dockerClient.removeContainer(value.Id, true)
        }
        (for {
          _ <- preparation
          _ <- pullImageIfNotPresent(createContainerRequest.Image)
          res <- dockerClient.createContainer(name, createContainerRequest)
          _ <- dockerClient.startContainer(res.Id)
        } yield {
          res.Id
        }).andThen {
          case Success(id)  => logger.info(s"Created container ${name} with Id ${id} suceeded")
          case Failure(err) => logger.error(s"Creation of container ${name} failed", err)
        }
    }
  }

  private def checkExistance(containerName: String): Future[Option[InspectContainerResponse]] = {
    dockerClient.inspectContainer(containerName).transform {
      case Success(result)                                   => Success(Some(result))
      case Failure(e: WrappedErrorResponse) if e.code == 404 => Success(None)
      case Failure(e)                                        => Failure(e)
    }
  }

  /** Ensures the existance of a network, returns the Id. */
  def ensureNetwork(name: String, createNetworkRequest: CreateNetworkRequest): Future[String] = {
    dockerClient
      .listNetworksFiltered(
        ListNetworkRequestFilter(
          name = Some(Vector(name))
        )
      )
      .flatMap {
        case networks if networks.nonEmpty =>
          logger.info(s"Network ${name} already exists with id ${networks.head.Id}")
          Future.successful(networks.head.Id)
        case networks =>
          dockerClient.createNetwork(createNetworkRequest).map(_.Id).andThen {
            case Success(id)  => logger.info(s"Creating Network ${name} with ${id} succeded")
            case Failure(err) => logger.error(s"Could not ensure network ${name}", err)
          }
      }
  }

  def killContainer(id: String): Future[Unit] = {
    ignore409 {
      dockerClient.killContainer(id)
    }
  }

  def removeContainer(id: String): Future[Unit] = {
    ignore409 {
      dockerClient.removeContainer(id, /*force = */ true)
    }
  }

  /** Ignore error 409 (Already in progress) from Docker */
  private def ignore409[T](f: Future[Unit]): Future[Unit] = {
    f.recover {
      case e: DockerClient.WrappedErrorResponse if e.code == 409 =>
        logger.debug(s"Ignoring error 409 on docker removal", e)
        ()
    }
  }
}
