package starfruit.ui

import language.reflectiveCalls

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javafx.beans.binding.BooleanBinding
import javafx.beans.binding.ObjectBinding
import javafx.beans.property.SimpleObjectProperty
import javafx.geometry._
import javafx.scene.Node
import javafx.scene.control._
import javafx.scene.layout._
import javafx.scene.paint.Color
import javafx.stage.Popup
import scala.collection.JavaConverters._

class RecurrencePane extends VBox { $ =>
  setFillWidth(true)
  setSpacing(15)
  val recurrenceRule = new HBox { $ =>
    setFillHeight(true)
    setSpacing(20)
    VBox.setVgrow(this, Priority.ALWAYS)
    val selectedRecurrence = new SimpleObjectProperty[RecurrenceSubPane](NoRecurrence)
    private val recurrenceToggleGroup = new ToggleGroup()
    val noRecurrence = recurrenceToggleGroup \ new RadioButton("No Recurrence") { setOnAction(_ => selectedRecurrence set NoRecurrence) }
//    val atLogin = recurrenceToggleGroup \ new RadioButton("At Login") { setOnAction(_ => selectedRecurrence set AtLoginRecurrence) }
    val hourlyMinutely = recurrenceToggleGroup \ new RadioButton("Hourly/Minutely") {
      val pane = new HourlyMinutelyRecurrence
      setOnAction(_ => selectedRecurrence set pane)
    }
    val daily = recurrenceToggleGroup \ new RadioButton("Daily") {
      val pane = new DailyRecurrence
      setOnAction(_ => selectedRecurrence set pane)
    }
    val weekly = recurrenceToggleGroup \ new RadioButton("Weekly") {
      val pane = new WeeklyRecurrence
      setOnAction(_ => selectedRecurrence set pane)
    }
    val monthly = recurrenceToggleGroup \ new RadioButton("Monthly") {
      val pane = new MonthlyRecurrence
      setOnAction(_ => selectedRecurrence set pane)
    }
    val yearly = recurrenceToggleGroup \ new RadioButton("Yearly") {
      val pane = new YearlyRecurrence
      setOnAction(_ => selectedRecurrence set pane)
    }
    noRecurrence.setSelected(true)
    
    val subrecurrence = new SubRecurrenceDialog
    private val popup = new Popup()
    popup.getScene.setFill(Color.WHITE)
    popup.getContent.add(subrecurrence)
    popup.sizeToScene()
    popup.setAutoHide(true)
    val subRepetition = new Button("Sub-Repetition") {
      setOnAction { _ =>
        val target = this.localToScreen(0, 0)
        popup.show(this, target.getX, target.getY)
      }
    }
    
    val filler = new Region
    VBox.setVgrow(filler, Priority.ALWAYS)
    $ \ vbox(noRecurrence, hourlyMinutely, daily, weekly, monthly, yearly, filler, subRepetition)(spacing = 5)
    $ \ new Separator(Orientation.VERTICAL)
    $ \ NoRecurrence
    
    
    selectedRecurrence.addListener { (_, _, pane) =>
      val children = getChildren.asScala
      children -= children.last
      children += pane
    }
  }
  $ \ new TitledVBox("Recurrence Rule", spacing = 10) \ recurrenceRule
  
