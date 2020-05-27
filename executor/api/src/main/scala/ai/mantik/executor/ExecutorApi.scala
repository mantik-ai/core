package ai.mantik.executor

import ai.mantik.executor.Errors.ExecutorException
import ai.mantik.executor.model.{ DeployServiceRequest, DeployServiceResponse, DeployedServicesQuery, DeployedServicesResponse, GrpcProxy, Job, JobStatus, ListWorkerRequest, ListWorkerResponse, PublishServiceRequest, PublishServiceResponse, StartWorkerRequest, StartWorkerResponse, StopWorkerRequest, StopWorkerResponse }
import net.reactivecore.fhttp.{ ApiBuilder, Output }

object ExecutorApi extends ApiBuilder {

  private def resultWithError[T <: Output](result: T) = {
    Output.ErrorSuccess(
      Output.circe[ExecutorException](),
      result
    )
  }

  val schedule = add(post("schedule")
    .expecting(input.circe[Job]())
    .responding(
      resultWithError(Output.text())
    ))

  val status = add(get("status")
    .expecting(input.AddQueryParameter("isolationSpace"))
    .expecting(input.AddQueryParameter("id"))
    .responding(
      resultWithError(
        Output.circe[JobStatus]()
      )
    )
  )

  val logs = add(get("logs")
    .expecting(input.AddQueryParameter("isolationSpace"))
    .expecting(input.AddQueryParameter("id"))
    .responding(
      resultWithError(
        Output.text()
      )
    )
  )

  val publishService = add(post("publishService")
    .expecting(input.circe[PublishServiceRequest]())
    .responding(
      resultWithError(
        Output.circe[PublishServiceResponse]()
      )
    )
  )

  val deployService = add(post("deployments")
    .expecting(input.circe[DeployServiceRequest]())
    .responding(
      resultWithError(
        Output.circe[DeployServiceResponse]()
      )
    )
  )

  val queryDeployedService = add(get("deployments")
    .expecting(input.circeQuery[DeployedServicesQuery])
    .responding(
      resultWithError(
        output.circe[DeployedServicesResponse]()
      )
    )
  )

  val deleteDeployedServices = add(delete("deployments")
    .expecting(input.circeQuery[DeployedServicesQuery])
    .responding(
      resultWithError {
        output.circe[Int]()
      }
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
