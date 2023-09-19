import commands.*
import config.ConfigData
import dev.minn.jda.ktx.jdabuilder.default
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.MemberCachePolicy


object Main {
    val jda: JDA

    init {
        CommandExecutor.addCommands(Ping, CalendarSync, Authenticate, ForumAdder, KeepAlive, Misc, Profile, RoleChooser)

        jda = default(
            ConfigData.config.discordToken,
            enableCoroutines = true,
        ) {
            enableIntents(listOf(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES))
            addEventListeners(CommandExecutor)
            setMemberCachePolicy(MemberCachePolicy.ALL)
        }.awaitReady()

        initSlashCommands()
    }

    fun report(update: Any?) {
        val msg = update.toString()
        println(msg)
        jda.getTextChannelById(ConfigData.config.updateChannelId)?.sendMessage(msg)?.queue()
    }

    private fun initSlashCommands() {
        jda.updateCommands().addCommands(CommandExecutor.commandData).queue()
    }

    var isLocal = true
}

fun main() {
    Main.jda
    val x = System.getProperty("os.name")
    Main.isLocal = x.equals("Windows 11")
}