/*
 * This file is part of the Mantik Project.
 * Copyright (c) 2020-2021 Mantik UG (Haftungsbeschränkt)
 * Authors: See AUTHORS file
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.
 *
 * Additionally, the following linking exception is granted:
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with other code, such other code is not for that reason
 * alone subject to any of the requirements of the GNU Affero GPL
 * version 3.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license.
 */
package ai.mantik.ui.server
import ai.mantik.ui.StateService
import ai.mantik.ui.model.{ErrorResponse, OperationId}
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.ParameterDirectives.ParamMagnet
import akka.http.scaladsl.server.{Route, RouteConcatenation, RouteResult}
import com.typesafe.scalalogging.Logger
import io.circe.{Encoder, Json}
import io.circe.syntax._

import java.nio.file.Paths
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/** HTTP Router for the embedded UI */
class UiRouter(resourceClassLoader: ClassLoader, stateService: StateService)(implicit ec: ExecutionContext) {
  val contentPrefix = "client/"
  val prefixPath = Paths.get(contentPrefix).toAbsolutePath

  /** Serves /api Requests */
  private val apiRoute = RouteConcatenation.concat(
    path("version") {
      pathEnd {
        get {
          respondJson(stateService.version)
        }
      }

    },
    path("settings") {
      pathEnd {
        get {
          respondJson(stateService.settings)
        }
      }
    },
    pathPrefix("jobs") {
      RouteConcatenation.concat(
        pathEnd {
          get {
            poll { pollVersion =>
              respondAsyncJson(stateService.jobs(pollVersion))
            }
          }
        },
        pathPrefix(Segment) { jobId =>
          RouteConcatenation.concat(
            pathEnd {
              poll { pollVersion =>
                respondAsyncJson(stateService.job(jobId, pollVersion))
              }
            },
            pathPrefix("operations") {
              pathPrefix(Segment) { operationIdString =>
                OperationId.fromString(operationIdString) match {
                  case Success(operationId) =>
                    pathPrefix("graph") {
                      pathEnd {
                        poll { pollVersion =>
                          respondAsyncJson(stateService.runGraph(jobId, operationId, pollVersion))
                        }
                      }
                    }
                  case Failure(bad) =>
                    _ => Future.successful(error(400, Some("Bad operationId")))
                }
              }
            }
          )
        }
      )
    },
    { _ =>
      Future.successful(error(404, Some("Not found")))
    }
  )

  /** Handles the pollVersion for Long polling. */
  private def poll(f: Option[Long] => Route): Route = {
    parameters("poll".as[Long].?) { v => f(v) }
  }

  /** Main Route */
  val route: Route = {
    RouteConcatenation.concat(
      // Serve main content
      pathSingleSlash {
        get {
          serveMainIndexHtml()
        }
      },
      // Serve API
      pathPrefix("api") {
        apiRoute
      },
      // Serve assets
      serveAssets(),
      // Redirect all to index.html, let the JavaScript router display 404 if available
      serveMainIndexHtml()
    )
  }

  /* Serve assets inside resources. */
  private def serveAssets(): Route = {
    // not serve .html
    val endings = Seq("css", "js", "ttf", "eot", "woff", "png", "svg", "ico")
    RouteConcatenation.concat(
      endings.map { ending =>
        servePathSuffix(ending)
      }: _*
    )
  }

  private def servePathSuffix(ending: String): Route = { request =>
    val r = (s".*(\\.${ending})").r
    pathSuffixTest(r) { path =>
      respondFromResource() ~ { _ =>
        Future.successful(error(404, Some("Route not found")))
      }
    }(request)
  }

  private def serveMainIndexHtml(): Route = { request =>
    getFromResource("client/index.html", ContentTypes.`text/html(UTF-8)`, resourceClassLoader)(request)
  }

  private def respondFromResource(): Route = {
    getFromResourceDirectory("client", resourceClassLoader)
  }

  private def respondJson[T](value: T)(implicit e: Encoder[T]): Route = {
    respondAsyncJson(Future.successful(value))
  }

  private def respondAsyncJson[T](value: Future[T])(implicit e: Encoder[T]): Route = { requestContext =>
    value.map { result =>
      RouteResult.Complete(
        HttpResponse().withEntity(
          ContentTypes.`application/json`,
          e(result).spaces2
        )
      )
    } recover { case e: StateService.EntryNotFoundException =>
      error(404, Option(e.getMessage))
    }
  }

  private def error(code: Int, message: Option[String]): RouteResult.Complete = {
    RouteResult.Complete(
      HttpResponse(status = StatusCodes.NotFound).withEntity(
        ContentTypes.`application/json`,
        ErrorResponse(
          code,
          message
        ).asJson.spaces2
      )
    )
  }
}
