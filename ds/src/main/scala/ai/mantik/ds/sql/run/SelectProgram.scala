package ai.mantik.ds.sql.run

import io.circe.{ Decoder, ObjectEncoder }
import io.circe.generic.semiauto

/**
 * A compiled Select Statement.
 *
 * @param selector a program doing the selection. called with a row, returns bool on the stack if it succeeds. If empty, the row is always selected.
 * @param projector a program doing the projection. called with a row, returns the translated row. If empty, the row is returned untouched.
 *
 * Note: this is part of the API to the Select Bridge.
 */
case class SelectProgram(
    selector: Option[Program],
    projector: Option[Program]
)

object SelectProgram {
  implicit val encoder: ObjectEncoder[SelectProgram] = semiauto.deriveEncoder[SelectProgram]
  implicit val decoder: Decoder[SelectProgram] = semiauto.deriveDecoder[SelectProgram]
}
