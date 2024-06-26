package starfruit

import javafx.beans.value.ObservableValue
import javafx.geometry._
import javafx.beans.binding._
import javafx.scene.Node
import javafx.scene.control._
import javafx.scene.layout._
import javafx.scene.paint.Color

package object ui {
  
  /*****************************************
   * builder api
   *****************************************/

  extension (peer: Pane) inline def \[N <: Node](n: Node): n.type = { peer.getChildren.add(n); n }
  implicit class BorderPaneBuilder(val peer: BorderPane) extends AnyVal {
    def top[N <: Node](node: N): N = { peer.setTop(node); node }
    def center[N <: Node](node: N): N = { peer.setCenter(node); node }
    def left[N <: Node](node: N): N = { peer.setLeft(node); node }
    def right[N <: Node](node: N): N = { peer.setRight(node); node }
    def bottom[N <: Node](node: N): N = { peer.setBottom(node); node }
  }
  implicit class GridPaneBuilder(val peer: GridPane) extends AnyVal {
    def update[N <: Node](col: Int, row: Int, node: N): N = { peer.add(node, col, row); node }
    def update[N <: Node](col: Int, row: Int, colSpan: Int, rowSpan: Int, node: N): N = { peer.add(node, col, row, colSpan, rowSpan); node }
  }
  implicit class ToolBarBuilder(val peer: ToolBar) extends AnyVal {
    def \[N <: Node](node: N): N = { peer.getItems.add(node); node }
  }
  implicit class MenuBarBuilder(val peer: MenuBar) extends AnyVal {
    def \[M <: Menu](node: M): M = { peer.getMenus.add(node); node }
  }
  implicit class MenuBuilder(val peer: Menu) extends AnyVal {
    def \[M <: MenuItem](node: M): M = { peer.getItems.add(node); node }
  }
  implicit class ToggleButtonBuilder(val peer: ToggleGroup) extends AnyVal {
    def \[T <: Toggle](node: T): T = { peer.getToggles.add(node); node }
  }
  
  /**********************************
   * Useful layouts
   **********************************/
  
  def hbox(nodes: Node*)(implicit spacing: Double = 10, alignment: Pos = Pos.BASELINE_LEFT, fillHeight: Boolean = false) = {
    val res = new HBox(nodes*)
    res.setSpacing(spacing)
    res.setAlignment(alignment)
    res.setFillHeight(fillHeight)
    res
  }
  def vbox(nodes: Node*)(implicit spacing: Double = 10, alignment: Pos = Pos.BASELINE_LEFT, fillWidth: Boolean = false) = {
    val res = new VBox(nodes*)
    res.setSpacing(spacing)
    res.setAlignment(alignment)
    res.setFillWidth(fillWidth)
    res
  }
  
  def gridPane(rows: Seq[Node]*)(implicit vgap: Double = 10, hgap: Double = 10) = new GridPane {
    setVgap(vgap)
    setHgap(hgap)
    for ((row, idx) <- rows.zipWithIndex)
      addRow(idx, row*)
  }
  
  def combobox[T](elems: T*) = {
    val res = new ComboBox[T]
    res.getItems.addAll(elems*)
    res.getSelectionModel.selectFirst()
    res
  }
  
  /*********************
   * MISC
   *********************/
  
  implicit class ObservableValueExt[T](val property: ObservableValue[T]) extends AnyVal {
    def map[U](f: T => U): Binding[U] = new ObjectBinding[U] {
      bind(property)
      override def computeValue = f(property.getValue)
    }
    def flatMap[U](f: T => Binding[U]): Binding[U] = new ObjectBinding[U] {
      bind(property)
      override def computeValue() = f(property.getValue).getValue
    }
  }
  
  implicit class Modifier[T](val t: T) extends AnyVal {
    @inline def modify(functions: T => Any*): T = { functions foreach (_(t)); t }
  }
  
  implicit class ColorExt(val c: Color) extends AnyVal {
    def colorToWeb = "#%02X%02X%02X".format((c.getRed * 255).toInt, (c.getGreen * 255).toInt, (c.getBlue * 255).toInt)
  }
}
