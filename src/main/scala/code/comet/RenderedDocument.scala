package code.comet

import java.awt.{Font, Color}

import net.liftweb.common.SimpleActor
import net.liftweb.http.{CometListener, RenderOut, CometActor}

class DocumentContent {
  private var documentContent: Vector[Line] = Vector.empty

  def apply(change: DocumentChange): Unit = {
    val (unchangedHead, remaining) = documentContent.splitAt(change.start)
    val (_, unchangedTail) = remaining.splitAt(change.end - change.start + 1)

    documentContent = unchangedHead ++ change.lines ++ unchangedTail
  }

  def apply(change: UpdateLines): Unit = {
    change.lines.foreach {
      case (lineNr, line) => apply(DocumentChange(change.id, lineNr, lineNr, Vector(line)))
    }
  }

  def clear: Unit = documentContent = Vector.empty

  def lines: Vector[Line] = documentContent

  override def toString: String = {
    documentContent.map(_.tokens.map(_.value).mkString("")).mkString("\n")
  }
}

class RenderedDocument extends CometActor with CometListener {
  var contents = new DocumentContent()
  var myDocument: Option[FileId] = None

  override protected def registerWith = DocumentEvents

  override def lowPriority = {
    case Clear => contents.clear; myDocument = None; reRender()
    case Show(id) => contents.clear; myDocument = Some(id); reRender()
    case dc@DocumentChange(id, start, end, newContent) if myDocument.filter(_ == id).isDefined => {
      contents.apply(dc)
      reRender()
    }
    case dc@UpdateLines(id, lines) if myDocument.filter(_ == id).isDefined => {
      lines.foreach(line => {
        println("Told to update line " + line._1)
      })

      contents.apply(dc)
      reRender()
    }
  }

  override def render = {
    ".code-line" #> contents.lines.map {
      case Line(lineNumber, tokens) =>
//        "* [id]" #> ("line-" + lineNumber) &
          ".code-token" #> tokens.map(render(_))
    }
  }

  private def render(token: Token) = {
    "* *" #> token.value &
      "* [style+]" #> Option(token.attributes.getForegroundColor).map(toHexString).map("color:%s;".format(_)).getOrElse("") &
      "* [style+]" #> Option(token.attributes.getBackgroundColor).map(toHexString).map("background-color:%s;".format(_)).getOrElse("") &
      "* [style+]" #> Option(token.attributes.getFontType).map(toWeight).getOrElse("")
  }

  private def toWeight(id: Int) = id match {
    case Font.PLAIN => "font-weight:normal;font-style:normal;"
    case Font.BOLD => "font-weight:bold;font-style:normal;"
    case Font.ITALIC => "font-weight:normal;font-style:italic;"
    case e => throw new RuntimeException("Unknown weight: " + e)
  }

  private def toHexString(color: Color) = {
    "#%02x%02x%02x".format(color.getRed, color.getGreen, color.getBlue)
  }
}

case class UpdateLines(id: FileId, lines: Vector[(Int, Line)])

case class DocumentChange(id: FileId, start: Int, end: Int, lines: Vector[Line])

case class Show(id: FileId)

case object Clear