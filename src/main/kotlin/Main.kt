import commands.ForumAdder
import commands.KeepAlive
import config.ConfigData
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.requests.GatewayIntent

object Main {
    val jda: JDA

    init {
        jda = initJda(JDABuilder.createDefault(ConfigData.config.discordToken))
        initSlashCommands()
    }

    private fun initJda(jda: JDABuilder): JDA {
        return jda.addEventListeners(KeepAlive, ForumAdder)
            .enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES)
            .build().awaitReady()
    }

    fun report(update: String) {
        println(update)
        jda.getTextChannelById(ConfigData.config.updateChannelId)?.sendMessage(update)?.queue()
    }

    private fun initSlashCommands() {
        jda.getGuildById(ConfigData.config.testServerId)?.updateCommands()?.addCommands(
            Commands.slash("forumadder", "Adds users to all created forum posts and threads depending on their roles")
                .addSubcommands(
                    SubcommandData("add", "Adds new parings of commands to roles")

                )
        )?.queue()
    }
}

fun main() {
    Main.report("Bot Started!")
}