  val recurrenceEnd = new GridPane { $ =>
    setSpacing(10)
    setHgap(10)
    setVgap(10)
    val endToggleButton = new ToggleGroup()
    val noEnd = endToggleButton \ new RadioButton("No end")
    val endAfter = endToggleButton \ new RadioButton("End after:")
    val endBy = endToggleButton \ new RadioButton("End by:")
    noEnd.setSelected(true)
    
    val endAfterOccurrences = new Spinner[Int](1, Int.MaxValue, 1, 1) { setPrefWidth(100)}
    val endByDate = new DatePicker(LocalDate.now()) { getEditor.setPrefColumnCount(10) }
    val endByHourPicker = new HourPicker()
//    val endByAnyTime = new CheckBox("Any time")
    
    def addRow(row: Int, nodes: Region*) = {
      add(nodes.head, 0, row)
      nodes.drop(1) match {
        case Seq() =>
        case elems => add(hbox(elems:_*)(10, Pos.BASELINE_LEFT), 1, row)
      }
    }
    addRow(0, noEnd)
    addRow(1, endAfter, endAfterOccurrences, new Label("occurrence(s)"))
    addRow(2, endBy, endByDate, endByHourPicker/* , endByAnyTime */)
    
    endAfterOccurrences.getParent.disableProperty bind endAfter.selectedProperty.not
    endByDate.getParent.disableProperty bind endBy.selectedProperty.not
  }
  
  $ \ new TitledVBox("Recurrence End", spacing = 10) \ recurrenceEnd
  
  val exceptions = $ \ new BorderPane { $ =>
    setStyle("-fx-border-color: darkgray; -fx-border-width: 1px; -fx-border-radius: 4px")
    $ top new Label("Exceptions") {
      BorderPane.setMargin(this, new Insets(10,10, 0, 10))
      BorderPane.setAlignment(this, Pos.CENTER)
    }
    
    val exceptionsList = $ center new ListView[LocalDate] {
      BorderPane.setMargin(this, new Insets(10, 5, 10, 10))
      val dateTimeFormatter = DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.FULL)
      setCellFactory(list => new ListCell[LocalDate]() {
          override def updateItem(item: LocalDate, empty) = {
            super.updateItem(item, empty)
            if (item != null) setText(item.format(dateTimeFormatter))
            else setText("")
          }
        })
    }
    
    val exceptionDate = new DatePicker { getEditor.setPrefColumnCount(10) }
    val addButton = new Button("Add")
    val changeButton = new Button("Change")
    val deleteButton = new Button("Delete")
    val excludeHolidays = new CheckBox("Exclude holidays").modify(_.setVisible(false)) //unsupported
    val onlyDuringWorktime = new CheckBox("Only during worktime")
    
    private val leftBox = $ right new VBox { $ =>
      BorderPane.setMargin(this, new Insets(10, 10, 10, 5))
      setSpacing(15)
      $ \ exceptionDate
      $ \ hbox(addButton, changeButton, deleteButton)(spacing = 10)
      $ \ excludeHolidays
      $ \ onlyDuringWorktime
    }
    exceptionsList.prefWidthProperty.bind(leftBox.widthProperty)
    
    addButton.disableProperty bind exceptionDate.valueProperty.isNull
    deleteButton.disableProperty bind exceptionsList.getSelectionModel.selectedItemProperty.isNull
    changeButton.disableProperty bind new BooleanBinding {
      bind(exceptionsList.getSelectionModel.selectedItemProperty, exceptionDate.valueProperty)
      override def computeValue() = {
        (exceptionsList.getSelectionModel.getSelectedItem, exceptionDate.getValue) match {
          case (a, b) if a == null || b == null => true
          case _ => false
        }
      }
    }
    addButton.setOnAction { _ =>
      if (!exceptionsList.getItems.contains(exceptionDate.getValue))
        exceptionsList.getItems add exceptionDate.getValue
      exceptionDate.setValue(null)
    }
    deleteButton.setOnAction { _ =>
      exceptionsList.getItems.remove(exceptionsList.getSelectionModel.getSelectedItem)
    }
    changeButton.setOnAction { _ =>
      exceptionsList.getItems.set(exceptionsList.getSelectionModel.getSelectedIndex, exceptionDate.getValue)
      exceptionDate.setValue(null)
    }
  }
  
  //enablement dependency logic
  val isNoRecurrence = recurrenceRule.selectedRecurrence.map[java.lang.Boolean] { 
    case NoRecurrence => true
    case _ => false
  }
  recurrenceEnd.disableProperty bind isNoRecurrence
  recurrenceRule.subRepetition.disableProperty bind isNoRecurrence
  
  exceptions.disableProperty bind recurrenceRule.selectedRecurrence.map { 
    case NoRecurrence | AtLoginRecurrence => true
    case _ => false
  }
  
  val disableEndByHourMinute = recurrenceRule.selectedRecurrence.map[java.lang.Boolean] {
    case _: HourlyMinutelyRecurrence => false
    case _ => true
  }
  recurrenceEnd.endByHourPicker.disableProperty bind disableEndByHourMinute
}

