package code.comet

class DocumentContent(startingContent: Vector[Line] = Vector.empty) {
  private var documentContent: Vector[Line] = startingContent

  def apply(change: DocumentChange): Unit = {
    val (unchangedHead, remaining) = documentContent.splitAt(change.start)
    val (removed, unchangedTail) = remaining.splitAt(change.end - change.start + 1)

    println("Removing: " + removed.map(_.tokens.map(_.value).mkString("")).mkString("\n"))

    documentContent = unchangedHead ++ change.lines ++ unchangedTail
  }

  def clear: Unit = documentContent = Vector.empty

  def lines: Vector[Line] = documentContent

  override def toString: String = {
    documentContent.map(_.tokens.map(_.value).mkString("")).mkString("\n")
  }
}
