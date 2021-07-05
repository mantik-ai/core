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

import java.security.SecureRandom
import java.util.Base64

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
      "AbC120d" -> "BAbC120d",
      "@Abc-1234" -> "AAbc-1234A",
      "my.domain" -> "Bmy.domain",
      "." -> "C.C",
      "Z" -> "BZ",
      "Z." -> "CZ.C",
      "Z/" -> "CZ_002fC",
      "__" -> "C____C",
      "Z_." -> "CZ__.C",
      "@@@@@" -> "C_0040_0040_0040_0040_0040C"
    )
    pairs.foreach { case (from, to) =>
      withClue(s"It should work for ${from} (expected to be ${to})") {
        KubernetesNamer.encodeLabelValue(from) shouldBe to
        KubernetesNamer.isValidLabel(to) shouldBe true
        KubernetesNamer.decodeLabelValue(to) shouldBe from
      }
    }
  }

  it should "create valid ids for ItemIds, Bug#245" in {
    var aEncoding = 0
    var cEncoding = 0
    var maxLength = 0
    for (i <- 0 until 1000) {
      val itemId = generateItemIdString()
      val encoded = KubernetesNamer.encodeLabelValue(itemId)
      val decoded = KubernetesNamer.decodeLabelValue(encoded)
      decoded shouldBe itemId
      withClue(s"${encoded} from ${itemId} should match Kubernetes requirements (iteration ${i})") {
        // https://kubernetes.io/docs/concepts/overview/working-with-objects/labels/
        KubernetesNamer.isValidLabel(encoded) shouldBe true
      }
      if (encoded.startsWith(KubernetesNamer.AtPrefix)) {
        aEncoding += 1
      }
      if (encoded.startsWith(KubernetesNamer.CustomPrefix)) {
        cEncoding += 1
      }
      maxLength = encoded.length.max(maxLength)
    }
    logger.info(s"Max Length: ${maxLength}")
    logger.info(s"Type A Encoding: ${aEncoding}")
    logger.info(s"Type C Encoding: ${cEncoding}")
  }

  val ByteCount = 32
  private val generator = new SecureRandom()

  private def generateItemIdString(): String = {
    // From ItemId.generate()
    val value = new Array[Byte](ByteCount)
    generator.nextBytes(value)
    // Use URL Encoding, we do not want "/"
    val encoded = Base64.getUrlEncoder.withoutPadding().encodeToString(value)
    "@" + encoded
  }
}
