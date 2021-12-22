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
package ai.mantik.executor.common.workerexec

import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.componently.utils.ConfigExtensions._
import ai.mantik.componently.utils.HostPort
import ai.mantik.executor.PayloadProvider
import akka.NotUsed
import akka.stream.scaladsl.Source
import akka.util.ByteString
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpEntity, MediaType, MediaTypes}
import akka.http.scaladsl.server.Directives._

import java.net.{Inet4Address, InetAddress, NetworkInterface}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.Try

/**
  * PayloadProvider using a local HTTP Server.
  * Note: payload is gone if the server is gone. No temporary support.
  */
@Singleton
class LocalServerPayloadProvider @Inject() (implicit akkaRuntime: AkkaRuntime)
    extends ComponentBase
    with PayloadProvider {

  private val subConfig = config.getConfig("mantik.executor.localPayloadProvider")
  private val port = subConfig.getInt("port")
  private val interface = subConfig.getString("interface")
  private val host = subConfig.getOptionalString("host")

  case class Entry(
      tagId: String,
      data: Source[ByteString, NotUsed],
      contentLength: Long,
      contentType: String,
      akkaMediaType: MediaType.Binary
  )

  private object lock
  private var nextKey: Int = 1
  private var entries: Map[Int, Entry] = Map.empty

  val HelloMessage = "This is the Mantik FileRepositoryServer"

  private val route = concat(
    path("/") {
      get(
        complete(HelloMessage)
      )
    },
    path("files") {
      get {
        complete(HelloMessage)
      }
    },
    path("files" / "") {
      get {
        complete(HelloMessage)
      }
    },
    path("files" / Remaining) { id =>
      get {
        logger.debug(s"Requesting file ${id}")
        val maybeNumericId = Try(id.toInt).toOption
        maybeNumericId match {
          case None =>
            logger.warn(s"Invalid request for id (nun numerical) ${id}")
            complete(
              400,
              "Expected numerical id, got ${id}"
            )
          case Some(numericId) =>
            val entry = lock.synchronized {
              entries.get(numericId)
            }
            entry match {
              case None =>
                logger.warn(s"Request for id ${numericId} not found")
                complete(
                  404,
                  s"File not found ${numericId}"
                )
              case Some(entry) =>
                logger.debug(s"Completing file ${id} (${entry.contentType}, ${entry.contentLength})")
                complete(
                  HttpEntity(entry.akkaMediaType, entry.data)
                )
            }
        }
      }
    },
    path("") {
      get {
        complete(HelloMessage)
      }
    }
  )

  private val bindResult = Await.result(Http().newServerAt(interface, port).bind(route), 60.seconds)

  /** Returns the bound port of the server. */
  def boundPort: Int = {
    bindResult.localAddress.getPort
  }

  /** Returns the address of the repository (must be reachable from the executor). */
  def address(): HostPort = _address

  /** Returns the root URL of the server */
  def rootUrl(): String = {
    s"http://${address().host}:${address().port}"
  }

  private lazy val _address = figureOutAddress()

  addShutdownHook {
    bindResult.terminate(60.seconds)
  }

  logger.info(s"Listening on ${interface}:${boundPort}, external ${address()}")

  override def provide(
      tagId: String,
      temporary: Boolean,
      data: Source[ByteString, NotUsed],
      byteCount: Long,
      contentType: String
  ): Future[String] = {
    val akkaMediaType = findAkkaMediaType(contentType)
    val url = lock.synchronized {
      val key = nextKey
      nextKey += 1
      val entry = Entry(
        tagId = tagId,
        data = data,
        contentLength = byteCount,
        contentType = contentType,
        akkaMediaType = akkaMediaType
      )
      entries += (key -> entry)
      s"${rootUrl()}/files/${key}"
    }
    Future.successful(url)
  }

  override def delete(tagId: String): Future[Unit] = {
    lock.synchronized {
      val updatedEntries = entries.filter { case (_, entry) => entry.tagId != tagId }
      entries = updatedEntries
    }
    Future.successful(())
  }

  private def findAkkaMediaType(contentType: String): MediaType.Binary = {
    contentType.split("/").toList match {
      case List(a, b) =>
        MediaTypes.getForKey(a -> b) match {
          case Some(b: MediaType.Binary) => b
          case _ =>
            MediaType.customBinary(a, b, MediaType.Compressible)
        }
      case somethingElse =>
        logger.error(s"Illegal Content Type ${contentType}")
        MediaTypes.`application/octet-stream`
    }
  }

  private def figureOutAddress(): HostPort = {
    if (host.isDefined) {
      return HostPort(host.get, boundPort)
    }
    // This is tricky: https://stackoverflow.com/questions/9481865/getting-the-ip-address-of-the-current-machine-using-java
    // We can't know which one is available from kubernetes
    // hopefully the first non-loopback is it.
    import scala.jdk.CollectionConverters._

    def score(address: InetAddress): Int = {
      address match {
        case v4: Inet4Address =>
          if (v4.getHostAddress.startsWith("192")) {
            +100
          } else {
            50
          }
        case x if x.isLoopbackAddress => -100
        case other                    => 0
      }
    }

    val addresses = (for {
      networkInterface <- NetworkInterface.getNetworkInterfaces.asScala
      if !networkInterface.isLoopback
      address <- networkInterface.getInetAddresses.asScala
    } yield address).toVector

    val ordered = addresses.sortBy(x => 0 - score(x))
    val address = ordered.headOption.getOrElse(InetAddress.getLocalHost)

    val result = HostPort(address.getHostAddress, boundPort)
    logger.info(s"Choosing ${result} from ${ordered}")
    result
  }
}
