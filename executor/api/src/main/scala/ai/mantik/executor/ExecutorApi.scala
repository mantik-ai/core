package ai.mantik.executor

import ai.mantik.executor.Errors.ExecutorException
import ai.mantik.executor.model.{ DeployServiceRequest, DeployServiceResponse, DeployedServicesQuery, DeployedServicesResponse, Job, JobStatus, PublishServiceRequest, PublishServiceResponse }
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
}
