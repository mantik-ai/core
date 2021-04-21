package ai.mantik.testutils

import java.io.{File, FileNotFoundException}
import java.nio.charset.StandardCharsets
import java.nio.file.Path

import org.apache.commons.io.IOUtils

/** Helpers for dealing with (test) Resources */
trait ResourceSupport {
  self: TestBase =>

  /** Returns the resource content as string. */
  def resourceAsString(name: String): String = {
    IOUtils.resourceToString(name, StandardCharsets.UTF_8)
  }

  /** Returns the resource path (note: this doesn't work if the resource is inside a JAR). */
  def resourcePath(name: String): Path = {
    val res = getClass.getResource(name)
    if (res == null) {
      throw new FileNotFoundException(s"Resource ${name} not found")
    }
    new File(res.toURI).toPath
  }
}
