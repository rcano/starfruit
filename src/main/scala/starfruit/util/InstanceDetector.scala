package starfruit.util

import better.files._
import java.net.{DatagramPacket, DatagramSocket, InetSocketAddress, SocketTimeoutException, InetAddress}


object InstanceDetector {
  def notifyInstance(instance: File): Boolean = {
    if (instance.exists) {
      val port = instance.contentAsString.toInt
      println("instance file found with port " + port)
      val socket = new DatagramSocket()
      socket.setSoTimeout(5)
      socket.send(new DatagramPacket("show".getBytes, 4, new InetSocketAddress("localhost", port)))
      
      try {
      val res = new DatagramPacket(new Array[Byte](4), 4)
        socket.receive(res)
        println("answer received")
        true
      } catch { case e: SocketTimeoutException => println("no answer received"); false } finally socket.close()
    } else false
  }
  def setupInstance(instance: File)(listener: => Unit): Unit = {
    val socket = new DatagramSocket(0, InetAddress.getByName("localhost"))
    println("listening on " + socket.getLocalAddress + ":" + socket.getLocalPort)
    instance.writeText(socket.getLocalPort.toString)
    val thread = new Thread(null, () => {
        val in = new DatagramPacket(new Array[Byte](4), 4)
        while (true) {
          try {
            socket.receive(in)
            println("query received")
            new String(in.getData) match {
              case "show" =>
                in.setData("done".getBytes)
                socket.send(in)
                listener
              case _ =>
            }
          } catch { case _: Exception => }
        }
      }, "Instance listener", 1024l)
    thread.setDaemon(true)
    thread.start()
  }
}
