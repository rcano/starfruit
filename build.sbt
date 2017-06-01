name := "starfruit"
version := "1.0"
scalaVersion := "2.12.2"
scalacOptions ++= Seq("-deprecation", "-feature", "-Yinfer-argument-types", "-Ypartial-unification", "-Xlint", "-opt:_", "-opt-warnings:_")
fork := true

libraryDependencies ++= Seq(
  "com.github.pathikrit" %% "better-files" % "2.17.1",
  "org.controlsfx" % "controlsfx" % "8.40.12",
  "com.beachape" %% "enumeratum" % "1.5.10",
  "com.github.benhutchison" %% "prickle" % "1.1.13",
  "com.lihaoyi" %% "fastparse" % "0.4.3",
  "org.scalatest" %% "scalatest" % "3.0.1" % "test",
  "org.scalacheck" %% "scalacheck" % "1.13.4" % "test"
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
  ShadeRule.keep("scala.runtime.AbstractFunction2").inAll,
  ShadeRule.keep("scala.runtime.StructuralCallSite").inAll,
  ShadeRule.keep("scala.runtime.java8.*").inAll,
  ShadeRule.keep("prickle.*").inAll,
  ShadeRule.keep("microjson.*").inAll,
  ShadeRule.keep("org.controlsfx.dialog.FontSelectorDialog").inAll
)
