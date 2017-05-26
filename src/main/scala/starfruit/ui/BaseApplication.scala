package starfruit.ui

import better.files.File
import javafx.application.Application
import javafx.scene._
import javafx.scene.text.Font
import javafx.stage._
import scala.collection.JavaConverters._


trait BaseApplication extends Application {
  def sceneRoot(): Parent
  
  def start(stage): Unit = {
    stage.setTitle("Starfruit")
    
    val root = sceneRoot()
    val scene = new Scene(root)
    println("named parameters " + getParameters.getNamed)
    val fontSize = getParameters.getNamed.asScala.get("fontSize").map(_.toDouble)
    if (util.Properties.isLinux || fontSize.isDefined) {
      val systemFontSize = Screen.getPrimary().getDpi match {
        case -1 => fontSize
        case screenDpi=> Some(screenDpi / 6) // 12 points
      }
    
      systemFontSize foreach { systemFontSize =>
        val dpiCss = File.newTemporaryFile("starfruit", "dpi.css").write(
          s"""
.text {
  -fx-font-size: $systemFontSize
}
.label {
  -fx-font-size: $systemFontSize
}
.text-input {
  -fx-font-size: $systemFontSize
}
""")
        
        println("loading dpi file " + dpiCss.pathAsString)
        scene.getStylesheets.add("file://" + dpiCss.pathAsString)
        BaseApplication.defaultFont = new Font(Font.getDefault.getName, systemFontSize)
      }
    }
    
    extraInitialize(stage)
    stage.setScene(scene)
    stage.sizeToScene()
    stage.show()
  }
  def extraInitialize(stage: Stage): Unit = {}
}
object BaseApplication {
  private[this] var _defaultFont: Font = _
  private[BaseApplication] def defaultFont_=(f: Font) = _defaultFont = f
  def defaultFont = if (_defaultFont == null) Font.getDefault else _defaultFont
}