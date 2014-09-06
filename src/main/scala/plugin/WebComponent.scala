package plugin

import java.lang.reflect.Field
import java.util

import com.intellij.AppTopics
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.editor.{EditorFactory, Document}
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

import com.intellij.openapi.util.TextRange

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
          connection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, new DocumentInstrumentor(p1))
          EditorFactory.getInstance().addEditorFactoryListener(new EditorFactoryListener {
            override def editorCreated(event: EditorFactoryEvent): Unit = {
              event.getEditor.getSelectionModel.addSelectionListener(new SelectionListener {
                override def selectionChanged(e: SelectionEvent): Unit = {
                  val doc: Document = e.getEditor.getDocument

                  if (e.getNewRange.getLength != 0) {
                    val startLine: Int = doc.getLineNumber(e.getNewRange.getStartOffset)
                    val endLine: Int = doc.getLineNumber(e.getNewRange.getEndOffset)
                    DocumentEvents ! LinesSelected(startLine, endLine)
                  } else {
                    DocumentEvents ! LineSelectionCleared
                  }
                }
              })
            }

            override def editorReleased(event: EditorFactoryEvent): Unit = {}
          }, p1)
        }
      })

    } catch {
      case e: Exception => e.printStackTrace()
    }
  }

  def disposeComponent() {}
}

class DocumentInstrumentor(project: Project) extends FileDocumentManagerListener {
  override def beforeAllDocumentsSaving(): Unit = {}

  override def fileContentReloaded(file: VirtualFile, document: Document): Unit = {}

  override def fileWithNoDocumentChanged(file: VirtualFile): Unit = {}

  override def beforeDocumentSaving(document: Document): Unit = {}


  override def fileContentLoaded(file: VirtualFile, document: Document): Unit = {
    document.addDocumentListener(new NofifyingListener(project, file, document))
  }

  override def unsavedDocumentsDropped(): Unit = {}

  override def beforeFileContentReload(file: VirtualFile, document: Document): Unit = {}
}



object FileEditorEvents extends FileEditorManagerListener {
  def fileOpened(p1: FileEditorManager, file: VirtualFile) {
    EditorSectionEventHandler ! FileOpened(FileId(file), File(file, p1.getProject))
  }

  def fileClosed(p1: FileEditorManager, p2: VirtualFile) {
    EditorSectionEventHandler ! FileClosed(FileId(p2))
  }

  def selectionChanged(p1: FileEditorManagerEvent) {
    Option(p1.getNewFile) match {
      case Some(file) => {
        DocumentEvents ! Selected(FileId(file), DocumentContentLoader.load(File(file, p1.getManager.getProject)))
      }
      case None => DocumentEvents ! ClearSelected
    }

    val maybeFile = Option(p1.getNewFile).map(FileId(_))

    EditorSectionEventHandler ! SelectionChanged(maybeFile)
  }
}