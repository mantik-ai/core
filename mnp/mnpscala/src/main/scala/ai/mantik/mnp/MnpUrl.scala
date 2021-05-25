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
package ai.mantik.mnp

import java.net.URL
import scala.util.Try
import scala.util.control.NonFatal

/**
  * An MNP Url of the form
  * {{{
  * mnp://address/session
  * }}}
  * (Describing a Session)
  * or
  *
  * {{{
  *  mnp://address/session/port
  * }}}
  * (Describing a port of a session (task dynamically generated)
  *
  * @param address host (including port)
  */
case class MnpUrl(
    address: String,
    session: String,
    port: Option[Int] = None
) {
  override def toString: String = {
    val plain = s"mnp://${address}/${session}"
    port match {
      case Some(p) => plain + s"/${p}"
      case None    => plain
    }
  }
}

object MnpUrl {
  private val prefix = "mnp://"

  def parse(s: String): Either[String, MnpUrl] = {
    if (!s.startsWith("mnp://")) {
      return Left("Expected mnp:// url")
    }
    val rest = s.stripPrefix(prefix)
    val parts = rest.split('/').toSeq

    parts match {
      case Seq(address, session) =>
        Right(MnpUrl(address, session))
      case Seq(address, session, port) =>
        Try(port.toInt).toEither.left.map(_ => s"Could not parse port").map { port =>
          MnpUrl(address, session, Some(port))
        }
      case somethingElse if somethingElse.size > 3 =>
        Left("Illegal Mnp URL, too many components")
      case _ =>
        Left(s"Illegal Mnp URL")
    }
  }
}
