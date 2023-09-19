package apis.google

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.Calendar

object Calendar {
    private const val applicationName = "8-Bot Calendar"
    private val jsonFactory: JsonFactory = GsonFactory.getDefaultInstance()

    fun getCalender(credentials: Credential): Calendar {
        val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
        return Calendar.Builder(httpTransport, jsonFactory, credentials).setApplicationName(applicationName).build()
    }

    val calendarColors = mapOf(
        1 to "Lavender",
        2 to "Sage",
        3 to "Grape",
        4 to "Flamingo",
        5 to "Banana",
        6 to "Tangerine",
        7 to "Peacock",
        8 to "Graphite",
        9 to "Blueberry",
        10 to "Basil",
        11 to "Tomato",
    )
}