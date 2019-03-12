package starfruit

import fastparse._, NoWhitespace._
import java.time._
import java.time.format.DateTimeFormatter
import java.util.EnumSet
import scala.util._

object ICalendar {
  sealed trait Element { def name: String }
  case class Section(name: String, elements: Seq[Element]) extends Element {
    def field[E <: Element: reflect.ClassTag](field: String): Option[E] = elements.collectFirst { case e: E if e.name == field => e }
  }
  case class Entry(name: String, valueType: Option[String], value: String) extends Element
  
  def element[_: P]: P[Element] = P( section | entry )
  def section[_: P] = P( "BEGIN:" ~ name.! flatMap (name => nl ~ element.rep ~ s"END:$name" ~ nl map (elems => Section(name, elems))) )
  def entry[_: P] = P( !"END" ~ name.! ~ (";" ~ name ~ "=" ~ name).!.? ~ ":" ~ (P("\r\n ").map(p => ' ') | !nl ~ AnyChar).rep.! ~ nl ).map((Entry.apply _).tupled)
  def name[_: P] = P( CharPred { case ';' | ':' | '=' | '\r' | '\n' => false; case _ => true }.rep(1) )
  def nl[_: P] = P("\r\n" | "\n")
  
  def parse(ical: String, defaultFont: String): Try[Seq[AlarmState]] = Try {
    val gmtDateParser = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssVV")
    val localDateParser = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    
    fastparse.parse(ical, section(_)) match {
      case Parsed.Success(vcalendar, idx) => 
        vcalendar.elements.collect {
          case vevent@ Section("VEVENT", elements) => 
            val valarm = vevent.field[Section]("VALARM").get
            val (font, foreground, background) = valarm.field[Entry]("X-KDE-KALARM-FONTCOLOR").fold((defaultFont, "#ffffff", "#fff0f5")) { s =>
              s.value.split("\\\\;") match {
                case Array(back, fore, font) =>
                  val Array(fontName, size, _*) = font.split("\\\\,")
                  (s"normal $size $fontName", fore, back)
                case Array(back, fore) => (defaultFont, fore, back)
              }
            }
            val atTime = vevent.field[Entry]("DTSTART").map(e => LocalDateTime.parse(e.value, localDateParser)).get
            val atLocalTime = if (atTime.toLocalTime == LocalTime.MIDNIGHT) None else Some(atTime.toLocalTime)
            val kalarmFlags = vevent.field[Entry]("X-KDE-KALARM-FLAGS").map(_.value)
            val cancelIfLate = kalarmFlags.flatMap { s =>
              val parts = s.split("\\\\;")
              parts.indexOf("LATECLOSE") match {
                case -1 => None
                case idx => Some(Alarm.CancelIfLateBy(Duration.ofMinutes(parts(idx + 1).toLong), true))
              }
            }
            val (recurrence, end) = vevent.field[Entry]("RRULE").map(_.value.split(";").map { entry =>
                val Array(a, b) = entry.split("=")
                (a, b)
              }.toMap).fold[(Alarm.Recurrence, Option[Int Either LocalDateTime])](Alarm.NoRecurrence -> None) { props =>
              val end = props.get("COUNT").map(i => Left(i.toInt)).orElse(
                props.get("UNTIL").map(date => Right(LocalDateTime.parse(date, gmtDateParser))))
              
              println("processing " + props)
              
              def day(d: String) = d match { case "MO" => DayOfWeek.MONDAY;  case "TU" => DayOfWeek.TUESDAY; case "WE" => DayOfWeek.WEDNESDAY; case "TH" => DayOfWeek.THURSDAY; case "FR" => DayOfWeek.FRIDAY; case "SA" => DayOfWeek.SATURDAY; case "SU" => DayOfWeek.SUNDAY }
              (props("FREQ") match {
                  case "MINUTELY" => 
                    val minutes = props.get("INTERVAL").map(_.toInt).getOrElse(1)
                    Alarm.HourMinutelyRecurrence(minutes / 60, minutes % 60)
                  
                  case "DAILY" =>
                    val every = props.get("INTERVAL").map(_.toInt).getOrElse(1)
                    val days = EnumSet.noneOf(classOf[DayOfWeek])
                    props.get("BYDAY").fold[Unit](days addAll EnumSet.allOf(classOf[DayOfWeek]))(_.split(",").map(day) foreach days.add)
                    Alarm.DailyRecurrence(every, days)
                  
                  case "WEEKLY" =>
                    val weeks = props.get("INTERVAL").map(_.toInt).getOrElse(1)
                    val days = EnumSet.noneOf(classOf[DayOfWeek])
                    props.get("BYDAY").fold[Unit](days addAll EnumSet.allOf(classOf[DayOfWeek]))(_.split(",").map(day) foreach days.add)
                    Alarm.WeeklyRecurrence(weeks, days)
                  
                  case "MONTHLY" =>
                    val every = props.get("INTERVAL").map(_.toInt).getOrElse(1)
                    val dom = props.get("BYDAY").map(byday => Alarm.NthWeekDayOfMonth(byday.dropRight(2).toInt, day(byday.takeRight(2)))).getOrElse(
                      Alarm.NthDayOfMonth(props("BYMONTHDAY").toInt))
                    Alarm.MonthlyRecurrence(every, dom)
                  
                  case "YEARLY" =>
                    val every = props.get("INTERVAL").map(_.toInt).getOrElse(1)
                    val dom = props.get("BYDAY").map(byday => Alarm.NthWeekDayOfMonth(byday.dropRight(2).toInt, day(byday.takeRight(2)))).getOrElse(
                      Alarm.NthDayOfMonth(props("BYMONTHDAY").toInt))
                    val months = EnumSet.noneOf(classOf[Month])
                    props.get("BYMONTH") foreach (_.split(",").map(_.toInt) foreach (i => months add Month.of(i)))
                    Alarm.YearlyRecurrence(every, dom, months, None)
                }) -> end
            }
            val alarm = Alarm(
              Alarm.TextMessage(valarm.field[Entry]("DESCRIPTION").get.value.replace("\\n", "\n").replace("\\,", ",")),
              font,
              foreground,
              background,
              sound = Alarm.NoSound,
              specialAction = None,
              when = Alarm.AtTime(atTime.toLocalDate, atLocalTime),
              recurrence = recurrence,
              subrepetition = None,
              end = end,
              exceptionOnDates = Seq.empty,
              exceptionOnWorkingTime = false,
              reminder = None,
              cancelIfLate = cancelIfLate
            )
            AlarmState(
              alarm,
              AlarmState.Active,
              vevent.field[Entry]("DTSTAMP").map(e => ZonedDateTime.parse(e.value, gmtDateParser)).get.toInstant)
        }
      case f: Parsed.Failure => throw new IllegalArgumentException(f.extra.trace(true).msg)
    }
  }
}

object ICalendarTest extends App {
  import better.files._
  
//  def reportParsed(p: Parsed[_]) = p match {
//    case Parsed.Success(value, idx) => println(value)
//    case f: Parsed.Failure =>
//      println("Failed:")
//      println(f.extra.traced.trace)
//  }
//  
//  reportParsed(ICalendar.entry.parse("PRODID:-//K Desktop Environment//NONSGML KAlarm 2.11.15-5//EN\r\n"))
//  reportParsed(ICalendar.section.parse("""BEGIN:STANDARD
//TZNAME:UYT
//TZOFFSETFROM:-0200
//TZOFFSETTO:-0300
//DTSTART:20060312T020000
//RRULE:FREQ=YEARLY;COUNT=10;BYDAY=2SU;BYMONTH=3
//END:STANDARD
//"""))
//  println()
//  
//  val file = File.home/"Documents"/"alarms.ics"
//  val text = file.contentAsString
//  reportParsed(ICalendar.section.parse(text))
  
  ICalendar.parse((File.home/"Documents"/"alarms.ics").contentAsString, "system").fold(_.printStackTrace, println)
}
