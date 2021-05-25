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
package ai.mantik.mnp

import ai.mantik.testutils.TestBase

class MnpUrlSpec extends TestBase {

  val samples = Seq(
    s"mnp://127.0.0.1:1234/session1/1234" -> MnpUrl("127.0.0.1:1234", "session1", Some(1234)),
    s"mnp://hostname/session/1234" -> MnpUrl("hostname", "session", Some(1234)),
    s"mnp://hostname.foo.bar:1234/session1" -> MnpUrl("hostname.foo.bar:1234", "session1")
  )

  it should "parse and serialize" in {
    samples.foreach {
      case (s, url) =>
        url.toString shouldBe s
        MnpUrl.parse(s) shouldBe Right(url)
    }
  }

  it should "handle errors" in {
    MnpUrl.parse("http://foo/bar").left.get should include("Expected mnp://")
    MnpUrl.parse("mnp://foo/bar/1/2").left.get should include("Illegal Mnp URL")
    MnpUrl.parse("mnp://foo/bar/port").left.get should include("Could not parse port")
  }
}
