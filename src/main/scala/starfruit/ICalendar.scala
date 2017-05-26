package starfruit

import fastparse.all._

object ICalendar {
  sealed trait Element
  case class Section(title: String, elements: Seq[Element]) extends Element
  case class Entry(name: String, valueType: Option[String], value: String) extends Element
  
  lazy val element: Parser[Element] = P( section | entry )
  lazy val section = P( "BEGIN:" ~ name.! flatMap (name => nl ~ element.rep ~ s"END:$name" ~ nl map (elems => Section(name, elems))) )
  lazy val entry = P( !"END" ~ name.! ~ (";" ~ name ~ "=" ~ name).!.? ~ ":" ~ (!nl ~ AnyChar).rep.! ~ nl ).map((Entry.apply _).tupled)
  lazy val name = P( CharPred { case ';' | ':' | '=' | '\r' | '\n' => false; case _ => true }.rep(min=1) )
  lazy val nl = P("\r\n" | "\n")
  
}

object ICalendarTest extends App {
  import better.files._
  
  def reportParsed(p: Parsed[_]) = p match {
    case Parsed.Success(value, idx) => println(value)
    case f: Parsed.Failure =>
      println("Failed:")
      println(f.extra.traced.trace)
  }
  
  reportParsed(ICalendar.entry.parse("PRODID:-//K Desktop Environment//NONSGML KAlarm 2.11.15-5//EN\r\n"))
  reportParsed(ICalendar.section.parse("""BEGIN:STANDARD
TZNAME:UYT
TZOFFSETFROM:-0200
TZOFFSETTO:-0300
DTSTART:20060312T020000
RRULE:FREQ=YEARLY;COUNT=10;BYDAY=2SU;BYMONTH=3
END:STANDARD
"""))
  println()
  
  val file = File.home/"Documents"/"alarms.ics"
  val text = file.contentAsString
  reportParsed(ICalendar.section.parse(text))
}
