package ai.mantik.executor

import io.circe.Decoder.Result
import io.circe._

/** Errors from Executor. */
object Errors {

  class ExecutorException(val msg: String, val statusCode: Int) extends RuntimeException(msg)

  implicit val encoder: ObjectEncoder[ExecutorException] = new ObjectEncoder[ExecutorException] {

    override def encodeObject(a: ExecutorException): JsonObject = JsonObject(
      "code" -> Json.fromInt(a.statusCode),
      "error" -> Json.fromString(a.msg)
    )
  }

  implicit val decoder: Decoder[ExecutorException] = new Decoder[ExecutorException] {
    override def apply(c: HCursor): Result[ExecutorException] = {
      for {
        code <- c.downField("code").as[Int]
        msg <- c.downField("error").as[String]
      } yield {
        code match {
          case 404 => new NotFoundException(msg)
          case 500 => new InternalException(msg)
          case _   => new ExecutorException(msg, code)
        }
      }
    }
  }

  /** A resource was not found. */
  class NotFoundException(msg: String) extends ExecutorException(msg, 404)

  /** A strange internal error. */
  class InternalException(msg: String) extends ExecutorException(msg, 500)
}
