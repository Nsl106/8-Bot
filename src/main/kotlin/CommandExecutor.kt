import dev.minn.jda.ktx.events.CoroutineEventListener
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent
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

object CommandExecutor : CoroutineEventListener {
    private val commands = mutableMapOf<String, CommandBase>()

    override suspend fun onEvent(event: GenericEvent) {
        when (event) {
            is SlashCommandInteractionEvent -> commands[event.name]?.onSlashCommand(event)
            is ButtonInteractionEvent       -> commands[event.componentId.split(':')[0]]?.onButton(event)
            is StringSelectInteractionEvent -> commands[event.componentId.split(':')[0]]?.onStringSelectMenu(event)
            is EntitySelectInteractionEvent -> commands[event.componentId.split(':')[0]]?.onEntitySelectMenu(event)
            is MessageReceivedEvent         -> commands.forEach { it.value.onMessage(event) }
            is ReadyEvent                   -> commands.forEach { it.value.onReady(event) }
            is ChannelUpdateArchivedEvent   -> commands.forEach { it.value.onThreadUpdateArchived(event) }
            is ChannelCreateEvent           -> commands.forEach { it.value.onChannelCreate(event) }
            is GuildMemberRoleAddEvent      -> commands.forEach { it.value.onRoleAdd(event) }
            is MessageDeleteEvent           -> commands.forEach { it.value.onMessageDelete(event) }
        }
    }

    fun addCommands(vararg commands: CommandBase) {
        commands.forEach { this.commands[it.name] = it }
    }

    val commandData get() = commands.toMap().mapNotNull { it.value.commandData() }
}

interface CommandBase {
    val name: String get() = javaClass.simpleName.lowercase()

    fun commandData(): SlashCommandData?

    suspend fun onSlashCommand(event: SlashCommandInteractionEvent) {}
    suspend fun onButton(event: ButtonInteractionEvent) {}
    suspend fun onStringSelectMenu(event: StringSelectInteractionEvent) {}
    suspend fun onEntitySelectMenu(event: EntitySelectInteractionEvent) {}
    suspend fun onMessage(event: MessageReceivedEvent) {}
    suspend fun onReady(event: ReadyEvent) {}
    suspend fun onThreadUpdateArchived(event: ChannelUpdateArchivedEvent) {}
    suspend fun onChannelCreate(event: ChannelCreateEvent) {}
    suspend fun onRoleAdd(event: GuildMemberRoleAddEvent) {}
    suspend fun onMessageDelete(event: MessageDeleteEvent) {}
}