package code.comet

import com.intellij.openapi.vfs.VirtualFile
import net.liftweb.http.{CometListener, RenderOut, CometActor}
import net.liftweb.common.SimpleActor
import plugin.{SelectionChanged, FileClosed, FileOpened}

class Stuff extends CometActor with CometListener {
  protected def registerWith = EditorSectionEventHandler

  var section: EditorSection = EditorSection()

  override protected def dontCacheRendering: Boolean = true

  override def lowPriority = {
    case editorSection: EditorSection => section = editorSection; reRender()
  }

  def render = {
    val selectedFileContent: Iterable[(String, String)] = section.selected.map(_.content).getOrElse(Nil)

    ".editor-tab *" #> section.openFiles.reverse.map(file => {
      ".filename *" #> file.name &
        ".filename [class]" #> (if(section.isSelected(file)) { "selected" } else { "" })
    }) &
    ".code-token" #> selectedFileContent.map {
      case (token, value) => "* *" #> value & "* [class+]" #> token
    }
  }
}
