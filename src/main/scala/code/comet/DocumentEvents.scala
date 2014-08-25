package code.comet

import net.liftweb.actor.LiftActor
import net.liftweb.http.ListenerManager

object DocumentEvents extends LiftActor with ListenerManager {
  override protected def createUpdate = null

  var theFile: Option[FileId] = None
  var documents: Map[FileId, DocumentContent] = Map().withDefaultValue(DocumentContent())

  override protected def lowPriority = {
    case Show(id) => theFile = Some(id); sendListenersMessage(documents(id))
    case Clear => theFile = None
    case dc@DocumentChange(id, _, _, _) => {
      documents = documents.updated(id, documents(id).apply(dc)._2)

      if (theFile == Some(id)) {
        sendListenersMessage(dc)
      }
    }
    case Reset(id, lines) => {
      documents = documents.updated(id, DocumentContent().resetTo(lines))

      if (theFile == Some(id)) {
        sendListenersMessage(documents(id))
      }
    }
  }
}
