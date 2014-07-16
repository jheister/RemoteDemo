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
    ".editor-tab *" #> section.openFiles.reverse.map(file => {
      ".filename *" #> file.name &
        ".filename [class]" #> section.selectedFile.filter(_ == file).map(_ => "selected").getOrElse("")
    }) &
    ".code-line *" #> section.selectedFile.map(_.content.split("\n")).getOrElse(Array())
  }
}
