package ai.mantik.executor.model

import io.circe.Decoder.Result
import io.circe._
import io.circe.generic.JsonCodec

sealed abstract class JobState(val name: String)

case object JobState {

  case object Pending extends JobState("pending")

  case object Running extends JobState("running")

  case object Finished extends JobState("finished")

  case object Failed extends JobState("failed")

  val All = Seq(Pending, Running, Finished, Failed)

  // Circe has problems auto-deriving them...
  implicit val encoder: Encoder[JobState] = new Encoder[JobState] {
    override def apply(a: JobState): Json = Json.fromString(a.name)
  }

  implicit val decoder: Decoder[JobState] = new Decoder[JobState] {
    override def apply(c: HCursor): Result[JobState] = c.value.asString match {
      case None => Left(DecodingFailure("Expected string", Nil))
      case Some(s) =>
        All.find(_.name == s) match {
          case Some(v) => Right(v)
          case None    => Left(DecodingFailure(s"Unknown type ${s}", Nil))
        }
    }
  }
}

/** Contains what we know about a job. */
@JsonCodec
case class JobStatus(
    state: JobState
)

object JobStatus