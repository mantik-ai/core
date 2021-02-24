package ai.mantik.executor.docker.api

import java.nio.file.Path

import ai.mantik.componently.utils.SecretReader
import akka.http.scaladsl.model.Uri
import com.typesafe.config.Config
import com.typesafe.sslconfig.ssl.{ KeyManagerConfig, KeyStoreConfig, SSLConfigSettings, TrustManagerConfig, TrustStoreConfig }

case class DockerConnectSettings(
    url: String,
    clientCert: Option[String],
    caCert: Option[String],
    clientCertSecret: SecretReader,
    dockerCertPath: Option[String],
    attachMinikube: Boolean
) {
  /**
   * Returns the docker URL as URI.
   * Note: DOCKER_HOST encodings TCP-Connection as tcp://, we are changing this to https.
   */
  def asUri: Uri = {
    if (url.startsWith("tcp://")) {
      Uri("https://" + url.stripPrefix("tcp://"))
    } else {
      Uri(url)
    }
  }

  /**
   * Derive connect settings from given values (e.g. by attaching to minikube).
   * If there is nothing to derive, it returns itself.
   */
  def derive(tempCertFile: Path): DockerConnectSettings = {
    val minikubeExtended = if (attachMinikube) {
      val minikubeSettings = extractMinikubeDockerSettings()
      val dockerUrl = minikubeSettings("DOCKER_HOST")
      copy(
        url = dockerUrl,
        dockerCertPath = Some(minikubeSettings("DOCKER_CERT_PATH")),
        attachMinikube = false // not needed anymore
      )
    } else {
      this
    }
    minikubeExtended.deriveClientIfNecessary(tempCertFile)
  }

  private def extractMinikubeDockerSettings(): Map[String, String] = {
    import sys.process._
    val minikubeSettings = "minikube docker-env".!!

    /*
    Sample output:

    export DOCKER_TLS_VERIFY="1"
    export DOCKER_HOST="tcp://192.168.99.100:2376"
    export DOCKER_CERT_PATH="/home/nos/.minikube/certs"
    export DOCKER_API_VERSION="1.39"
    # Run this command to configure your shell:
    # eval $(minikube docker-env)
     */
    val lines = minikubeSettings.linesIterator.toIndexedSeq.filter { line =>
      line.trim.startsWith("export")
    }
    lines.map { line =>
      line.trim.stripPrefix("export ").split("=").toList match {
        case List(key, value) =>
          key.trim -> value.trim.stripPrefix("\"").stripSuffix("\"")
        case other => throw new IllegalStateException(s"Unexpected value ${other}")
      }
    }.toMap
  }

  /** Derive a client certificate (from PEM Files)  */
  private def deriveClientIfNecessary(tempCertFile: Path): DockerConnectSettings = {
    dockerCertPath match {
      case None => this // not given
      case Some(directory) =>
        val keyFile = s"$directory/key.pem"
        val certFile = s"$directory/cert.pem"
        val caCertFile = s"$directory/ca.pem"
        val call = s"openssl pkcs12 -export -in $certFile -inkey $keyFile -out $tempCertFile -password pass:changeme"

        import sys.process._
        val result = call.!

        if (result != 0) {
          throw new IllegalStateException("Could not convert keys")
        }

        copy(
          clientCertSecret = SecretReader.fixed("changeme"),
          clientCert = Some(tempCertFile.toString),
          caCert = Some(caCertFile),
          dockerCertPath = None
        )
    }
  }

  /**
   * Returns SSL Config settings to use.
   * (In case of PEM Files, you have to call this on [[deriveClientCertIfNecessary]].
   */
  lazy val sslConfigSettings: Option[SSLConfigSettings] = {
    if (clientCert.isEmpty) {
      None
    } else {
      val sslSettings = SSLConfigSettings().withTrustManagerConfig(
        TrustManagerConfig().withTrustStoreConfigs(
          caCert.map { caCert =>
            TrustStoreConfig(
              filePath = Some(caCert),
              data = None
            ).withStoreType("PEM")
          }.toList
        )
      ).withKeyManagerConfig(
          KeyManagerConfig().withKeyStoreConfigs(
            clientCert.map { cert =>
              KeyStoreConfig(
                filePath = Some(cert),
                data = None
              ).withStoreType("PKCS12")
                .withPassword(Some(clientCertSecret.read()))
            }.toList
          )
        )
      Some(sslSettings)
    }
  }

}

object DockerConnectSettings {

  val SubConfigPath = "mantik.executor.docker"

  def fromConfig(config: Config): DockerConnectSettings = {
    import ai.mantik.componently.utils.ConfigExtensions._
    val subConfig = config.getConfig(SubConfigPath)
    DockerConnectSettings(
      url = subConfig.getString("url"),
      clientCert = subConfig.getOptionalString("clientCert"),
      caCert = subConfig.getOptionalString("caCert"),
      clientCertSecret = new SecretReader("clientCertPassword", subConfig),
      dockerCertPath = subConfig.getOptionalString("dockerCertPath"),
      attachMinikube = subConfig.getBoolean("attachMinikube")
    )
  }
}
