package starfruit.ui

import language.reflectiveCalls

import better.files._
import javafx.application.Platform
import javafx.geometry._
import javafx.scene.Scene
import javafx.scene.control._
import javafx.scene.layout._
import javafx.scene.paint.Color
import javafx.scene.text._
import javafx.stage._
import java.time.{DayOfWeek, Instant, LocalDateTime, Month}
import starfruit.Alarm
import scala.collection.JavaConverters._

class AlarmDialog(parent: Window, initial: Option[Alarm]) extends Stage() {
  initOwner(parent)
  initModality(Modality.WINDOW_MODAL)
  
  val alarmPane = new AlarmPane()
  val recurrencePane = new RecurrencePane()
  
  val extraButton = new ToggleButton("More options >>")
  extraButton.selectedProperty.addListener((_, _, selected) => extraButton.setText(if (selected) "Less Options <<" else "More Options >>" ))
  alarmPane.extra.visibleProperty bind extraButton.selectedProperty
  recurrencePane.exceptions.visibleProperty bind extraButton.selectedProperty
  
  val tryButton = new Button("Try")
  val okButton = new Button("✓ Ok")
  okButton.setDefaultButton(true)
  val cancelButton = new Button("🚫 Cancel")
  cancelButton.setCancelButton(true)
  cancelButton.setOnAction(_ => close())
  
  tryButton.setOnAction { _ =>
    Utils.newAlert(parent.getScene)(getMessage.get().fold(_.toString, identity),
                                    alarmPane.action.fontSelectorDialog.foregroundColor.getValue.colorToWeb,
                                    alarmPane.action.fontSelectorDialog.backgroundColor.getValue.colorToWeb,
                                    getFont,
                                    ButtonType.OK).show()
  }
  
  setScene(new Scene(
      new BorderPane { $ =>
        setPadding(new Insets(15))
        val tabbedPane = $ center new TabPane()
        tabbedPane.getTabs.add(new Tab("Alarm", alarmPane).modify(_.setClosable(false)))
        tabbedPane.getTabs.add(new Tab("Recurrence", recurrencePane).modify(_.setClosable(false)))
        
        $ bottom new BorderPane { $ =>
          BorderPane.setMargin(this, new Insets(10, 0, 0, 0))
          $ left extraButton
          $ right hbox(tryButton, okButton, cancelButton)(10, alignment = Pos.CENTER_RIGHT)
        }
      }).modify(_.getStylesheets.addAll(parent.getScene.getStylesheets)))
  
  
  setOnShowing(_ => Platform.runLater { () => alarmPane.action.selectedMode.getValue match { //must schedule for later, otherwise we receive the event, request focus, and then event processor continues and ends up assigning focus to the tab
        case m: alarmPane.action.alarmMode.type => m.message.requestFocus()
        case m: alarmPane.action.fileContentsMode.type => m.path.requestFocus()
        case m: alarmPane.action.commandOutputMode.type => m.script.requestFocus()
      }})
  