sealed trait RecurrenceSubPane extends Node
case object NoRecurrence extends Region with RecurrenceSubPane
case object AtLoginRecurrence extends Region with RecurrenceSubPane
class HourlyMinutelyRecurrence extends HBox with RecurrenceSubPane { $ =>
  setAlignment(Pos.BASELINE_LEFT)
  setSpacing(10)
  $ \ new Label("Recur every")
  val hourPicker = $ \ new HourPicker(unboundHours = true)
  $ \ new Label("hour:minutes")
}
trait DaysSelection {
  object selectedDays extends GridPane { $ => 
    $.setHgap(10)
    $.setVgap(10)
    add(new Label("On:"), 0, 0)
    val monday = $(1, 0) = new CheckBox("Monday").modify(_.setSelected(true))
    val tuesday = $(1, 1) = new CheckBox("Tuesday").modify(_.setSelected(true))
    val wednesday = $(1, 2) = new CheckBox("Wednesday").modify(_.setSelected(true))
    val thursday = $(1, 3) = new CheckBox("Thursday").modify(_.setSelected(true))
    val friday = $(2, 0) = new CheckBox("Friday").modify(_.setSelected(true))
    val saturday = $(2, 1) = new CheckBox("Saturday").modify(_.setSelected(true))
    val sunday = $(2, 2) = new CheckBox("Sunday").modify(_.setSelected(true))
  }
}
class DailyRecurrence extends BorderPane with RecurrenceSubPane with DaysSelection { $ =>
  val days = new Spinner[Int](1, Int.MaxValue, 1, 1) { getEditor.setPrefColumnCount(4) }
  $ top new HBox(new Label("Recur every"), days, new Label("day(s)")) { setSpacing(15); setAlignment(Pos.BASELINE_LEFT) }
  
  $ center selectedDays
}
class WeeklyRecurrence extends BorderPane with RecurrenceSubPane with DaysSelection { $ =>
  val weeks = new Spinner[Int](1, Int.MaxValue, 1, 1) { getEditor.setPrefColumnCount(4) }
  $ top hbox(new Label("Recur every"), weeks, new Label("week(s)"))(spacing = 15, alignment = Pos.BASELINE_LEFT)
  
  $ center selectedDays
  
  import selectedDays._
  val today = java.time.LocalDate.now()
  Seq(monday, tuesday, wednesday, thursday, friday, saturday, sunday) filterNot (d => today.getDayOfWeek.toString equalsIgnoreCase d.getText) foreach (_.setSelected(false))
}
trait MonthSelection {
  val mode = new ToggleGroup()
  val onDayMode = mode \ new RadioButton("On day")
  val onDay = combobox((1 to 31).map(_.toString) :+ "Last" :_*)
  val onTheDayMode = mode \ new RadioButton("On the")
  val onTheDayNumber = combobox("1st", "2nd", "3rd", "4th", "5th", "Last", "2nd Last", "3rd Last", "4th Last", "5th Last")
  val onTheDayDay = combobox("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
  
  protected val monthsPane = new GridPane { $ =>
    $.setHgap(10)
    $.setVgap(10)
    $(0, 0) = onDayMode
    $(1, 0) = onDay
    $(0, 1) = onTheDayMode
    $(1, 1) = onTheDayNumber
    $(2, 1) = onTheDayDay
  }
  
  onDay.disableProperty bind onDayMode.selectedProperty.not
  val isNotOnTheDayMode = onTheDayMode.selectedProperty.not
  onTheDayNumber.disableProperty bind isNotOnTheDayMode
  onTheDayDay.disableProperty bind isNotOnTheDayMode
}
class MonthlyRecurrence extends BorderPane with RecurrenceSubPane with MonthSelection { $ =>
  val months = new Spinner[Int](1, Int.MaxValue, 1, 1) { getEditor.setPrefColumnCount(4) }
  $ top hbox(new Label("Recur every"), months, new Label("month(s)"))(spacing = 15, alignment = Pos.BASELINE_LEFT)
  
