package starfruit
package ui

import scala.reflect.Selectable.reflectiveSelectable

import better.files._
import java.time.{Clock, Duration, LocalDateTime, LocalTime, ZoneId, ZonedDateTime}
import java.time.temporal.ChronoUnit
import javafx.application.Platform
import javafx.collections.ListChangeListener
import javafx.collections.transformation.SortedList
import javafx.scene.Scene
import javafx.scene.control._
import javafx.scene.image.Image
import javafx.scene.input.{KeyCode, KeyCombination, KeyEvent, MouseButton}
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.stage.FileChooser
import javafx.stage.Stage
import org.controlsfx.dialog.ExceptionDialog
import org.controlsfx.validation.ValidationMessage
import org.controlsfx.validation.decoration.GraphicValidationDecoration
import scala.jdk.CollectionConverters.*
import scala.util.*
import scala.util.chaining.*
import play.api.libs.json.Json
import better.files.File.CopyOptions

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

  lazy val sceneRoot: MainWindow = new MainWindow()

  val dataFolder = (File.home / ".local" / "share" / "starfruit").createDirectoryIfNotExists(createParents = true)
  val alarmsFile = ".starfruit-alarms"
  val alarmsGraveyard = ".starfruit-graveyard"
  val alarms = javafx.collections.FXCollections.observableArrayList[AlarmState]()

  private def alarmtState2Table(s: AlarmState) = {
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
  }

  val alarmsTableSortedList = new SortedList(new FxCollectionsExtra.ObservableView(alarms)(alarmtState2Table))

  override def extraInitialize(stage: Stage) = {
    stage.getIcons.add(new Image("/starfruit.png"))

    /** *********************************** UI tunning
      */

    def showEditAlarmDialog(baseAlarm: Option[Alarm]) = {
      val dialog = new AlarmDialog(sceneRoot.getScene.getWindow, baseAlarm)
      var resAlarm: Option[Alarm] = None
      dialog.okButton.setOnAction { _ =>
        val alarm = dialog.getAlarm
        val now = wallClock.instant
        val nextOccurrence = AlarmState(alarm, AlarmState.Active, now).nextOccurrence
        if (
          nextOccurrence.isBefore(
            now
          ) && (baseAlarm.isEmpty || baseAlarm.get.when != alarm.when || baseAlarm.get.recurrence != alarm.recurrence)
        ) {
          val date = LocalDateTime.ofInstant(nextOccurrence, ZoneId.systemDefault)
          new Alert(
            Alert.AlertType.CONFIRMATION,
            s"The alarm has a next ocurrence [${sceneRoot.alarmsTable.timeCol.formatter.format(date)}]" +
              " which is before now.\nAre you sure you wish to create it like this?"
          ).modify(
            _.setResizable(true),
            a => {
              a.getDialogPane.setPrefHeight(350)
              a.getDialogPane.setPrefWidth(550)
            }
          ).showAndWait()
            .ifPresent {
              case ButtonType.OK =>
                resAlarm = Some(alarm)
                dialog.close()
              case _ =>
            }
        } else {
          resAlarm = Some(alarm)
          dialog.close()
        }
      }
      dialog.showAndWait()
      resAlarm
    }

    sceneRoot.toolBar.newButton.displayAlarm setOnAction { _ =>
      showEditAlarmDialog(None) foreach { alarm => `do`(NewAlarm(alarm)) }
    }
    Seq(
      sceneRoot.toolBar.copyButton,
      sceneRoot.toolBar.editButton,
      sceneRoot.toolBar.deleteButton
    ) foreach (_.disableProperty `bind` sceneRoot.alarmsTable.getSelectionModel.selectedItemProperty.isNull)
    sceneRoot.toolBar.copyButton.setOnAction { _ =>
      val selected = alarms.get(alarmsTableSortedList.getSourceIndex(sceneRoot.alarmsTable.getSelectionModel.getSelectedIndex))
      showEditAlarmDialog(Some(selected.alarm)) foreach (n => `do`(NewAlarm(n)))
    }
    sceneRoot.toolBar.editButton.setOnAction { _ =>
      val selected = alarms.get(alarmsTableSortedList.getSourceIndex(sceneRoot.alarmsTable.getSelectionModel.getSelectedIndex))
      if (!showingAlarms.contains(selected.alarm)) {
        showEditAlarmDialog(Some(selected.alarm)) foreach (n => `do`(EditAlarm(selected, n)))
      }
    }
    sceneRoot.alarmsTable.setOnMouseClicked { evt =>
      if (evt.getClickCount == 2 && evt.getButton == MouseButton.PRIMARY && !sceneRoot.toolBar.editButton.isDisabled())
        sceneRoot.toolBar.editButton.fire()
    }
    sceneRoot.toolBar.deleteButton.setOnAction { _ =>
      val selected = alarms.get(alarmsTableSortedList.getSourceIndex(sceneRoot.alarmsTable.getSelectionModel.getSelectedIndex))
      if (!showingAlarms.contains(selected.alarm)) {
        new Alert(
          Alert.AlertType.CONFIRMATION,
          s"Do you really wish to delete the alarm\n${selected.alarm.message.get().fold(_.toString, identity)}?." +
            "\nThis operation is only undoable as long as you don't close the application."
        ).showAndWait().ifPresent {
          case ButtonType.OK => `do`(DeleteAlarm(selected))
          case _ =>
        }
      }
    }
    sceneRoot.toolBar.undoButton.setDisable(true)
    sceneRoot.toolBar.redoButton.setDisable(true)
    alarms `addListener` new ListChangeListener[AlarmState] {
      def onChanged(evt: ListChangeListener.Change[? <: AlarmState]) = {
        sceneRoot.toolBar.undoButton.setDisable(undoQueue.isEmpty)
        sceneRoot.toolBar.redoButton.setDisable(redoQueue.isEmpty)
        //when the list changes, persist it
        storeAlarms(alarms.asScala.toSeq).failed.foreach { ex =>
          new Alert(Alert.AlertType.ERROR, "Failed persisting alarms: " + ex.toString).modify(_.setResizable(true)).show()
        }

//        if there is a removed set, purge the undo/redo actions that target them
        evt.next()
        if (evt.getRemovedSize > 0) {
          val removed = evt.getRemoved.asScala
          undoQueue = undoQueue.filterNot {
            case EditAlarm(old, _) if removed.exists(_.alarm == old.alarm) => true
            case NewAlarm(a) if removed.exists(_.alarm == a) => true
            case _ => false
          }
          redoQueue = redoQueue.filterNot {
            case EditAlarm(_, curr) if removed.exists(_.alarm == curr) => true
            case _ => false
          }
        }
      }
    }
    sceneRoot.toolBar.undoButton.setOnAction(_ => undo())
    sceneRoot.toolBar.redoButton.setOnAction(_ => redo())

    sceneRoot.menuBar.fileMenu.viewLog.setOnAction { _ =>
      val logStage = new Stage()
      logStage.initOwner(sceneRoot.getScene.getWindow)
      logStage.setTitle("Log")
      val logScene = new Scene(new TextArea(actionsLog.mkString("\n")).modify(_.setEditable(false)), 500, 500)
      logStage.setScene(logScene)
      logStage.sizeToScene()
      logStage.show()
    }

    sceneRoot.menuBar.fileMenu.importAlarms.setOnAction { _ =>
      val fileChooser = new FileChooser()
        .modify(_.setTitle("Open calendar file"), _.getExtensionFilters.add(new FileChooser.ExtensionFilter("Calendar File", "*.ics")))
      Option(fileChooser.showOpenDialog(sceneRoot.getScene.getWindow)).foreach { file =>
        ICalendar.parse(
          file.toScala.contentAsString,
          "normal " + Font.getDefault.getSize + " \"" + Font.getDefault.getFamily + "\""
        ) match {
          case Success(alarms) => `do`(ImportAlarms(alarms))
          case Failure(ex) =>
            new Alert(Alert.AlertType.ERROR, "Something went wrong:\n" + ex, ButtonType.OK).modify(_.setResizable(true)).show()
        }
      }
    }
    sceneRoot.menuBar.fileMenu.exit.setOnAction(_ => Platform.exit())

    val graphicsDecorator = new GraphicValidationDecoration()
    sceneRoot.findTextField.textProperty.addListener((_, _, _) => graphicsDecorator.removeDecorations(sceneRoot.findTextField))
    sceneRoot.findTextField.setOnAction { _ =>
      val text = sceneRoot.findTextField.getText
      sceneRoot.alarmsTable.getItems.asScala.indexWhere(_._5.toLowerCase `contains` text.toLowerCase) match {
        case -1 => graphicsDecorator.applyValidationDecoration(ValidationMessage.error(sceneRoot.findTextField, "Not found"))
        case other => sceneRoot.alarmsTable.getSelectionModel.select(other)
      }
    }
    def findNextOrPrev(nextOrPrev: Boolean): Unit = {
      val text = sceneRoot.findTextField.getText
      if (text.nonEmpty) {
        val selected = sceneRoot.alarmsTable.getSelectionModel.getSelectedIndex.max(0)
        val nextIdx =
          if (nextOrPrev)
            sceneRoot.alarmsTable.getItems.asScala.indexWhere(_._5.toLowerCase `contains` text.toLowerCase, selected + 1)
          else
            sceneRoot.alarmsTable.getItems.asScala.lastIndexWhere(_._5.toLowerCase `contains` text.toLowerCase, selected - 1)
        nextIdx match {
          case -1 => //do nothing in this case
          case other => sceneRoot.alarmsTable.getSelectionModel.select(other)
        }
      }
    }
    sceneRoot.findNext.setOnAction(_ => findNextOrPrev(true))
    sceneRoot.findPrevious.setOnAction(_ => findNextOrPrev(false))

    stage.getScene.getAccelerators.put(KeyCombination.valueOf("Shortcut+F"), () => sceneRoot.toolBar.findButton.fire())
    stage.getScene.getAccelerators.put(
      KeyCombination.valueOf("F3"),
      { () =>
        sceneRoot.toolBar.findButton.fire()
        sceneRoot.findNext.fire()
      }
    )
    stage.getScene.getAccelerators.put(
      KeyCombination.valueOf("Shift+F3"),
      { () =>
        sceneRoot.toolBar.findButton.fire()
        sceneRoot.findPrevious.fire()
      }
    )
    //need to prevet the findTextField from capturing these keys
    sceneRoot.findTextField.addEventHandler(
      KeyEvent.KEY_PRESSED,
      { (evt: KeyEvent) =>
        if (evt.getCode == KeyCode.F3) {
          if (evt.isShiftDown) sceneRoot.findPrevious.fire()
          else sceneRoot.findNext.fire()
        }
      }
    )

    println("System font size " + Font.getDefault.getSize)

    sceneRoot.alarmsTable.setItems(alarmsTableSortedList)
    alarmsTableSortedList.comparatorProperty `bind` sceneRoot.alarmsTable.comparatorProperty

    migrateAlarmsIfNeeded()
    loadAlarms().fold[Unit](
      ex => new Alert(Alert.AlertType.ERROR, "Failed loading alarms: " + ex).modify(_.setResizable(true)).show(),
      alarms.addAll(_*)
    )
    Platform.runLater(() =>
      alarms.asScala.filter(_.state == AlarmState.Showing) foreach showAlarm
    ) //run later to ensure stage initialization
  }

  /** ****************************** Definition of actions
    */
  sealed trait Action {
    def `do`(): Unit
    def undo(): Unit
  }
  case class NewAlarm(alarm: Alarm) extends Action {
    def `do`() = alarms.synchronized { alarms.add(AlarmState(alarm, AlarmState.Active, wallClock.instant)) }
    def undo() = alarms.synchronized { alarms.removeIf(_.alarm eq alarm) }
  }
  case class DeleteAlarm(alarm: AlarmState) extends Action {
    def `do`() = {
      alarms.synchronized { alarms.remove(alarm) }
      buryInGraveyard(alarm).failed.foreach(e =>
        new ExceptionDialog(new Exception("Failed writing alarm to the graveyard file. Drive issues?", e)).showAndWait()
      )
    }
    def undo() = alarms.synchronized { alarms.add(alarm) }
  }
  case class EditAlarm(old: AlarmState, updated: Alarm) extends Action {
    def `do`() = alarms.synchronized {
      val newAlarm = if (updated.when != old.alarm.when || updated.recurrence != old.alarm.recurrence) {
        AlarmState(updated, AlarmState.Active, wallClock.instant)
      } else {
        old.copy(alarm = updated)
      }
      alarms.set(alarms.indexOf(old), newAlarm)
    }
    def undo() = alarms.synchronized { alarms.set(alarms.asScala.indexWhere(_.alarm eq updated), old) }
  }
  case class ImportAlarms(importedAlarms: Seq[AlarmState]) extends Action {
    def `do`() = alarms.synchronized { alarms.addAll(importedAlarms*) }
    def undo() = alarms.synchronized { importedAlarms foreach (ia => alarms.removeIf(_.alarm == ia.alarm)) }
  }

  var undoQueue = List.empty[Action]
  var redoQueue = List.empty[Action]
  var actionsLog = List.empty[String]

  def `do`(a: Action): Unit = {
    actionsLog ::= s"${wallClock.instant} doing $a"
    undoQueue ::= a
    Try(a.`do`()).failed.foreach(e => actionsLog ::= e.getStackTrace.mkString("\n"))
    actionsLog = actionsLog.take(20)
  }
  def redo() = {
    val head :: tail = redoQueue: @unchecked
    redoQueue = tail
    `do`(head)
  }
  def undo(): Unit = {
    val elem :: rest = undoQueue: @unchecked
    actionsLog ::= s"${wallClock.instant} undoing $elem"
    undoQueue = rest
    redoQueue ::= elem
    Try(elem.undo()).failed.foreach(e => actionsLog ::= e.getStackTrace.mkString("\n"))
    actionsLog = actionsLog.take(20)
  }

  /** ************************************************** configuration for the state machine periodic task
    */

  private val showingAlarms = collection.mutable.Map[Alarm, Alert]()
  val wallClock = Clock.tickMinutes(ZoneId.systemDefault)
  val checkerThread = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(runnable =>
    new Thread(null, runnable, "Clock", 1024 * 100).modify(_.setDaemon(true))
  )
  checkerThread.scheduleAtFixedRate(
    () => {
      val now = wallClock.instant()
      alarms.synchronized {
        val toRunLater = collection.mutable.Buffer[Runnable]()
        val newStates = alarms.asScala
          .map { state =>
//          println("checking " + state)
            val checkResult = AlarmStateMachine.checkAlarm(now, state)
//          println("  ===> " + checkResult)
            checkResult match {
              case AlarmStateMachine.KeepState => state
              case AlarmStateMachine.NotifyAlarm(next) =>
                toRunLater += (() => showAlarm(next))
                next
              case AlarmStateMachine.NotifyReminder(next) =>
                toRunLater += (() =>
                  Utils
                    .newAlert(sceneRoot.getScene)(
                      "Reminder occurring in " + Duration.between(now, next.nextOccurrence),
                      next.alarm.message.get().fold(_.toString, identity),
                      next.alarm.foregroundColor,
                      next.alarm.backgroundColor,
                      next.alarm.font,
                      ButtonType.OK
                    )
                    .show()
                )
                next
              case AlarmStateMachine.AutoCloseAlarmNotification(next) =>
                toRunLater += (() => showingAlarms.remove(next.alarm) foreach (_.close()))
                AlarmStateMachine.advanceAlarm(next.copy(state = AlarmState.Active))
            }
          }
          .filter(_.state != AlarmState.Ended)
        if (toRunLater.nonEmpty) {
          //must ensure we update the alarms first, before triggering the dialogs, otherwise they may overlap if the javafx runtime takes
          //too long to display the dialog and this recurrent check happens again
          Platform.runLater { () =>
            val idx = sceneRoot.alarmsTable.getSelectionModel.getSelectedIndex
            alarms.setAll(newStates.toSeq*)
            sceneRoot.alarmsTable.getSelectionModel.select(idx)
          }
          toRunLater foreach Platform.runLater
        }
      }
    },
    0,
    5,
    scala.concurrent.duration.SECONDS
  )

  def showAlarm(state: AlarmState): Unit = {
    val now = wallClock.instant()
    val message = state.alarm.message.get()

    if (message.toOption.exists(_.isBlank()) && state.alarm.message.isInstanceOf[Alarm.ScriptOutputMessage]) return

    val editButton = new ButtonType("edit")
    val deferButton = new ButtonType("defer")
    val alert = Utils.newAlert(sceneRoot.getScene)(
      Utils.instantToUserString(state.nextOccurrence),
      message.fold(_.toString, identity),
      state.alarm.foregroundColor,
      state.alarm.backgroundColor,
      state.alarm.font,
      editButton,
      deferButton,
      ButtonType.OK
    )
    showingAlarms(state.alarm) = alert
    alert.showAndWait().ifPresent { btn =>
      showingAlarms.remove(state.alarm)
      btn match {
        case `deferButton` =>
          val newAtTime =
            new DeferToDialog().modify(_.getDialogPane.getScene.getStylesheets.addAll(sceneRoot.getScene.getStylesheets)).showAndWait()
          if (newAtTime.isPresent) {
            val next = newAtTime.get match {
              case Alarm.AtTime(localDate, localTime) =>
                state.copy(
                  nextOccurrence = ZonedDateTime.of(localDate, localTime.getOrElse(LocalTime.MIDNIGHT), ZoneId.systemDefault).toInstant,
                  state = AlarmState.Active
                )
              case Alarm.TimeFromNow(hours, minutes) =>
                state.copy(
                  nextOccurrence = state.nextOccurrence.plus(hours, ChronoUnit.HOURS).plus(minutes, ChronoUnit.MINUTES),
                  state = AlarmState.Active
                )
            }
            println("Deferring alarm to " + next)
            alarms.set(alarms.indexOf(state), next)

          } else {
            showAlarm(state) //trigger again showing this alarm
          }

        case `editButton` =>
          val dialog = new AlarmDialog(sceneRoot.getScene.getWindow, Some(state.alarm))
          var resAlarm: Option[Alarm] = None
          dialog.okButton.setOnAction { _ =>
            resAlarm = Some(dialog.getAlarm)
            dialog.close()
          }
          dialog.showAndWait()
          resAlarm.fold {
            showAlarm(state) //trigger again showing this alarm
          } { n =>
            `do`(EditAlarm(state, n))
          }

        case ButtonType.OK =>
          //must run this later, to ensure the alarms where properly updated
          Platform.runLater { () =>
            val futureInstances = Iterator
              .iterate(AlarmStateMachine.advanceAlarm(state.copy(state = AlarmState.Active)))(AlarmStateMachine.advanceAlarm)
              .filter(s => s.nextOccurrence.isAfter(now) || s.state == AlarmState.Ended)
            val next = futureInstances.next()
            println("Advancing alarm to " + next)
            if (next.state == AlarmState.Ended) {
              alarms.remove(state)
              buryInGraveyard(state).failed.foreach(e =>
                new ExceptionDialog(new Exception("Failed writing alarm to the graveyard file. Drive issues?", e)).showAndWait()
              )
            } else alarms.set(alarms.indexOf(state), next)
          }
      }
    }
  }

  /** ********************** MICS
    */
  implicit def codec: io.Codec = io.Codec.UTF8

  def storeAlarms(alarms: Seq[AlarmState]): Try[File] = writeWithPickler(AlarmPicklersV2, dataFolder / alarmsFile, alarms)
  def buryInGraveyard(alarm: AlarmState): Try[Unit] = Try {
    import AlarmPicklersV2.given
    (dataFolder / alarmsGraveyard).appendText(Json.toJson(alarm).pipe(Json.stringify) + "\n")
  }
  def loadAlarms(): Try[Seq[AlarmState]] = if ((dataFolder / alarmsFile).exists()) loadWithPickler(AlarmPicklersV2, dataFolder / alarmsFile)
    else Success(Seq.empty)

  def loadWithPickler(pickler: AlarmPicklers, file: File): Try[Seq[AlarmState]] = Try {
    import pickler.given
    Json.parse(file.contentAsString).as[Seq[AlarmState]]
  }
  def writeWithPickler(pickler: AlarmPicklers, file: File, alarms: Seq[AlarmState]): Try[File] = Try {
    import pickler.given
    val content = Json.toJson(alarms).pipe(Json.stringify)
    val backupFile = file.sibling(file.name + "~")
    backupFile.writeText(content).moveTo(file)(using CopyOptions(overwrite = true))
  }

  /** Migrates the alarms from v1 to v2 and onto the right location if they are found under home */
  def migrateAlarmsIfNeeded(): Unit = {
    if ((File.home / alarmsFile).exists && (dataFolder / alarmsFile).notExists) {
      println("Migrating alarms")
      loadWithPickler(AlarmPicklersV1, File.home / alarmsFile).flatMap { alarms =>
        storeAlarms(alarms)
      }.get
      println("Migrating graveyard")

      (File.home / alarmsGraveyard).lines.filterNot(_.isBlank()).map { line =>
        import AlarmPicklersV1.given  
        Json.parse(line).as[AlarmState]
      }.foreach { deadAlarm =>
        import AlarmPicklersV2.given  
        (dataFolder / alarmsGraveyard).appendLine(Json.stringify(Json.toJson(deadAlarm)))
      }
      println("done migrating")
    }
  }
}
