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
  var selected: Option[FileId] = None
  var editors: Map[FileId, DocumentContent] = Map()

  override protected def registerWith = DocumentEvents

  override def lowPriority = {
    case EditorCreated(id, content) => {
      val newContent: DocumentContent = new DocumentContent
      newContent.resetTo(DocumentContent.forRendering(content))
      editors = editors.updated(id, newContent)
    }
    case EditorClosed(id) => editors = editors.filterKeys(_ != id)
    case EditorSelected(id) => selected = Some(id); reRender()
    case ClearSelected => selected = None; reRender()
    case dc: DocumentChange => {
      val cmd = editors(dc.id).apply(dc)
      partialUpdate(cmd)
    }
    case selection: TextSelected => {
      selected.foreach(c => {
        val cmd = editors(c).apply(selection)
        partialUpdate(cmd)
      })
    }

    case TextSelectionCleared => {
      selected.foreach(c => {
        val cmd = editors(c).apply(TextSelectionCleared)
        partialUpdate(cmd)
      })
    }
  }

  override def render = {
    ".editor *" #> ("*" #> selected.map(editors(_).lines()).getOrElse(Vector.empty).map(TokenRender.render(_))) &
    ".editor [id]" #> selected.map(editors(_).theId()).getOrElse("empty-editor")
  }
}

object TokenRender {
  import net.liftweb.util.Helpers._

    def render(line: RenderedLine): CssSel = {
      ".code-line [id]" #> line.id &
      ".code-token" #> line.tokens.map(t => render(t))
    }

  def render(token: RenderedToken) = {
    "* *" #> token.value &
      "* [id]" #> token.id &
      "* [style+]" #> Option(token.getForegroundColor).map(toHexString).map("color:%s;".format(_)).getOrElse("") &
      "* [style+]" #> Option(token.getBackgroundColor).map(toHexString).map("background-color:%s;".format(_)).getOrElse("") &
      "* [style+]" #> Option(token.getFontType).map(toWeight).getOrElse("")
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

case class DocumentChange(id: FileId, start: Int, end: Int, lines: Vector[Line]) extends EditorEvent

case class EditorCreated(id: FileId, content: Vector[Line])

case class EditorClosed(id: FileId)

case class EditorSelected(id: FileId)

case object ClearSelected

case class TextPosition(line: Int, pos: Int)

case class TextSelected(start: TextPosition, end: TextPosition) extends EditorEvent {
  def pickFrom(lines: Vector[RenderedLine]): Vector[String] = {
    val selectable = lines.drop(start.line).take(end.line - start.line + 1).map(_.tokens).toList

    val filteredStart = filterHead(selectable, _.dropWhile(_.token.start < start.pos))
    val filteredEnd = filterHead(filteredStart.reverse, _.takeWhile(t => (t.token.start + t.value.length) <= end.pos)).reverse

    filteredEnd.flatMap(_.map(_.id)).toVector
  }

  private def filterHead(list: List[Vector[RenderedToken]], f: Vector[RenderedToken] => Vector[RenderedToken]) = {
    val (head :: tail) = list
    f(head) :: tail
  }
}

case object TextSelectionCleared extends EditorEvent