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
package ai.mantik.planner

import ai.mantik.ds.element.Bundle
import ai.mantik.ds.helper.circe.DiscriminatorDependentCodec
import ai.mantik.elements.NamedMantikId
import io.circe.generic.semiauto
import io.circe.{Decoder, Encoder}

/**
  * An Action is something the user requests to be executed.
  *
  * They are translated to a Plan by the [[Planner]].
  */
sealed trait Action[T] {

  /** Executes the action, when an implicit context is available. */
  def run()(implicit context: PlanningContext): T = context.execute(this, ActionMeta())

  /** Executes the action with a name. */
  def run(name: String)(implicit context: PlanningContext): T = context.execute(this, ActionMeta(name = Some(name)))
}

/** Meta Information about an action */
case class ActionMeta(
    name: Option[String] = None
)

object Action {

  /** Fetch a dataset. */
  case class FetchAction(dataSet: DataSet) extends Action[Bundle]

  /** An item should be saved */
  case class SaveAction(item: MantikItem) extends Action[Unit]

  /** An item should be pushed (indicates also saving). */
  case class PushAction(item: MantikItem) extends Action[Unit]

  /**
    * Deploy some item.
    * Returns the deployment state of the item.
    */
  case class Deploy(item: MantikItem, nameHint: Option[String] = None, ingressName: Option[String] = None)
      extends Action[DeploymentState]

  private val codec: Encoder[Action[_]] with Decoder[Action[_]] = new DiscriminatorDependentCodec[Action[_]]("type") {
    override val subTypes = Seq(
      makeSubType[FetchAction]("fetch"),
      makeSubType[SaveAction]("save"),
      makeSubType[PushAction]("push"),
      makeSubType[Deploy]("deploy")
    )
  }

  implicit val rawEncoder: Encoder[Action[_]] = codec
  implicit val rawDecoder: Decoder[Action[_]] = codec
  implicit def encoder[T]: Encoder[Action[T]] = codec.contramap(x => x: Action[_])
  implicit def decoder[T]: Decoder[Action[T]] = codec.map { x =>
    // As each concrete type has a fixed T, this should work.
    x.asInstanceOf[Action[T]]
  }
}

object ActionMeta {
  implicit val encoder: Encoder[ActionMeta] = semiauto.deriveEncoder[ActionMeta]
  implicit val decoder: Decoder[ActionMeta] = semiauto.deriveDecoder[ActionMeta]
}
