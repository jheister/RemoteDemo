package code.comet

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
                         selectedFile: Option[EditorFile] = None) {
  def openFile(file: EditorFile) = copy(openFiles = file :: openFiles)

  def closeFile(file: EditorFile) = copy(openFiles = openFiles.filterNot(file.is))

  def changeSelection(maybeFile: Option[EditorFile]) = copy(selectedFile = maybeFile)

  def update(newFile: EditorFile) = copy(openFiles.map(possiblyUpdate(newFile)), selectedFile.map(possiblyUpdate(newFile)))

  private def possiblyUpdate(newFile: EditorFile)(file: EditorFile) =
    if (file.is(newFile)) {
      newFile
    } else {
      file
    }

}

case class EditorFile(name: String, content: List[(String, String)]) {
  def is(file: EditorFile) = name == file.name
}

object EditorFile {
  def apply(file: VirtualFile, content: List[(String, String)]): EditorFile =
    EditorFile(file.getName, content)
}