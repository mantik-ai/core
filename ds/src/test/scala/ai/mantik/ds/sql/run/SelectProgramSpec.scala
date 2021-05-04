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
package ai.mantik.ds.sql.run

import ai.mantik.ds.sql.Select
import ai.mantik.ds.testutil.TestBase
import ai.mantik.ds.{FundamentalType, TabularData}
import io.circe.syntax._

class SelectProgramSpec extends TestBase {

  it should "be serializable" in {
    val sampleData = TabularData(
      "x" -> FundamentalType.Int32,
      "y" -> FundamentalType.Int32,
      "z" -> FundamentalType.StringType
    )
    val select = Select.parse(sampleData, "select x where y = 1").getOrElse(fail)
    val program = Compiler.compile(select).getOrElse(fail)
    (program: TableGeneratorProgram).asJson.as[TableGeneratorProgram] shouldBe Right(program)
  }
}
