package starfruit

import better.files._
import java.time.{LocalDate, LocalTime, Instant, DayOfWeek, Month, Duration, LocalDateTime}
import java.util.EnumSet
import prickle._
import scala.collection.JavaConverters._
import scala.reflect.ClassTag
import scala.sys.process._
import scala.util.{Failure, Success, Try}

case class Alarm(
  message: Alarm.MessageSource,
  font: String,
  foregroundColor: String,
  backgroundColor: String,
  sound: Alarm.Sound,
  specialAction: Option[Alarm.SpecialAction],
  when: Alarm.Time,
  recurrence: Alarm.Recurrence,
  subrepetition: Option[Alarm.Repetition],
  end: Option[Int Either LocalDateTime],
  exceptionOnDates: Seq[LocalDate],
  exceptionOnWorkingTime: Boolean,
  reminder: Option[Alarm.Reminder],
  cancelIfLate: Option[Alarm.CancelIfLateBy]
)
object Alarm {
  sealed trait MessageSource {
    def get(): Try[String]
  }
  case class TextMessage(message: String) extends MessageSource { def get = Success(message) }
  case class FileContentsMessage(path: File) extends MessageSource { def get = Try(path.lineIterator.mkString("\n")) }
  case class ScriptOutputMessage(script: String) extends MessageSource { def get = Try(Seq("bash", "-c", script).!!) }
  
  sealed trait Sound
  case object NoSound extends Sound
  case object Beep extends Sound
  case class SoundFile(file: String, repeat: Boolean, pauseBetweenRepetitions: Int, volume: Float) extends Sound
  
  case class SpecialAction(preAlarmCommand: String, postAlarmCommand: String, executeOnDeferred: Boolean, cancelAlarmOnError: Boolean, doNotNotifyErrors: Boolean)
  
  sealed trait Time
  case class AtTime(date: LocalDate, time: Option[LocalTime]) extends Time
  case class TimeFromNow(hours: Int, minutes: Int) extends Time
  
  sealed trait Recurrence
  case object NoRecurrence extends Recurrence
  case class HourMinutelyRecurrence(hours: Int, minutes: Int) extends Recurrence
  case class DailyRecurrence(every: Int, onDays: EnumSet[DayOfWeek]) extends Recurrence
  case class WeeklyRecurrence(every: Int, onDays: EnumSet[DayOfWeek]) extends Recurrence
  case class MonthlyRecurrence(every: Int, on: DayOfMonth) extends Recurrence
  case class YearlyRecurrence(every: Int, dayOfMonth: DayOfMonth, onMonths: EnumSet[Month], onFebruary29NonLeapAction: Option[February29NonLeapAction]) extends Recurrence
  
  sealed trait DayOfMonth
  case class NthDayOfMonth(day: Int) extends DayOfMonth
  case class NthWeekDayOfMonth(day: Int, weekday: DayOfWeek) extends DayOfMonth
  
  sealed trait February29NonLeapAction
  object February29NonLeapAction {
    case object MoveNextDay extends February29NonLeapAction
    case object MovePrevDay extends February29NonLeapAction
  }
  
  case class Repetition(every: Duration, endAfter: Int Either Duration)
  
  case class Reminder(duration: Duration, before: Boolean, forFirstOccurrenceOnly: Boolean)
  case class CancelIfLateBy(duration: Duration, autoCloseWindowAfterThisTime: Boolean)
}

object AlarmPicklers {
//  implicit val pconfig = PConfig.Default.copy(areSharedObjectsSupported = false)
  
  implicit object FilePickler extends Pickler[File] with Unpickler[File] {
    override def pickle[P](v: File, state)(implicit config: PConfig[P]): P = config.makeString(v.toString)
    override def unpickle[P](v: P, state)(implicit config: PConfig[P]) = config.readString(v).map(_.toFile)
  }
  implicit object LocalDatePickler extends Pickler[LocalDate] with Unpickler[LocalDate] {
    override def pickle[P](v: LocalDate, state)(implicit config: PConfig[P]): P = config.makeString(v.toString)
    override def unpickle[P](v: P, state)(implicit config: PConfig[P]) = config.readString(v).map(LocalDate.parse)
  }
  implicit object LocalTimePickler extends Pickler[LocalTime] with Unpickler[LocalTime] {
    override def pickle[P](v: LocalTime, state)(implicit config: PConfig[P]): P = config.makeString(v.toString)
    override def unpickle[P](v: P, state)(implicit config: PConfig[P]) = config.readString(v).map(LocalTime.parse)
  }
  implicit object LocalDateTimePickler extends Pickler[LocalDateTime] with Unpickler[LocalDateTime] {
    override def pickle[P](v: LocalDateTime, state)(implicit config: PConfig[P]): P = config.makeString(v.toString)
    override def unpickle[P](v: P, state)(implicit config: PConfig[P]) = config.readString(v).map(LocalDateTime.parse)
  }
  implicit object DurationPickler extends Pickler[Duration] with Unpickler[Duration] {
    override def pickle[P](v: Duration, state)(implicit config: PConfig[P]): P = config.makeString(v.toString)
    override def unpickle[P](v: P, state)(implicit config: PConfig[P]) = config.readString(v).map(Duration.parse)
  }
  implicit object InstantPickler extends Pickler[Instant] with Unpickler[Instant] {
    override def pickle[P](v: Instant, state)(implicit config: PConfig[P]): P = config.makeString(v.toString)
    override def unpickle[P](v: P, state)(implicit config: PConfig[P]) = config.readString(v).map(Instant.parse)
  }
  
