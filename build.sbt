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
    "net.liftweb"       %% "lift-webkit"        % liftVersion        % "compile",
    "net.liftmodules"   %% "lift-jquery-module_2.6" % "2.8",
    "org.eclipse.jetty" % "jetty-webapp"        % "8.1.7.v20120910",
    "org.eclipse.jetty" % "jetty-plus"          % "8.1.7.v20120910",
    "org.eclipse.jetty.orbit" % "javax.servlet" % "3.0.0.v201112011016",
    "ch.qos.logback"    % "logback-classic"     % "1.0.6",
    "org.specs2"        %% "specs2"             % "2.3.12"           % "test"
  )
}

