package starfruit.ui

import javafx.geometry._
import java.time.Duration
import java.time.LocalTime
import javafx.beans.binding._
import javafx.scene.Node
import javafx.scene.control._
import javafx.scene.layout._

class TitledVBox(val title: Labeled, spacing: Int = 0, alignment: Pos = Pos.CENTER_LEFT) extends VBox {
  def this(title: String) = this(new Label(title))
  def this(title: String, spacing: Int) = this(new Label(title), spacing)
  def this(title: String, spacing: Int, alignment: Pos) = this(new Label(title), spacing, alignment)
  setSpacing(spacing)
  setAlignment(alignment)
  setFillWidth(true)
  setPadding(new Insets(10))
  setStyle("-fx-border-color: darkgray; -fx-border-width: 1px; -fx-border-radius: 4px")
  this \ title.modify(_.setMaxWidth(Int.MaxValue), _.setAlignment(Pos.CENTER))
}

class HourPicker(initial: LocalTime = LocalTime.MIDNIGHT, unboundHours: Boolean = false) extends HBox { $ =>
  setAlignment(Pos.BASELINE_CENTER)
  val hours = $ \ new Spinner[Int](0, if (unboundHours) Int.MaxValue else 23, initial.getHour, 1) { getEditor.setPrefColumnCount(3); getStyleClass.add(Spinner.STYLE_CLASS_ARROWS_ON_LEFT_VERTICAL) }
  val minutes = $ \ new Spinner[Int](0, 59, initial.getMinute, 1) { getEditor.setPrefColumnCount(3) }
  
  def getTime = LocalTime.of(hours.getValue, minutes.getValue)
  def setTime(t: LocalTime): Unit = { hours.getValueFactory.setValue(t.getHour); minutes.getValueFactory.setValue(t.getMinute) }
}

class DurationPicker(val modes: DurationPicker.Type*) extends BorderPane { $ =>
  val mode = $ right combobox(modes:_*)
  val valueSpinner = new Spinner[Int](1, Int.MaxValue, 1, 1) { getEditor.setPrefColumnCount(7) }
  val hourPicker = new HourPicker()
  
  mode.getSelectionModel.selectedItemProperty.addListener((_, _, prop) => prop match {
      case DurationPicker.HoursMinutes => $ left hourPicker
      case _ => $ left valueSpinner
    })
  mode.getSelectionModel.selectLast()
  mode.getSelectionModel.selectFirst()
  
  def getDuration: Duration = mode.getSelectionModel.getSelectedItem match {
    case DurationPicker.Minutes => Duration ofMinutes valueSpinner.getValue
    case DurationPicker.HoursMinutes => Duration.ofMinutes(hourPicker.minutes.getValue + hourPicker.hours.getValue * 60)
    case DurationPicker.Days => Duration ofDays valueSpinner.getValue
    case DurationPicker.Weeks => Duration ofDays valueSpinner.getValue * 7
  }
  def setDuration(d: Duration): Unit = {
    d.toDays match {
      case days if days >= 7 && days % 7 == 0 => // weeks mode
        mode.getSelectionModel.select(DurationPicker.Weeks)
        valueSpinner.getValueFactory.setValue(days.toInt / 7)
      case days if days > 0 && d.toHours % 24 == 0 =>
        mode.getSelectionModel.select(DurationPicker.Days)
        valueSpinner.getValueFactory.setValue(days.toInt)
      case _ =>
        d.toMinutes match {
          case min if min >= 60 =>
            mode.getSelectionModel.select(DurationPicker.HoursMinutes)
            hourPicker.hours.getValueFactory.setValue(d.toHours.toInt)
            hourPicker.minutes.getValueFactory.setValue(d.toMinutes.toInt % 60)
          case justMins =>
            mode.getSelectionModel.select(DurationPicker.Minutes)
            valueSpinner.getValueFactory.setValue(justMins.toInt)
        }
    }
  }
}
object DurationPicker {
  sealed abstract class Type(name: String) { override def toString = name }
  case object Minutes extends Type("minutes") 
  case object HoursMinutes extends Type("hours/minutes")
  case object Days extends Type("days")
  case object Weeks extends Type("weeks")
  val All = Seq(Minutes, HoursMinutes, Days, Weeks)
}