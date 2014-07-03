package code.comet

import net.liftweb.http.ListenerManager
import net.liftweb.actor.LiftActor
import plugin.FileOpened

object EventBus extends LiftActor with ListenerManager {


  override protected def lowPriority = {
    case msg: Any =>     println("Got event " + msg); sendListenersMessage(msg)
  }

  protected def createUpdate = "Initial"
}
