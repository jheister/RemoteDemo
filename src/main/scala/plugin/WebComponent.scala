package plugin

import java.lang.reflect.Field
import java.util

import com.intellij.AppTopics
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.event.{DocumentEvent, DocumentListener}
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
import com.intellij.openapi.editor.Document

import com.intellij.openapi.util.TextRange

import scala.collection.immutable.IndexedSeq
import scala.collection.immutable.Range.Inclusive

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
          val connection = p1.getMessageBus.connect()
          connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, FileEditorEvents)
          connection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, new FileDocumentManagerListener {
            override def beforeAllDocumentsSaving(): Unit = {}

            override def fileContentReloaded(file: VirtualFile, document: Document): Unit = {}

            override def fileWithNoDocumentChanged(file: VirtualFile): Unit = {}

            override def beforeDocumentSaving(document: Document): Unit = {}

            var openedFiles: List[FileId] = Nil

            override def fileContentLoaded(file: VirtualFile, document: Document): Unit = {
              if (!openedFiles.contains(FileId(file.getName))) {
                openedFiles = FileId(file.getName) :: openedFiles
                val listener: NofifyingListener = new NofifyingListener(p1, file, document)
                listener.reset()
                FileDocumentManager.getInstance().getDocument(file).addDocumentListener(listener)
              } else {
                println("Not tracking duplicate opening of file")
              }
            }

            override def unsavedDocumentsDropped(): Unit = {}

            override def beforeFileContentReload(file: VirtualFile, document: Document): Unit = {}
          })
        }
      })

    } catch {
      case e: Exception => e.printStackTrace()
    }
  }

  def disposeComponent() {}
}




object FileEditorEvents extends FileEditorManagerListener {
  def fileOpened(p1: FileEditorManager, file: VirtualFile) {
    EditorSectionEventHandler ! FileOpened(FileId(file.getName), EditorFile(file.getName, Vector()))
  }

  def fileClosed(p1: FileEditorManager, p2: VirtualFile) {
    EditorSectionEventHandler ! FileClosed(FileId(p2.getName), p2.getName)
  }

  def selectionChanged(p1: FileEditorManagerEvent) {
    Option(p1.getNewFile) match {
      case Some(file) => {
        DocumentEvents ! Show(FileId(file.getName))
      }
      case None => DocumentEvents ! Clear
    }

    val maybeFile = Option(p1.getNewFile).map(_.getName).map(FileId(_))

    EditorSectionEventHandler ! SelectionChanged(maybeFile)
  }
}





trait EditorEvent

case class ContentChanged(id: FileId, file: EditorFile, changedLines: Vector[Line]) extends EditorEvent

case class FileOpened(id: FileId, file: EditorFile) extends EditorEvent

case class FileClosed(id: FileId, file: String) extends EditorEvent

case class SelectionChanged(newFile: Option[FileId]) extends EditorEvent
