package starfruit

import better.files.*
import java.time.{DayOfWeek, Duration, LocalDate, LocalDateTime, LocalTime, Month}
import java.util as ju
import play.api.libs.json.*
import scala.deriving.Mirror
import scala.jdk.CollectionConverters.*
import scala.language.future
import scala.reflect.ClassTag
import scala.sys.process.*
import scala.util.{Success, Try}
import play.api.libs.json.Json.MacroOptions

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
  case class TextMessage(message: String) extends MessageSource { def get() = Success(message) }
  case class FileContentsMessage(path: File) extends MessageSource { def get() = Try(path.lineIterator.mkString("\n")) }
  case class ScriptOutputMessage(script: String) extends MessageSource { def get() = Try(Seq("bash", "-c", script).!!) }

  sealed trait Sound
  case object NoSound extends Sound
  case object Beep extends Sound
  case class SoundFile(file: String, repeat: Boolean, pauseBetweenRepetitions: Int, volume: Float) extends Sound

  case class SpecialAction(
      preAlarmCommand: String,
      postAlarmCommand: String,
      executeOnDeferred: Boolean,
      cancelAlarmOnError: Boolean,
      doNotNotifyErrors: Boolean
  )

  sealed trait Time
  case class AtTime(date: LocalDate, time: Option[LocalTime]) extends Time
  case class TimeFromNow(hours: Int, minutes: Int) extends Time

  sealed trait Recurrence
  case object NoRecurrence extends Recurrence
  case class HourMinutelyRecurrence(hours: Int, minutes: Int) extends Recurrence
  case class DailyRecurrence(every: Int, onDays: ju.EnumSet[DayOfWeek]) extends Recurrence
  case class WeeklyRecurrence(every: Int, onDays: ju.EnumSet[DayOfWeek]) extends Recurrence
  case class MonthlyRecurrence(every: Int, on: DayOfMonth) extends Recurrence
  case class YearlyRecurrence(
      every: Int,
      dayOfMonth: DayOfMonth,
      onMonths: ju.EnumSet[Month],
      onFebruary29NonLeapAction: Option[February29NonLeapAction]
  ) extends Recurrence

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

/** Models how entities are pickeld based on abstract definitions for lists, classes and similar
  */
trait AlarmPicklers {
  given genSeqFormat[T: Format]: Format[collection.Seq[T]]
  given seqFormat[T: Format]: Format[Seq[T]] = genSeqFormat.bimap(_.toSeq, identity)
  given genSetFormat[T: Format]: Format[collection.Set[T]] = genSeqFormat.bimap(_.toSet, _.toSeq)
  given setFormat[T: Format]: Format[Set[T]] = genSetFormat.bimap(_.toSet, identity)
  def optionHandlers: OptionHandlers

  given JsonConfiguration = JsonConfiguration(optionHandlers = optionHandlers)

  trait SumTypeClasses[T] {
    def classes: Map[Class[? <: T], Format[? <: T]]
  }
  object SumTypeClasses {
    inline given [T](using mirror: Mirror.SumOf[T]): SumTypeClasses[T] = {
      val clss =
        compiletime.summonAll[Tuple.Map[mirror.MirroredElemTypes, [x] =>> ClassTag[x]]].toList.asInstanceOf[List[ClassTag[? <: T]]]
      val fmtss = compiletime.summonAll[Tuple.Map[mirror.MirroredElemTypes, [x] =>> Format[x]]].toList.asInstanceOf[List[Format[? <: T]]]
      new SumTypeClasses[T] {
        val classes = clss.map(_.runtimeClass.asInstanceOf[Class[? <: T]]).zip(fmtss).toMap
      }
    }
  }

  given sumTypeFormat[T: SumTypeClasses]: Format[T]

  given eitherFormat[A: Format, B: Format]: Format[Either[A, B]]

  given Format[File] = Format.of[String].bimap(_.toFile, _.toString)

