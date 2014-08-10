package code.comet

import java.awt.Color

import com.intellij.openapi.editor.markup.TextAttributes
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
    val selectedFileContent: Iterable[(String, String, TextAttributes)] = section.selected.map(_.content).getOrElse(Nil)

    ".editor-tab *" #> section.openFiles.reverse.map(file => {
      ".filename *" #> file.name &
        ".filename [class]" #> (if(section.isSelected(file)) { "selected" } else { "" })
    }) &
    ".code-token" #> selectedFileContent.map {
      case (token, value, attributes) => {
        "* *" #> value &
        "* [class+]" #> token &
        "* [style+]" #> Option(attributes.getForegroundColor).map(toHexString).map("color:%s;".format(_)).getOrElse("")
      }
    }
  }

  private def toHexString(color: Color) = {
    "#%02x%02x%02x".format(color.getRed, color.getGreen, color.getBlue)
  }
}
