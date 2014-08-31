import sbtassembly.Plugin.{MappingSet, MergeStrategy}
import sbtassembly.Plugin.AssemblyKeys._

name := "RemoteDemo"

version := "0.0.1"

organization := ""

scalaVersion := "2.11.1"

resolvers ++= Seq("snapshots"     at "https://oss.sonatype.org/content/repositories/snapshots",
                  "releases"        at "https://oss.sonatype.org/content/repositories/releases")

unmanagedResourceDirectories in Test <+= (baseDirectory) { _ / "src/main/webapp" }

scalacOptions ++= Seq("-deprecation", "-unchecked")

libraryDependencies ++= {
  val liftVersion = "2.6-M4"
  Seq(
    ("net.liftweb"       %% "lift-webkit"        % liftVersion).exclude("javax.mail", "mail"),
    "net.liftmodules"   %% "lift-jquery-module_2.6" % "2.8",
    "org.eclipse.jetty" % "jetty-webapp"        % "8.1.7.v20120910",
    "org.eclipse.jetty" % "jetty-plus"          % "8.1.7.v20120910",
    "org.eclipse.jetty.orbit" % "javax.servlet" % "3.0.0.v201112011016",
    "ch.qos.logback"    % "logback-classic"     % "1.0.6",
    "org.scalatest" %% "scalatest" % "2.1.3"
  )
}

FixPluginIdeaFile.settings

ResolveIntellij.settings

sbtassembly.Plugin.assemblySettings

mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
{
  case "META-INF/ECLIPSEF.RSA"     => MergeStrategy.discard
  case "META-INF/eclipse.inf"     => MergeStrategy.discard
  case "META-INF/mailcap"     => MergeStrategy.first
  case "META-INF/mimetypes.default" => MergeStrategy.first
  case "plugin.properties" => MergeStrategy.concat
  case "scala/io/Position$.class" => MergeStrategy.first
  case "scala/io/Position.class" => MergeStrategy.first
  case x => old(x)
}
}

fullClasspath in assembly := { (fullClasspath in Compile).value.filterNot(_.data.getAbsolutePath.contains(unmanagedBase.value.getAbsolutePath)) }

net.virtualvoid.sbt.graph.Plugin.graphSettings

assembledMappings in assembly ++= Seq(
  MappingSet(None, Vector((baseDirectory.value / "plugin.xml") -> "META-INF/plugin.xml"))
)