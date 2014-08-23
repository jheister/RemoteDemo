package code.comet

import java.awt.{Font, Color}

import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.vfs.VirtualFile
import net.liftweb.http.js.{JsCmd, JsCmds}
import net.liftweb.http.js.JsCmds.SetHtml
import net.liftweb.http.{SHtml, CometListener, RenderOut, CometActor}
import net.liftweb.common.SimpleActor
import plugin._

import scala.xml.NodeSeq

class Stuff extends CometActor with CometListener {
  val noop = "noop" #> ""

  protected def registerWith = EditorSectionEventHandler

  var section: EditorSection = new EditorSection()

  override protected def dontCacheRendering: Boolean = true

  override def lowPriority = {
    case (editorSection: EditorSection, ContentChanged(_, newFile, lines)) if (newFile.lines.size == editorSection.selectedFile.map(_.lines.size).getOrElse(0)) => {
      section = editorSection
      val updates: Seq[JsCmd] = lines.map {
        case Line(line, tokens) => {
          JsCmds.SetHtml("line-" + line, tokens.map(render(_)(<span class="code-token"></span>)).fold(NodeSeq.Empty)(_ ++ _))
        }
      }
      partialUpdate(JsCmds.seqJsToJs(updates))
    }
    case (editorSection: EditorSection, _) => {
      section = editorSection
      reRender()
    }
  }

  def render = {
    val lines = section.selectedFile.map(_.lines).getOrElse(Vector.empty)

    ".editor-tab *" #> section.openFilesList.map(file => {
      ".filename *" #> file.name &
        ".filename [class]" #> (if(file.selected) { "selected" } else { "" })
    }) &
    ".code-line" #> lines.map {
      case Line(lineNumber, tokens) =>
        "* [id]" #> ("line-" + lineNumber) &
        ".code-token" #> tokens.map(render(_))
    }
  }

  private def render(token: Token) = {
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
