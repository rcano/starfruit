package starfruit

import language.implicitConversions

import java.time._
import java.time.temporal.{ChronoField, ChronoUnit, TemporalAdjusters}
import scala.collection.JavaConverters._

case class AlarmState(alarm: Alarm, state: AlarmState.State, started: Instant, nextOcurrence: Instant, recurrenceInstance: Int,
                      nextReminder: Option[Instant], reminderInstance: Option[Int])
object AlarmState {
  def apply(alarm: Alarm, state: AlarmState.State, now: Instant): AlarmState = {
    val firstOccurrence = alarm.when match {
      case Alarm.AtTime(date, time) => ZonedDateTime.of(date, time.getOrElse(LocalTime.MIDNIGHT), ZoneId.systemDefault).toInstant
      case Alarm.TimeFromNow(hours, minutes) => now.plus(hours, ChronoUnit.HOURS).plus(minutes, ChronoUnit.MINUTES)
    }
    val reminder = alarm.reminder map { case Alarm.Reminder(duration, inAdvance, firstOccurrenceOnly) =>
      if (inAdvance) firstOccurrence.minus(duration) else firstOccurrence.plus(duration)
    }
    AlarmState(alarm, state, now, firstOccurrence, 0, reminder, reminder.map(_ => 0))
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
  
  private implicit def comparableOrderingOps[C <: java.lang.Comparable[C]](c: C)(implicit ord: Ordering[C]) = ord.mkOrderingOps(c) 
  def checkAlarm(now: Instant, state: AlarmState): CheckResult = {
    import state.alarm._
    //first check is the alarm is due
    if (now >= state.nextOcurrence) {
      if (state.state == AlarmState.Showing) {
        //the only automatic thing to happen here is closing the dialog. So check that
        cancelIfLate.map { lateDefinition =>
          if (Duration.between(state.nextOcurrence, now) > lateDefinition.duration && lateDefinition.autoCloseWindowAfterThisTime)
            AutoCloseAlarmNotification(advanceAlarm(state))
          else KeepState
        } getOrElse KeepState
      } else { //if we are late and not showing:
        cancelIfLate.fold[CheckResult] {
          //no definition for cancellation, so show the thing
          NotifyAlarm(state.copy(state = AlarmState.Showing))
        } { duration =>
          if (Duration.between(state.nextOcurrence, now) > duration.duration) //expiry time happened, so skip this occurrence
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
        if (now >= t && (!reminder.get.forFirstOcurrenceOnly || state.reminderInstance.get == 0)) {
          //TODO proper reminder logic
          //calculate next reminder, for that pretend we advance the alarm. This ensures that this reminder wont trigger again
          val next = advanceAlarm(state)
          NotifyReminder(state.copy(nextReminder = next.nextReminder, reminderInstance = state.reminderInstance map 1.+))
          
        } else KeepState
      }
    }
  }
  
  def advanceAlarm(state: AlarmState): AlarmState = {
    import state.alarm._
    
    val shouldEnd = end.collect {
      case Left(times) if times <= state.recurrenceInstance + 1 => state.copy(state = AlarmState.Ended)
      case Right(at) if at.atZone(ZoneId.systemDefault).toInstant <= state.nextOcurrence=> state.copy(state = AlarmState.Ended)
    }
    
    shouldEnd foreach (return _)
    
    val stateWithOcurrenceUpdated = recurrence match {
      case Alarm.NoRecurrence => state.copy(state = AlarmState.Ended)
        
      case Alarm.HourMinutelyRecurrence(h, m) => 
        val next = Iterator.iterate(state.nextOcurrence)(_.plus(h, ChronoUnit.HOURS).plus(m, ChronoUnit.MINUTES)).filterNot(i =>
          exceptionOnDates contains ZonedDateTime.ofInstant(i, ZoneId.systemDefault).toLocalDate).next
        state.copy(nextOcurrence = next, recurrenceInstance = state.recurrenceInstance + 1)
        
      case Alarm.DailyRecurrence(every, onDays) =>
        var next = ZonedDateTime.ofInstant(state.nextOcurrence, ZoneId.systemDefault)
        next = Iterator.iterate(next)(_.plusDays(every)).filter(d => onDays.contains(d.getDayOfWeek) && d.isAfter(next)).filterNot(d =>
          exceptionOnDates contains d.toLocalDate).next
        state.copy(nextOcurrence = next.toInstant, recurrenceInstance = state.recurrenceInstance + 1)
        
      case Alarm.WeeklyRecurrence(every, onDays) =>
        var next = ZonedDateTime.ofInstant(state.nextOcurrence, ZoneId.systemDefault)
        val onDaysScala = onDays.asScala
        next = Iterator.iterate(next)(_.plusWeeks(every)).flatMap(week => 
          onDaysScala.iterator.map(d => week.`with`(ChronoField.DAY_OF_WEEK, d.getValue))).filterNot(d =>
          exceptionOnDates contains d.toLocalDate).find(_ isAfter next).get
        state.copy(nextOcurrence = next.toInstant, recurrenceInstance = state.recurrenceInstance + 1)
        
      case Alarm.MonthlyRecurrence(every, onDayOfMonth) =>
        val next = Iterator.iterate(ZonedDateTime.ofInstant(state.nextOcurrence, ZoneId.systemDefault).plusMonths(every))(_.plusMonths(every)).
          flatMap(calculateDateAtDayOfMonth(_, onDayOfMonth, None)).filterNot(d => exceptionOnDates contains d.toLocalDate).next
        state.copy(nextOcurrence = next.toInstant, recurrenceInstance = state.recurrenceInstance + 1)
        
      case Alarm.YearlyRecurrence(every, onDayOfMonth, onMonths, febAction) =>
        var next = ZonedDateTime.ofInstant(state.nextOcurrence, ZoneId.systemDefault)
        val onMonthsScala = onMonths.asScala
        
        next = Iterator.iterate(next)(_.plusYears(1)).flatMap(date =>
          onMonthsScala.iterator.map(m => date.withMonth(m.getValue)).flatMap(calculateDateAtDayOfMonth(_, onDayOfMonth, febAction))
        ).filterNot(d => exceptionOnDates contains d.toLocalDate).find(_ isAfter next).get
        
        state.copy(nextOcurrence = next.toInstant, recurrenceInstance = state.recurrenceInstance + 1)
    }
    //calculate reminder based on new time
    reminder.fold(stateWithOcurrenceUpdated) {
      case Alarm.Reminder(duration, inAdvance, true) if state.reminderInstance.get >= 1 => stateWithOcurrenceUpdated
      case Alarm.Reminder(duration, inAdvance, firstOccurrenceOnly) =>
        val reminderTime = if (inAdvance) stateWithOcurrenceUpdated.nextOcurrence.minus(duration) else stateWithOcurrenceUpdated.nextOcurrence.plus(duration)
        stateWithOcurrenceUpdated.copy(nextReminder = Some(reminderTime))
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
