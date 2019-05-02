package ai.mantik.planner.select

private[select] object Utils {

  /** Combines a list of Either values into a single either value with list of values.. */
  def flatEither[L, T](in: List[Either[L, T]]): Either[L, List[T]] = {
    val builder = List.newBuilder[T]
    val it = in.iterator
    while (it.hasNext) {
      val n = it.next()
      n match {
        case Left(error) => return Left(error)
        case Right(ok)   => builder += ok
      }
    }
    Right(builder.result())
  }

}
