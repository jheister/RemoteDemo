package code.comet

import java.awt.{Font, Color}

import net.liftweb.common.SimpleActor
import net.liftweb.http.{CometListener, RenderOut, CometActor}
import net.liftweb.util.CssSel

import scala.xml.NodeSeq


class RenderedDocument extends CometActor with CometListener {
  var contents = DocumentContent()

  override protected def registerWith = DocumentEvents

  override def lowPriority = {
    case replacecmentDoc: DocumentContent => contents = replacecmentDoc; reRender()
    case dc: DocumentChange => {
      val (updateCmd, newContents) = contents.apply(dc)

      contents = newContents

      if (updateCmd.isDefined) {
        partialUpdate(updateCmd.get)
      } else {
        reRender()
      }
    }
  }

  override def render = {
    ".editor *" #> contents.documentContent.map(TokenRender.render(_))
  }
}

object TokenRender {
  import net.liftweb.util.Helpers._

    def render(line: RenderedLine): CssSel = {
      ".code-line [id]" #> line.id &
      ".code-token" #> line.tokens.map(t => render(t))
    }

  def render(token: Token) = {
    "* *" #> token.value &
      "* [style+]" #> Option(token.attributes.getForegroundColor).map(toHexString).map("color:%s;".format(_)).getOrElse("") &
      "* [style+]" #> Option(token.attributes.getBackgroundColor).map(toHexString).map("background-color:%s;".format(_)).getOrElse("") &
      "* [style+]" #> Option(token.attributes.getFontType).map(toWeight).getOrElse("")
  }

  private def toWeight(id: Int) = id match {
    case Font.PLAIN => "font-weight:normal;font-style:normal;"
    case Font.BOLD => "font-weight:bold;font-style:normal;"
    case Font.ITALIC => "font-weight:normal;font-style:italic;"
    case e => throw new RuntimeException("Unknown weight: " + e)
  }

  private def toHexString(color: Color) = {
    "#%02x%02x%02x".format(color.getRed, color.getGreen, color.getBlue)
  }
}

case class Reset(id: FileId, lines: Vector[Line])

case class DocumentChange(id: FileId, start: Int, end: Int, lines: Vector[Line])

case class Show(id: FileId)

case object Clear