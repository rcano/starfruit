package starfruit
package ui

import java.time.{LocalDateTime, ZoneId}
import javafx.application.Application
import javafx.scene.paint.Color
import language.reflectiveCalls

import prickle._, AlarmPicklers._

object Main {
  def main(args: Array[String]): Unit = {
    System.setProperty("prism.lcdtext", "false")
    System.setProperty("prism.text", "t2k")
    Application.launch(classOf[Main], args:_*)
  }
}
class Main extends BaseApplication {
  val sceneRoot = new MainWindow()
  implicit val pconfig = PConfig.Default.copy(areSharedObjectsSupported = false)

  val alarms = javafx.collections.FXCollections.observableArrayList[Alarm]()
  sceneRoot.alarmsTable.setItems(new FxCollectionsExtra.ObservableView(alarms)(a =>
      (LocalDateTime.ofInstant(null, ZoneId.systemDefault), a.recurrence.toString, Color.web(a.backgroundColor), "🖅", a.message.toString)
    ))
  
  sceneRoot.toolBar.newButton.displayAlarm setOnAction { _ => 
    val dialog = new AlarmDialog(sceneRoot.getScene.getWindow, None)
    var resAlarm: Option[Alarm] = None
    dialog.okButton.setOnAction { _ =>
      resAlarm = Some(dialog.getAlarm)
      dialog.close()
    }
    dialog.showAndWait()
//    println("Got alarm\n" + resAlarm)
    resAlarm foreach { alarm =>
//      val pickled = Pickle.intoString(alarm)
//      println(pickled)
//      val rehydrated = Unpickle[Alarm].fromString(pickled)
//      println("Rehydrated: " + rehydrated)
      `do`(NewAlarm(alarm))
    }
  }
  Seq(sceneRoot.toolBar.copyButton, sceneRoot.toolBar.editButton, sceneRoot.toolBar.deleteButton) foreach (
    _.disableProperty bind sceneRoot.alarmsTable.getSelectionModel.selectedItemProperty.isNull)
  sceneRoot.toolBar.editButton.setOnAction { _ =>
    val selected = alarms.get(sceneRoot.alarmsTable.getSelectionModel.getSelectedIndex)
    val dialog = new AlarmDialog(sceneRoot.getScene.getWindow, Some(selected))
    var resAlarm: Option[Alarm] = None
    dialog.okButton.setOnAction { _ =>
      resAlarm = Some(dialog.getAlarm)
      dialog.close()
    }
    dialog.showAndWait()
    resAlarm foreach (n => `do`(EditAlarm(selected, n)))
  }
  sceneRoot.toolBar.deleteButton.setOnAction { _ =>
    `do`(DeleteAlarm(alarms.get(sceneRoot.alarmsTable.getSelectionModel.getSelectedIndex)))
  }
  sceneRoot.toolBar.undoButton.setDisable(true)
  sceneRoot.toolBar.redoButton.setDisable(true)
  alarms.addListener { evt =>
    sceneRoot.toolBar.undoButton.setDisable(undoQueue.isEmpty)
    sceneRoot.toolBar.redoButton.setDisable(redoQueue.isEmpty)
  }
  sceneRoot.toolBar.undoButton.setOnAction(_ => undo())
  sceneRoot.toolBar.redoButton.setOnAction(_ => redo())
  
  sealed trait Action {
    def `do`(): Unit
    def undo(): Unit
  }
  case class NewAlarm(alarm: Alarm) extends Action {
    def `do`() = alarms.add(alarm)
    def undo() = alarms.remove(alarm)
  }
  case class DeleteAlarm(alarm: Alarm) extends Action {
    def `do`() = alarms.remove(alarm)
    def undo() = alarms.add(alarm)
  }
  case class EditAlarm(old: Alarm, updated: Alarm) extends Action {
    def `do`() = alarms.set(alarms.indexOf(old), updated)
    def undo() = alarms.set(alarms.indexOf(updated), old)
  }
  
  val undoQueue = collection.mutable.Stack.empty[Action]
  val redoQueue = collection.mutable.Stack.empty[Action]
  
  def `do`(a: Action): Unit = {
    undoQueue push a
    a.`do`()
  }
  def redo() = `do`(redoQueue.pop)
  def undo(): Unit = {
    val elem = undoQueue.pop
    redoQueue push elem
    elem.undo()
  }
}
