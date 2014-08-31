import java.io.{File, PrintStream}
import org.sbtidea.SbtIdeaPlugin
import sbt.Keys._

import sbt.{IO, TaskKey}
import sbt._
import scala.xml._
import scala.xml.transform.{RewriteRule, RuleTransformer}

object FixPluginIdeaFile {
  val fixIt = TaskKey[File]("fix-it")
  val genIdeaPlugin = Command.command("gen-idea-plugin") { state =>
    val extracted = Project.extract(state)

    val ignoringUnmanagedLibs: State = extracted.append(Seq(unmanagedBase := baseDirectory.value / "lib"), state)
    SbtIdeaPlugin.doCommand(ignoringUnmanagedLibs, Seq())
    extracted.runTask(fixIt, ignoringUnmanagedLibs)

    state
  }

  val settings = Seq(
    commands += genIdeaPlugin,
    fixIt := {
      val metaInf = baseDirectory.value / ".idea_modules/META-INF"

      IO.copyFile(baseDirectory.value / "plugin.xml", metaInf / "plugin.xml")

      val xml = XML.loadFile(".idea_modules/RemoteDemo.iml")

      val out: PrintStream = new PrintStream(".idea_modules/RemoteDemo.iml")

      out.println(Transform(xml))

      out.close()

      new File(".idea_modules/RemoteDemo.iml")
    }
  )
}

object Transform extends RuleTransformer(RewriteFile)

object RewriteFile extends RewriteRule {
  override def transform(ns: Seq[scala.xml.Node]): Seq[scala.xml.Node] = ns match {
    case e: Elem if e.label == "module" => {
      e.copy(
        child = Seq(<component name="DevKit.ModuleBuildProperties" url="file://$MODULE_DIR$/META-INF/plugin.xml" />) ++ e.child) % Attribute(None, "type", Text("PLUGIN_MODULE"), Null)
    }
    case e: Elem if (e.attributes.get("type").map(_.toString) == Some("inheritedJdk")) => <orderEntry type="jdk" jdkName="IDEA IC-135.909" jdkType="IDEA JDK" />

    case other => other
  }
}
