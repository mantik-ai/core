package ai.mantik.mnp

class MnpException(msg: String) extends RuntimeException

class SessionInitException(msg: String) extends MnpException(msg) {

}

class ProtocolException(msg: String) extends MnpException(msg) {

}