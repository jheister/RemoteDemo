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
      documents = documents.updated(id, documents(id).apply(dc))

      println("===========================")
      println(documents(id).toString)
      println("===========================")

      println("Change is from line %s to %s.".format(dc.start, dc.end))
      println(dc.lines.map(_.tokens.map(_.value).mkString("")).mkString("\n"))

      println("===========================")


      sendListenersMessage(dc)
    }
    case Reset(id, lines) => {
      documents = documents.updated(id, DocumentContent(lines))
      sendListenersMessage(documents(id))
      println("===========================")
      println(documents(id).toString)
      println("===========================")
    }
  }
}
