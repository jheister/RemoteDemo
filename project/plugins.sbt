//Enable the sbt idea plugin
addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.6.0")

//Enable the sbt eclipse plugin
addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "2.4.0")

libraryDependencies ++= Seq(
  "org.apache.commons" % "commons-compress" % "1.5",
  "commons-io" % "commons-io" % "2.4"
)

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.11.2")

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.7.4")