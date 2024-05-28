package org.team9432.discord.eightbot.commands.calendar

import org.team9432.discord.eightbot.apis.google.CalendarApi
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.Calendar

class CalendarTextFormatter {
    private val dateFormat = SimpleDateFormat("EEEE, MMMM d")
    private val simpleDateFormat = SimpleDateFormat("MMMM d")
    private val timeFormat = SimpleDateFormat("h:mm a")

    val today = Date()
    val tomorrow = Date.from(Instant.now().plus(1, ChronoUnit.DAYS))

    private fun getDateText(date: Date, todayText: String = "Today", tomorrowText: String = "Tomorrow", excludeDayName: Boolean = false) =
        if (date.onSameDayAs(today)) todayText
        else if (date.onSameDayAs(tomorrow)) tomorrowText
        else if (excludeDayName) simpleDateFormat.format(date)
        else dateFormat.format(date)

    private fun Date.onSameDayAs(other: Date): Boolean {
        val thisCalendar = Calendar.getInstance().also { it.time = this }
        val otherCalendar = Calendar.getInstance().also { it.time = other }

        return thisCalendar[Calendar.YEAR] == otherCalendar[Calendar.YEAR] && thisCalendar[Calendar.DAY_OF_YEAR] == otherCalendar[Calendar.DAY_OF_YEAR]
    }

    fun getText(event: CalendarApi.CalendarEvent): String {

        val eventStart = event.start.date
        val eventInProgress = eventStart.before(today)
        val eventEnd = if (event.isAllDayEvent) Date.from(event.end.date.toInstant().minus(1, ChronoUnit.MILLIS)) else event.end.date

        val startTime = timeFormat.format(eventStart)
        val endTime = timeFormat.format(eventEnd)

        return if (eventInProgress) {
            if (event.isAllDayEvent) {
                if (eventEnd.onSameDayAs(today)) {
                    "For the rest of the day"
                } else {
                    "Until ${getDateText(eventEnd, tomorrowText = "tomorrow", excludeDayName = true)}"
                }
            } else {
                if (eventStart.onSameDayAs(eventEnd) || eventEnd.onSameDayAs(today)) {
                    "In progress until $endTime"
                } else {
                    "In progress until ${getDateText(eventEnd, tomorrowText = "tomorrow")} at $endTime"
                }
            }
        } else {
            if (event.isAllDayEvent) {
                if (eventStart.onSameDayAs(eventEnd)) {
                    getDateText(eventStart)
                } else {
                    "${getDateText(eventStart)} to ${getDateText(eventEnd, excludeDayName = true)}"
                }
            } else {
                if (eventStart.onSameDayAs(eventEnd)) {
                    "${getDateText(eventStart)} from $startTime to $endTime"
                } else {
                    "${getDateText(eventStart)} at $startTime to ${getDateText(eventEnd, excludeDayName = true)} at $endTime"
                }
            }
        }
    }
}