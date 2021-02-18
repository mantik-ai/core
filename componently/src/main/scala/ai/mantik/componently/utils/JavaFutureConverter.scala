package ai.mantik.componently.utils

import java.util.concurrent.CompletionStage
import java.util.function.BiConsumer
import scala.concurrent.{ Future, Promise }

object JavaFutureConverter {

  /** Extends CompletionStage with conversions to Scala.  */
  implicit class CompletionStageExtensions[T](completionStage: CompletionStage[T]) {
    def asScala: Future[T] = {
      val promise = Promise[T]
      completionStage.whenComplete(new BiConsumer[T, Throwable] {
        override def accept(t: T, u: Throwable): Unit = {
          if (u != null) {
            promise.failure(u)
          } else {
            promise.success(t)
          }
        }
      })
      promise.future
    }
  }
}
