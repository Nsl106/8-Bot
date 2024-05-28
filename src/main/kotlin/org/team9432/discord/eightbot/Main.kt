package org.team9432.discord.eightbot

import dev.minn.jda.ktx.events.CoroutineEventManager
import dev.minn.jda.ktx.jdabuilder.default
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.MemberCachePolicy
import org.team9432.discord.eightbot.commands.*
import org.team9432.discord.eightbot.commands.calendar.Calendar
import org.team9432.discord.eightbot.config.Config

lateinit var jda: JDA
    private set

fun main(args: Array<String>): Unit = runBlocking {
    Config.setFile(args.first())

    CommandExecutor.registeredCommands(Ping, Calendar, AutoAdd, KeepAlive, Misc, Profile)

    jda = default(
        Config.getConfig().discord.token,
        enableCoroutines = true,
    ) {
        enableIntents(listOf(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES))
        addEventListeners(CommandExecutor)
        setMemberCachePolicy(MemberCachePolicy.ALL)
        setEventManager(CoroutineEventManager())
    }.awaitReady()

    jda.updateCommands().addCommands(CommandExecutor.commandData).queue()

    CommandExecutor.commands.forEach {
        launch { it.loop() }
    }
}