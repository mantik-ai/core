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

import scala.util.Try

/** Base trait for MNP Urls. */
sealed trait MnpUrl {
  def address: String
}

object MnpUrl {
  val prefix = "mnp://"

  def parse(s: String): Either[String, MnpUrl] = {
    if (!s.startsWith(prefix)) {
      return Left(s"Expected ${prefix} url")
    }
    val rest = s.stripPrefix(prefix)
    val parts = rest.split('/').toList

    parts match {
      case Seq(address) if address.nonEmpty =>
        Right(MnpAddressUrl(address))
      case Seq(address, session) =>
        Right(MnpAddressUrl(address).withSession(session))
      case Seq(address, session, port) =>
        Try(port.toInt).toEither.left.map(_ => s"Could not parse port").map { port =>
          MnpAddressUrl(address).withSession(session).withPort(port)
        }
      case somethingElse if somethingElse.size > 3 =>
        Left("Illegal Mnp URL, too many components")
      case _ =>
        Left(s"Illegal Mnp URL")
    }
  }
}

/** A MnpUrl describing a Host */
case class MnpAddressUrl(
    address: String
) extends MnpUrl {

  /** Add a session to the address. */
  def withSession(session: String): MnpSessionUrl = MnpSessionUrl(this, session)

  override def toString: String = MnpUrl.prefix + address
}

object MnpAddressUrl {
  def parse(s: String): Either[String, MnpAddressUrl] = {
    MnpUrl.parse(s).flatMap {
      case a: MnpAddressUrl => Right(a)
      case somethingElse    => Left(s"Expected address url, got ${somethingElse}")
    }
  }
}

/** A MnpUrl describing a session of the form mnp://address/session */
case class MnpSessionUrl(
    base: MnpAddressUrl,
    session: String
) extends MnpUrl {
  override def toString: String = {
    base.toString + "/" + session
  }

  override def address: String = base.address

  /** Add a port to the session. */
  def withPort(port: Int): MnpSessionPortUrl = MnpSessionPortUrl(this, port)
}

object MnpSessionUrl {
  def build(address: String, session: String): MnpSessionUrl = {
    MnpSessionUrl(MnpAddressUrl(address), session)
  }
}

/** A MnpUrl describing a session and a Mnp Port of the form mnp://address/session/port */
case class MnpSessionPortUrl(
    base: MnpSessionUrl,
    port: Int
) extends MnpUrl {
  override def toString: String = {
    base.toString + "/" + port
  }

  override def address: String = base.address

  /** Returns the session name */
  def session: String = base.session
}

object MnpSessionPortUrl {
  def build(address: String, session: String, port: Int): MnpSessionPortUrl = {
    MnpSessionPortUrl(MnpSessionUrl.build(address, session), port)
  }
}
