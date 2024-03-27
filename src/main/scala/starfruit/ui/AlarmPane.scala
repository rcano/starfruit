package starfruit.ui

import better.files._
import java.time.LocalDate
import javafx.beans.binding.BooleanBinding
import javafx.beans.property.Property
import javafx.beans.property.SimpleObjectProperty
import javafx.css.CssMetaData
import javafx.geometry._
import javafx.scene.control._
import javafx.scene.layout._
import javafx.scene.media.AudioClip
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.stage.FileChooser
import javafx.stage.Popup
import org.controlsfx.dialog.FontSelectorDialog
import tangerine._

import scala.jdk.CollectionConverters.*
import scala.util.chaining.*

class AlarmPane extends VBox { $ =>
  setSpacing(15)
  object action extends BorderPane { $ =>
    setPadding(new Insets(20))
    setStyle("-fx-border-color: darkgray; -fx-border-width: 1px; -fx-border-radius: 4px")
    $ `top` new Label("Action").tap { l =>
      BorderPane.setMargin(l, Margin(bot = 10))
      BorderPane.setAlignment(l, Pos.CENTER)
    }

    
    sealed trait AlarmType extends Pane
    object alarmMode extends StackPane with AlarmType { $ =>
      val message = $ \ new TextArea().modify(_.setPrefRowCount(4), _.setMinHeight(Region.USE_PREF_SIZE))
    }
    
    object fileContentsMode extends HBox with AlarmType { $ =>
      val path = $ \ new TextField().modify(HBox.setHgrow(_, Priority.ALWAYS))
      val browseButton = $ \ new Button("browse").modify(_.prefHeightProperty.bind(path.heightProperty))
      
      browseButton.setOnAction { _ =>
        val chooser = new FileChooser().modify(_.setTitle("Select file"))
        if (path.getText != null && path.getText.nonEmpty) chooser.setInitialFileName(path.getText)
        Option(chooser.showOpenDialog(getScene.getWindow)) foreach (f => path.setText(f.getAbsolutePath))
      }
    }
    object commandOutputMode extends VBox with AlarmType { $ =>
      setSpacing(10)
      $ \ new Label("Script")
      val script = $ \ new TextArea().modify(_.setPrefRowCount(4), _.setMinHeight(Region.USE_PREF_SIZE))
      VBox.setVgrow(script, Priority.ALWAYS)
    }
    val selectedMode = new SimpleObjectProperty[AlarmType](alarmMode)
    val actionType = combobox("Text message", "File Contents", "Command output")
    selectedMode `bind` actionType.getSelectionModel.selectedItemProperty.map {
      case "Text message" => alarmMode
      case "File Contents" => fileContentsMode
      case "Command output" => commandOutputMode
    }
    
    $ `center` new BorderPane { $ =>
      $ `top` hbox(new Label("Display type:"), actionType)(100, Pos.CENTER).modify(BorderPane.setMargin(_, new Insets(0, 0, 10, 0)))
      centerProperty.bind(selectedMode)
    }
    
    sealed trait SoundType
    object noSound extends SoundType
    object beep extends SoundType
    object soundFile extends VBox with SoundType { $ =>
      setSpacing(10)
      setFillWidth(true)
      val path = new TextField
      val testButton = new Button("test")
      val browseButton = new Button("browse")
      Seq(testButton, browseButton).foreach(_.prefWidthProperty.bind(path.heightProperty))
      $ \ hbox(testButton, path.modify(HBox.setHgrow(_, Priority.ALWAYS)), browseButton)
      
      val repeat = new CheckBox("Repeat")
      val pauseBetweenRepetitions = new Spinner[Int](0, Int.MaxValue, 0, 1) { getEditor.setPrefColumnCount(4) }
      
      $ \ new TitledVBox(repeat, spacing = 10) \ hbox(new Label("Pause between repetitions:"), pauseBetweenRepetitions, new Label("second(s)")).modify(
        _.disableProperty `bind` repeat.selectedProperty.not)
      
      val volume = new Slider(0, 100, 100).modify(_.setBlockIncrement(1),
                                                  _.setMajorTickUnit(50),
                                                  _.setMinorTickCount(10),
                                                  _.setShowTickLabels(true), _.setShowTickMarks(true))
      
      $ \ new TitledVBox("Volume", spacing = 10) \ volume
      