  $ center monthsPane
}
class YearlyRecurrence extends VBox with RecurrenceSubPane with MonthSelection { $ =>
  setSpacing(15)
  val years = new Spinner[Int](1, Int.MaxValue, 1, 1) { getEditor.setPrefColumnCount(4) }
  def mkrow(nodes: Node*) = $ \ hbox(nodes:_*)(spacing = 15, alignment = Pos.BASELINE_LEFT)
  mkrow(new Label("Recur every"), years, new Label("year(s)"))
  
  $ \ monthsPane
  
  val months = Seq("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec").map(new CheckBox(_))
  months.zipWithIndex foreach {
    case (cb, idx) => 
      val month = java.time.Month.of(idx + 1)
      month.maxLength
      cb.disableProperty bind new BooleanBinding {
        bind(onDayMode.selectedProperty, onDay.getSelectionModel.selectedIndexProperty)
        override def computeValue = !(onDayMode.isSelected && onDay.getSelectionModel.getSelectedIndex < month.maxLength)
      }
  }
  
  $ \ new GridPane { $ =>
    setHgap(10)
    setVgap(10)
    $(0, 0) = new Label("Months:") 
    months.zipWithIndex foreach { case (month, idx) => $(1 + idx % 4, idx / 4) = month }
  }
  
  val onFeb29 = combobox("None", "1 Mar", "28 Feb")
  mkrow(new Label("February 29th alarm in non-leap years:"), onFeb29)
  
  onFeb29.getParent.disableProperty bind new ObjectBinding[java.lang.Boolean] {
    bind(onDayMode.selectedProperty, onDay.valueProperty, months(1).selectedProperty)
    override def computeValue = !(onDayMode.isSelected && onDay.getValue == "29" && months(1).isSelected)
  }
}

class SubRecurrenceDialog extends BorderPane { $ =>
  setPadding(new Insets(10))
  val repeatEnabled = new CheckBox("Repeat every")
  val repeatEvery = new DurationPicker(DurationPicker.Minutes, DurationPicker.HoursMinutes, DurationPicker.Days)
  $ top hbox(repeatEnabled, repeatEvery)(spacing = 10)
  
  val mode = new ToggleGroup()
  val repetitionsMode = mode \ new RadioButton("Number of repetitions:")
  val repetitions = new Spinner[Int](1, Int.MaxValue, 1, 1) { getEditor.setPrefColumnCount(4) }
  val durationMode = mode \ new RadioButton("Duration")
  val duration = new DurationPicker(DurationPicker.Minutes, DurationPicker.HoursMinutes, DurationPicker.Days)
  
  $ center new VBox { $ =>
    BorderPane.setMargin(this, new Insets(10, 0, 0, 0))
    setSpacing(30)
    setStyle("-fx-border-color: darkgray; -fx-border-width: 1px; -fx-border-radius: 4px")
    $ \ hbox(repetitionsMode, repetitions)(spacing = 10)
    $ \ hbox(durationMode, duration)(spacing = 10)
  }
  
  repeatEvery.disableProperty.bind(repeatEnabled.selectedProperty.not)
  getCenter.disableProperty.bind(repeatEnabled.selectedProperty.not)
}

class RecurrencePaneTest extends BaseApplication {
  def sceneRoot = new RecurrencePane
}