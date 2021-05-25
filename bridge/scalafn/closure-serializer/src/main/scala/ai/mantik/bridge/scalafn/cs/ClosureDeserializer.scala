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

import com.esotericsoftware.kryo.io.Input

import java.net.URLClassLoader
import java.nio.file.Path
import scala.ref.PhantomReference

/**
  * Deserializes JARs built using [[ClosureSerializer]].
  *
  * @param parentClassLoader class loader for loading classes before trying inside JAR.
  */
class ClosureDeserializer(parentClassLoader: ClassLoader = Thread.currentThread().getContextClassLoader) {

  /** Deserialize a Bundle serialized by [[ClosureSerializer]] again. */
  def deserialize(jarFile: Path): AnyRef = {
    val url = jarFile.toUri.toURL
    val classLoader = new URLClassLoader(
      Array(url),
      parentClassLoader
    )

    val rootUrl = Option(classLoader.getResource(ClosureSerializer.MainJarElementName)).getOrElse {
      throw new ClosureSerializerException(s"No main element found")
    }

    val instantiator = new CsKryoInstantiator(
      classLoader = classLoader
    )
    val kryo = instantiator.newKryo()

    val inputStream = rootUrl.openStream()
    val input = new Input(inputStream)
    try {
      kryo.readClassAndObject(input)
    } finally {
      input.close()
    }
  }
}
