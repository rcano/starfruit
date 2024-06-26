package starfruit

import language.implicitConversions

import java.time._
import java.time.temporal.{ChronoField, ChronoUnit, TemporalAdjusters}
import scala.jdk.CollectionConverters.*

case class AlarmState(alarm: Alarm, state: AlarmState.State, started: Instant, nextOccurrence: Instant, recurrenceInstance: Int,
                      subrecurrenceInstance: Int, nextReminder: Option[Instant], reminderInstance: Option[Int])
object AlarmState {
  def apply(alarm: Alarm, state: AlarmState.State, now: Instant): AlarmState = {
    val firstOccurrence = alarm.when match {
      case Alarm.AtTime(date, time) => ZonedDateTime.of(date, time.getOrElse(LocalTime.MIDNIGHT), ZoneId.systemDefault).toInstant
      case Alarm.TimeFromNow(hours, minutes) => now.plus(hours, ChronoUnit.HOURS).plus(minutes, ChronoUnit.MINUTES)
    }
    val reminder = alarm.reminder map { case Alarm.Reminder(duration, inAdvance, firstOccurrenceOnly) =>
        if (inAdvance) firstOccurrence.minus(duration) else firstOccurrence.plus(duration)
    }
    AlarmState(alarm, state, now, firstOccurrence, 0, 0, reminder, reminder.map(_ => 0))
  }
  
  sealed trait State
  case object Active extends State
  case object Showing extends State
  case object Ended extends State
  
}

object AlarmStateMachine {
  sealed trait CheckResult { def next: AlarmState }
  case object KeepState extends CheckResult { def next = ??? }
  case class StateChanged(next: AlarmState) extends CheckResult
  case class NotifyReminder(next: AlarmState) extends CheckResult
  case class NotifyAlarm(next: AlarmState) extends CheckResult
  case class AutoCloseAlarmNotification(next: AlarmState) extends CheckResult
  
  private implicit def comparableOrderingOps[C <: java.lang.Comparable[C]](c: C)(implicit ord: Ordering[C]): ord.OrderingOps = ord.mkOrderingOps(c) 
  def checkAlarm(now: Instant, state: AlarmState): CheckResult = {
    import state.alarm._
    //first check is the alarm is due
    if (now >= state.nextOccurrence) {
      if (state.state == AlarmState.Showing) {
        //the only automatic thing to happen here is closing the dialog. So check that
        cancelIfLate.map { lateDefinition =>
          if (Duration.between(state.nextOccurrence, now) > lateDefinition.duration && lateDefinition.autoCloseWindowAfterThisTime)
            AutoCloseAlarmNotification(advanceAlarm(state))
          else KeepState
        } getOrElse KeepState
      } else { //if we are late and not showing:
        cancelIfLate.fold[CheckResult] {
          //no definition for cancellation, so show the thing
          NotifyAlarm(state.copy(state = AlarmState.Showing))
        } { duration =>
          if (Duration.between(state.nextOccurrence, now) > duration.duration) //expiry time happened, so skip this occurrence
            StateChanged(advanceAlarm(state))
          else //should still show the alarm
            NotifyAlarm(state.copy(state = AlarmState.Showing))
        }
      }
      
      
      //if it's still not time for the alarm, check for reminders
    } else {
      state.nextReminder.fold[CheckResult] {
        //nothing to remind
        KeepState
      } { t =>
        if (now >= t && (!reminder.get.forFirstOccurrenceOnly || state.reminderInstance.get == 0)) {
          //TODO proper reminder logic
          //calculate next reminder, for that pretend we advance the alarm. This ensures that this reminder wont trigger again
          val next = advanceAlarm(state)
          println(Console.YELLOW + "next reminder " + next.nextReminder + Console.RESET)
          NotifyReminder(state.copy(nextReminder = next.nextReminder, reminderInstance = state.reminderInstance map 1.+))
          
        } else KeepState
      }
    }
  }
  
