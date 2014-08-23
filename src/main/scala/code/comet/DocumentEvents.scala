package code.comet

import net.liftweb.actor.LiftActor
import net.liftweb.http.ListenerManager

object DocumentEvents extends LiftActor with ListenerManager {
  override protected def createUpdate = null

  override protected def lowPriority = {
    case evt: Any => sendListenersMessage(evt)
  }
}
