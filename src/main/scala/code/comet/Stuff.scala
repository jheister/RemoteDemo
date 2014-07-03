package code.comet

import net.liftweb.http.{CometListener, RenderOut, CometActor}
import net.liftweb.common.SimpleActor
import plugin.{SelectionChanged, FileClosed, FileOpened}

class Stuff extends CometActor with CometListener {
  protected def registerWith = EventBus

  var openFiles: Map[String, String] = Map()
  var selected: Option[String]= None


  override protected def dontCacheRendering: Boolean = true

  override def lowPriority = {
    case FileOpened(file) => openFiles = openFiles.updated(file.getPath, ""); reRender()
    case FileClosed(file) => openFiles = openFiles - file.getPath; reRender()
    case SelectionChanged(maybeFile) => selected = maybeFile.map(_.getPath); reRender()
    case e: Any => println("Got a random event " + e)
  }

  def render = {
    println("rendering " + openFiles)
    ".files *" #> openFiles.keys.map(file => {
      if (Some(file) == selected) {
        ".filename *" #> file &
        ".filename [class]" #> "selected"
      } else {
        ".filename *" #> file
      }
    })
  }
}
