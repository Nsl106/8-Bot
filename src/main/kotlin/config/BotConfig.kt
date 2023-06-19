package config

data class BotConfig(
    val discordToken: String,
    val updateChannelId: Long,
    val testServerId: Long,
)
