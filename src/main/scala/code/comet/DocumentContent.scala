package code.comet

import com.intellij.openapi.editor.markup.TextAttributes
import net.liftweb.http.js.jquery.JqJE.{JqRemove, JqId}
import net.liftweb.http.js.jquery.JqJsCmds
import net.liftweb.http.js.{JsMember, JsExp, JsCmds, JsCmd}
import net.liftweb.util.CssSel

import scala.util.Random
import scala.xml.{Elem, NodeSeq}
import net.liftweb.util.Helpers._

class DocumentContent() {
  private val id = Random.alphanumeric.take(15).mkString
  private var documentContent: Vector[RenderedLine] = Vector.empty
  private var textSelection: Vector[String] = Vector.empty

  def lines() = documentContent

  def theId() = id

  def apply(evt: EditorEvent): JsCmd = evt match {
    case change: DocumentChange => {
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

      documentContent = unchangedHead ++ toAdd ++ unchangedTail
      updateCmd
    }
    case selection: TextSelected => {
      val newSelected = selection.pickFrom(documentContent)
      val toDeselect = textSelection.filterNot(newSelected.contains)
      val toSelect = newSelected.filterNot(textSelection.contains)

      textSelection = newSelected
      JsCmds.seqJsToJs(toSelect.map(select) ++ toDeselect.map(deselect))
    }
    case TextSelectionCleared => {
      val cmd = JsCmds.seqJsToJs(textSelection.map(deselect))
      textSelection = Vector()
      cmd
    }
  }

  def resetTo(content: Vector[RenderedLine]) = {
    documentContent = content
    this
  }

  override def toString: String = {
    documentContent.map(_.tokens.map(_.value).mkString("")).mkString("\n")
  }

  def select(id: String) = JqId(id).~>(JqAddClass("selection")).cmd

  def deselect(id: String) = JqId(id).~>(JqRemoveClass("selection")).cmd

  case class JqAddClass(clazz: JsExp) extends JsExp with JsMember {
    override def toJsCmd = "addClass(" + clazz.toJsCmd + ")"
  }

  case class JqRemoveClass(clazz: JsExp) extends JsExp with JsMember {
    override def toJsCmd = "removeClass(" + clazz.toJsCmd + ")"
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