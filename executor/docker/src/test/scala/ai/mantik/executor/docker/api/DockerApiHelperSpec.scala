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
package ai.mantik.executor.docker.api

import ai.mantik.testutils.TestBase

class DockerApiHelperSpec extends TestBase {

  "decodeStatusCodeFromStatus" should "work" in {
    DockerApiHelper.decodeStatusCodeFromStatus("exited (0)") shouldBe Some(0)
    DockerApiHelper.decodeStatusCodeFromStatus("Up 4 Hours") shouldBe None
    DockerApiHelper.decodeStatusCodeFromStatus("Exited (0) 9 days ago") shouldBe Some(0)
    DockerApiHelper.decodeStatusCodeFromStatus("Exited (255) 4 hours ago") shouldBe Some(255)
  }
}
