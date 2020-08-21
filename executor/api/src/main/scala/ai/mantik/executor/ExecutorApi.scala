package ai.mantik.executor

import ai.mantik.executor.Errors.ExecutorException
import ai.mantik.executor.model._
import net.reactivecore.fhttp.{ ApiBuilder, Output }

object ExecutorApi extends ApiBuilder {

  private def resultWithError[T <: Output](result: T) = {
    Output.ErrorSuccess(
      Output.circe[ExecutorException](),
      result
    )
  }

  val publishService = add(post("publishService")
    .expecting(input.circe[PublishServiceRequest]())
    .responding(
      resultWithError(
        Output.circe[PublishServiceResponse]()
      )
    )
  )

  val nameAndVersion = add(get("version")
    .responding(
      resultWithError {
        Output.text()
      }
    )
  )

  val grpcProxy = add(get("grpcProxy")
    .expecting(input.AddQueryParameter("isolationSpace"))
    .responding(
      resultWithError(
        output.circe[GrpcProxy]()
      )
    )
  )

  val startWorker = add(post("worker")
    .expecting(input.circe[StartWorkerRequest]())
    .responding(
      resultWithError(
        output.circe[StartWorkerResponse]()
      )
    )
  )

  val listWorker = add(get("worker")
    .expecting(input.circeQuery[ListWorkerRequest])
    .responding(
      resultWithError(
        output.circe[ListWorkerResponse]()
      )
    )
  )

  val stopWorker = add(delete("worker")
    .expecting(input.circeQuery[StopWorkerRequest])
    .responding(
      resultWithError(
        output.circe[StopWorkerResponse]()
      )
    )
  )
}
