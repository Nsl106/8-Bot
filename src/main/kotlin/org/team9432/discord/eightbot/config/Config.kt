package org.team9432.discord.eightbot.config

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

object Config {
    private var config: Config? = null

    fun getConfig() = config ?: throw Exception("You must call setFile() before accessing the config!")

    suspend fun setFile(filePath: String) = coroutineScope {
        launch(Dispatchers.IO) {
            val fileText = File(filePath).readText()
            config = Json.decodeFromString<Config>(fileText)
        }
    }

    @Serializable
    data class Config(
        val discord: DiscordConfig,
        val mongodb: MongoDbConfig,
        val googleCalendar: GoogleCalendarConfig,
    )

    @Serializable
    data class DiscordConfig(
        val token: String,
        val updateChannelId: Long,
        val testServerId: Long,
    )

    @Serializable
    data class MongoDbConfig(
        val url: String,
        val databaseName: String,
    )

    @Serializable
    data class GoogleCalendarConfig(
        val apiKey: String,
    )
}