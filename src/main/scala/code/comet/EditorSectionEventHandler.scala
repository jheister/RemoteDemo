package code.comet

import com.intellij.openapi.vfs.VirtualFile
import net.liftweb.http.ListenerManager
import net.liftweb.actor.LiftActor
import plugin.{SelectionChanged, FileClosed, FileOpened}

object EditorSectionEventHandler extends LiftActor with ListenerManager {
  var model = EditorSection()
  
  override protected def lowPriority = {
    case FileOpened(file) => model = model.openFile(file); updateListeners()
    case FileClosed(file) => model = model.closeFile(file); updateListeners()
    case SelectionChanged(maybeFile) => model = model.changeSelection(maybeFile); updateListeners()
  }

  protected def createUpdate = model
}

case class EditorSection(openFiles: List[EditorFile] = Nil,
                         selectedFile: Option[EditorFile] = None) {
  def openFile(file: EditorFile) = copy(openFiles = file :: openFiles)

  def closeFile(file: EditorFile) = copy(openFiles = openFiles.filterNot(_ == file))

  def changeSelection(maybeFile: Option[EditorFile]) = copy(selectedFile = maybeFile)
}

case class EditorFile(name: String, content: String)

object EditorFile {
  def apply(file: VirtualFile): EditorFile =
    EditorFile(file.getName, new String(file.contentsToByteArray(), file.getCharset))//todo: get content from editor, not file
}