  implicit def javaSetPickler[T: Pickler] = new Pickler[java.util.Set[T]] {
    override def pickle[P](v: java.util.Set[T], state)(implicit config: PConfig[P]): P = Pickle(v.asScala.toSeq, state)
  }
  implicit def javaSetUnpickler[T: Unpickler] = new Unpickler[java.util.Set[T]] {
    override def unpickle[P](v: P, state)(implicit config: PConfig[P]) = Unpickle[Seq[T]].from(v, state).map(seq => seq.toSet.asJava)
  }
  implicit def javaEnumSetPickler[T <: java.lang.Enum[T]: Pickler]: Pickler[EnumSet[T]] = javaSetPickler[T].asInstanceOf[Pickler[EnumSet[T]]]
  implicit def javaEnumSetUnpickler[T <: java.lang.Enum[T]: Unpickler: ClassTag] = new Unpickler[EnumSet[T]] {
    override def unpickle[P](v: P, state)(implicit config: PConfig[P]) = Unpickle[Seq[T]].from(v, state).map { seq => 
      val res = EnumSet.noneOf[T](implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]])
      seq foreach res.add
      res
    }
  }
  implicit def enumPickler[T <: java.lang.Enum[T]] = new Pickler[T] {
    override def pickle[P](v: T, state)(implicit config: PConfig[P]): P = config.makeNumber(v.ordinal)
  }
  implicit def enumUnpickler[T <: java.lang.Enum[T]: ClassTag] = new Unpickler[T] {
    override def unpickle[P](v: P, state)(implicit config: PConfig[P]) = config.readNumber(v).map(ord => implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]].getEnumConstants()(ord.toInt))
  }
  
  implicit def eitherPickler[L: Pickler, R: Pickler] = new Pickler[L Either R] {
    override def pickle[P](v: L Either R, state)(implicit config: PConfig[P]): P = v match {
      case Left(l) => config.makeObject("left", Pickle(l, state))
      case Right(r) => config.makeObject("right", Pickle(r, state))
    }
  }
  implicit def eitherUnpickler[L: Unpickler, R: Unpickler] = new Unpickler[L Either R] {
    override def unpickle[P](v: P, state)(implicit config: PConfig[P]) = config.readObjectField(v, "left") match {
      case Success(l) => Unpickle[L].from(l, state).map(Left.apply)
      case Failure(_) => config.readObjectField(v, "right").flatMap(r => Unpickle[R].from(r, state).map(Right.apply))
    }
  }

  implicit val messageAlarmPickler = CompositePickler[Alarm.MessageSource].concreteType[Alarm.TextMessage].concreteType[Alarm.FileContentsMessage].concreteType[Alarm.ScriptOutputMessage]
  implicit val soundPickler = CompositePickler[Alarm.Sound].concreteType[Alarm.NoSound.type].concreteType[Alarm.Beep.type].concreteType[Alarm.SoundFile]
  implicit val dayOfMonthPickler = CompositePickler[Alarm.DayOfMonth].concreteType[Alarm.NthDayOfMonth].concreteType[Alarm.NthWeekDayOfMonth]
  implicit val february29NonLeapActionPickler = CompositePickler[Alarm.February29NonLeapAction].concreteType[Alarm.February29NonLeapAction.MoveNextDay.type].
    concreteType[Alarm.February29NonLeapAction.MovePrevDay.type]
  implicit val recurrencePickler = CompositePickler[Alarm.Recurrence].concreteType[Alarm.NoRecurrence.type].concreteType[Alarm.HourMinutelyRecurrence].
    concreteType[Alarm.DailyRecurrence].concreteType[Alarm.WeeklyRecurrence].concreteType[Alarm.MonthlyRecurrence].concreteType[Alarm.YearlyRecurrence]
  implicit val timePickler = CompositePickler[Alarm.Time].concreteType[Alarm.AtTime].concreteType[Alarm.TimeFromNow]
  
  implicit val alarmPickler = implicitly[Pickler[Alarm]]
  implicit val alarmUnpickler = implicitly[Unpickler[Alarm]]
}