      testButton.disableProperty `bind` path.textProperty.map(t => t == null || t.isEmpty)
      browseButton.setOnAction { _ =>
        val popup = getScene.getWindow.asInstanceOf[Popup]
        val chooser = new FileChooser().modify(_.setTitle("Select audio file"),
                                               _.getExtensionFilters.addAll(new FileChooser.ExtensionFilter("Audio Files", "*.wav", "*.mp3", "*.aac")))
        if (path.getText != null && path.getText.nonEmpty) chooser.setInitialFileName(path.getText)
        Option(chooser.showOpenDialog(AlarmPane.this.getScene.getWindow)) foreach (f => path.setText(f.getAbsolutePath))
        popup.show(AlarmPane.this.getScene.getWindow)
      }
      var lastClip: Option[AudioClip] = None
      testButton.setOnAction { _ => 
        if (!path.getText.toFile.exists) {
          val popup = getScene.getWindow.asInstanceOf[Popup]
          new Alert(Alert.AlertType.ERROR, "File doesn't exists", ButtonType.OK).showAndWait()
          popup.show(AlarmPane.this.getScene.getWindow)
        } else {
          lastClip foreach (_.stop)
          val clip = new AudioClip(path.getText.toFile.uri.toString)
          lastClip = Some(clip)
          clip.setVolume(volume.getValue / 100.0)
          clip.play()
        }
      }
    }
    
    val selectedSound = new SimpleObjectProperty[SoundType](noSound)
    val sound = combobox("None", "Beep", "Sound file")
    sound.getSelectionModel.selectedItemProperty.addListener((_, prev, prop) => prop match {
        case "None" => selectedSound `set` noSound
        case "Beep" => selectedSound `set` beep
        case "Sound file" =>
          val popup = new Popup()
          popup.setAutoHide(true)
          popup.getScene.setFill(Color.WHITE)
          popup.getContent.add(soundFile.modify((_.setPadding(new Insets(10)))))
          val target = sound.localToScreen(0, sound.getHeight)
          popup.sizeToScene()
          popup.show(sound, target.getX, target.getY)
          selectedSound `set` soundFile
          popup.setOnHidden { _ =>
            if (soundFile.path.getText.isEmpty) { //roll back
              sound.getSelectionModel.select(prev)
            }
          }
      })
    
    object fontSelectorDialog extends FontSelectorDialog(Font.getDefault) {
      val fontPane = getDialogPane.getContent.asInstanceOf[GridPane { def getFont(): Font; def setFont(f: Font): Unit }].modify(
        p => p.setPrefSize(Font.getDefault.getSize * p.getPrefWidth / 13,
                           Font.getDefault.getSize * p.getPrefHeight / 13),
        _.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE))
      val foregroundColor = new ColorPicker(Color.BLACK)
      val backgroundColor = new ColorPicker(Color.LAVENDERBLUSH)
      getDialogPane `setContent` new BorderPane { $ =>
        $ `top` gridPane(Seq(new Label("Foreground color:"), foregroundColor),
                       Seq(new Label("background color:"), backgroundColor)).modify(BorderPane.setMargin(_, new Insets(0, 0, 10, 0)))
        $ `center` fontPane
      }
    }
    val fontAndColor = new Button("Font & Color").modify(_.setMaxWidth(Int.MaxValue))
    fontAndColor.setOnAction {_ =>
      fontSelectorDialog.show()
      fontSelectorDialog.fontPane.getScene.getStylesheets.addAll(fontAndColor.getScene.getStylesheets)
      fontSelectorDialog.fontPane.getScene.getWindow.sizeToScene()
      alarmMode.message.fontProperty `bind` fontSelectorDialog.resultProperty
    }
    
    alarmMode.sceneProperty.addListener { (_, _, scene) => 
      alarmMode.applyCss()
      val region = alarmMode.message.lookup(".content").asInstanceOf[Region]
      region.backgroundProperty `bind` fontSelectorDialog.backgroundColor.valueProperty.map { c =>
        new Background(new BackgroundFill(c, CornerRadii.EMPTY, new Insets(0)))
      }
      alarmMode.message.getCssMetaData.asScala.find(_.getProperty == "-fx-text-fill") foreach {
        case prop: CssMetaData[TextArea, Color]@unchecked => 
          val textFill = prop.getStyleableProperty(alarmMode.message).asInstanceOf[Property[Color]]
          textFill.bind(fontSelectorDialog.foregroundColor.valueProperty)
      }
    }
    
    
    object specialActionsPane extends VBox { $ =>
      setSpacing(10)
      val preAlarmCommand = new TextField()
      val executeForDeferredAlarms = new CheckBox("Execute for deferred alarms").modify(_.setMaxWidth(Int.MaxValue))
      val cancelAlarmOnError = new CheckBox("Cancel alarm on error").modify(_.setMaxWidth(Int.MaxValue))
      val dontNotifyError = new CheckBox("Do not notify errors").modify(_.setMaxWidth(Int.MaxValue))
      val postAlarmCommand = new TextField()
      
