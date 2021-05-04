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
package ai.mantik.planner.integration

class HelloDataSetSpec extends IntegrationTestBase with Samples {

  it should "read datasets" in new EnvWithDataSet {
    val content = mnistTrain.fetch.run()
    content.rows shouldNot be(empty)
  }

  it should "read datasets through selects" in new EnvWithDataSet {
    val a = mnistTrain.select("select x as y")
    val b = a.select("select y as z")
    val c = b.select("select z as a")
    val content = c.fetch.run()
    content.rows shouldNot be(empty)
  }
}
