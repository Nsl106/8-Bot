package org.team9432.discord.eightbot.apis.google

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.team9432.discord.eightbot.config.Config
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*


object CalendarApi {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                this.explicitNulls = false
            })
        }
    }

    suspend fun testId(calendarId: String): Boolean {
        val url = URLBuilder(
            protocol = URLProtocol.HTTPS,
            host = "www.googleapis.com",
            pathSegments = listOf("calendar", "v3", "calendars", calendarId, "events"),
            parameters = Parameters.build {
                append("key", Config.getConfig().googleCalendar.apiKey)
                append("timeMin", "2024-01-01T00:00:00-00:00")
                append("timeMax", "2024-01-01T00:00:00-01:00")
            }
        ).build()

        val result = client.get(url)
        println(result.bodyAsText())
        return result.status.value == 200
    }

    suspend fun getEvents(calendarId: String, daysInAdvance: Long = 14, important: Boolean = false): CalendarEventsList? {
        val url = URLBuilder(
            protocol = URLProtocol.HTTPS,
            host = "www.googleapis.com",
            pathSegments = listOf("calendar", "v3", "calendars", calendarId, "events"),
            parameters = Parameters.build {
                append("key", Config.getConfig().googleCalendar.apiKey)
                append("timeMin", Instant.now().toRFC3339())
                append("timeMax", Instant.now().plus(daysInAdvance, ChronoUnit.DAYS).toRFC3339())
                append("singleEvents", "true")
                append("orderBy", "startTime")
                if (important) {
                    append("q", "important")
                }
            }
        ).build()

        val result = client.get(url)
        return if (result.status.value != 200) null else result.body()
    }

    private val rfc3339Format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX")
    private val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd")
    private fun Instant.toRFC3339() = rfc3339Format.format(Date.from(this))
    private fun String.fromRFC3339() = rfc3339Format.parse(this)

    @Serializable
    data class CalendarEventsList(val items: List<CalendarEvent>)

    @Serializable
    data class CalendarEvent(val summary: String = "Unnamed Event", val description: String = "", val htmlLink: String, val start: CalendarTime, val end: CalendarTime) {
        val isAllDayEvent = start.dateTime == null && start.day != null && end.dateTime == null && end.day != null
    }

    @Serializable
    data class CalendarTime(val dateTime: String?, @SerialName("date") val day: String?) {
        val date: Date
            get() {
                return when {
                    dateTime != null -> dateTime.fromRFC3339()
                    day != null -> simpleDateFormat.parse(day)
                    else -> Date.from(Instant.ofEpochMilli(0))
                }
            }
    }
}
