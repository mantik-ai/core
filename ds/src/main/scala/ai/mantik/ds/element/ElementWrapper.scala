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
package ai.mantik.ds.element
import scala.language.implicitConversions

/** Helper structure which allows automatic conversion of primitives into Elements */
case class ElementWrapper(
  element: Element
)

object ElementWrapper {
  implicit def makeElementWrapper[T](value: T)(implicit valueEncoder: ValueEncoder[T]): ElementWrapper = {
    ElementWrapper(valueEncoder.wrap(value))
  }

  implicit def makeElementWrapperFromElement(value: Element): ElementWrapper = {
    ElementWrapper(value)
  }
}
