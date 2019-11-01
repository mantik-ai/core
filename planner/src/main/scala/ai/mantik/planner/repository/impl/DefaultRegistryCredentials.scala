package ai.mantik.planner.repository.impl

import ai.mantik.componently.utils.SecretReader
import com.typesafe.config.Config

/** Reads default mantik credentials from the config */
private[mantik] class DefaultRegistryCredentials(config: Config) {

  private val configRoot = config.getConfig("mantik.core.registry")

  /** URL of the Mantik Registry. */
  val url = configRoot.getString("url")

  /** Username for Mantik Registry. */
  val user = configRoot.getString("username")

  /** Password for Mantik Registry. */
  val password = new SecretReader("password", configRoot)
}
