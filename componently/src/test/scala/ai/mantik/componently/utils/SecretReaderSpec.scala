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
