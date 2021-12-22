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
package ai.mantik.executor

import ai.mantik.componently.Component
import akka.NotUsed
import akka.stream.scaladsl.Source
import akka.util.ByteString

import scala.concurrent.Future

/**
  * Responsible for providing reachable payload on the Executor Side.
  * Note: this component does not always have to be present.
  */
trait PayloadProvider extends Component {

  /**
    * Provide some data on Executor side.
    * @param tagId a user defined id, used to delete the file afterwards. May be used multiple times.
    * @param temporary if true, the file is temporary and will be automatically deleted after some time
    * @param data the data source (note: it can be requested multiple times.)
    * @param byteCount the length of the data
    * @param contentType content type.
    * @return the URL of the provided data
    */
  def provide(
      tagId: String,
      temporary: Boolean,
      data: Source[ByteString, NotUsed],
      byteCount: Long,
      contentType: String
  ): Future[String]

  /** Delete all files referenced by the given tag id. */
  def delete(tagId: String): Future[Unit]

}
