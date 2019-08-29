package ai.mantik.componently.utils

import io.circe.generic.JsonCodec

/** Like [[java.net.InetSocketAddress]] but JSON Serializable. */
@JsonCodec
case class HostPort(host: String, port: Int)
