package starfruit
package ui

import language.reflectiveCalls
import better.files._
import java.time.{LocalDateTime, ZoneId, Clock, Duration}
import javafx.application.Platform
import javafx.collections.transformation.SortedList
import javafx.scene.control._
import javafx.scene.image.Image
import javafx.scene.input.{KeyCode, KeyCombination, KeyEvent}
import javafx.scene.paint.Color
import javafx.stage.FileChooser
import javafx.stage.Stage
import org.controlsfx.validation.ValidationMessage
import org.controlsfx.validation.decoration.GraphicValidationDecoration
import prickle._, AlarmPicklers._
import scala.collection.JavaConverters._
import scala.util._

class MainApplication extends BaseApplication {
  Platform.setImplicitExit(false)
  util.InstanceDetector.setupInstance(Main.instanceFile) {
    println("got pinged")
    Platform.runLater { () => 
      println("showing panel?")
      val stage = sceneRoot.getScene.getWindow.asInstanceOf[Stage]
      //the following code will look incredibly dumb: it is. Javafx is that dumb.
      stage.setX(stage.getX)
      stage.setY(stage.getY)
      stage.setWidth(stage.getWidth)
      stage.setHeight(stage.getHeight)
      stage.show()
      stage.toFront()
    }
  }
  
  val sceneRoot = new MainWindow()
  implicit val pconfig = PConfig.Default.copy(areSharedObjectsSupported = false)

  val alarmsFile = File.home / ".starfruit-alarms"
  val alarms = javafx.collections.FXCollections.observableArrayList[AlarmState]()
  
