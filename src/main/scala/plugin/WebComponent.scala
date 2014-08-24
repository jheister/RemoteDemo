package plugin

import java.lang.reflect.Field
import java.util

import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.event.{DocumentEvent, DocumentListener}
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
          p1.getMessageBus.connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, FileEditorEvents)
        }
      })

    } catch {
      case e: Exception => e.printStackTrace()
    }
  }

  def disposeComponent() {}
}

object LineChangePublishingListener extends DocumentListener {
  override def beforeDocumentChange(event: DocumentEvent): Unit = {
    val doc = event.getDocument

    val start = doc.getLineNumber(event.getOffset)
    val oldEnd = (start - 1) + event.getOldFragment.toString.split("\n", -1).size

    val pre = doc.getText(new TextRange(doc.getLineStartOffset(start), event.getOffset))

    val post = doc.getText(new TextRange(event.getOffset + event.getOldLength, doc.getLineEndOffset(doc.getLineNumber(event.getOffset + event.getOldLength))))

    val newSnippet = pre + event.getNewFragment + post

    val newLines = newSnippet.split("\n", -1).map(v => Line(0, Vector(Token(v.replace("\n", ""), new TextAttributes())))).toVector

    val file = FileDocumentManager.getInstance().getFile(doc)
    DocumentEvents ! DocumentChange(FileId(file.getName), start, oldEnd, newLines)
  }

  override def documentChanged(event: DocumentEvent): Unit = {
  }
}

object FileEditorEvents extends FileEditorManagerListener {
  def fileOpened(p1: FileEditorManager, p2: VirtualFile) {
    EditorSectionEventHandler ! FileOpened(FileId(p2.getName), EditorFile(p2.getName, Vector()))

    val highlighterProviders = FileTypeEditorHighlighterProviders.INSTANCE.allForFileType(p2.getFileType)

    val doc = FileDocumentManager.getInstance().getDocument(p2)

    val highlighter = highlighterProviders.get(0).getEditorHighlighter(p1.getProject, p2.getFileType, p2, new DefaultColorsScheme(DefaultColorSchemesManager.getInstance()))

//    doc.removeDocumentListener(LineChangePublishingListener)
    doc.addDocumentListener(LineChangePublishingListener)

    highlighter.setEditor(new HighlighterClient {
      def repaint(start: Int, end: Int) {
        val changedLines: Vector[Line] = new LineIterator(TokenIterator(highlighter.createIterator(0))).toVector
          .dropWhile(_.lineNumber < doc.getLineNumber(start)).takeWhile(_.lineNumber <= doc.getLineNumber(end))

        DocumentEvents ! UpdateLines(FileId(p2.getName), changedLines.map(l => (l.lineNumber, l)))
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
    Option(p1.getNewFile) match {
      case Some(file) => {
        DocumentEvents ! Show(FileId(file.getName))
        val doc = FileDocumentManager.getInstance().getDocument(file)

        val lines = doc.getText.split("\n", -1).map(v => Line(0, Vector(Token(v.replace("\n", ""), new TextAttributes())))).toVector

        DocumentEvents ! DocumentChange(FileId(file.getName), 0, lines.size, lines)
      }
      case None => DocumentEvents ! Clear
    }

    val maybeFile = Option(p1.getNewFile).map(_.getName).map(FileId(_))

    EditorSectionEventHandler ! SelectionChanged(maybeFile)
  }
}

class BasicTokenIterator(iterator: HighlighterIterator) extends Iterator[(Int, Int, Token)] {
  val doc: Document = iterator.getDocument

  override def hasNext: Boolean = !iterator.atEnd()

  override def next() = {
    val data = (doc.getLineNumber(iterator.getStart),
                doc.getLineNumber(iterator.getEnd),
                Token(doc.getText(new TextRange(iterator.getStart, iterator.getEnd)), iterator.getTextAttributes))
    iterator.advance()

    data
  }
}

object TokenIterator {
  def apply(iterator: HighlighterIterator) = new BasicTokenIterator(iterator).flatMap {
    case (start, end, token) if (start == end) => Vector((start, token))
    case (start, end, token) => {
      val splitTokens: Vector[Token] = token.splitAcrossLines

      val lines = start to end
      val tokensPerLine = lines.zip(splitTokens).toVector

      if (lines.size != tokensPerLine.size) {
        throw new RuntimeException("Splitting tokens failed. %s lines but %s tokens.".format(lines.size, tokensPerLine.size))
      }

      tokensPerLine.filterNot(_._2.value.isEmpty)
    }
  }
}

class LineIterator(underlying: Iterator[(Int, Token)]) extends Iterator[Line] {
  private val iterator = underlying.buffered

  override def hasNext: Boolean = iterator.hasNext

  override def next(): Line = {
    val (lineNr, _) = iterator.head

    val remainingTokens = tokenInRow(Vector(), lineNr)

    Line(lineNr, remainingTokens)
  }

  @tailrec private def tokenInRow(acc: Vector[Token], line: Int): Vector[Token] = {
    if (!iterator.hasNext || iterator.head._1 != line) {
      acc
    } else {
      tokenInRow(acc :+ iterator.next()._2, line)
    }
  }
}

trait EditorEvent

case class ContentChanged(id: FileId, file: EditorFile, changedLines: Vector[Line]) extends EditorEvent

case class FileOpened(id: FileId, file: EditorFile) extends EditorEvent

case class FileClosed(id: FileId, file: String) extends EditorEvent

case class SelectionChanged(newFile: Option[FileId]) extends EditorEvent
