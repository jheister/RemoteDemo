package plugin

import com.intellij.openapi.components.ApplicationComponent
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.nio.SelectChannelConnector
import org.eclipse.jetty.webapp.WebAppContext
import org.eclipse.jetty.server.handler.ContextHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.{Project, ProjectManagerListener, ProjectManager}
import com.intellij.openapi.fileEditor.{FileEditorManager, FileEditorManagerEvent, FileEditorManagerListener, FileEditorManagerAdapter}
import com.intellij.openapi.vfs.VirtualFile
import code.comet.{EditorFile, EditorSectionEventHandler}

class WebComponent extends ApplicationComponent {
  def getComponentName: String = "Web Component"

  def initComponent() {
    try {
      val server = new Server
      val scc = new SelectChannelConnector
      scc.setPort(4567)
      server.setConnectors(Array(scc))

      val context = new WebAppContext()
      context.setServer(server)
      context.setWar(context.getClass.getClassLoader.getResource("webapp").toExternalForm)

      val context0: ContextHandler = new ContextHandler();
      context0.setHandler(context)
      server.setHandler(context0)

      server.start()


      val bus = ApplicationManager.getApplication().getMessageBus()
      bus.connect().subscribe(ProjectManager.TOPIC, new ProjectManagerListener {
        def projectClosing(p1: Project) {}

        def canCloseProject(p1: Project): Boolean = true

        def projectClosed(p1: Project) {}

        def projectOpened(p1: Project) {
          p1.getMessageBus.connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, FileEditorEvents)
        }
      })

    } catch {
      case e: Exception => e.printStackTrace()
    }
  }

  def disposeComponent() {}
}

object FileEditorEvents extends FileEditorManagerListener {
  def fileOpened(p1: FileEditorManager, p2: VirtualFile) {
    println("Opened")
    EditorSectionEventHandler ! FileOpened(EditorFile(p2))
  }

  def fileClosed(p1: FileEditorManager, p2: VirtualFile) {
    EditorSectionEventHandler ! FileClosed(EditorFile(p2))
  }

  def selectionChanged(p1: FileEditorManagerEvent) {
    EditorSectionEventHandler ! SelectionChanged(Option(p1.getNewFile).map(EditorFile(_)))
  }
}

case class FileOpened(file: EditorFile)

case class FileClosed(file: EditorFile)

case class SelectionChanged(newFile: Option[EditorFile])