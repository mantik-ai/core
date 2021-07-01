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
import io.circe.Json
import io.circe.syntax._

import java.time.Instant

class InstantCodecSpec extends TestBase {

  it should "parse times correctly" in {
    val samples = Seq(
      "2021-06-18T10:24:22.830364507+02:00" -> Instant.parse("2021-06-18T08:24:22.830364507Z"),
      "2021-06-18T10:12:13Z" -> Instant.parse("2021-06-18T10:12:13Z")
    )
    import InstantCodec._
    samples.foreach { case (s, i) =>
      Json.fromString(s).as[Instant].forceRight shouldBe i
      i.asJson.as[Instant].forceRight shouldBe i
    }
  }
}
