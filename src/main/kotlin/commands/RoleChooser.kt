package commands

import CommandBase
import Main.jda
import data.*
import dev.minn.jda.ktx.events.CoroutineEventListener
import dev.minn.jda.ktx.events.onButton
import dev.minn.jda.ktx.events.onStringSelect
import dev.minn.jda.ktx.interactions.commands.Command
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.commands.subcommand
import dev.minn.jda.ktx.interactions.components.SelectOption
import dev.minn.jda.ktx.interactions.components.StringSelectMenu
import dev.minn.jda.ktx.interactions.components.danger
import dev.minn.jda.ktx.interactions.components.row
import dev.minn.jda.ktx.messages.send
import net.dv8tion.jda.api.entities.UserSnowflake
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu
import util.Menu

object RoleChooser : CommandBase {
    private val data = JsonFile(name, name)
    override fun commandData() = Command(name, "Creates a reaction role") {
        subcommand("create", "Creates a reaction role") {
            option<String>("message", "The text to send along with the role menu", required = true)
        }

        isGuildOnly = true
        defaultPermissions = DefaultMemberPermissions.DISABLED
    }

    override suspend fun onSlashCommand(event: SlashCommandInteractionEvent) {
        when (event.subcommandName) {
            "create" -> handleCreate(event)
        }
    }

    private suspend fun handleCreate(event: SlashCommandInteractionEvent) {
        val result = Menu(
            message = "Please select roles",
            interaction = event,
            id = "rolechooseradd"
        ).addEntityMenu(
            name = "roles",
            types = listOf(EntitySelectMenu.SelectTarget.ROLE),
            placeholder = "Select roles",
            valueRange = 1..25,
        ).execute() ?: return


        val roles = result.component1()

        val actionRows = mutableListOf<ActionRow>()
        val menu = StringSelectMenu(
            customId = event.id,
            placeholder = "Select Roles!",
            valueRange = 0..25,
            options = roles.map { SelectOption(event.guild!!.getRoleById(it)!!.name, it) }
        )

        val clearButton = danger(event.id + "clearbutton", "Reset")

        actionRows.addAll(listOf(row(menu), row(clearButton)))

        event.channel.send(
            content = event.getOption("message")!!.asString,
            components = actionRows,
        ).queue()

        val guildObj = data.mainObj.getObjectOrNew(event.guild!!.id)
        val listenerIds = guildObj.getArrayOrNew(event.id)
        roles.forEach { listenerIds.add(it) }
        guildObj.setArray(event.id, listenerIds)

        data.save()

        updateListeners()
    }

    private val listeners = mutableListOf<CoroutineEventListener>()

    private fun updateListeners() {
        listeners.forEach { it.cancel() }
        listeners.clear()

        val mainData = data.mainObj
        for (guildId in mainData.keys) {
            val guildData = mainData.getAsJsonObject(guildId)
            for (listenerId in guildData.keys) {
                val roleOptions = guildData.getArray(listenerId)!!.map { it.asString }

                val guild = jda.getGuildById(guildId)!!
                val selectListener = jda.onStringSelect(listenerId) {
                    it.deferEdit().queue()
                    val user = UserSnowflake.fromId(it.user.id)
                    val selectedRoles = it.values
                    for (roleId in selectedRoles) {
                        jda.getRoleById(roleId)?.let { role -> guild.addRoleToMember(user, role).queue() }
                    }
                }
                val clearListener = jda.onButton(listenerId + "clearbutton") {
                    it.deferEdit().queue()
                    val user = UserSnowflake.fromId(it.user.id)
                    for (roleId in roleOptions) {
                        jda.getRoleById(roleId)?.let { role -> guild.removeRoleFromMember(user, role).queue() }
                    }
                }

                listeners.add(selectListener)
                listeners.add(clearListener)
            }
        }
    }

    override suspend fun onReady(event: ReadyEvent) {
        updateListeners()
    }

}