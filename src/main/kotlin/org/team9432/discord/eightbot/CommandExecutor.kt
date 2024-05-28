package org.team9432.discord.eightbot

import dev.minn.jda.ktx.events.CoroutineEventListener
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateArchivedEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import net.dv8tion.jda.api.events.message.MessageDeleteEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData

object CommandExecutor: CoroutineEventListener {
    private val registeredCommands = mutableMapOf<String, CommandBase>()

    override suspend fun onEvent(event: GenericEvent) {
        when (event) {
            is SlashCommandInteractionEvent -> registeredCommands[event.name]?.onSlashCommand(event)
            is ButtonInteractionEvent -> registeredCommands[event.componentId.substringBefore(':')]?.onButton(event)
            is StringSelectInteractionEvent -> registeredCommands[event.componentId.substringBefore(':')]?.onStringSelectMenu(event)
            is EntitySelectInteractionEvent -> registeredCommands[event.componentId.substringBefore(':')]?.onEntitySelectMenu(event)
            is MessageReceivedEvent -> registeredCommands.forEach { it.value.onMessage(event) }
            is ReadyEvent -> registeredCommands.forEach { it.value.onReady(event) }
            is ChannelUpdateArchivedEvent -> registeredCommands.forEach { it.value.onThreadUpdateArchived(event) }
            is ChannelCreateEvent -> registeredCommands.forEach { it.value.onChannelCreate(event) }
            is ChannelDeleteEvent -> registeredCommands.forEach { it.value.onChannelDelete(event) }
            is GuildMemberRoleAddEvent -> registeredCommands.forEach { it.value.onRoleAdd(event) }
            is MessageDeleteEvent -> registeredCommands.forEach { it.value.onMessageDelete(event) }
        }
    }

    fun registeredCommands(vararg commands: CommandBase) {
        commands.forEach { this.registeredCommands[it.name] = it }
    }

    val commandData get() = commands.mapNotNull { it.commandData() }
    val commands get() = registeredCommands.values.toList()
}

interface CommandBase {
    val name: String get() = javaClass.simpleName.lowercase()

    fun commandData(): SlashCommandData? = null

    suspend fun onSlashCommand(event: SlashCommandInteractionEvent) {}
    suspend fun onButton(event: ButtonInteractionEvent) {}
    suspend fun onStringSelectMenu(event: StringSelectInteractionEvent) {}
    suspend fun onEntitySelectMenu(event: EntitySelectInteractionEvent) {}
    suspend fun onMessage(event: MessageReceivedEvent) {}
    suspend fun onReady(event: ReadyEvent) {}
    suspend fun onThreadUpdateArchived(event: ChannelUpdateArchivedEvent) {}
    suspend fun onChannelCreate(event: ChannelCreateEvent) {}
    suspend fun onChannelDelete(event: ChannelDeleteEvent) {}
    suspend fun onRoleAdd(event: GuildMemberRoleAddEvent) {}
    suspend fun onMessageDelete(event: MessageDeleteEvent) {}

    suspend fun loop() {}
}