package code.comet

import net.liftweb.actor.LiftActor
import net.liftweb.http.ListenerManager
import plugin.{DocumentContentLoader, Highlighter}

object DocumentEvents extends LiftActor with ListenerManager {
  override protected def createUpdate = null

  override protected def lowPriority = {
    case msg: Any => sendListenersMessage(msg)
  }
}