package ai.mantik.elements.errors

class ConfigurationException(msg: String, cause: Throwable = null)
    extends MantikException(ErrorCodes.Configuration, msg, cause)
