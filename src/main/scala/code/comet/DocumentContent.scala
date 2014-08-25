package code.comet

import net.liftweb.http.js.jquery.JqJE.{JqRemove, JqId}
import net.liftweb.http.js.jquery.JqJsCmds
import net.liftweb.http.js.{JsExp, JsCmds, JsCmd}
import net.liftweb.util.CssSel

import scala.util.Random
import scala.xml.{Elem, NodeSeq}
import net.liftweb.util.Helpers._

case class DocumentContent(documentContent: Vector[RenderedLine] = Vector.empty) {
  def apply(change: DocumentChange): (Option[JsCmd], DocumentContent) = {
    val (unchangedHead, remaining) = documentContent.splitAt(change.start)
    val (toRemove, unchangedTail) = remaining.splitAt(change.end - change.start + 1)
    val toAdd: Vector[RenderedLine] = change.lines.map(RenderedLine(Random.alphanumeric.take(15).mkString, _))

    val updateCmd: Option[JsCmd] = if (toRemove.isEmpty) { None } else {
      val sel: CssSel = "*" #> toAdd.map(TokenRender.render(_))
      val cmds = toRemove.tail.map(r => JqId(r.id).~>(JqRemove()).cmd) :+ JsCmds.Replace(toRemove.head.id, sel(<span class="code-line"><span class="code-token"></span></span>))

      Some(JsCmds.seqJsToJs(cmds))
    }

    (updateCmd, DocumentContent(documentContent = unchangedHead ++ toAdd ++ unchangedTail))
  }

  override def toString: String = {
    documentContent.map(_.tokens.map(_.value).mkString("")).mkString("\n")
  }

  def resetTo(lines: Vector[Line]) = DocumentContent(lines.map(RenderedLine(Random.alphanumeric.take(15).mkString, _)))
}

case class RenderedLine(id: String, line: Line) {
  def tokens = line.tokens
}
