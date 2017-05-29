package starfruit
package ui

import javafx.scene.text.Font
import language.reflectiveCalls
import java.time.{LocalDateTime, ZoneId, Instant, Clock, Duration}
import javafx.application.{Application, Platform}
import javafx.scene.control._
import javafx.scene.paint.Color
import scala.collection.JavaConverters._

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

  val alarms = javafx.collections.FXCollections.observableArrayList[AlarmState]()
  sceneRoot.alarmsTable.setItems(new FxCollectionsExtra.ObservableView(alarms)({ s =>
        val recString = s.alarm.recurrence match {
          case Alarm.NoRecurrence => "no recurrence"
          case Alarm.HourMinutelyRecurrence(h, m) => s"${h}H ${m}M"
          case Alarm.DailyRecurrence(every, _) => s"every ${every} day(s)"
          case Alarm.WeeklyRecurrence(every, onDays) => s"every ${every} week(s), ${onDays.size} days a week."
          case Alarm.MonthlyRecurrence(every, Alarm.NthDayOfMonth(d)) => s"every ${every} month(s), on the $d"
          case Alarm.MonthlyRecurrence(every, Alarm.NthWeekDayOfMonth(week, day)) =>
            week match {
              case -5 | -4 => s"every ${every} month(s), the ${week}th last $day"
              case -3 => s"every ${every} month(s), the 3rd last $day"
              case -2 => s"every ${every} month(s), the 2nd last $day"
              case -1 => s"every ${every} month(s), the last $day"
              case 1 => s"every ${every} month(s), the first $day"
              case 2 => s"every ${every} month(s), the 2nd $day"
              case 3 => s"every ${every} month(s), the 3rd $day"
              case 4 | 5 => s"every ${every} month(s), the ${week}th $day"
            }
            
          case Alarm.YearlyRecurrence(every, _, _, _) => s"every ${every} year(s)"
        }
        val msg = s.alarm.message match {
          case m: Alarm.TextMessage => m.message
          case m: Alarm.ScriptOutputMessage => "script: " + m.script
          case m: Alarm.FileContentsMessage => "file: " + m.path
        }
        (LocalDateTime.ofInstant(s.nextOccurrence, ZoneId.systemDefault), recString, Color.web(s.alarm.backgroundColor), "🖅", msg)
      }))
  
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
    val dialog = new AlarmDialog(sceneRoot.getScene.getWindow, Some(selected.alarm))
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
    def `do`() = alarms.add(AlarmState(alarm, AlarmState.Active, wallClock.instant))
    def undo() = alarms.remove(alarm)
  }
  case class DeleteAlarm(alarm: AlarmState) extends Action {
    def `do`() = alarms.remove(alarm)
    def undo() = alarms.add(alarm)
  }
  case class EditAlarm(old: AlarmState, updated: Alarm) extends Action {
    def `do`() = alarms.set(alarms.indexOf(old), AlarmState(updated, AlarmState.Active, wallClock.instant))
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
  
  private val showingAlarms = collection.mutable.Map[Alarm, Alert]()
  
  val wallClock = Clock.tickMinutes(ZoneId.systemDefault)
  val checkerThread = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(runnable => new Thread(null, runnable, "Clock", 1024*100))
  checkerThread.scheduleAtFixedRate(() => {
      val now = wallClock.instant()
      val newStates = alarms.asScala.map { state =>
        println("checking " + state)
        val checkResult = AlarmStateMachine.checkAlarm(now, state)
        println("  ===> " + checkResult)
        checkResult match {
          case AlarmStateMachine.KeepState => state
          case AlarmStateMachine.NotifyAlarm(next) =>
            Platform.runLater { () =>
              val message = next.alarm.message.get()
              val alert = Utils.newAlert(sceneRoot.getScene)(message.fold(_.toString, identity), next.alarm.foregroundColor,
                                   next.alarm.backgroundColor, next.alarm.font, ButtonType.OK)
              showingAlarms(next.alarm) = alert
              alert.showAndWait.ifPresent(_ => 
                //must run this later, to ensure the alarms where properly updated
                Platform.runLater { () =>
                  val next2 = AlarmStateMachine.advanceAlarm(next.copy(state = AlarmState.Active))
                  if (next2.state == AlarmState.Ended) alarms.remove(next)
                  else alarms.set(alarms.indexOf(next), next2)
                })
            }
            next
          case AlarmStateMachine.NotifyReminder(next) =>
            Platform.runLater(() => Utils.newAlert(sceneRoot.getScene)("Reminder for:\n" + next.alarm.message.get().fold(_.toString, identity) + 
                                             "\nocurring in " + Duration.between(now, next.nextOccurrence), next.alarm.foregroundColor,
                                             next.alarm.backgroundColor, next.alarm.font, ButtonType.OK).show())
            next
          case AlarmStateMachine.AutoCloseAlarmNotification(next) =>
            Platform.runLater(() => showingAlarms.remove(next.alarm) foreach (_.close()))
            AlarmStateMachine.advanceAlarm(next.copy(state = AlarmState.Active))
        }}
      Platform.runLater { () =>
        val idx = sceneRoot.alarmsTable.getSelectionModel.getSelectedIndex
        alarms.setAll(newStates.filter(_.state != AlarmState.Ended):_*)
        sceneRoot.alarmsTable.getSelectionModel.select(idx)
      }
    }, 0, 1, scala.concurrent.duration.SECONDS)
}
