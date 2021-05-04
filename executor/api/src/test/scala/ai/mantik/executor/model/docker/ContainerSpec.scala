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
package ai.mantik.executor.model.docker

import ai.mantik.testutils.TestBase

class ContainerSpec extends TestBase {

  "tag" should "work" in {
    Container("foo").imageTag shouldBe None
    Container("foo:mytag").imageTag shouldBe Some("mytag")
    Container("url/foo:mytag").imageTag shouldBe Some("mytag")
    Container("url:3000/foo:mytag").imageTag shouldBe Some("mytag")
  }

  "splitImageRepoTag" should "work" in {
    Container.splitImageRepoTag("foo:bar") shouldBe Some("foo" -> "bar")
    Container.splitImageRepoTag("foo") shouldBe None
    Container.splitImageRepoTag("url:3000/foo") shouldBe None
    Container.splitImageRepoTag("url:3000/foo:mytag") shouldBe Some("url:3000/foo" -> "mytag")
  }

  "simpleImageName" should "work" in {
    Container("foo").simpleImageName shouldBe "foo"
    Container("foo:mytag").simpleImageName shouldBe "foo"
    Container("url/foo:mytag").simpleImageName shouldBe "foo"
    Container("url:3000/foo:mytag").simpleImageName shouldBe "foo"
  }
}
