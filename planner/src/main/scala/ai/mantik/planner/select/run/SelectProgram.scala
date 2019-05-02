package ai.mantik.planner.select.run

/**
 * A compiled Select Statement.
 *
 * @param selector a program doing the selection. called with a row, returns bool on the stack if it succeeds.
 * @param projector a program doing the projection. called with a row, returns the translated row.
 */
case class SelectProgram(
    selector: Program,
    projector: Program
)
