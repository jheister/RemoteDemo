package plugin

import java.lang.reflect.Field
import java.util
import java.util.UUID

import com.intellij.AppTopics
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.editor.ex.{FocusChangeListener, EditorEx}
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.{Editor, EditorFactory, Document}
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.event._
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl
import com.intellij.psi.{PsiFile, PsiDocumentManager}
import com.intellij.psi.PsiDocumentManager.Listener
import com.intellij.util.messages.MessageBusConnection
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.nio.SelectChannelConnector
import org.eclipse.jetty.webapp.WebAppContext
import org.eclipse.jetty.server.handler.ContextHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.{Project, ProjectManagerListener, ProjectManager}
import com.intellij.openapi.fileEditor._
import com.intellij.openapi.vfs.VirtualFile
import code.comet._
import scala.annotation.tailrec
import scala.collection.JavaConversions._
import com.intellij.openapi.fileTypes.FileTypeEditorHighlighterProviders
import com.intellij.openapi.editor.colors.impl.DefaultColorsScheme
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter
import com.intellij.openapi.editor.highlighter.{EditorHighlighter, HighlighterIterator, HighlighterClient}

import com.intellij.openapi.util.{Key, TextRange}

import scala.collection.immutable.IndexedSeq
import scala.collection.immutable.Range.Inclusive
import scala.util.Random

object ServerStarter {
  def start(port: Int) = {
    val server = new Server
    val scc = new SelectChannelConnector
    scc.setPort(port)
    server.setConnectors(Array(scc))

    val context = new WebAppContext()
    context.setServer(server)
    context.setWar(context.getClass.getClassLoader.getResource("webapp").toExternalForm)

    val context0: ContextHandler = new ContextHandler();
    context0.setHandler(context)
    server.setHandler(context0)

    server.start()
  }
}

object BroadcastingSelectionChangeListener extends SelectionListener {
  override def selectionChanged(e: SelectionEvent): Unit = {
    val doc: Document = e.getEditor.getDocument

    if (e.getNewRange.getLength != 0) {
      val startLine: Int = doc.getLineNumber(e.getNewRange.getStartOffset)
      val endLine: Int = doc.getLineNumber(e.getNewRange.getEndOffset)

      val startPos = e.getNewRange.getStartOffset - doc.getLineStartOffset(startLine)
      val endPos = e.getNewRange.getEndOffset - doc.getLineStartOffset(endLine)

      DocumentEvents ! TextSelected(TextPosition(startLine, startPos), TextPosition(endLine, endPos))
    } else {
      DocumentEvents ! TextSelectionCleared
    }
  }
}

class WebComponent extends ApplicationComponent {
  def getComponentName: String = "Web Component"

  def initComponent() {
    try {
      ServerStarter.start(4568)

      val bus = ApplicationManager.getApplication().getMessageBus()
      bus.connect().subscribe(ProjectManager.TOPIC, new ProjectManagerListener {
        def projectClosing(p1: Project) {}

        def canCloseProject(p1: Project): Boolean = true

        def projectClosed(p1: Project) {}

        def projectOpened(p1: Project) {
          val connection = p1.getMessageBus.connect()
          connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, FileEditorEvents)
          EditorFactory.getInstance().addEditorFactoryListener(FileEditorEvents, p1)
        }
      })

    } catch {
      case e: Exception => e.printStackTrace()
    }
  }

  def disposeComponent() {}
}

object FileEditorEvents extends FileEditorManagerListener with EditorFactoryListener {
  val EditorId = Key.create[String]("editor-id")
  val DocListener = Key.create[DocumentListener]("broadcasting-listener")

  override def editorCreated(event: EditorFactoryEvent): Unit = {
    if (event.getEditor.getUserData(EditorId) != null) {
      println("Editor reuse???")
    }
    event.getEditor.putUserData(EditorId, UUID.randomUUID().toString)
    event.getEditor.getSelectionModel.addSelectionListener(BroadcastingSelectionChangeListener)
    val listener = new NofifyingListener(event.getEditor.getProject, fileId(event.getEditor), event.getEditor.getDocument)

    event.getEditor.putUserData(DocListener, listener)
    event.getEditor.getDocument.addDocumentListener(listener)

    val file = FileDocumentManager.getInstance().getFile(event.getEditor.getDocument)
    EditorSectionEventHandler ! FileOpened(fileId(event.getEditor), File(file, event.getEditor.getProject))
    DocumentEvents ! EditorCreated(fileId(event.getEditor), DocumentContentLoader.load(File(file, event.getEditor.getProject)))
  }

  override def editorReleased(event: EditorFactoryEvent): Unit = {
    event.getEditor.getDocument.removeDocumentListener(event.getEditor.getUserData(DocListener))

    EditorSectionEventHandler ! FileClosed(fileId(event.getEditor))
    DocumentEvents ! EditorClosed(fileId(event.getEditor))
  }

  def fileOpened(p1: FileEditorManager, file: VirtualFile) {}

  def fileClosed(p1: FileEditorManager, p2: VirtualFile) {}

  def selectionChanged(p1: FileEditorManagerEvent) {
    val maybeFile = Option(p1.getNewEditor).map(_.asInstanceOf[TextEditor].getEditor).map(fileId)

    EditorSectionEventHandler ! SelectionChanged(maybeFile)
    DocumentEvents ! maybeFile.map(EditorSelected(_)).getOrElse(ClearSelected)
  }

  def fileId(editor: Editor) = {
    FileId(editor.getUserData(EditorId))
  }
}