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

import com.esotericsoftware.kryo.util.{DefaultClassResolver, MapReferenceResolver}
import com.esotericsoftware.kryo.{ClassResolver, Kryo}
import com.twitter.chill.{AllScalaRegistrar, KryoBase, KryoInstantiator}

/** Assembles Kryo-Objects for ClosureSerializer */
private[cs] class CsKryoInstantiator(
    classResolver: ClassResolver = new DefaultClassResolver(),
    classLoader: ClassLoader
) extends KryoInstantiator {

  override def newKryo(): KryoBase = {
    val kryo = new KryoBase(classResolver, new MapReferenceResolver)

    // Also see ScalaKryoIsntantiator

    kryo.setInstantiatorStrategy(new org.objenesis.strategy.StdInstantiatorStrategy)

    kryo.setClassLoader(classLoader)
    kryo.setRegistrationRequired(false)

    val reg = new AllScalaRegistrar
    reg(kryo)

    kryo
  }
}
