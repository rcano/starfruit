package starfruit
package ui

import java.time.LocalDate
import javafx.beans.binding.BooleanBinding
import javafx.scene.control._

class DeferToDialog extends Dialog[Alarm.Time] {
  setHeaderText("Defer to")
  private val dialogPane = getDialogPane
  
  private val deferButton = new ButtonType("Defer", ButtonBar.ButtonData.OK_DONE)
  dialogPane.getButtonTypes.addAll(deferButton, ButtonType.CANCEL)
  
  private val mode = new ToggleGroup()
    
  private val atDateTime = mode \ new RadioButton("At date/time:")
  private val atDate = new DatePicker(LocalDate.now())
  private val atTime = new HourPicker()
  private val atAnyTime = new CheckBox("Any time")
  atDateTime.setSelected(true)
    
  private val timeFromNow = mode \ new RadioButton("Time from now:")
  private val timeFromNowTime = new HourPicker(unboundHours = true)
    
  Seq(atDate, atTime, atAnyTime) foreach (_.disableProperty bind atDateTime.selectedProperty.not)
  atTime.disableProperty bind new BooleanBinding {
    bind(atDateTime.selectedProperty, atAnyTime.selectedProperty)
    def computeValue = !atDateTime.isSelected || atAnyTime.isSelected
  }
  timeFromNowTime.disableProperty bind timeFromNow.selectedProperty.not
    
  val timePane = gridPane(Seq(atDateTime, atDate, atTime, atAnyTime),
                          Seq(timeFromNow, timeFromNowTime))
  dialogPane.setContent(timePane)
  
  setResultConverter {
    case `deferButton` => 
      if (atDateTime.isSelected) Alarm.AtTime(atDate.getValue, if (atAnyTime.isSelected) None else Some(atTime.getTime))
      else Alarm.TimeFromNow(timeFromNowTime.hours.getValue, timeFromNowTime.minutes.getValue)
    case _ => null
  }
}
