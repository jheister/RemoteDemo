import java.io.{FileOutputStream, BufferedOutputStream, FileInputStream, BufferedInputStream}
import java.net.URL

import org.apache.commons.compress.archivers.tar.{TarArchiveEntry, TarArchiveInputStream}
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.sbtidea.SbtIdeaPlugin
import sbt.{IO, File, TaskKey}
import sbt.Keys._
import sbt._

object ResolveIntellij {
  val resolveIntellij = TaskKey[File]("resolve-intellij")
  val extractIntellij = TaskKey[Unit]("extract-intellij")
  val intellijDir = SettingKey[File]("intellij-dir")

  val settings = Seq(
    intellijDir := baseDirectory.value / "intellij",
    resolveIntellij := {
      val downloadedFile: File = target.value / "idea.tar.gz"
      IO.download(new URL("http://download.jetbrains.com/idea/ideaIC-13.1.4b.tar.gz?_ga=1.258227146.1997551731.1359582546"), downloadedFile)

      downloadedFile
    },
    extractIntellij := {
      val file = target.value / "idea.tar.gz"
      intellijDir.value.mkdirs()

      val tar = new TarArchiveInputStream(new GzipCompressorInputStream(new BufferedInputStream(new FileInputStream(file))))

      new TarEntryIterator(tar).filter(_.getName.contains("idea-IC-135.1230/lib/")).filter(_.getName.endsWith(".jar")).foreach(entry => {
        val thisFile = intellijDir.value / new File(entry.getName).getName

        val out = new BufferedOutputStream(new FileOutputStream(thisFile))
        val buffer = new Array[Byte](1024)
        var len = tar.read(buffer)
        while (len != -1) {
          out.write(buffer, 0, len)
          len = tar.read(buffer)
        }

        out.close()
      })
    },
    unmanagedBase in Compile := intellijDir.value
  )

  class TarEntryIterator(tar: TarArchiveInputStream) extends Iterator[TarArchiveEntry] {
    var nextElem: TarArchiveEntry = null

    override def hasNext: Boolean = {
      if (nextElem == null) { readNext()}
      nextElem != null
    }

    override def next(): TarArchiveEntry = {
      if (nextElem == null) { readNext() }
      nextElem match {
        case null => Iterator.empty.next()
        case elem => {nextElem = null; elem}
      }
    }

    private def readNext() = {
      nextElem = tar.getNextTarEntry
    }
  }
}