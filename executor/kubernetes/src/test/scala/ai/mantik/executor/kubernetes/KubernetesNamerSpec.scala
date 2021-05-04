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
package ai.mantik.executor.kubernetes

import ai.mantik.testutils.TestBase

class KubernetesNamerSpec extends TestBase {

  it should "work in an easy example" in {
    val namer = new KubernetesNamer("id1", "prefix-")

    namer.configName shouldBe "prefix-id1-config"
    namer.jobName shouldBe "prefix-id1-job"
    namer.replicaSetName shouldBe "prefix-id1-rs"
    namer.serviceName shouldBe "prefix-id1-service"
    namer.podName("A") shouldBe "prefix-id1-a"
    namer.podName("b") shouldBe "prefix-id1-b"
    namer.podName("A") shouldBe "prefix-id1-a"
    namer.podName(":+-4") shouldBe "prefix-id1--4"
    namer.podName("config") shouldBe "prefix-id1-config0"
    namer.podName("Config") shouldBe "prefix-id1-config1"
    namer.podName("Config") shouldBe "prefix-id1-config1"
    namer.podName("config") shouldBe "prefix-id1-config0"
  }

  it should "escape labels" in {
    val pairs = Seq(
      "" -> "",
      "AbC120d" -> "AbC120d",
      "my.domain" -> "my.domain",
      "." -> "Z002e",
      "Z" -> "Z_",
      "Z." -> "Z_.",
      "Z/" -> "Z__002f",
      "__" -> "Z005f_005f",
      "Z_." -> "Z__005f."
    )
    pairs.foreach { case (from, to) =>
      KubernetesNamer.encodeLabelValue(from) shouldBe to
    }
    pairs.foreach { case (from, to) =>
      KubernetesNamer.decodeLabelValue(to) shouldBe from
    }
  }
}
