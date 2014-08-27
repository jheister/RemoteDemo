package code.comet

import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.liftweb.http.ListenerManager
import net.liftweb.actor.LiftActor
import plugin._

object EditorSectionEventHandler extends LiftActor with ListenerManager {
  val model = new EditorSection()
  
  override protected def lowPriority = {
    case event: EditorEvent => {
      model.update(event)
      sendListenersMessage((model, event))
    }
  }

  protected def createUpdate = (model, new EditorEvent {})
}

class EditorSection() {
  private var openFiles: Map[FileId, File] = Map()
  private var selected: Option[FileId] = None

  def openFilesList = (openFiles.map {
    case (id, file) => OpenFile(file, selected.find(_ == id).isDefined)
  }).toList.sortBy(_.name)

  def selectedFile = selected.flatMap(openFiles.get)

  def update(event: EditorEvent) = {
    event match {
      case FileOpened(id, file) => openFiles = openFiles.updated(id, file)
      case FileClosed(id, file) => openFiles = openFiles.filterKeys(_ != id)
      case SelectionChanged(maybeFile) => selected = maybeFile
    }
  }
}

case class OpenFile(file: File, selected: Boolean) {
  def name = file.file.getName
}

case class FileId(name: String)

case class EditorFile(name: String, lines: Vector[Line]) {
  def is(file: EditorFile) = name == file.name
}

case class Line(lineNumber: Int, tokens: Vector[Token])

case class Token(value: String, attributes: TextAttributes) {
  def splitAcrossLines: Vector[Token] =  value.split("\n", -1).map(Token(_, attributes)).toVector
}