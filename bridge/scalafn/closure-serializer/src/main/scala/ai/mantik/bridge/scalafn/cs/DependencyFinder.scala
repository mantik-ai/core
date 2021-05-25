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

import org.apache.xbean.asm7.ClassReader
import org.apache.xbean.asm7.commons.{ClassRemapper, Remapper}
import org.apache.xbean.asm7.shade.commons.EmptyVisitor

import scala.collection.mutable

/** Helper for finding dependency graph of classes. */
class DependencyFinder(byteCodeLoader: ClassByteCodeLoader) {

  /** Returns the raw dependencies of a class */
  def rawDependenciesOf(canonicalName: String): Seq[String] = {
    val bytes = byteCodeLoader.rawClassByteCodeFromName(canonicalName)
    val reader = new ClassReader(bytes)

    val remapper = new DependencyFinder.CollectingRemapper()
    val visitor = new ClassRemapper(new EmptyVisitor() {}, remapper)
    reader.accept(visitor, ClassReader.SKIP_DEBUG)
    remapper.referenced
  }

  /**
    * Find transitive dependencies of a class.
    * @param start starting points
    * @param filter class filter
    * @param includeStart if true, also include starting points  (if not filtered out)
    */
  def transitiveDependencies(start: Seq[String], filter: ClassFilter, includeStart: Boolean): Seq[String] = {
    val result = mutable.Set[String]()
    val toInvestigate = mutable.Set[String]()

    val startFiltered = start.filter(filter.include)
    toInvestigate ++= startFiltered
    if (includeStart) {
      result ++= startFiltered
    }

    while (toInvestigate.nonEmpty) {
      val next = toInvestigate.head
      toInvestigate.remove(next)
      val dependencies = rawDependenciesOf(next)
      val filtered = dependencies.filter(filter.include)
      val newClasses = filtered.diff(result.toSeq)
      result ++= newClasses
      toInvestigate ++= newClasses
    }
    result.toIndexedSeq
  }

}

object DependencyFinder {

  /** An ASM Remapper whose only purpose is to collect dependencies. */
  private class CollectingRemapper extends Remapper {
    val collector = mutable.Set[String]()
    override def map(internalName: String): String = {
      collector += slashToDot(internalName)
      super.map(internalName)
    }

    private def slashToDot(name: String): String = {
      name.replace('/', '.')
    }

    def referenced: Seq[String] = collector.toIndexedSeq
  }
}
