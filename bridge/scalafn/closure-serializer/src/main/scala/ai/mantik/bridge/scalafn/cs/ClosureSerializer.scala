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

import com.twitter.chill.{ClosureCleaner, Kryo, Output}
import org.slf4j.LoggerFactory

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.nio.file.{Files, Path}

/**
  * Serializes Closures into JAR-Files including their dependencies.
  *
  * @param classLoader Class loader for loading classes
  * @param classFilter Class filter to ignore well known classes.
  */
final class ClosureSerializer(
    classLoader: ClassLoader = Thread.currentThread().getContextClassLoader,
    classFilter: ClassFilter = ClassFilter.default
) {
  private val logger = LoggerFactory.getLogger(getClass)
  private val byteCodeLoader = new ClassByteCodeLoader(classLoader)
  private val dependencyFinder = new DependencyFinder(byteCodeLoader)
  private val jarBuilder = new JarBuilder(byteCodeLoader)

  /** A Helper so that you can clean closures before serializing them. */
  def cleanClosure[T <: AnyRef](fn: T): T = {
    ClosureCleaner.clean(fn)
  }

  /**
    * Serializes an object including dependencies (and closures), so that it can be transferred.
    * Note: closures should be cleaned using [[cleanClosure()]]
    */
  def serialize(closure: AnyRef): Path = {
    val tracker = new TrackingClassResolver()
    val kryo = newKryoForSerializing(tracker)

    val buffer = new ByteArrayOutputStream()
    val output = new Output(buffer)
    kryo.writeClassAndObject(output, closure)

    val classes = tracker.implicitRegistered().flatMap(c => Option(c.getName))
    val transitives = dependencyFinder.transitiveDependencies(classes, classFilter, true)
    output.close()
    val bytes = buffer.toByteArray

    val result = buildJar(transitives, bytes)
    logger.debug(s"Final Closure JAR size: ${Files
      .size(result)} (kryo size: ${bytes.length}, direct classes: ${classes.size}, transitive: ${transitives.size})")
    result
  }

  private def buildJar(classes: Seq[String], main: Array[Byte]): Path = {
    val mainReader = new ByteArrayInputStream(main)
    jarBuilder.buildJar(
      classes,
      Seq(
        ClosureSerializer.MainJarElementName -> mainReader
      )
    )
  }

  private def newKryoForSerializing(tracker: TrackingClassResolver): Kryo = {
    val instantiator = new CsKryoInstantiator(tracker, classLoader)
    val kryo = instantiator.newKryo()
    kryo.setAutoReset(false) // otherwise TrackingClassResolver will lost it's state
    kryo
  }
}

object ClosureSerializer {

  /** Name of the kryo element inside the Jar. */
  val MainJarElementName = "__root__"
}