  val alarmsTableSortedList = new SortedList(new FxCollectionsExtra.ObservableView(alarms)({ s =>
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
  sceneRoot.alarmsTable.setItems(alarmsTableSortedList)
  alarmsTableSortedList.comparatorProperty bind sceneRoot.alarmsTable.comparatorProperty
  
  /*************************************
   * UI tunning
   *************************************/
  sceneRoot.toolBar.newButton.displayAlarm setOnAction { _ => 
    val dialog = new AlarmDialog(sceneRoot.getScene.getWindow, None)
    var resAlarm: Option[Alarm] = None
    dialog.okButton.setOnAction { _ =>
      resAlarm = Some(dialog.getAlarm)
      dialog.close()
    }
    dialog.showAndWait()
    resAlarm foreach { alarm => `do`(NewAlarm(alarm)) }
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
    //when the list changes, persist it
    storeAlarms(alarms.asScala).failed.foreach { ex => new Alert(Alert.AlertType.ERROR, "Failed persisting alarms: " + ex.toString).modify(_.setResizable(true)).show() }
    ()
  }
  sceneRoot.toolBar.undoButton.setOnAction(_ => undo())
  sceneRoot.toolBar.redoButton.setOnAction(_ => redo())
  
  sceneRoot.menuBar.fileMenu.importAlarms.setOnAction { _ =>
    val fileChooser = new FileChooser().modify(_.setTitle("Open calendar file"),
                                               _.getExtensionFilters.add(new FileChooser.ExtensionFilter("Calendar File", "*.ics")))
    Option(fileChooser.showOpenDialog(sceneRoot.getScene.getWindow)).foreach { file =>
      ICalendar.parse(file.toScala.contentAsString) match {
        case Success(alarms) => `do`(ImportAlarms(alarms))
        case Failure(ex) => new Alert(Alert.AlertType.ERROR, "Something went wrong:\n" + ex, ButtonType.OK).modify(_.setResizable(true)).show()
      }
    }
  }
  sceneRoot.menuBar.fileMenu.exit.setOnAction(_ => Platform.exit())
  
  val graphicsDecorator = new GraphicValidationDecoration()
  sceneRoot.findTextField.textProperty.addListener((_, _,_) => graphicsDecorator.removeDecorations(sceneRoot.findTextField))
  sceneRoot.findTextField.setOnAction { _ =>
    val text = sceneRoot.findTextField.getText
    sceneRoot.alarmsTable.getItems.asScala.indexWhere(_._5.toLowerCase contains text.toLowerCase) match {
      case -1 => graphicsDecorator.applyValidationDecoration(ValidationMessage.error(sceneRoot.findTextField, "Not found"))
      case other => sceneRoot.alarmsTable.getSelectionModel.select(other)
    }
  }
  private def findNextOrPrev(nextOrPrev: Boolean) {
    val text = sceneRoot.findTextField.getText
    if (text.nonEmpty) {
      val selected = sceneRoot.alarmsTable.getSelectionModel.getSelectedIndex.max(0)
      val nextIdx = if (nextOrPrev)
        sceneRoot.alarmsTable.getItems.asScala.indexWhere(_._5.toLowerCase contains text.toLowerCase, selected + 1)  
      else 
        sceneRoot.alarmsTable.getItems.asScala.lastIndexWhere(_._5.toLowerCase contains text.toLowerCase, selected - 1)  
      nextIdx match {
        case -1 => //do nothing in this case
        case other => sceneRoot.alarmsTable.getSelectionModel.select(other)
      }
    }
  }
  sceneRoot.findNext.setOnAction(_ => findNextOrPrev(true))
  sceneRoot.findPrevious.setOnAction(_ => findNextOrPrev(false))
  
  override def extraInitialize(stage) = {
    stage.getIcons.add(new Image("/starfruit.png"))
    stage.getScene.getAccelerators.put(KeyCombination.valueOf("Shortcut+F"), () => sceneRoot.toolBar.findButton.fire())
    stage.getScene.getAccelerators.put(KeyCombination.valueOf("F3"), { () =>
        sceneRoot.toolBar.findButton.fire()
        sceneRoot.findNext.fire()
      })
    stage.getScene.getAccelerators.put(KeyCombination.valueOf("Shift+F3"), { () =>
        sceneRoot.toolBar.findButton.fire()
        sceneRoot.findPrevious.fire()
      })
    //need to prevet the findTextField from capturing these keys
    sceneRoot.findTextField.addEventHandler(KeyEvent.KEY_PRESSED, { evt: KeyEvent =>
        if (evt.getCode == KeyCode.F3) {
          if (evt.isShiftDown) sceneRoot.findPrevious.fire()
          else sceneRoot.findNext.fire()
        }
      })
  }
  
  /********************************
   * Definition of actions
   *******************************/
  sealed trait Action {
    def `do`(): Unit
    def undo(): Unit
  }
  case class NewAlarm(alarm: Alarm) extends Action {
    def `do`() = alarms.synchronized { alarms.add(AlarmState(alarm, AlarmState.Active, wallClock.instant)) }
    def undo() = alarms.synchronized { alarms.removeIf(_.alarm eq alarm) }
  }
  case class DeleteAlarm(alarm: AlarmState) extends Action {
    def `do`() = alarms.synchronized { alarms.remove(alarm) }
    def undo() = alarms.synchronized { alarms.add(alarm) }
  }
  case class EditAlarm(old: AlarmState, updated: Alarm) extends Action {
    def `do`() = alarms.synchronized { alarms.set(alarms.indexOf(old), AlarmState(updated, AlarmState.Active, wallClock.instant)) }
    def undo() = alarms.synchronized { alarms.set(alarms.asScala.indexWhere(_.alarm eq updated), old) }
  }
  case class ImportAlarms(importedAlarms: Seq[AlarmState]) extends Action {
    def `do`() = alarms.synchronized { alarms.addAll(importedAlarms:_*) }
    def undo() = alarms.synchronized { importedAlarms foreach (ia => alarms.removeIf(_.alarm == ia.alarm)) }
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
  
  /****************************************************
   * configuration for the state machine periodic task
   ***************************************************/
  loadAlarms.fold[Unit](ex => new Alert(Alert.AlertType.ERROR, "Failed loading alarms: " + ex).modify(_.setResizable(true)).show(), alarms.addAll(_:_*))
  println("Loaded alarms " + alarms)
  Platform.runLater(() => alarms.asScala.filter(_.state == AlarmState.Showing) foreach showAlarm) //run later to ensure stage initialization
  
  
  private val showingAlarms = collection.mutable.Map[Alarm, Alert]()
  val wallClock = Clock.tickMinutes(ZoneId.systemDefault)
  val checkerThread = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(runnable => new Thread(null, runnable, "Clock", 1024*100).modify(_.setDaemon(true)))
  checkerThread.scheduleAtFixedRate(() => {
      val now = wallClock.instant()
      alarms.synchronized {
        var changesDetected = false
        val newStates = alarms.asScala.map { state =>
//          println("checking " + state)
          val checkResult = AlarmStateMachine.checkAlarm(now, state)
//          println("  ===> " + checkResult)
          checkResult match {
            case AlarmStateMachine.KeepState => state
            case AlarmStateMachine.NotifyAlarm(next) =>
              changesDetected = true
              showAlarm(next)
              next
            case AlarmStateMachine.NotifyReminder(next) =>
              changesDetected = true
              Platform.runLater(() => Utils.newAlert(sceneRoot.getScene)("Reminder for:\n" + next.alarm.message.get().fold(_.toString, identity) + 
                                                                         "\nocurring in " + Duration.between(now, next.nextOccurrence), next.alarm.foregroundColor,
                                                                         next.alarm.backgroundColor, next.alarm.font, ButtonType.OK).show())
              next
            case AlarmStateMachine.AutoCloseAlarmNotification(next) =>
              changesDetected = true
              Platform.runLater(() => showingAlarms.remove(next.alarm) foreach (_.close()))
              AlarmStateMachine.advanceAlarm(next.copy(state = AlarmState.Active))
          }
        }.filter(_.state != AlarmState.Ended)
        if (changesDetected) {
          Platform.runLater { () =>
            val idx = sceneRoot.alarmsTable.getSelectionModel.getSelectedIndex
            alarms.setAll(newStates:_*)
            sceneRoot.alarmsTable.getSelectionModel.select(idx)
          }
        }
      }
    }, 0, 5, scala.concurrent.duration.SECONDS)
  
  def showAlarm(state: AlarmState): Unit = {
    Platform.runLater { () =>
      val now = wallClock.instant()
      val message = state.alarm.message.get()
      val alert = Utils.newAlert(sceneRoot.getScene)(message.fold(_.toString, identity), state.alarm.foregroundColor,
                                                     state.alarm.backgroundColor, state.alarm.font, ButtonType.OK)
      showingAlarms(state.alarm) = alert
      alert.showAndWait.ifPresent(_ => 
        //must run this later, to ensure the alarms where properly updated
        Platform.runLater { () =>
          val futureInstances = Iterator.iterate(AlarmStateMachine.advanceAlarm(state.copy(state = AlarmState.Active)))(
                AlarmStateMachine.advanceAlarm).filter(s => s.nextOccurrence.isAfter(now) || s.state == AlarmState.Ended)
          val next2 = futureInstances.next()
          println("Advancing alarm to " + next2)
          if (next2.state == AlarmState.Ended) alarms.remove(state)
          else alarms.set(alarms.indexOf(state), next2)
        })
    }
  }
  
  /************************
   * MICS
   ***********************/
  implicit def codec = io.Codec.UTF8
  
  def storeAlarms(alarms: Seq[AlarmState]): Try[File] = Try {
    val backupFile = alarmsFile.sibling(alarmsFile.name + "~").createIfNotExists(false, false)
    backupFile.writeText(Pickle.intoString(alarms)).moveTo(alarmsFile, true)
  }
  def loadAlarms(): Try[Seq[AlarmState]] = if (alarmsFile.exists()) Unpickle[Seq[AlarmState]].fromString(alarmsFile.contentAsString) else Success(Seq.empty)
}