  initial foreach { initial =>
    initial.message match {
      case m: Alarm.TextMessage =>
        alarmPane.action.actionType.getSelectionModel.select("Text message")
        alarmPane.action.alarmMode.message.setText(m.message)
      case m: Alarm.FileContentsMessage =>
        alarmPane.action.actionType.getSelectionModel.select("File Contents")
        alarmPane.action.fileContentsMode.path.setText(m.path.toString)
      case m: Alarm.ScriptOutputMessage =>
        alarmPane.action.actionType.getSelectionModel.select("Command output")
        alarmPane.action.commandOutputMode.script.setText(m.script)
    }
    val font = {
      val Array(style, size, font) = initial.font.split(" ", 3)
      val weight = Option(FontWeight.findByName(style))
      val posture = Option(FontPosture.findByName(style))
      weight map (Font.font(font.drop(1).dropRight(1), _, size.toFloat)) orElse (posture map (Font.font(font.drop(1).dropRight(1), _, size.toFloat))) getOrElse
        Font.font(font.drop(1).dropRight(1), size.toFloat)
    }
    alarmPane.action.fontSelectorDialog.fontPane.setFont(font)
    alarmPane.action.alarmMode.message.setFont(font)
    alarmPane.action.fontSelectorDialog.foregroundColor.setValue(Color.web(initial.foregroundColor))
    alarmPane.action.fontSelectorDialog.backgroundColor.setValue(Color.web(initial.backgroundColor))
    initial.sound match {
      case Alarm.NoSound => alarmPane.action.sound.getSelectionModel.select("None")
      case Alarm.Beep => alarmPane.action.sound.getSelectionModel.select("Beep")
      case s: Alarm.SoundFile => 
        alarmPane.action.sound.getSelectionModel.select("Sound file")
        alarmPane.action.soundFile.path.setText(s.file)
        alarmPane.action.soundFile.repeat.setSelected(s.repeat)
        alarmPane.action.soundFile.pauseBetweenRepetitions.getValueFactory.setValue(s.pauseBetweenRepetitions)
        alarmPane.action.soundFile.volume.setValue(s.volume)
    }
    initial.specialAction foreach { sp =>
      alarmPane.action.specialActionsPane.preAlarmCommand.setText(sp.preAlarmCommand)
      alarmPane.action.specialActionsPane.postAlarmCommand.setText(sp.postAlarmCommand)
      alarmPane.action.specialActionsPane.executeForDeferredAlarms.setSelected(sp.executeOnDeferred)
      alarmPane.action.specialActionsPane.cancelAlarmOnError.setSelected(sp.cancelAlarmOnError)
      alarmPane.action.specialActionsPane.dontNotifyError.setSelected(sp.doNotNotifyErrors)
    }
    initial.when match {
      case w: Alarm.AtTime =>
        alarmPane.time.atDateTime.setSelected(true)
        alarmPane.time.atDate.setValue(w.date)
        w.time foreach (alarmPane.time.atTime.setTime)
      case w: Alarm.TimeFromNow =>
        alarmPane.time.timeFromNow.setSelected(true)
        alarmPane.time.timeFromNowTime.hours.getValueFactory.setValue(w.hours)
        alarmPane.time.timeFromNowTime.minutes.getValueFactory.setValue(w.minutes)
    }
    setRecurrence(initial.recurrence)
    initial.subrepetition foreach { subrep =>
      recurrencePane.recurrenceRule.subrecurrence.repeatEnabled.setSelected(true)
      recurrencePane.recurrenceRule.subrecurrence.repeatEvery.setDuration(subrep.every)
      subrep.endAfter.fold({ left =>
          recurrencePane.recurrenceRule.subrecurrence.repetitions.getValueFactory.setValue(left)
        }, { right =>
          recurrencePane.recurrenceRule.subrecurrence.duration.setDuration(right)
        })
      initial.end.fold {
        recurrencePane.recurrenceEnd.noEnd.setSelected(true)
      } { 
        case Left(end) => recurrencePane.recurrenceEnd.endAfterOccurrences.getValueFactory.setValue(end)
        case Right(end) => 
          recurrencePane.recurrenceEnd.endByDate.setValue(end.toLocalDate)
          recurrencePane.recurrenceEnd.endByHourPicker.setTime(end.toLocalTime)
      }
      initial.exceptionOnDates foreach recurrencePane.exceptions.exceptionsList.getItems.add
      recurrencePane.exceptions.onlyDuringWorktime.setSelected(initial.exceptionOnWorkingTime)
      initial.reminder foreach { reminder =>
        alarmPane.extra.reminder.setSelected(true)
        alarmPane.extra.reminderDurationPicker.setDuration(reminder.duration)
        alarmPane.extra.reminderType.getSelectionModel.select(if (reminder.before) 0 else 1)
        alarmPane.extra.reminderForFirstRecurrenceOnly.setSelected(reminder.forFirstOccurrenceOnly)
      }
      initial.cancelIfLate foreach { cancel =>
        alarmPane.extra.cancelIfLate.setSelected(true)
        alarmPane.extra.cancelDurationPicker.setDuration(cancel.duration)
        alarmPane.extra.cancelAutoClose.setSelected(cancel.autoCloseWindowAfterThisTime)
      }
    }
  }
  private def setRecurrence(rec: Alarm.Recurrence) = {
    def setDays(days: java.util.EnumSet[DayOfWeek], daysSelection: DaysSelection): Unit = {
      daysSelection.selectedDays.monday.setSelected(days.contains(DayOfWeek.MONDAY))
      daysSelection.selectedDays.tuesday.setSelected(days.contains(DayOfWeek.TUESDAY))
      daysSelection.selectedDays.wednesday.setSelected(days.contains(DayOfWeek.WEDNESDAY))
      daysSelection.selectedDays.thursday.setSelected(days.contains(DayOfWeek.THURSDAY))
      daysSelection.selectedDays.friday.setSelected(days.contains(DayOfWeek.FRIDAY))
      daysSelection.selectedDays.saturday.setSelected(days.contains(DayOfWeek.SATURDAY))
      daysSelection.selectedDays.sunday.setSelected(days.contains(DayOfWeek.SUNDAY))
    }
    def setDayOfMonth(dof: Alarm.DayOfMonth, ms: MonthSelection): Unit = dof match {
      case Alarm.NthDayOfMonth(day) =>
        ms.onDayMode.setSelected(true)
        ms.onDay.getSelectionModel.select(if (day == -1) "Last" else day.toString)
      case Alarm.NthWeekDayOfMonth(day, dayOfWeek) =>
        ms.onTheDayNumber.getSelectionModel.select(day match {
            case -5 => "5th Last"
            case -4 => "4th Last"
            case -3 => "3rd Last"
            case -2 => "2nd Last"
            case -1 => "Last"
            case 1 => "1st"
            case 2 => "2nd"
            case 3 => "3rd"
            case 4 => "4th"
            case 5 => "5th"
          })
        ms.onTheDayDay.getItems.asScala.find(dayOfWeek.toString.equalsIgnoreCase) foreach (ms.onTheDayDay.getSelectionModel.select)
    }
    rec match {
      case Alarm.NoRecurrence => recurrencePane.recurrenceRule.noRecurrence.setSelected(true)
      case rec: Alarm.HourMinutelyRecurrence => 
        recurrencePane.recurrenceRule.hourlyMinutely.setSelected(true)
        recurrencePane.recurrenceRule.hourlyMinutely.pane.hourPicker.modify(_.hours.getValueFactory.setValue(rec.hours), _.minutes.getValueFactory.setValue(rec.minutes))
      case rec: Alarm.DailyRecurrence =>
        recurrencePane.recurrenceRule.daily.setSelected(true)
        recurrencePane.recurrenceRule.daily.pane.modify(_.days.getValueFactory.setValue(rec.every), setDays(rec.onDays, _))
      case rec: Alarm.WeeklyRecurrence =>
        recurrencePane.recurrenceRule.weekly.setSelected(true)
        recurrencePane.recurrenceRule.weekly.pane.modify(_.weeks.getValueFactory.setValue(rec.every), setDays(rec.onDays, _))
      case rec: Alarm.MonthlyRecurrence =>
        recurrencePane.recurrenceRule.monthly.setSelected(true)
        recurrencePane.recurrenceRule.monthly.pane.modify(_.months.getValueFactory.setValue(rec.every), setDayOfMonth(rec.on, _))
      case rec: Alarm.YearlyRecurrence =>
        recurrencePane.recurrenceRule.yearly.setSelected(true)
        recurrencePane.recurrenceRule.yearly.pane.modify(
          _.years.getValueFactory.setValue(rec.every),
          setDayOfMonth(rec.dayOfMonth, _),
          _.months.zipWithIndex.filter(e => rec.onMonths.contains(Month.of(e._2))).foreach(_._1.setSelected(true)),
          _.onFeb29.getSelectionModel.select(rec.onFebruary29NonLeapAction.fold("None") {
              case Alarm.February29NonLeapAction.MovePrevDay => "28 Feb"
              case Alarm.February29NonLeapAction.MoveNextDay => "1 Mar"
            }))
    }
  }
  
