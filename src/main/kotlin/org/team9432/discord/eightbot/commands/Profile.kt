package org.team9432.discord.eightbot.commands

import dev.minn.jda.ktx.interactions.commands.Command
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.commands.subcommand
import dev.minn.jda.ktx.messages.reply_
import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.Command.Choice
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import org.team9432.discord.eightbot.CommandBase
import org.team9432.discord.eightbot.jda

object Profile: CommandBase {
    override fun commandData() = Command(name, "Changes the bots profile") {
        subcommand("status", "Changes the bots status") {
            option<String>("status", "The new status", required = true) {
                addChoices(OnlineStatus.entries.map { Choice(it.name, it.name) })
            }
        }

        isGuildOnly = true
        defaultPermissions = DefaultMemberPermissions.DISABLED
    }

    private fun handleStatus(event: SlashCommandInteractionEvent) {
        event.reply_("Success!", ephemeral = true).queue()
        jda.presence.setPresence(OnlineStatus.fromKey(event.getOption("status")?.asString), true)
    }

    override suspend fun onSlashCommand(event: SlashCommandInteractionEvent) {
        when (event.subcommandName) {
            "status" -> handleStatus(event)
        }
    }
}