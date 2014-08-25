package code.comet

import com.intellij.openapi.editor.markup.TextAttributes
import net.liftweb.util.{CssSel, CanBind}
import org.scalatest.{MustMatchers, FunSpec, FunSuite}

class TokenRenderTest extends FunSpec with MustMatchers {
  describe("Token rendering") {
    it("renders multiple tokens in one line") {
      val line = RenderedLine("line-1", Line(0, Vector(Token("hello", new TextAttributes()), Token("world", new TextAttributes()))))

      val html = TokenRender.render(line)(<span class="code-line"><span class="code-token"></span></span>)


      (html \@ "id") must be("line-1")
      (html \ "span").map(_.text).toList must be(List("hello", "world"))
    }

    it("renders multiple lines") {
      val line1 = RenderedLine("line-1", Line(0, Vector(Token("hello", new TextAttributes()), Token("world", new TextAttributes()))))
      val line2 = RenderedLine("line-2", Line(0, Vector(Token("another", new TextAttributes()), Token("line", new TextAttributes()))))

      import net.liftweb.util.Helpers._

      val selector: CssSel = "*" #> Vector(line1, line2).map(TokenRender.render)

      val html = selector(<span class="code-line"><span class="code-token"></span></span>).theSeq.toList

      html.map(_ \@ "id") must be(List("line-1", "line-2"))
    }
  }
}