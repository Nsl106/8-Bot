package org.team9432.discord.eightbot.commands

import dev.minn.jda.ktx.messages.Mentions
import dev.minn.jda.ktx.messages.reply_
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.team9432.discord.eightbot.CommandBase
import org.team9432.discord.eightbot.jda
import kotlin.system.exitProcess

object Misc: CommandBase {
    override suspend fun onMessage(event: MessageReceivedEvent) {
        val message = event.message.contentDisplay
        when {
            message.startsWith("!react") -> {
                val emoji = Emoji.fromFormatted(event.message.contentRaw.split(" ")[1])
                event.message.referencedMessage?.addReaction(emoji)?.queue()
            }

            message.startsWith("!disconnect") && event.message.author.idLong == 837144554871193632L -> {
                event.message.reply_("shutting down", mentions = Mentions.of()).queue()
                jda.shutdownNow()
                exitProcess(0)
            }
        }
    }
}