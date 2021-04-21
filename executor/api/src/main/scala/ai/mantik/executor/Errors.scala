package ai.mantik.executor

import io.circe.Decoder.Result
import io.circe._

/** Errors from Executor. */
object Errors {

  class ExecutorException(val msg: String, val statusCode: Int, cause: Throwable = null)
      extends RuntimeException(msg, cause)

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
          case 400 => new BadRequestException(msg)
          case 404 => new NotFoundException(msg)
          case 409 => new ConflictException(msg)
          case 500 => new InternalException(msg)
          case 502 => new CouldNotExecutePayload(msg)
          case _   => new ExecutorException(msg, code)
        }
      }
    }
  }

  /** A resource was not found. */
  class NotFoundException(msg: String, cause: Throwable = null) extends ExecutorException(msg, 404, cause)

  /** A Conflict (e.g. Exists Already). */
  class ConflictException(msg: String) extends ExecutorException(msg, 409)

  /** The request was not ok */
  class BadRequestException(msg: String) extends ExecutorException(msg, 400)

  /** A strange internal error. */
  class InternalException(msg: String) extends ExecutorException(msg, 500)

  /** There was a problem with the payload. */
  class CouldNotExecutePayload(msg: String) extends ExecutorException(msg, 502)
}
