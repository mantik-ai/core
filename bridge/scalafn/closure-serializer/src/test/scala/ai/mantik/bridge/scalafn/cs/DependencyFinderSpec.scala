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

import ai.mantik.bridge.scalafn.cs
import ai.mantik.testutils.TestBase

class DependencyFinderSpec extends TestBase {

  trait Env {
    val loader = new ClassByteCodeLoader()
    val finder = new DependencyFinder(loader)
  }

  it should "find dependencies of a simple class" in new Env {
    val dependencies = finder.rawDependenciesOf("java.lang.Thread")
    dependencies.size shouldBe >(10)
  }

  "transitiveDependencies" should "find transitive dependencies of a simple class" in new Env {
    val dependencies = finder.transitiveDependencies(Seq("java.lang.Thread"), ClassFilter.all, true)
    dependencies should contain("java.lang.Thread")
    dependencies.size shouldBe >(100)
  }

  it should "not crash on empty start" in new Env {
    finder.transitiveDependencies(Nil, ClassFilter.all, true) shouldBe empty
  }

  it should "obey the class filter" in new Env {
    val ownClass = classOf[cs.DependencyFinder].getCanonicalName
    val dependencies = finder.transitiveDependencies(Seq(ownClass), ClassFilter.default, true)
    dependencies should contain(ownClass)
    dependencies should not contain ("java.lang.Object")
  }
}
