package code.comet

import com.intellij.openapi.editor.markup.TextAttributes
import org.scalatest.{FunSpec, MustMatchers}


class DocumentContentTest extends FunSpec with MustMatchers {
  describe("Document Contents") {
    it("Loads a change event when empty") {
      val content = new DocumentContent

      content.apply(DocumentChange(null, 0, 5, Vector(line("0"), line("1"), line("2"), line("3"), line("4"))))._2
             .toString must be(
        """0
          |1
          |2
          |3
          |4""".stripMargin)
    }

    it("Updates the second line when event provides replacement") {
      val content = new DocumentContent

      content.apply(DocumentChange(null, 0, 5, Vector(line("0"), line("1"), line("2"), line("3"), line("4"))))._2
             .apply(DocumentChange(null, 1, 1, Vector(line("new-1"))))._2
             .toString must be(
        """0
          |new-1
          |2
          |3
          |4""".stripMargin)
    }

    it("Updates the fourth line when event provides replacement") {
      val content = new DocumentContent

      content.apply(DocumentChange(null, 0, 5, Vector(line("0"), line("1"), line("2"), line("3"), line("4"))))._2
             .apply(DocumentChange(null, 4, 4, Vector(line("new-4"))))._2
             .toString must be(
        """0
          |1
          |2
          |3
          |new-4""".stripMargin)
    }

    it("Updates multiple lines") {
      val content = new DocumentContent

      content.apply(DocumentChange(null, 0, 5, Vector(line("0"), line("1"), line("2"), line("3"), line("4"))))._2
             .apply(DocumentChange(null, 2, 3, Vector(line("new-2"), line("new-3"))))._2
             .toString must be(
        """0
          |1
          |new-2
          |new-3
          |4""".stripMargin)
    }

    it("updates line 0 when split into two") {
      val content = new DocumentContent

      content.apply(DocumentChange(null, 0, 5, Vector(line("0"), line("1"), line("2"), line("3"), line("4"))))._2
             .apply(DocumentChange(null, 0, 0, Vector(line("new-0"), line("new-0-1"))))._2
             .apply(DocumentChange(null, 1, 1, Vector(line("    new-0-1"))))._2
             .toString must be(
        """new-0
          |    new-0-1
          |1
          |2
          |3
          |4""".stripMargin)
    }
  }

  def line(contents: String) = Line(0, Vector(Token(contents, new TextAttributes())))
}
