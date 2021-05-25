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
package ai.mantik.bridge.scalafn.bridge

import ai.mantik.componently.AkkaRuntime
import ai.mantik.ds.element.{RootElement, TabularRow}
import ai.mantik.ds.formats.StreamCodec
import ai.mantik.ds.functional.FunctionType
import ai.mantik.mnp.server.ServerTask
import akka.{Done, NotUsed}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.ByteString

import scala.concurrent.{Future, Promise}
import scala.util.Success

/** A Single 1:1 Transformation */
case class Transformation(
    functionType: FunctionType,
    flow: Flow[RootElement, RootElement, NotUsed]
)

/** A Server task using a transformation to execute */
class TransformingTask(
    inputContentType: String,
    outputContentType: String,
    transformation: Transformation
)(implicit akkaRuntime: AkkaRuntime)
    extends ServerTask {
  private val inputPromise = Promise[Source[ByteString, NotUsed]]
  private val resultPromise = Promise[Done]
  import ai.mantik.componently.AkkaHelper._

  private val inputDecoder = StreamCodec.decoder(
    transformation.functionType.input,
    inputContentType
  )
  private val outputEncoder = StreamCodec.encoder(
    transformation.functionType.output,
    outputContentType
  )

  override def push(id: Int, source: Source[ByteString, NotUsed]): Future[Done] = {
    inputPromise.complete(Success(source))
    resultPromise.future
  }

  override def pull(id: Int): Source[ByteString, NotUsed] = {
    // Use this helper for completing the task
    val monitor = Sink.onComplete[ByteString] { result =>
      resultPromise.tryComplete(result)
    }
    val futureSource: Future[Source[ByteString, NotUsed]] = inputPromise.future.map { input =>
      input
        .via(inputDecoder)
        .via(transformation.flow)
        .via(outputEncoder)
    }

    Source
      .fromFutureSource(futureSource)
      .mapMaterializedValue(_ => NotUsed)
      .wireTap(monitor)
  }

  override def finished: Future[Done] = {
    resultPromise.future
  }

  override def shutdown(): Unit = {
    // Not needed
  }
}
