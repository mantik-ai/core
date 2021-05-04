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
package ai.mantik.planner.util

import ai.mantik.componently.utils.GlobalLocalAkkaRuntime
import ai.mantik.testutils.{AkkaSupport, FakeClock, TestBase}

/** Test base which already initializes the AkkaRuntime. */
abstract class TestBaseWithAkkaRuntime extends TestBase with AkkaSupport with GlobalLocalAkkaRuntime {

  override val clock: FakeClock = new FakeClock()

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    clock.resetTime()
    enterTestcase()
  }

  override protected def afterEach(): Unit = {
    exitTestcase()
  }
}
