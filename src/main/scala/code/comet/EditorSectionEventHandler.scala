package code.comet

import com.intellij.openapi.editor.markup.TextAttributes
import net.liftweb.http.ListenerManager
import net.liftweb.actor.LiftActor
import plugin._

object EditorSectionEventHandler extends LiftActor with ListenerManager {
  val model = new EditorSection()
  
  override protected def lowPriority = {
    case event: EditorEvent => {
      model.update(event)
      updateListeners()
    }
  }

  protected def createUpdate = model
}

class EditorSection() {
  private var openFiles: Map[FileId, EditorFile] = Map()
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
      case ContentChanged(id, file) => openFiles = openFiles.updated(id, file)
    }
  }
}

case class OpenFile(file: EditorFile, selected: Boolean) {
  def lines = file.lines

  def name = file.name
}

case class FileId(name: String)

case class EditorFile(name: String, lines: Vector[Line]) {
  def is(file: EditorFile) = name == file.name
}

case class Line(lineNumber: Int, tokens: Vector[Token])

case class Token(tokenType: String, value: String, attributes: TextAttributes)