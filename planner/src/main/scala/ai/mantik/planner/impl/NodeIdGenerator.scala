package ai.mantik.planner.impl

/** Simple generator for generating Ids in a Graph. */
private[impl] class NodeIdGenerator {
  object lock
  private var nextId = 1
  def makeId(): String = {
    val id = lock.synchronized {
      val id = nextId
      nextId += 1
      id
    }
    id.toString
  }
}
