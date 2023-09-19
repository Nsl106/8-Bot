package commands

import CommandBase
import Main
import dev.minn.jda.ktx.interactions.commands.Command
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageDeleteEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu.SelectTarget.*
import net.dv8tion.jda.api.interactions.components.selections.SelectOption
import util.Menu

object Misc : CommandBase {
    override fun commandData(): SlashCommandData? = null

    override suspend fun onMessage(event: MessageReceivedEvent) {
        if (!event.message.contentDisplay.startsWith("!react")) return

        val emoji = Emoji.fromFormatted(event.message.contentRaw.split(" ")[1])
        event.message.referencedMessage?.addReaction(emoji)?.queue()
    }
}