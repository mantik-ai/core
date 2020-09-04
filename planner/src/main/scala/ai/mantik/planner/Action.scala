package ai.mantik.planner

import ai.mantik.ds.element.Bundle
import ai.mantik.ds.helper.circe.DiscriminatorDependentCodec
import ai.mantik.elements.NamedMantikId
import io.circe.{ Decoder, Encoder }

/**
 * An Action is something the user requests to be executed.
 *
 * They are translated to a Plan by the [[Planner]].
 */
sealed trait Action[T] {
  /** Executes the action, when an implicit context is available. */
  def run()(implicit context: PlanningContext): T = context.execute(this)
}

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
  case class Deploy(item: MantikItem, nameHint: Option[String] = None, ingressName: Option[String] = None) extends Action[DeploymentState]

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