      $ \ new TitledVBox("Pre-Alarm Action", 10) { $ =>
        $ \ hbox(new Label("Command:"), preAlarmCommand)(10)
        $ \ executeForDeferredAlarms
        $ \ cancelAlarmOnError
        $ \ dontNotifyError
      }
      $ \ new TitledVBox("Post-Alarm Action", 10) \ hbox(new Label("Command:"), postAlarmCommand)(10)
    }
    val specialActions = new Button("Special Actions").modify(_.setMaxWidth(Int.MaxValue))
    specialActions.setOnAction { _ =>
      val popup = new Popup()
      popup.getScene.setFill(Color.WHITE)
      popup.setAutoHide(true)
      popup.getContent.add(specialActionsPane.modify(_.setPadding(new Insets(10))))
      val target = specialActions.localToScreen(0, specialActions.getHeight)
      popup.sizeToScene()
      popup.show(sound, target.getX, target.getY)
    }
    
    $ `bottom` new BorderPane { $ =>
      BorderPane.setMargin(this, new Insets(10, 0, 0, 0))
      $ `left` hbox(new Label("Sound:"), sound)(spacing = 10)
      $ `right` vbox(fontAndColor, specialActions)(spacing = 10, fillWidth = true)
    }
  }
  VBox.setVgrow(action, Priority.ALWAYS)
  
  val time = $ \ new TitledVBox("Time", 10) with Selectable { $ =>
    val mode = new ToggleGroup()
    
    val atDateTime = mode \ new RadioButton("At date/time:")
    val atDate = new DatePicker(LocalDate.now())
    val atTime = new HourPicker()
    val atAnyTime = new CheckBox("Any time")
    atDateTime.setSelected(true)
    
    val timeFromNow = mode \ new RadioButton("Time from now:")
    val timeFromNowTime = new HourPicker(unboundHours = true)
    
    Seq(atDate, atTime, atAnyTime) foreach (_.disableProperty `bind` atDateTime.selectedProperty.not)
    atTime.disableProperty `bind` new BooleanBinding {
      bind(atDateTime.selectedProperty, atAnyTime.selectedProperty)
      def computeValue = !atDateTime.isSelected || atAnyTime.isSelected
    }
    timeFromNowTime.disableProperty `bind` timeFromNow.selectedProperty.not
    
    $ \ gridPane(Seq(atDateTime, atDate, atTime, atAnyTime),
                 Seq(timeFromNow, timeFromNowTime))
  }
  
  
  val extra = new VBox with Selectable { $ =>
      setSpacing(20)
      val reminder = new CheckBox("Reminder:")
      val reminderDurationPicker = new DurationPicker(DurationPicker.All*)
      val reminderType = combobox("in advance", "afterwards")
      val reminderForFirstRecurrenceOnly = new CheckBox("Reminder for first recurrence only")
    
      val cancelIfLate = new CheckBox("Cancel if late by")
      val cancelDurationPicker = new DurationPicker(DurationPicker.All*)
      val cancelAutoClose = new CheckBox("Auto-close window after this time")
    
      $ \ hbox(reminder, reminderDurationPicker, reminderType)(10)
      $ \ hbox(new Region().modify(_.setPrefWidth(50)), reminderForFirstRecurrenceOnly)
      $ \ hbox(cancelIfLate, cancelDurationPicker)(10)
      $ \ hbox(new Region().modify(_.setPrefWidth(50)), cancelAutoClose)
    
      Seq(reminderDurationPicker, reminderType, reminderForFirstRecurrenceOnly).foreach(_.disableProperty `bind` reminder.selectedProperty.not)
      Seq(cancelDurationPicker, cancelAutoClose).foreach(_.disableProperty `bind` cancelIfLate.selectedProperty.not)
    }
    val extraTitledPane = $ \ new TitledPane("Extra", extra).modify(_.setAnimated(false), _.setExpanded(false))
}


class AlarmPaneTest extends BaseApplication {
  def sceneRoot = new AlarmPane()
}