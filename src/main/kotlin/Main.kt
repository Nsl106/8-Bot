import commands.ForumAdder
import commands.KeepAlive
import config.ConfigData
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.requests.GatewayIntent

object Main {
    val jda: JDA

    init {
        jda = initJda(JDABuilder.createDefault(ConfigData.getConfig().discordToken))
    }

    private fun initJda(jda: JDABuilder): JDA {
        return jda.addEventListeners(KeepAlive, ForumAdder)
            .enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES)
            .build().awaitReady()
    }

    fun update(update: String) {
        println(update)
        jda.getTextChannelById(ConfigData.getConfig().updateChannelId)?.sendMessage(update)?.queue()
    }
}

fun main() {
    Main.update("Bot Started!")
}