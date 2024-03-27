package starfruit.ui

import java.time.{Instant, ZonedDateTime, ZoneId}
import java.time.format.DateTimeFormatter
import javafx.scene.Scene
import javafx.scene.control._
import javafx.stage.Modality

object Utils {

  val userInstantFormatter = DateTimeFormatter.RFC_1123_DATE_TIME
  def instantToUserString(i: Instant): String = userInstantFormatter `format` ZonedDateTime.ofInstant(i, ZoneId.systemDefault)
  
  def newAlert(parentScene: Scene)(header: String, msg: String,
                                   foreground: String, background: String, font: String,
                                   buttons: ButtonType*) = {
    val res = new Alert(Alert.AlertType.INFORMATION, msg, buttons*)
    res.initModality(Modality.NONE)
    res.setResizable(true)
    res.getDialogPane.getScene.getStylesheets.addAll(parentScene.getStylesheets)
    res.getDialogPane.setHeaderText(header)
    val label = res.getDialogPane.lookup(".content").asInstanceOf[Label]
    label.setStyle(s"-fx-text-fill: $foreground; -fx-font: $font")
    label.getParent.setStyle(s"-fx-background-color: $background")
    res.getDialogPane.getScene.getWindow.sizeToScene()
    res
  }
}
