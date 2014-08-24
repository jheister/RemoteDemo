package plugin

import code.comet.{Line, Token}
import com.intellij.openapi.editor.markup.TextAttributes
import org.scalatest.{FunSpec, MustMatchers, FunSuite}

class LineIteratorTest extends FunSpec with MustMatchers {
  describe("Line Iterator") {
    it("Groups sequentially provided tokens into lines") {
      val tokens = List(
        (0, Token("public", new TextAttributes())),
        (0, Token(" ", new TextAttributes())),
        (0, Token("class", new TextAttributes())),
        (0, Token(" ", new TextAttributes())),
        (0, Token("Blah", new TextAttributes())),
        (0, Token(" ", new TextAttributes())),
        (0, Token("{", new TextAttributes())),
        (1, Token("    ", new TextAttributes())),
        (1, Token("public", new TextAttributes()))
      )

      rendered(new LineIterator(tokens.iterator)) must be(
        """public class Blah {
          |    public""".stripMargin)
    }
  }

  private def rendered(iterator: Iterator[Line]) = iterator.map(line => line.tokens.map(_.value).mkString("")).mkString("\n")
}
