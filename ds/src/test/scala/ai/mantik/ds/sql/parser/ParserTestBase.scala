package ai.mantik.ds.sql.parser

import ai.mantik.ds.testutil.TestBase
import org.parboiled2.{ Parser, Rule1 }

abstract class ParserTestBase extends TestBase {
  type ParserImpl <: Parser
  protected def makeParser(s: String): ParserImpl

  def testEquality[T](rule: ParserImpl => Rule1[T], text: String, expected: T): Unit = {
    import Parser.DeliveryScheme.Either
    val parser = makeParser(text)
    // use __run instead of rule(parser).run()
    // see https://groups.google.com/forum/#!topic/parboiled-user/uwcy6MVZV5s
    val result = parser.__run(rule(parser))
    result match {
      case Left(error) =>
        fail(error.format(parser))
      case Right(value) =>
        if (value != expected) {
          fail(s"When parsing ${text}: ${value} is not equal to ${expected}")
        }
    }
  }
}
