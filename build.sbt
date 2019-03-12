name := "starfruit"
version := "1.1"
scalaVersion := "2.12.8"
scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-Yinfer-argument-types", "-Yno-adapted-args", "-Xlint", "-Ypartial-unification",
   "-opt:l:method,inline", "-opt-inline-from:scala.**,starfruit.**,tangerine.**", "-opt-warnings:_", "-Ywarn-dead-code", "-Ywarn-extra-implicit", "-Ywarn-inaccessible", "-Ywarn-infer-any", "-Ywarn-nullary-override", "-Ywarn-nullary-unit", "-Ywarn-numeric-widen", "-Ywarn-unused:_", "-Ywarn-value-discard")
fork := true

lazy val jfxVersion = "11"
lazy val jfxClassifier = settingKey[String]("jfxClassifier")
jfxClassifier := {
  if (scala.util.Properties.isWin) "win"
  else if (scala.util.Properties.isLinux) "linux"
  else if (scala.util.Properties.isMac) "mac"
  else throw new IllegalStateException(s"Unknown OS: ${scala.util.Properties.osName}")
}

dependsOn(RootProject(file("../tangerine")))

autoScalaLibrary := false
libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-library" % scalaVersion.value % "provided",
  "org.openjfx" % "javafx-graphics" % jfxVersion % "provided" classifier jfxClassifier.value,
  "org.openjfx" % "javafx-controls" % jfxVersion % "provided" classifier jfxClassifier.value,
  "org.openjfx" % "javafx-base" % jfxVersion % "provided" classifier jfxClassifier.value,
  "org.openjfx" % "javafx-media" % jfxVersion % "provided" classifier jfxClassifier.value,
  "org.openjfx" % "javafx-swing" % jfxVersion % "provided" classifier jfxClassifier.value,

  "com.github.pathikrit" %% "better-files" % "3.7.0",
  "org.controlsfx" % "controlsfx" % "9.0.0",
  "com.beachape" %% "enumeratum" % "1.5.13",
  "com.github.benhutchison" %% "prickle" % "1.1.14",
  "com.lihaoyi" %% "fastparse" % "2.1.0",
  "org.scalatest" %% "scalatest" % "3.0.5" % "test",
  "org.scalacheck" %% "scalacheck" % "1.14.0" % "test"
)

mainClass in reStart := Some("starfruit.ui.DevAppReloader")
mainClass in assembly := Some("starfruit.ui.Main")

assemblyShadeRules in assembly := Seq(
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
  val modules = attributedJars.flatMap { aj =>
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
    "--add-exports=javafx.base/com.sun.javafx.runtime=controlsfx",
  )
}

reStart/mainClass := Some("expenser.ui.DevAppReloader")
javacOptions := javaOptions.value


enablePlugins(JavaAppPackaging)
mappings in (Compile, packageDoc) := Seq()

Universal/mappings := {
  val prev = (Universal/mappings).value
  val modules = moduleJars.value
  prev.filterNot { case (file, mapping) => modules.exists(_._1.data == file) } ++
  (for { (file, module) <- modules } yield file.data -> s"libmods/${file.data.name}")
}

javaOptions in Universal ++= Seq(
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