  private def getMessage = alarmPane.action.selectedMode.get match {
    case m: alarmPane.action.alarmMode.type => Alarm.TextMessage(m.message.getText)
    case m: alarmPane.action.fileContentsMode.type => Alarm.FileContentsMessage(m.path.getText.toFile)
    case m: alarmPane.action.commandOutputMode.type => Alarm.ScriptOutputMessage(m.script.getText)
  }
  private def getFont = {
    val styles = alarmPane.action.fontSelectorDialog.fontPane.getFont.getStyle.split(" ").map(_.toUpperCase)
    val invalidWeights = Seq(FontWeight.LIGHT, FontWeight.EXTRA_BOLD, FontWeight.EXTRA_LIGHT, FontWeight.MEDIUM, FontWeight.SEMI_BOLD)
    val weight = styles.map(FontWeight.findByName).filter(w => w != null && !invalidWeights.contains(w)).headOption.map(_.toString) //I have to remove invalid weights that CSS don't support
    val style = styles.collectFirst { case "REGULAR" => "normal"; case i@"ITALIC" => "italic" }
    
    (weight.orElse(style).getOrElse("normal") + " " + alarmPane.action.fontSelectorDialog.fontPane.getFont.getSize + " \"" + alarmPane.action.fontSelectorDialog.fontPane.getFont.getFamily + "\"").toLowerCase
  }
  def getAlarm: Alarm = {
    Alarm(
      message = getMessage,
      font = getFont,
      foregroundColor = alarmPane.action.fontSelectorDialog.foregroundColor.getValue.colorToWeb,
      backgroundColor = alarmPane.action.fontSelectorDialog.backgroundColor.getValue.colorToWeb,
      sound = alarmPane.action.selectedSound.get match {
        case _: alarmPane.action.noSound.type => Alarm.NoSound
        case _: alarmPane.action.beep.type => Alarm.Beep
        case s: alarmPane.action.soundFile.type => Alarm.SoundFile(s.path.getText, s.repeat.isSelected, s.pauseBetweenRepetitions.getValue, s.volume.getValue.toFloat)
      },
      specialAction = (alarmPane.action.specialActionsPane.preAlarmCommand.getText, alarmPane.action.specialActionsPane.postAlarmCommand.getText) match {
        case (pre, post) if pre.nonEmpty || post.nonEmpty => Some(
            Alarm.SpecialAction(pre, post, alarmPane.action.specialActionsPane.executeForDeferredAlarms.isSelected,
                                alarmPane.action.specialActionsPane.cancelAlarmOnError.isSelected,
                                alarmPane.action.specialActionsPane.dontNotifyError.isSelected))
        case _ => None
      },
      when = if (alarmPane.time.atDateTime.isSelected) Alarm.AtTime(alarmPane.time.atDate.getValue, if (alarmPane.time.atAnyTime.isSelected) None else Some(alarmPane.time.atTime.getTime))
      else Alarm.TimeFromNow(alarmPane.time.timeFromNowTime.hours.getValue, alarmPane.time.timeFromNowTime.minutes.getValue),
      recurrence = getRecurrence,
      subrepetition = if (recurrencePane.recurrenceRule.subrecurrence.repeatEnabled.isSelected) {
        Some(Alarm.Repetition(
            recurrencePane.recurrenceRule.subrecurrence.repeatEvery.getDuration,
            if (recurrencePane.recurrenceRule.subrecurrence.repetitionsMode.isSelected) Left(recurrencePane.recurrenceRule.subrecurrence.repetitions.getValue)
            else Right(recurrencePane.recurrenceRule.subrecurrence.duration.getDuration)))
      } else None,
      end = recurrencePane.recurrenceEnd.endToggleButton.getSelectedToggle match {
        case recurrencePane.recurrenceEnd.endAfter => 
          Some(Left(recurrencePane.recurrenceEnd.endAfterOccurrences.getValue))
        case recurrencePane.recurrenceEnd.endBy =>
          Some(Right(LocalDateTime.of(recurrencePane.recurrenceEnd.endByDate.getValue, recurrencePane.recurrenceEnd.endByHourPicker.getTime)))
        case _ => None
      },
      exceptionOnDates = recurrencePane.exceptions.exceptionsList.getItems.asScala,
      exceptionOnWorkingTime = recurrencePane.exceptions.onlyDuringWorktime.isSelected,
      reminder = alarmPane.extra.reminder.isSelected match {
        case true => Some(Alarm.Reminder(alarmPane.extra.reminderDurationPicker.getDuration,
                                         alarmPane.extra.reminderType.getSelectionModel.getSelectedIndex == 0,
                                         alarmPane.extra.reminderForFirstRecurrenceOnly.isSelected))
        case false => None
      },
      cancelIfLate = alarmPane.extra.cancelIfLate.isSelected match {
        case true => Some(Alarm.CancelIfLateBy(alarmPane.extra.cancelDurationPicker.getDuration,
                                               alarmPane.extra.cancelAutoClose.isSelected))
        case false => None
      }
    )
  }
  
