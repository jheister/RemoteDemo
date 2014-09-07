package code.comet

import com.intellij.openapi.editor.markup.TextAttributes
import net.liftweb.http.js.jquery.JqJE.{JqRemove, JqId}
import net.liftweb.http.js.jquery.JqJsCmds
import net.liftweb.http.js.{JsExp, JsCmds, JsCmd}
import net.liftweb.util.CssSel

import scala.util.Random
import scala.xml.{Elem, NodeSeq}
import net.liftweb.util.Helpers._

case class DocumentContent(id: String, documentContent: Vector[RenderedLine] = Vector.empty) {
  def apply(change: DocumentChange): (JsCmd, DocumentContent) = {
    val (unchangedHead, remaining) = documentContent.splitAt(change.start)
    val (toRemove, unchangedTail) = remaining.splitAt(change.end - change.start + 1)
    val toAdd: Vector[RenderedLine] = DocumentContent.forRendering(change.lines)

    if (toRemove.isEmpty && !documentContent.isEmpty) {
      throw new RuntimeException("No lines removed though document has lines: " + documentContent.size)
    }

    val sel: CssSel = "*" #> toAdd.map(TokenRender.render(_))
    val htmlToAdd = sel(<span class="code-line"><span class="code-token"></span></span>)

    val updateCmd: JsCmd = if (toRemove.isEmpty) {
      JqJsCmds.JqSetHtml(id, htmlToAdd)
    } else {
      JsCmds.seqJsToJs(toRemove.tail.map(r => JqId(r.id).~>(JqRemove()).cmd) :+ JsCmds.Replace(toRemove.head.id, htmlToAdd))
    }

    (updateCmd, copy(documentContent = unchangedHead ++ toAdd ++ unchangedTail))
  }

  override def toString: String = {
    documentContent.map(_.tokens.map(_.value).mkString("")).mkString("\n")
  }
}

object DocumentContent {
  def forRendering(lines: Vector[Line]) = {
    lines.map { line =>
      val meaningful = line.tokens.filterNot(_.value.isEmpty)

      val tokens = if (meaningful.isEmpty) {
        Vector(Token(" ", new TextAttributes(), 0))
      } else {
        meaningful
      }

      RenderedLine(Random.alphanumeric.take(15).mkString, tokens.map(RenderedToken(Random.alphanumeric.take(15).mkString, _)))
    }
  }
}

case class RenderedToken(id: String, token: Token) {
  def getFontType = token.attributes.getFontType

  def getBackgroundColor = token.attributes.getBackgroundColor

  def getForegroundColor = token.attributes.getForegroundColor

  def value = token.value
}

case class RenderedLine(id: String, tokens: Vector[RenderedToken])