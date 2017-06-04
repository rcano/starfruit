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
      val systemFontSize = Screen.getPrimary().getDpi match {
        case -1 => fontSize
        case screenDpi=> Some(screenDpi / 6) // 12 points
      }
    
      systemFontSize foreach { systemFontSize =>
        val fontFactoryClass = classOf[com.sun.javafx.font.PrismFontFactory]
        fontFactoryClass.getDeclaredField("systemFontSize").modify(_.setAccessible(true)).setFloat(null, systemFontSize.toFloat)
      }
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