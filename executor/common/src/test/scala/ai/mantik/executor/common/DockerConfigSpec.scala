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
package ai.mantik.executor.common

import ai.mantik.testutils.TestBase

class DockerConfigSpec extends TestBase {

  "resolveImage" should "work" in {
    def resolveImage(image: String, imageRepo: Option[String], imageTag: Option[String]): String = {
      DockerConfig(defaultImageTag = imageTag, defaultImageRepository = imageRepo, Nil).resolveImageName(image)
    }

    resolveImage("foo", Some("repo"), Some("tag")) shouldBe "repo/foo:tag"
    resolveImage("foo", None, Some("tag")) shouldBe "foo:tag"
    resolveImage("foo", Some("repo"), None) shouldBe "repo/foo"
    resolveImage("otherrepo/foo", Some("repo"), Some("tag")) shouldBe "otherrepo/foo:tag"
    resolveImage("foo:othertag", Some("repo"), Some("tag")) shouldBe "repo/foo:othertag"
    resolveImage("otherrepo/foo:othertag", Some("repo"), Some("tag")) shouldBe "otherrepo/foo:othertag"
    resolveImage("foo", None, None) shouldBe "foo"
    resolveImage("foo", Some("localhost:32000"), Some("latest")) shouldBe "localhost:32000/foo:latest"
  }
}
