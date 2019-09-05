
sealed trait Action[T]

case class SaveAction() extends Action[Unit]
case class Deploy() extends Action[String]

sealed trait PlanOp[T]
case object Empty extends PlanOp[Unit]
case class DeployAlgorithm() extends PlanOp[String]


def foo[T](in: Action[T]): PlanOp[T] = {
  in match {
    case _: SaveAction => Empty
    case _: Deploy => DeployAlgorithm()
  }
}