package starfruit
package ui

import better.files._
import javafx.application.Application

object Main {
  val instanceFile = File.home / ".starfruit.instance"
  def main(args: Array[String]): Unit = {
    System.setProperty("prism.lcdtext", "false")
    System.setProperty("prism.text", "t2k")
    if (!util.InstanceDetector.notifyInstance(instanceFile)) Application.launch(classOf[MainApplication], args*) else sys.exit(0)
  }
}