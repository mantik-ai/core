package ai.mantik.executor.docker.api

import java.util.Locale

import scala.util.Try

object DockerApiHelper {

  private val statusCodeExtractRegex = "^exited \\((\\d+)\\).*".r

  /**
    * Try to decode the status code from human readable status field.
    * This can be useful, if we want to know if a container failed
    * from reading the container listing without further inspection.
    */
  def decodeStatusCodeFromStatus(status: String): Option[Int] = {
    val lc = status.toLowerCase(Locale.US)
    lc match {
      case statusCodeExtractRegex(value) =>
        Try(value.toInt).toOption
      case _ => None
    }
  }
}
