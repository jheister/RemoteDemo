package code.comet

import java.awt.{Font, Color}

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.liftweb.common.SimpleActor
import net.liftweb.http.js.JsCmds.Run
import net.liftweb.http.js._
import net.liftweb.http.js.jquery.JqJE.{JqAttr, JqId}
import net.liftweb.http.js.jquery.JqJsCmds
import net.liftweb.http.{CometListener, RenderOut, CometActor}
import net.liftweb.util.CssSel

import scala.util.Random

class RenderedDocument extends CometActor with CometListener {
  var selected: Option[(FileId, DocumentContent)] = None

  var textSelection: Option[LinesSelected] = None

  override protected def registerWith = DocumentEvents

  override def lowPriority = {
    case Selected(id, content) => {
      selected = Some((id, DocumentContent(Random.alphanumeric.take(15).mkString).resetTo(content)))
      reRender()
    }
    case ClearSelected => selected = None; reRender()
    case dc: DocumentChange => {
      selected.filter(_._1 == dc.id).foreach {
        case (id, contents) => {
          val (updateCmd, newContents) = contents.apply(dc)

          selected = Some((id, newContents))
          partialUpdate(updateCmd)
        }
      }
    }
    case selection: LinesSelected => {
      selected.map(_._2.documentContent).foreach {lines =>
        val select = selection.pickFrom(lines).map(line => JqId(line.id).~>(JqAddClass("selection")).cmd)

        partialUpdate(JsCmds.seqJsToJs(deselect().toSeq ++ select))

        textSelection = Some(selection)
      }
    }

    case LineSelectionCleared => {
      deselect().foreach(cmd => partialUpdate(cmd))
      textSelection = None
    }
  }

  def deselect() = {
    selected.map(_._2.documentContent).map { lines =>
      JsCmds.seqJsToJs(textSelection.map { oldSelection =>
        oldSelection.pickFrom(lines).map(line => JqId(line.id).~>(JqRemoveClass("selection")).cmd)
      }.getOrElse(Vector.empty))
    }
  }

  case class JqAddClass(clazz: JsExp) extends JsExp with JsMember {
    override def toJsCmd = "addClass(" + clazz.toJsCmd + ")"
  }

  case class JqRemoveClass(clazz: JsExp) extends JsExp with JsMember {
    override def toJsCmd = "removeClass(" + clazz.toJsCmd + ")"
  }

  override def render = {
    ".editor *" #> ("*" #> selected.map(_._2.documentContent).getOrElse(Vector.empty).map(TokenRender.render(_))) &
    ".editor [id]" #> selected.map(_._2.id).getOrElse("empty-editor")
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

trait EditorEvent

case class FileOpened(id: FileId, file: File) extends EditorEvent

case class FileClosed(id: FileId) extends EditorEvent

case class SelectionChanged(newFile: Option[FileId]) extends EditorEvent

case class File(file: VirtualFile, project: Project)

case class DocumentChange(id: FileId, start: Int, end: Int, lines: Vector[Line])

case class Selected(id: FileId, content: Vector[Line])

case object ClearSelected

case class LinesSelected(startLine: Int, endLine: Int) {
  def pickFrom[T](lines: Vector[T]): Vector[T] =
    lines.drop(startLine).take(endLine - startLine + 1)
}

case object LineSelectionCleared