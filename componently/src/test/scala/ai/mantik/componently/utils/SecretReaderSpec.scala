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
package ai.mantik.componently.utils

import java.nio.charset.StandardCharsets

import ai.mantik.testutils.{TempDirSupport, TestBase}
import com.typesafe.config.ConfigFactory
import org.apache.commons.io.FileUtils

class SecretReaderSpec extends TestBase with TempDirSupport {

  trait Env {
    val tempContent = "secret temp content"
    val tempFile = tempDirectory.resolve("my_file")
    FileUtils.write(tempFile.toFile, tempContent, StandardCharsets.UTF_8)

    val config = ConfigFactory.parseString(s"""
                                              |my {
                                              |  plain_value = "plain:foobar"
                                              |  env_value = "env:HOME"
                                              |  file_value = "file:${tempFile}"
                                              |  other_value = "secret"
                                              |}
                                              |""".stripMargin)
  }

  it should "read plain values" in new Env {
    val reader = new SecretReader("my.plain_value", config)
    reader.read() shouldBe "foobar"
  }

  it should "read env values" in new Env {
    val reader = new SecretReader("my.env_value", config)
    reader.read() shouldBe System.getenv("HOME")
  }

  it should "read file values" in new Env {
    val reader = new SecretReader("my.file_value", config)
    reader.read() shouldBe tempContent
  }

  it should "fall back to pure values" in new Env {
    val reader = new SecretReader("my.other_value", config)
    reader.read() shouldBe "secret"
  }
}
