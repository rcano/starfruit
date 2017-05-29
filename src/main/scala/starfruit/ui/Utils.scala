package starfruit.ui

import javafx.scene.Scene
import javafx.scene.control._

object Utils {

  def newAlert(parentScene: Scene)(msg: String,
               foreground: String, background: String, font: String,
               buttons: ButtonType*) = {
    val res = new Alert(Alert.AlertType.INFORMATION, msg, buttons:_*)
    res.setResizable(true)
    res.getDialogPane.getScene.getStylesheets.addAll(parentScene.getStylesheets)
    val label = res.getDialogPane.lookup(".content").asInstanceOf[Label]
    label.setStyle(s"-fx-text-fill: $foreground; -fx-font: $font")
    label.getParent.setStyle(s"-fx-background-color: $background")
    res.getDialogPane.getScene.getWindow.sizeToScene()
    res
  }
}
