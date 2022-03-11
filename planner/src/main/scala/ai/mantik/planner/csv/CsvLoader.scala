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
package ai.mantik.planner.csv

import ai.mantik.ds.TabularData
import ai.mantik.planner.repository.ContentTypes
import ai.mantik.planner._
import akka.NotUsed
import akka.stream.scaladsl.{FileIO, Source => AkkaSource}
import akka.util.ByteString

import java.io.{File, FileNotFoundException}
import java.net.URI

/** Loader DSLs for CSV Files */
object CsvLoader {

  /** A Local file, will be serialized */
  def file(fileName: String, options: CsvOptions = CsvOptions()): CsvLoader.SourceSelected = {
    SourceSelected(new File(fileName).toURI.toString, options, serialize = true)
  }

  /** A Remote (HTTP/HTTPS) Url. */
  def url(url: String, options: CsvOptions = CsvOptions()): CsvLoader.SourceSelected = {
    SourceSelected(url, options, serialize = false)
  }

  /** A Source is selected, waiting for data format. */
  case class SourceSelected(url: String, options: CsvOptions, serialize: Boolean) {
    def withFormat(format: TabularData): SourceAndFormatSelected = SourceAndFormatSelected(this, format)

    /** Skip the first line of the CSV File. */
    def skipHeader: SourceSelected = withOption(_.copy(skipHeader = Some(true)))

    /** Set some CSV Options. */
    def withOption(f: CsvOptions => CsvOptions): SourceSelected = copy(options = f(options))
  }

  /** Source and format selected, can load now. */
  case class SourceAndFormatSelected(source: SourceSelected, format: TabularData) {

    /**
      * Convert CSV into DataSet
      * Note: this can incur transferring the CSV File content to Mantik Engine.
      */
    def load()(implicit planningContext: PlanningContext): DataSet = {

      val maybeUrl = if (source.serialize) {
        None
      } else {
        Some(source.url)
      }

      val header = CsvMantikHeaderBuilder(
        maybeUrl,
        format,
        source.options
      ).convert

      val maybeFileId: Option[String] = if (source.serialize) {
        val akkaSource = loadUrl(source.url)
        val (fileId, _) = planningContext.storeFile(
          ContentTypes.Csv,
          akkaSource,
          temporary = true
        )
        Some(fileId)
      } else {
        None
      }

      DataSet(
        source = Source.constructed(
          maybeFileId
            .map { fileId =>
              PayloadSource.Loaded(fileId, ContentTypes.Csv)
            }
            .getOrElse(PayloadSource.Empty)
        ),
        mantikHeader = header,
        bridge = BuiltInItems.CsvBridge
      )
    }
  }

  private def loadUrl(url: String): AkkaSource[ByteString, NotUsed] = {
    // Currently only file URLs are allowed
    val file = new File(new URI(url))
    if (!file.exists()) {
      throw new FileNotFoundException(s"File ${file} not found")
    }
    val path = file.toPath
    FileIO.fromPath(path).mapMaterializedValue { _ => NotUsed }
  }
}
