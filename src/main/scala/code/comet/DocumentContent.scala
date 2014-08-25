package code.comet

case class DocumentContent(documentContent: Vector[Line] = Vector.empty) {
  def apply(change: DocumentChange): DocumentContent = {
    val (unchangedHead, remaining) = documentContent.splitAt(change.start)
    val (removed, unchangedTail) = remaining.splitAt(change.end - change.start + 1)

    println("Removing: " + removed.map(_.tokens.map(_.value).mkString("")).mkString("\n"))

    DocumentContent(documentContent = unchangedHead ++ change.lines ++ unchangedTail)
  }

  def lines: Vector[Line] = documentContent

  override def toString: String = {
    documentContent.map(_.tokens.map(_.value).mkString("")).mkString("\n")
  }
}