  def advanceAlarm(state: AlarmState): AlarmState = {
    import state.alarm._
    
    val shouldEnd = end.collect {
      case Left(times) if times <= state.recurrenceInstance + 1 => state.copy(state = AlarmState.Ended)
      case Right(at) if at.atZone(ZoneId.systemDefault).toInstant <= state.nextOccurrence => state.copy(state = AlarmState.Ended)
    }
    
    shouldEnd match
      case Some(value) => return value
      case _ => 
    
    
    val stateWithOccurrenceUpdated = subrepetition match {
      case Some(Alarm.Repetition(every, Left(instances))) if state.subrecurrenceInstance < instances =>
        state.copy(nextOccurrence = state.nextOccurrence.plus(every), subrecurrenceInstance = state.subrecurrenceInstance + 1)
      case Some(Alarm.Repetition(every, Right(until))) if every.multipliedBy(state.subrecurrenceInstance) < until =>
        state.copy(nextOccurrence = state.nextOccurrence.plus(every), subrecurrenceInstance = state.subrecurrenceInstance + 1)
        
        
      case _ =>
        recurrence match {
          case Alarm.NoRecurrence => state.copy(state = AlarmState.Ended)
        
          case Alarm.HourMinutelyRecurrence(h, m) => 
            val next = Iterator.iterate(state.nextOccurrence)(_.plus(h, ChronoUnit.HOURS).plus(m, ChronoUnit.MINUTES)).filterNot(i =>
              exceptionOnDates contains ZonedDateTime.ofInstant(i, ZoneId.systemDefault).toLocalDate).filter(_ > state.nextOccurrence).next
            state.copy(nextOccurrence = next, recurrenceInstance = state.recurrenceInstance + 1, subrecurrenceInstance = 0)
        
          case Alarm.DailyRecurrence(every, onDays) =>
            var next = ZonedDateTime.ofInstant(state.nextOccurrence, ZoneId.systemDefault)
            next = Iterator.iterate(next)(_.plusDays(every)).filter(d => onDays.contains(d.getDayOfWeek) && d.isAfter(next)).filterNot(d =>
              exceptionOnDates contains d.toLocalDate).next
            state.copy(nextOccurrence = next.toInstant, recurrenceInstance = state.recurrenceInstance + 1, subrecurrenceInstance = 0)
        
          case Alarm.WeeklyRecurrence(every, onDays) =>
            var next = ZonedDateTime.ofInstant(state.nextOccurrence, ZoneId.systemDefault)
            val onDaysScala = onDays.asScala
            next = Iterator.iterate(next)(_.plusWeeks(every)).flatMap(week => 
              onDaysScala.iterator.map(d => week.`with`(ChronoField.DAY_OF_WEEK, d.getValue))).filterNot(d =>
              exceptionOnDates contains d.toLocalDate).find(_ `isAfter` next).get
            state.copy(nextOccurrence = next.toInstant, recurrenceInstance = state.recurrenceInstance + 1, subrecurrenceInstance = 0)
        
          case Alarm.MonthlyRecurrence(every, onDayOfMonth) =>
            val next = Iterator.iterate(ZonedDateTime.ofInstant(state.nextOccurrence, ZoneId.systemDefault).plusMonths(every))(_.plusMonths(every)).
            flatMap(calculateDateAtDayOfMonth(_, onDayOfMonth, None)).filterNot(d => exceptionOnDates contains d.toLocalDate).next
            state.copy(nextOccurrence = next.toInstant, recurrenceInstance = state.recurrenceInstance + 1, subrecurrenceInstance = 0)
        
          case Alarm.YearlyRecurrence(every, onDayOfMonth, onMonths, febAction) =>
            var next = ZonedDateTime.ofInstant(state.nextOccurrence, ZoneId.systemDefault)
            val onMonthsScala = onMonths.asScala
        
            next = Iterator.iterate(next)(_.plusYears(1)).flatMap(date =>
              onMonthsScala.iterator.map(m => date.withMonth(m.getValue)).flatMap(calculateDateAtDayOfMonth(_, onDayOfMonth, febAction))
            ).filterNot(d => exceptionOnDates contains d.toLocalDate).find(_ `isAfter` next).get
        
            state.copy(nextOccurrence = next.toInstant, recurrenceInstance = state.recurrenceInstance + 1, subrecurrenceInstance = 0)
        }
    }
    
    //calculate reminder based on new time
    if (stateWithOccurrenceUpdated.state == AlarmState.Ended) {
      stateWithOccurrenceUpdated.copy(nextReminder = None)
    } else {
      reminder.fold(stateWithOccurrenceUpdated) {
        case Alarm.Reminder(duration, inAdvance, true) if state.reminderInstance.get >= 1 => stateWithOccurrenceUpdated
        case Alarm.Reminder(duration, inAdvance, firstOccurrenceOnly) =>
          val reminderTime = if (inAdvance) stateWithOccurrenceUpdated.nextOccurrence.minus(duration) else stateWithOccurrenceUpdated.nextOccurrence.plus(duration)
          stateWithOccurrenceUpdated.copy(nextReminder = Some(reminderTime))
      }
    }
  }
  
  /**
   * uses the passed date as base and try to set it to the given day of month, if the configuration is valid, return a Some, otherwise None.
   */
  private def calculateDateAtDayOfMonth(date: ZonedDateTime, dof: Alarm.DayOfMonth, febAction: Option[Alarm.February29NonLeapAction]): Option[ZonedDateTime] = {
    val yearMonth = YearMonth.of(date.getYear, date.getMonth)
    dof match {
      case Alarm.NthDayOfMonth(-1) => Some(date.withDayOfMonth(yearMonth.atEndOfMonth.getDayOfMonth))
      case Alarm.NthDayOfMonth(29) if date.getMonth == Month.FEBRUARY && !yearMonth.isLeapYear =>
        febAction map {
          case Alarm.February29NonLeapAction.MovePrevDay => date.withDayOfMonth(28)
          case Alarm.February29NonLeapAction.MoveNextDay => date.plusMonths(1).withDayOfMonth(1)
        }
      case Alarm.NthDayOfMonth(d) => if (yearMonth.isValidDay(d)) Some(date.withDayOfMonth(d)) else None
        
      case Alarm.NthWeekDayOfMonth(desiredWeek, dayOfWeek) =>
        val res = date.`with`(TemporalAdjusters.dayOfWeekInMonth(desiredWeek, dayOfWeek))
        if (res.getMonth == date.getMonth) Some(res)
        else None
        
    }
  }
}
