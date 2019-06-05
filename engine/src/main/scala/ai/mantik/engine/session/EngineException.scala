package ai.mantik.engine.session

/** An exception in the mantik engine. */
class EngineException(msg: String) extends RuntimeException(msg)

/** A Session was not found. */
class SessionNotFoundException(sessionId: String) extends EngineException(
  s"Session ${sessionId} not found"
)

/** A Mantik item was not found in the repository. */
class ArtefactNotFoundException(name: String) extends EngineException(
  s"Artefact ${name} not found"
)

/** A Mantik item is not of the expected type. */
class ItemWrongTypeException(id: String, wanted: Class[_], got: AnyRef) extends EngineException(
  s"Item ${id} has wrong type, expected: ${wanted.getSimpleName}, got ${got.getClass.getSimpleName}"
)

/** A Required item was not found. */
class ItemNotFoundException(id: String) extends EngineException(
  s"No item ${id} found in session"
)