  given [T: Format]: Format[ju.Set[T]] = genSetFormat[T].bimap(_.asJava, _.asScala)
  given [T: Format]: Format[ju.List[T]] = genSeqFormat[T].bimap(_.asJava, _.asScala)
  given [T <: java.lang.Enum[T]: ClassTag]: Format[T] = {
    val enumClass = summon[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]]
    Format(
      Reads.of[Int].map(enumClass.getEnumConstants()(_)),
      Writes.of[Int].contramap(_.ordinal())
    )
  }
  given enumSetFormat[T <: java.lang.Enum[T]: Format: ClassTag]: Format[ju.EnumSet[T]] =
    Format
      .of[collection.Set[T]]
      .bimap(
        s => {
          val res = ju.EnumSet.noneOf[T](implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]])
          s foreach res.add
          res
        },
        _.asScala
      )

  given Format[Alarm.TextMessage] = Json.format
  given Format[Alarm.FileContentsMessage] = Json.format
  given Format[Alarm.ScriptOutputMessage] = Json.format
  given Format[Alarm.MessageSource] = sumTypeFormat
  given Format[Alarm.NoSound.type] = Json.format
  given Format[Alarm.Beep.type] = Json.format
  given Format[Alarm.SoundFile] = Json.format
  given Format[Alarm.Sound] = sumTypeFormat
  given Format[Alarm.NthDayOfMonth] = Json.format
  given Format[Alarm.NthWeekDayOfMonth] = Json.format
  given Format[Alarm.DayOfMonth] = sumTypeFormat
  given Format[Alarm.February29NonLeapAction.MoveNextDay.type] = Json.format
  given Format[Alarm.February29NonLeapAction.MovePrevDay.type] = Json.format
  given Format[Alarm.February29NonLeapAction] = sumTypeFormat
  given Format[Alarm.NoRecurrence.type] = Json.format
  given Format[Alarm.HourMinutelyRecurrence] = Json.format
  given Format[Alarm.DailyRecurrence] = Json.format
  given Format[Alarm.MonthlyRecurrence] = Json.format
  given Format[ju.EnumSet[Month]] = enumSetFormat
  given Format[Alarm.YearlyRecurrence] = Json.format
  given Format[Alarm.Recurrence] = sumTypeFormat
  given Format[Alarm.WeeklyRecurrence] = Json.format
  given Format[Alarm.AtTime] = Json.format
  given Format[Alarm.TimeFromNow] = Json.format
  given Format[Alarm.Time] = sumTypeFormat
  given Format[Alarm.SpecialAction] = Json.format
  given Format[Alarm.Repetition] = Json.format
  given Format[Alarm.Reminder] = Json.format
  given Format[Alarm.CancelIfLateBy] = Json.format
  given Format[Alarm] = Json.format

  given Format[AlarmState.Active.type] = Json.format
  given Format[AlarmState.Showing.type] = Json.format
  given Format[AlarmState.Ended.type] = Json.format
  given Format[AlarmState.State] = sumTypeFormat
  given Format[AlarmState] = Json.format

}

object AlarmPicklersV1 extends AlarmPicklers {
  given genSeqFormat[T](using Format[T]): Format[collection.Seq[T]] = {
    val path = __ \ "#elems"
    Format(path.read(using Reads.seq.widen), path.write(Writes.iterableWrites2))
  }
  lazy val optionHandlers: OptionHandlers = new OptionHandlers {
    override def writeHandler[T](jsPath: JsPath)(implicit writes: Writes[T]): OWrites[Option[T]] =
      (jsPath \ "#elems").write(Writes.iterableWrites2).contramap(identity)
    override def readHandler[T](jsPath: JsPath)(implicit r: Reads[T]): Reads[Option[T]] =
      (jsPath \ "#elems").read(using Reads.seq.widen).map(_.headOption)
  }
  given sumTypeFormat[T](using mirror: SumTypeClasses[T]): Format[T] = {
    val reads: Reads[T] = (__ \ "#cls")
      .read[String]
      .flatMap(cls =>
        mirror.classes.get(Class.forName(cls).asInstanceOf) match
          case Some(format) => (__ \ "#val").read(using format.widen)
          case _ => throw new IllegalStateException(s"Class $cls is not part of the sum type")
      )
    Format(
      reads,
      v =>
        Json.obj(
          "#cls" -> v.getClass.getName(),
          "#val" -> mirror.classes(v.getClass).asInstanceOf[Writes[T]].writes(v)
        )
    )
  }
  given eitherFormat[A: Format, B: Format]: Format[Either[A, B]] = {
    Format(
      (__ \ "left").read[A].map(Left(_)).orElse((__ \ "right").read[B].map(Right(_))),
      {
        case Left(v) => Json.obj("left" -> Writes.of[A].writes(v))
        case Right(v) => Json.obj("right" -> Writes.of[B].writes(v))
      }
    )
  }
}
object AlarmPicklersV2 extends AlarmPicklers {
  lazy val optionHandlers: OptionHandlers = OptionHandlers.Default
  given genSeqFormat[T](using tFormat: Format[T]): Format[collection.Seq[T]] = Format(Reads.seq[T].widen, Writes.seq[T].contramap(_.toSeq))
  given sumTypeFormat[T](using mirror: SumTypeClasses[T]): Format[T] = {
    val reads: Reads[T] = (__ \ "#cls")
      .read[String]
      .flatMap(cls =>
        mirror.classes.get(Class.forName(cls).asInstanceOf) match
          case Some(format) => format.widen
          case _ => throw new IllegalStateException(s"Class $cls is not part of the sum type")
      )
    Format(
      reads,
      v => Json.obj("#cls" -> v.getClass.getName()) ++ mirror.classes(v.getClass).asInstanceOf[OWrites[T]].writes(v)
    )
  }
  given eitherFormat[A: Format, B: Format]: Format[Either[A, B]] = {
    val lftFmt = Reads.of[A].map[Left[A, B]](Left(_)).widen[Either[A, B]]
    val rghtFmt = Reads.of[B].map[Right[A, B]](Right(_)).widen[Either[A, B]]
    Format(
      lftFmt.orElse(rghtFmt),
      {
        case Left(v) => Writes.of[A].writes(v)
        case Right(v) => Writes.of[B].writes(v)
      }
    )
  }
}

@main def testAlarmPicklers: Unit = {
  val parsed = Json.parse((File.home / ".starfruit-alarms").contentAsString)
  import AlarmPicklersV1.given
  parsed.validate[collection.Seq[AlarmState]] match
    case JsSuccess(alarms, path) => 
      // println(pprint.apply(alarms))
      import AlarmPicklersV2.given
      val converted = Json.prettyPrint(Json.toJson(alarms))
      println(converted)
      println(pprint.apply(Json.parse(converted).as[collection.Seq[AlarmState]]))
    case err: JsError => println(pprint.apply(err))

}
