package plugin

import code.comet._
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager
import com.intellij.openapi.editor.colors.impl.DefaultColorsScheme
import com.intellij.openapi.editor.event.{DocumentEvent, DocumentListener}
import com.intellij.openapi.editor.highlighter.{HighlighterClient, HighlighterIterator}
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileTypeEditorHighlighterProviders
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
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

  override def next() = {    val text: String = doc.getText(new TextRange(iterator.getStart, iterator.getEnd))

    val data = (doc.getLineNumber(iterator.getStart),
      doc.getLineNumber(iterator.getEnd),
      Token(text, iterator.getTextAttributes))
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

class NofifyingListener(project: Project, file: VirtualFile, doc: Document) extends DocumentListener {
  val highlighterProviders = FileTypeEditorHighlighterProviders.INSTANCE.allForFileType(file.getFileType)
  val highlighter = highlighterProviders.get(0).getEditorHighlighter(project, file.getFileType, file, new DefaultColorsScheme(DefaultColorSchemesManager.getInstance()))
  highlighter.setText(doc.getText())
  highlighter.setEditor(new HighlighterClient {
    override def repaint(start: Int, end: Int): Unit = { /* Not repainting from here since we don't render until highlighter is done */}

    override def getDocument: Document = doc

    override def getProject: Project = project
  })

  override def beforeDocumentChange(event: DocumentEvent): Unit = {}

  override def documentChanged(event: DocumentEvent): Unit = {
    highlighter.documentChanged(event)
    val (start, oldEnd, newEnd) = lineChangeOffsets(event)


    val lines = allLines
    val changedLines: Vector[Line] =
      lines.dropWhile(_.lineNumber < start).takeWhile(_.lineNumber <= newEnd)

    println("==============here================")
    println(lines.map(l => l.lineNumber + ":" +  l.tokens.map(_.value).mkString("")).mkString("\n"))
    println("==============here================")

    DocumentEvents ! DocumentChange(FileId(file.getName), start, oldEnd, changedLines)
  }

  def allLines = new LineIterator(TokenIterator(highlighter.createIterator(0), doc)).toVector

  def reset() {
    DocumentEvents ! Reset(FileId(file.getName), allLines)
  }

  private def lineChangeOffsets(event: DocumentEvent) = {
    val start = event.getDocument.getLineNumber(event.getOffset)
    val oldEnd = (start - 1) + event.getOldFragment.toString.split("\n", -1).size
    val newEnd = (start - 1) + event.getNewFragment.toString.split("\n", -1).size

    (start, oldEnd, newEnd)
  }
}