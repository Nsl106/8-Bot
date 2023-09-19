package commands

import CommandBase
import dev.minn.jda.ktx.interactions.commands.Command
import dev.minn.jda.ktx.messages.reply_
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent


object Ping : CommandBase {
    override fun commandData() = Command(name, "ping")

    override suspend fun onMessage(event: MessageReceivedEvent) {
        if (event.message.contentDisplay == "!ping") {
            event.message.addReaction(Emoji.fromUnicode("U+1F3D3")).queue()
        }
    }

    override suspend fun onSlashCommand(event: SlashCommandInteractionEvent) {
        event.reply_(Emoji.fromUnicode("U+1F3D3").asReactionCode).queue()
    }
}