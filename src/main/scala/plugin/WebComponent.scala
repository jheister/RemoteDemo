package plugin

import com.intellij.openapi.components.ApplicationComponent
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.nio.SelectChannelConnector
import org.eclipse.jetty.webapp.WebAppContext
import org.eclipse.jetty.server.handler.ContextHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.{Project, ProjectManagerListener, ProjectManager}
import com.intellij.openapi.fileEditor._
import com.intellij.openapi.vfs.VirtualFile
import code.comet.EventBus
import scala.collection.JavaConversions._
import com.intellij.openapi.fileTypes.FileTypeEditorHighlighterProviders
import com.intellij.openapi.editor.colors.impl.DefaultColorsScheme
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter
import com.intellij.openapi.editor.highlighter.HighlighterClient
import com.intellij.openapi.editor.Document
import plugin.SelectionChanged
import plugin.FileClosed
import plugin.FileOpened

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
    val highlighterProviders = FileTypeEditorHighlighterProviders.INSTANCE.allForFileType(p2.getFileType)
    println("Opened")

    val doc = FileDocumentManager.getInstance().getDocument(p2)

    val highlighter = highlighterProviders.get(0).getEditorHighlighter(p1.getProject, p2.getFileType, p2, new DefaultColorsScheme(DefaultColorSchemesManager.getInstance()))

    val lexEdHigh = highlighter.asInstanceOf[LexerEditorHighlighter]

    highlighter.setEditor(new HighlighterClient {
      def repaint(p1: Int, p2: Int) {}

      def getDocument: Document = doc

      def getProject: Project = p1.getProject
    })

    highlighter.setText(doc.getText)

    val iterator = highlighter.createIterator(0)

    println("I am here " + iterator.atEnd() + " and am " + lexEdHigh.isValid)

    while (!iterator.atEnd()) {
      println("token " + iterator.getTokenType)
      iterator.advance()
    }

    EventBus ! FileOpened(p2)
  }

  def fileClosed(p1: FileEditorManager, p2: VirtualFile) {
    EventBus ! FileClosed(p2)
  }

  def selectionChanged(p1: FileEditorManagerEvent) {
    EventBus ! SelectionChanged(Option(p1.getNewFile))
  }
}

case class FileOpened(file: VirtualFile)

case class FileClosed(file: VirtualFile)

case class SelectionChanged(newFile: Option[VirtualFile])