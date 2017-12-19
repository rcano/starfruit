package starfruit.ui

import javafx.application.Application
import javafx.scene._
import javafx.stage._
import scala.collection.JavaConverters._


trait BaseApplication extends Application {
  def sceneRoot(): Parent
  override def init() = {
    println("named parameters " + getParameters.getNamed)
    val fontSize = getParameters.getNamed.asScala.get("fontSize").map(_.toDouble)
    if (util.Properties.isLinux || fontSize.isDefined) {
      println("primary screen dpi " + Screen.getPrimary().getDpi)
      val systemFontSize = Screen.getPrimary().getDpi match {
        case -1 => None
        case screenDpi=> Some(screenDpi / 12) // 12 points
      }
      
      fontSize.orElse(systemFontSize) foreach (systemFontSize => classOf[com.sun.javafx.font.PrismFontFactory].getDeclaredField("systemFontSize").
                              modify(_.setAccessible(true)).setFloat(null, systemFontSize.toFloat))
    }
  }
  def start(stage): Unit = {
    stage.setTitle("Starfruit")
    
    val root = sceneRoot()
    val scene = new Scene(root)
    
    stage.setScene(scene)
    stage.sizeToScene()
    extraInitialize(stage)
    stage.show()
  }
  def extraInitialize(stage: Stage): Unit = {}
}