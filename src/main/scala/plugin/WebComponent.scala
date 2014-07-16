package plugin

import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.editor.Document
import com.intellij.psi.{PsiFile, PsiDocumentManager}
import com.intellij.psi.PsiDocumentManager.Listener
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.nio.SelectChannelConnector
import org.eclipse.jetty.webapp.WebAppContext
import org.eclipse.jetty.server.handler.ContextHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.{Project, ProjectManagerListener, ProjectManager}
import com.intellij.openapi.fileEditor._
import com.intellij.openapi.vfs.VirtualFile
import code.comet.{EditorFile, EditorSectionEventHandler}
import scala.collection.JavaConversions._
import com.intellij.openapi.fileTypes.FileTypeEditorHighlighterProviders
import com.intellij.openapi.editor.colors.impl.DefaultColorsScheme
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter
import com.intellij.openapi.editor.highlighter.HighlighterClient
import com.intellij.openapi.editor.Document

import com.intellij.openapi.util.TextRange

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
          val psiDocumentManager = com.intellij.psi.PsiDocumentManager.getInstance(p1)

          psiDocumentManager.addListener(new Listener {
            override def documentCreated(p1: Document, p2: PsiFile): Unit = println("Doc created " + p1)

            override def fileCreated(p1: PsiFile, p2: Document): Unit = println("File created " + p1)
          })
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
    EditorSectionEventHandler ! FileOpened(EditorFile(p2, Nil))

    val highlighterProviders = FileTypeEditorHighlighterProviders.INSTANCE.allForFileType(p2.getFileType)
    println("Opened")

    val doc = FileDocumentManager.getInstance().getDocument(p2)

    val highlighter = highlighterProviders.get(0).getEditorHighlighter(p1.getProject, p2.getFileType, p2, new DefaultColorsScheme(DefaultColorSchemesManager.getInstance()))

    val lexEdHigh = highlighter.asInstanceOf[LexerEditorHighlighter]

    highlighter.setEditor(new HighlighterClient {
      def repaint(x: Int, y: Int) {
        val iterator = highlighter.createIterator(0)

        val stuff = new Iterator[(String, String)] {
          def hasNext: Boolean = !iterator.atEnd()

          def next(): (String, String) = {
            val data = (iterator.getTokenType.toString, doc.getText(new TextRange(iterator.getStart, iterator.getEnd)))
            iterator.advance()

            data
          }
        }

        EditorSectionEventHandler ! ContentChanged(EditorFile(p2, stuff.toList))
      }

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

    doc.addDocumentListener(highlighter)
  }

  def fileClosed(p1: FileEditorManager, p2: VirtualFile) {
    EditorSectionEventHandler ! FileClosed(EditorFile(p2, Nil))
  }

  def selectionChanged(p1: FileEditorManagerEvent) {
    EditorSectionEventHandler ! SelectionChanged(Option(p1.getNewFile).map(EditorFile(_, Nil)))
  }
}

case class ContentChanged(file: EditorFile)

case class FileOpened(file: EditorFile)

case class FileClosed(file: EditorFile)

case class SelectionChanged(newFile: Option[EditorFile])
