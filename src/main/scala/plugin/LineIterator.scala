package plugin

import code.comet._
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager
import com.intellij.openapi.editor.colors.impl.DefaultColorsScheme
import com.intellij.openapi.editor.event.{DocumentEvent, DocumentListener}
import com.intellij.openapi.editor.highlighter.{HighlighterClient, HighlighterIterator}
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.{FileEditorManager, FileDocumentManager}
import com.intellij.openapi.fileTypes.FileTypeEditorHighlighterProviders
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.{Computable, TextRange}
import com.intellij.openapi.vfs.VirtualFile

import scala.annotation.tailrec

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

class BasicTokenIterator(iterator: HighlighterIterator, doc: Document) extends Iterator[(Int, Int, Token)] {
  override def hasNext: Boolean = !iterator.atEnd()

  override def next() = {
    val text: String = doc.getText(new TextRange(iterator.getStart, iterator.getEnd))

    val startingLine: Int = doc.getLineNumber(iterator.getStart)

    val startOnLine = iterator.getStart - doc.getLineStartOffset(startingLine)

    val data = (startingLine,
      doc.getLineNumber(iterator.getEnd),
      Token(text, iterator.getTextAttributes, startOnLine))
    iterator.advance()

    data
  }
}

object TokenIterator {
  def apply(iterator: HighlighterIterator, doc: Document) = new BasicTokenIterator(iterator, doc).flatMap {
    case (start, end, token) if (start == end) => Vector((start, token))
    case (start, end, token) => {
      val splitTokens: Vector[Token] = token.splitAcrossLines

      val lines = start to end
      val tokensPerLine = lines.zip(splitTokens).toVector

      if (lines.size != tokensPerLine.size) {
        throw new RuntimeException("Splitting tokens failed. %s lines but %s tokens.".format(lines.size, tokensPerLine.size))
      }

      tokensPerLine
    }
  }
}

case class NoopHighlighterClient(project: Project, document: Document) extends HighlighterClient {
  override def repaint(start: Int, end: Int): Unit = {}

  override def getDocument: Document = document

  override def getProject: Project = project
}

class Highlighter(project: Project, file: VirtualFile, document: Document) {
  val highlighterProviders = FileTypeEditorHighlighterProviders.INSTANCE.allForFileType(file.getFileType)
  val highlighter = highlighterProviders.get(0).getEditorHighlighter(project, file.getFileType, file, new DefaultColorsScheme(DefaultColorSchemesManager.getInstance()))
  highlighter.setText(document.getText())
  highlighter.setEditor(NoopHighlighterClient(project, document))

  def lines() = new LineIterator(TokenIterator(highlighter.createIterator(0), document)).toVector
}

object DocumentContentLoader {
  def load(file: File) = {
    val lines = ApplicationManager.getApplication.runReadAction(new Computable[Vector[Line]] {
      override def compute(): Vector[Line] = {
        val document: Document = FileDocumentManager.getInstance().getDocument(file.file)

        new Highlighter(file.project, file.file, document).lines()
      }
    })

    lines
  }
}

class NofifyingListener(project: Project, id: FileId, doc: Document) extends DocumentListener {
  val highlighter = new Highlighter(project, FileDocumentManager.getInstance().getFile(doc), doc)

  override def beforeDocumentChange(event: DocumentEvent): Unit = {}

  override def documentChanged(event: DocumentEvent): Unit = {
    highlighter.highlighter.documentChanged(event)
    val (start, oldEnd, newEnd) = lineChangeOffsets(event)

    val lines = highlighter.lines()
    val changedLines: Vector[Line] =
      lines.dropWhile(_.lineNumber < start).takeWhile(_.lineNumber <= newEnd)

    DocumentEvents ! DocumentChange(id, start, oldEnd, changedLines)
  }

  private def lineChangeOffsets(event: DocumentEvent) = {
    val start = event.getDocument.getLineNumber(event.getOffset)
    val oldEnd = (start - 1) + event.getOldFragment.toString.split("\n", -1).size
    val newEnd = (start - 1) + event.getNewFragment.toString.split("\n", -1).size

    (start, oldEnd, newEnd)
  }
}