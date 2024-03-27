name := "starfruit"
version := "1.5"
scalaVersion := "3.4.0"
scalacOptions ++= Seq("-deprecation", "-unchecked", "-Wunused:all", "-rewrite", "-source", "3.4-migration")
fork := true

lazy val jfxVersion = "21.0.2"
lazy val jfxClassifier = settingKey[String]("jfxClassifier")
jfxClassifier := {
  if (scala.util.Properties.isWin) "win"
  else if (scala.util.Properties.isLinux) "linux"
  else if (scala.util.Properties.isMac) "mac"
  else throw new IllegalStateException(s"Unknown OS: ${scala.util.Properties.osName}")
}

dependsOn(RootProject(file("../tangerine")))

libraryDependencies ++= Seq(
  "org.openjfx" % "javafx-graphics" % jfxVersion % "provided" classifier jfxClassifier.value,
  "org.openjfx" % "javafx-controls" % jfxVersion % "provided" classifier jfxClassifier.value,
  "org.openjfx" % "javafx-base" % jfxVersion % "provided" classifier jfxClassifier.value,
  "org.openjfx" % "javafx-media" % jfxVersion % "provided" classifier jfxClassifier.value,
  "org.openjfx" % "javafx-swing" % jfxVersion % "provided" classifier jfxClassifier.value,

  "com.lihaoyi" %% "sourcecode" % "0.3.1",
  "com.lihaoyi" %% "pprint" % "0.8.1",
  "com.github.pathikrit" %% "better-files" % "3.9.2",
  "org.controlsfx" % "controlsfx" % "11.2.1",
  "com.typesafe.play" %% "play-json" % "2.10.4",
  "com.lihaoyi" %% "fastparse" % "3.0.2",
  "org.scalatest" %% "scalatest" % "3.2.18" % "test",
  "org.scalacheck" %% "scalacheck" % "1.17.0" % "test"
)


reStart / mainClass := Some("starfruit.ui.DevAppReloader")
assembly / mainClass := Some("starfruit.ui.Main")

assembly / assemblyShadeRules := Seq(
  ShadeRule.keep("starfruit.**").inAll,
  ShadeRule.keep("scala.Predef$").inAll,
  ShadeRule.keep("better.files.File$").inAll,
  ShadeRule.keep("scala.io.Codec$").inAll,
  ShadeRule.keep("scala.collection.mutable.MultiMap").inAll,
  ShadeRule.keep("scala.collection.mutable.Stack").inAll,
  ShadeRule.keep("scala.sys.process.package$").inAll,
  ShadeRule.keep("scala.runtime.AbstractFunction2").inAll,
  ShadeRule.keep("scala.runtime.StructuralCallSite").inAll,
  ShadeRule.keep("scala.runtime.java8.*").inAll,
  ShadeRule.keep("prickle.*").inAll,
  ShadeRule.keep("microjson.*").inAll,
  ShadeRule.keep("org.controlsfx.dialog.FontSelectorDialog").inAll,
  ShadeRule.keep("org.controlsfx.validation.decoration.GraphicValidationDecoration").inAll,
  ShadeRule.keep("org.controlsfx.control.HiddenSidesPane").inAll,
  ShadeRule.keep("fastparse.**").inAll,
  ShadeRule.keep("sourcecode.**").inAll
)

//coursierChecksums := Nil

lazy val moduleJars = taskKey[Seq[(Attributed[File], java.lang.module.ModuleDescriptor)]]("moduleJars")
moduleJars := {
  val attributedJars = (Compile/dependencyClasspathAsJars).value//.filterNot(_.metadata.get(moduleID.key).exists(_.organization == "org.scala-lang"))
  val moduleIdAttr = AttributeKey[ModuleID]("moduleID")
  val modules = attributedJars.filterNot(_.metadata.get(moduleIdAttr).exists(_.name.matches("scala.?-library.*"))).flatMap { aj =>
    try {
      val module = java.lang.module.ModuleFinder.of(aj.data.toPath).findAll().iterator.next.descriptor
      Some(aj -> module)//.filter(!_._2.modifiers.contains(java.lang.module.ModuleDescriptor.Modifier.AUTOMATIC))
    } catch { case _: java.lang.module.FindException => None }
  }
  modules
}

javaOptions ++= {
  val modules = moduleJars.value
  Seq(
    "--add-modules=" + modules.map(_._2.name).mkString(","),
    "--module-path=" + modules.map(_._1.data.getAbsolutePath).mkString(java.io.File.pathSeparator),
    "--add-opens=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED",
    "--add-opens=javafx.base/javafx.collections=ALL-UNNAMED",
    "--add-opens=org.controlsfx.controls/org.controlsfx.dialog=ALL-UNNAMED",
    "--add-exports=javafx.base/com.sun.javafx.runtime=controlsfx",
  )
}

reStart/mainClass := Some("expenser.ui.DevAppReloader")
javacOptions := javaOptions.value


enablePlugins(JavaAppPackaging)
Compile / packageDoc / mappings := Seq()

Universal/mappings := {
  val prev = (Universal/mappings).value
  val modules = moduleJars.value
  prev.filterNot { case (file, mapping) => modules.exists(_._1.data == file) } ++
  (for { (file, module) <- modules } yield file.data -> s"libmods/${file.data.name}")
}

Universal / javaOptions ++= Seq(
  "-J-Xmx100m",
  "-J-Xss512k",
  "-J-XX:CICompilerCount=2",
  "-J-XX:VMThreadStackSize=2048",
  "-J--add-modules=" + moduleJars.value.map(_._2.name).mkString(","),
  "-J--add-opens=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED",
  "-J--add-opens=javafx.base/javafx.collections=ALL-UNNAMED",
  "-J--add-exports=javafx.base/com.sun.javafx.runtime=controlsfx",
)

bashScriptExtraDefines ++= {
  val modules = moduleJars.value
  Seq(
    "addJava --module-path=" + modules.map(f => "${app_home}/../libmods/" + f._1.data.name).mkString(":")
  )
}
batScriptExtraDefines ++= {
  val modules = moduleJars.value
  Seq(
    "call :add_java \"--module-path=" + modules.map(f => "%APP_HOME%\\libmods\\" + f._1.data.name).mkString(";") + "\""
  )
}
