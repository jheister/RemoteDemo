package plugin

import java.lang.reflect.Field
import java.util

import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.markup.TextAttributes
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
import code.comet._
import scala.collection.JavaConversions._
import com.intellij.openapi.fileTypes.FileTypeEditorHighlighterProviders
import com.intellij.openapi.editor.colors.impl.DefaultColorsScheme
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter
import com.intellij.openapi.editor.highlighter.{HighlighterIterator, HighlighterClient}
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
    EditorSectionEventHandler ! FileOpened(FileId(p2.getName), EditorFile(p2.getName, Vector()))

    val highlighterProviders = FileTypeEditorHighlighterProviders.INSTANCE.allForFileType(p2.getFileType)

    val doc = FileDocumentManager.getInstance().getDocument(p2)

    val highlighter = highlighterProviders.get(0).getEditorHighlighter(p1.getProject, p2.getFileType, p2, new DefaultColorsScheme(DefaultColorSchemesManager.getInstance()))

    highlighter.setEditor(new HighlighterClient {
      def repaint(start: Int, end: Int) {
//        val snippetToRedraw = doc.getText(new TextRange(start, end))
//
        val startLine = doc.getLineNumber(start)
        val endLine = doc.getLineNumber(end)

        val content = convert(highlighter.createIterator(0), doc).toVector

        val lines = content.groupBy(_._1).map {
          case (lineNr, tokens) => Line(lineNr, tokens.map(_._2))
        }

        val allLines: Vector[Line] = lines.toVector.sortBy(_.lineNumber)
        val changedLines: Vector[Line] = allLines.dropWhile(_.lineNumber < startLine).takeWhile(_.lineNumber <= endLine)


        val changeEvent: ContentChanged = ContentChanged(FileId(p2.getName), EditorFile(p2.getName, allLines), changedLines)

        EditorSectionEventHandler ! changeEvent
      }

      def getDocument: Document = doc

      def getProject: Project = p1.getProject
    })

    highlighter.setText(doc.getText)

    doc.addDocumentListener(highlighter)
  }

  def fileClosed(p1: FileEditorManager, p2: VirtualFile) {
    EditorSectionEventHandler ! FileClosed(FileId(p2.getName), p2.getName)
  }

  def selectionChanged(p1: FileEditorManagerEvent) {
    EditorSectionEventHandler ! SelectionChanged(Option(p1.getNewFile).map(_.getName).map(FileId(_)))
  }

  def convert(iterator: HighlighterIterator, doc: Document) = new Iterator[(Int, Token)] {
    def hasNext: Boolean = !iterator.atEnd()

    def next() = {
      val lineNr = doc.getLineNumber(iterator.getEnd)
      val data = (lineNr, Token(iterator.getTokenType.toString, doc.getText(new TextRange(iterator.getStart, iterator.getEnd)).replace("\n", ""), iterator.getTextAttributes))
      iterator.advance()

      data
    }
  }

}

trait EditorEvent

case class ContentChanged(id: FileId, file: EditorFile, changedLines: Vector[Line]) extends EditorEvent

case class FileOpened(id: FileId, file: EditorFile) extends EditorEvent

case class FileClosed(id: FileId, file: String) extends EditorEvent

case class SelectionChanged(newFile: Option[FileId]) extends EditorEvent
