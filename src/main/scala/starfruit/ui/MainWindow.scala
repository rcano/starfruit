package starfruit.ui

import java.time.LocalDateTime
import java.time.format.{DateTimeFormatter, FormatStyle}
import javafx.animation.{Timeline, KeyFrame}
import javafx.beans.property.ReadOnlyObjectWrapper
import javafx.geometry._, Orientation._
import javafx.scene.control._
import javafx.scene.input.KeyCode
import javafx.scene.layout._
import javafx.scene.paint.Color
import javafx.util.StringConverter
import org.controlsfx.control.HiddenSidesPane

class MainWindow extends BorderPane { $ =>
  val menuBar = new MenuBar { $ =>
    val fileMenu = $ \ new Menu("_File") { $ =>
      val importAlarms = $ \ new MenuItem("Import Alarms...")
//      val exportSelectedAlarms = $ \ new MenuItem("Export Selected Alarms...")
      $ \ new SeparatorMenuItem
      val exit = $ \ new MenuItem("_Quit")
    }
//    val editMenu = $ \ new Menu("Edit") { $ =>
//      val selectAll = $ \ new MenuItem("Select All")
//      val deselect = $ \ new MenuItem("Deselect")
//      $ \ new SeparatorMenuItem
//      val findNext = $ \ new MenuItem("Find Next")
//      val findPrevious = $ \ new MenuItem("Find Previous")
//    }
  }
  val toolBar = new ToolBar { $ =>
    val newButton = $ \ new Button("🗌 New") {
      val displayAlarm = new MenuItem("🗌 New Display Alarm")
      val commandAlarm = new MenuItem("💻 New Command Alarm")
      val audioAlarm = new MenuItem("🎜 New Audio Alarm")
      
      setOnAction { evt =>
        val menu = new ContextMenu(displayAlarm, commandAlarm, audioAlarm)
        menu.show(this, Side.BOTTOM, 0, 0)
      }
    }
    $ \ new Separator(VERTICAL)
    val copyButton = $ \ new Button("🗐 Copy")
    val editButton = $ \ new Button("🗔 Edit")
    val deleteButton = $ \ new Button("🗑 Delete")
    $ \ new Separator(VERTICAL)
    val undoButton = $ \ new Button("⎌ Undo")
    val redoButton = $ \ new Button("⎌ Redo")
    $ \ new Separator(VERTICAL)
    val findButton = $ \ new Button("🔍 Find").modify(_.setOnAction { _ =>
        hiddenPanel.setPinnedSide(Side.BOTTOM)
        val timeline = new Timeline()
        timeline.getKeyFrames.add(new KeyFrame(javafx.util.Duration.millis(100), _ => findTextField.requestFocus()))
        timeline.play()
      })
    
  }
  $ top new VBox(menuBar, toolBar)

  val hiddenPanel = $ center new HiddenSidesPane().modify(_.setTriggerDistance(0), _.setAnimationDelay(javafx.util.Duration.ZERO),
                                                          _.setAnimationDuration(javafx.util.Duration.seconds(0.2)))
  
  type TableColumns = (LocalDateTime, String, Color, String, String)
  val alarmsTable = new TableView[TableColumns] {
    val timeCol = new TableColumn[TableColumns, LocalDateTime]("Time") {
      setCellValueFactory { cellDataFeatures => new ReadOnlyObjectWrapper(cellDataFeatures.getValue._1) }
      val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
      setCellFactory { col => 
        val res = new cell.TextFieldTableCell[TableColumns, LocalDateTime]
        res.setConverter(new StringConverter[LocalDateTime] {
            override def fromString(s) = ???
            override def toString(t: LocalDateTime) = t.format(formatter)
          })
        res
      }
    }
    val repeatCol = new TableColumn[TableColumns, String]("Repeat") {
      setCellValueFactory { cellDataFeatures => new ReadOnlyObjectWrapper(cellDataFeatures.getValue._2) }
    }
    val colorCol = new TableColumn[TableColumns, Color]("") {
      setPrefWidth(40)
      setSortable(false)
      setCellValueFactory { cellDataFeatures => new ReadOnlyObjectWrapper(cellDataFeatures.getValue._3) }
      setCellFactory { col =>
        new TableCell[TableColumns, Color] {
          override def updateItem(item: Color, isEmpty) = {
            super.updateItem(item, isEmpty)
            if (item == null) {
              setStyle(null)
            } else {
              setStyle("-fx-background-color: " + item.colorToWeb)
            }
          }
        }
      }
    }
    val typeCol = new TableColumn[TableColumns, String]("") {
      setPrefWidth(40)
      setSortable(false)
      setCellValueFactory { cellDataFeatures => new ReadOnlyObjectWrapper(cellDataFeatures.getValue._4) }
    }
    val messageCol = new TableColumn[TableColumns, String]("Message, File or Command") {
      setSortable(true)
      setCellValueFactory { cellDataFeatures => new ReadOnlyObjectWrapper(cellDataFeatures.getValue._5.replaceAll("\r\n|\n", " ")) }
    }
    messageCol.setPrefWidth(400)
    
    getColumns.setAll(timeCol, repeatCol, colorCol, typeCol, messageCol)
  }
  hiddenPanel.setContent(alarmsTable)
  
  val findTextField = new TextField()
  val findNext = new Button("⎘ Next")
  val findPrevious = new Button("⎗ Previous")
  val cancelFind = new Button("🗙").modify(_.setStyle("-fx-text-fill: red"), _.setOnAction(_ => hiddenPanel.setPinnedSide(null)))
  val findPane = hbox(new Label("Find:"), findTextField, findNext, findPrevious, cancelFind).modify(
    _.setStyle("-fx-background-color: -fx-background"),
    _.setPadding(new Insets(0, 0, 10, 0)))
  hiddenPanel.setBottom(findPane)
  
  findPane.setOnKeyPressed { evt =>
    if (evt.getCode == KeyCode.ESCAPE) {
      hiddenPanel.setPinnedSide(null)
    }
  }
  
}
