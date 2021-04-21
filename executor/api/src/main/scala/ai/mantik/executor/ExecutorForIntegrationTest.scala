package ai.mantik.executor

trait ExecutorForIntegrationTest {

  /** Returns the executor to use. */
  def executor: Executor

  /** Remove running containers */
  def scrap(): Unit

  /** Start the executor */
  def start(): Unit

  /** Stop running servers */
  def stop(): Unit
}
