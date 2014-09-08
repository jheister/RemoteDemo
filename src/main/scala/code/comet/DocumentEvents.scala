package code.comet

import com.intellij.openapi.fileEditor.{TextEditor, FileEditorManager}
import com.intellij.openapi.project.ProjectManager
import net.liftweb.actor.LiftActor
import net.liftweb.http.ListenerManager
import plugin.{FileEditorEvents, DocumentContentLoader, Highlighter}
import scala.collection.JavaConversions._

object DocumentEvents extends LiftActor with ListenerManager {
  override protected def createUpdate = null
//    ProjectManager.getInstance().getOpenProjects().foreach { project =>
//      val editorManager = FileEditorManager.getInstance(project)
//
//      val editorsToCreate = editorManager.getAllEditors()
//        .map(_.asInstanceOf[TextEditor].getEditor)
//        .map(FileEditorEvents.createdEvent)
//
//      val maybeSelect = Option(editorManager.getSelectedTextEditor).map(e => EditorSelected(FileEditorEvents.fileId(e))).toSeq
//
//      (editorsToCreate ++ maybeSelect)
//    }

  override protected def lowPriority = {
    case msg: Any => sendListenersMessage(msg)
  }
}