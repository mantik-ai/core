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
package ai.mantik.executor.common.test.integration

import java.util.Base64

import akka.util.ByteString

object TestData {

  // Source: SelectSpec, just dumping it out as Base64
  // Created like this:
  // val conf = MantikInitConfiguration(header = header.toJson)
  //    val initRequest = InitRequest(
  //      sessionId = "abcd",
  //      inputs = Vector(ConfigureInputPort(ContentTypes.MantikBundleContentType)), outputs = Vector(ConfigureOutputPort(ContentTypes.MantikBundleContentType)),
  //      configuration = Some(Any.pack(conf))
  //    )
  //    val asBase64 = Base64.getEncoder.encodeToString(initRequest.toByteArray)
  //    println("==Header===")
  //    println(asBase64.grouped(80).mkString("\n"))
  //    println("==STOP==")
  private val serializedInitRequest =
    """CgRhYmNkEqcHCkN0eXBlLmdvb2dsZWFwaXMuY29tL2FpLm1hbnRpay5icmlkZ2UucHJvdG9zLk1hbnRp
    |a0luaXRDb25maWd1cmF0aW9uEt8GCtwGewogICJraW5kIiA6ICJjb21iaW5lciIsCiAgImJyaWRnZSIg
    |OiAiYnVpbHRpbi9zZWxlY3QiLAogICJpbnB1dCIgOiBbCiAgICB7CiAgICAgICJ0eXBlIiA6ICJ0YWJ1
    |bGFyIiwKICAgICAgImNvbHVtbnMiIDogewogICAgICAgICJ4IiA6ICJpbnQzMiIsCiAgICAgICAgInki
    |IDogInN0cmluZyIKICAgICAgfQogICAgfQogIF0sCiAgIm91dHB1dCIgOiBbCiAgICB7CiAgICAgICJ0
    |eXBlIiA6ICJ0YWJ1bGFyIiwKICAgICAgImNvbHVtbnMiIDogewogICAgICAgICJ5IiA6ICJpbnQzMiIK
    |ICAgICAgfQogICAgfQogIF0sCiAgInByb2dyYW0iIDogewogICAgInR5cGUiIDogInNlbGVjdCIsCiAg
    |ICAiaW5wdXQiIDogewogICAgICAidHlwZSIgOiAic291cmNlIiwKICAgICAgInBvcnQiIDogMCwKICAg
    |ICAgInJlc3VsdCIgOiB7CiAgICAgICAgInR5cGUiIDogInRhYnVsYXIiLAogICAgICAgICJjb2x1bW5z
    |IiA6IHsKICAgICAgICAgICJ4IiA6ICJpbnQzMiIsCiAgICAgICAgICAieSIgOiAic3RyaW5nIgogICAg
    |ICAgIH0KICAgICAgfQogICAgfSwKICAgICJzZWxlY3RvciIgOiBudWxsLAogICAgInByb2plY3RvciIg
    |OiB7CiAgICAgICJhcmdzIiA6IDEsCiAgICAgICJyZXRTdGFja0RlcHRoIiA6IDEsCiAgICAgICJzdGFj
    |a0luaXREZXB0aCIgOiAxLAogICAgICAib3BzIiA6IFsKICAgICAgICAiZ2V0IiwKICAgICAgICAwCiAg
    |ICAgIF0KICAgIH0sCiAgICAicmVzdWx0IiA6IHsKICAgICAgInR5cGUiIDogInRhYnVsYXIiLAogICAg
    |ICAiY29sdW1ucyIgOiB7CiAgICAgICAgInkiIDogImludDMyIgogICAgICB9CiAgICB9CiAgfSwKICAi
    |cXVlcnkiIDogIlNFTEVDVCAoXCJ4XCIpIEFTIFwieVwiIEZST00gJDAiCn0aHQobYXBwbGljYXRpb24v
    |eC1tYW50aWstYnVuZGxlIh0KG2FwcGxpY2F0aW9uL3gtbWFudGlrLWJ1bmRsZQ==""".stripMargin.replace("\n", "").trim

  /** A Serialized Init Request for the select bridge */
  val selectInitRequest: ByteString = ByteString(Base64.getDecoder.decode(serializedInitRequest))
}
