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
package ai.mantik.bridge.scalafn

import ai.mantik.ds.functional.FunctionConverter
import com.twitter.chill.ClosureCleaner
import shapeless._
import shapeless.ops.function.FnToProduct

/**
  * Captures a function, to be used to transport it via ClosureSerializer to ScalaFn-Bridge.
  * It must be constructed outside of unserializable code in order to get it to work
  * as the function converter is usually containing your current class inside it's dependencies.
  */
class UserDefinedFunction[I, O](
    val fn: Function[I, O],
    val functionConverter: FunctionConverter[I, O]
)

object UserDefinedFunction {

  /**
    * Generate a UserDefinedFunction from a generic function from one parameter to one parameter.
    * The parameters can be tuples.
    *
    * @param f the function
    */
  def apply[I, O](f: Function[I, O])(implicit functionConverter: FunctionConverter[I, O]): UserDefinedFunction[I, O] =
    new UserDefinedFunction[I, O](
      ClosureCleaner.clean(f),
      functionConverter
    )

  /** Generates a UserDefined function from a generic function. The function will be converted to
    * tupled variant by Shapeless.
    * @param f the function
    * @tparam F type of the function
    * @tparam I converted input type of the function (by shapeless)
    * @tparam O converted output type of the function (by shapeless)
    */
  def apply[F, I, O](f: F)(
      implicit fp: FnToProduct.Aux[F, I => O],
      fc: FunctionConverter[I, O]
  ): UserDefinedFunction[I, O] = {
    new UserDefinedFunction[I, O](fp(f), fc)
  }
}