  def getRecurrence: Alarm.Recurrence = {
    def days(daysSelection: DaysSelection) = {
      val res = java.util.EnumSet.noneOf(classOf[DayOfWeek])
      if (daysSelection.selectedDays.monday.isSelected) res.add(DayOfWeek.MONDAY)
      if (daysSelection.selectedDays.tuesday.isSelected) res.add(DayOfWeek.TUESDAY)
      if (daysSelection.selectedDays.wednesday.isSelected) res.add(DayOfWeek.WEDNESDAY)
      if (daysSelection.selectedDays.thursday.isSelected) res.add(DayOfWeek.THURSDAY)
      if (daysSelection.selectedDays.friday.isSelected) res.add(DayOfWeek.FRIDAY)
      if (daysSelection.selectedDays.saturday.isSelected) res.add(DayOfWeek.SATURDAY)
      if (daysSelection.selectedDays.sunday.isSelected) res.add(DayOfWeek.SUNDAY)
      res
    }
    def dayOfMonth(ms: MonthSelection) = {
      if (ms.onDayMode.isSelected) {
        Alarm.NthDayOfMonth(ms.onDay.getValue match {
            case "Last" => -1
            case d => d.toInt
          })
      } else {
        Alarm.NthWeekDayOfMonth(ms.onTheDayNumber.getValue match {
            case "5th Last" => -5
            case "4th Last" => -4
            case "3rd Last" => -3
            case "2nd Last" => -2
            case "Last" => -1
            case "1st" => 1
            case "2nd" => 2
            case "3rd" => 3
            case "4th" => 4
            case "5th" => 5
          }, DayOfWeek.valueOf(ms.onTheDayDay.getValue.toUpperCase))
      }
    }
    recurrencePane.recurrenceRule.selectedRecurrence.getValue match {
      case NoRecurrence => Alarm.NoRecurrence
      case AtLoginRecurrence => ???
      case r: HourlyMinutelyRecurrence => Alarm.HourMinutelyRecurrence(r.hourPicker.hours.getValue, r.hourPicker.minutes.getValue)
      case r: DailyRecurrence => Alarm.DailyRecurrence(r.days.getValue, days(r))
      case r: WeeklyRecurrence => Alarm.WeeklyRecurrence(r.weeks.getValue, days(r))
      case r: MonthlyRecurrence => Alarm.MonthlyRecurrence(r.months.getValue, dayOfMonth(r))
      case r: YearlyRecurrence => 
        val selectedMonths = r.months.zipWithIndex.filter(e => e._1.isSelected && !e._1.isDisabled).map(e => Month.of(e._2 + 1))
        val onMonths = java.util.EnumSet.noneOf(classOf[Month])
        selectedMonths foreach onMonths.add
        Alarm.YearlyRecurrence(r.years.getValue, dayOfMonth(r), onMonths, r.onFeb29.getValue match {
            case "None" => None
            case "28 Feb" => Some(Alarm.February29NonLeapAction.MovePrevDay)
            case "1 Mar" => Some(Alarm.February29NonLeapAction.MoveNextDay)
          })
    }
  }
}
