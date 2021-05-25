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
package ai.mantik.bridge.scalafn.cs

import com.esotericsoftware.kryo.{ClassResolver, Kryo, Registration}
import com.esotericsoftware.kryo.io.{Input, Output}
import com.esotericsoftware.kryo.util.DefaultClassResolver

import scala.collection.mutable.ArrayBuffer

/** A Kryo Class Resolver, which tracks implicit resolved classes. */
private[cs] class TrackingClassResolver extends ClassResolver {
  private val backend = new DefaultClassResolver()
  private val implicitRegisteredBuffer = new ArrayBuffer[Class[_]]

  /** Returns implicit registered classes. */
  def implicitRegistered(): Seq[Class[_]] = {
    implicitRegisteredBuffer.toIndexedSeq
  }

  override def setKryo(kryo: Kryo): Unit = {
    backend.setKryo(kryo)
  }

  override def register(registration: Registration): Registration = {
    backend.register(registration)
  }

  override def registerImplicit(`type`: Class[_]): Registration = {
    implicitRegisteredBuffer += `type`
    backend.registerImplicit(`type`)
  }

  override def getRegistration(`type`: Class[_]): Registration = {
    backend.getRegistration(`type`)
  }

  override def getRegistration(classID: Int): Registration = {
    backend.getRegistration(classID)
  }

  override def writeClass(output: Output, `type`: Class[_]): Registration = {
    backend.writeClass(output, `type`)
  }

  override def readClass(input: Input): Registration = {
    backend.readClass(input)
  }

  override def reset(): Unit = {
    implicitRegisteredBuffer.clear()
    backend.reset()
  }
}
