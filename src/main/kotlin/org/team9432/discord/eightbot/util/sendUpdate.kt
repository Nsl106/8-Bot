package org.team9432.discord.eightbot.util

import org.team9432.discord.eightbot.config.Config
import org.team9432.discord.eightbot.jda

fun sendUpdate(update: Any?) {
    val msg = update.toString()
    println(msg)
    jda.getTextChannelById(Config.getConfig().discord.updateChannelId)?.sendMessage(msg)?.queue()
}