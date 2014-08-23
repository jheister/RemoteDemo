package code.comet

import net.liftweb.common.SimpleActor
import net.liftweb.http.{RenderOut, CometListener, CometActor}

class OpenFiles extends CometActor with CometListener {
  override protected def registerWith = EditorSectionEventHandler

  var files: List[OpenFile] = Nil

  override def lowPriority = {
    case (editorSection: EditorSection, _) => {
      files = editorSection.openFilesList
      reRender()
    }
  }

  override def render = {
    ".editor-tab *" #> files.map(file => {
      ".filename *" #> file.name &
      ".filename [class]" #> (if(file.selected) { "selected" } else { "" })
    })
  }
}
