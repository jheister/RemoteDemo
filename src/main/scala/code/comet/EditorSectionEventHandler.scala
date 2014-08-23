package code.comet

import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.vfs.VirtualFile
import net.liftweb.http.ListenerManager
import net.liftweb.actor.LiftActor
import plugin.{ContentChanged, SelectionChanged, FileClosed, FileOpened}

object EditorSectionEventHandler extends LiftActor with ListenerManager {
  var model = EditorSection()
  
  override protected def lowPriority = {
    case FileOpened(file) => model = model.openFile(file); updateListeners()
    case FileClosed(file) => model = model.closeFile(file); updateListeners()
    case SelectionChanged(maybeFile) => model = model.changeSelection(maybeFile); updateListeners()
    case ContentChanged(file) => model = model.update(file); updateListeners()
  }

  protected def createUpdate = model
}

case class EditorSection(openFiles: List[EditorFile] = Nil,
                         selectedFile: Option[String] = None) {
  def openFile(file: EditorFile) = copy(openFiles = file :: openFiles)

  def closeFile(file: String) = copy(openFiles = openFiles.filterNot(_.name == file))

  def changeSelection(maybeFile: Option[String]) = copy(selectedFile = maybeFile)

  def update(newFile: EditorFile) = copy(openFiles.map(possiblyUpdate(newFile)))

  def selected: Option[EditorFile] = selectedFile.flatMap(name => openFiles.find(_.name == name))

  def isSelected(file: EditorFile) = selected.map(_ == file).getOrElse(false)

  private def possiblyUpdate(newFile: EditorFile)(file: EditorFile) =
    if (file.is(newFile)) {
      newFile
    } else {
      file
    }

}

case class EditorFile(name: String, lines: Vector[Line]) {
  def is(file: EditorFile) = name == file.name
}

case class Line(lineNumber: Int, tokens: Vector[Token])

case class Token(tokenType: String, value: String, attributes: TextAttributes)