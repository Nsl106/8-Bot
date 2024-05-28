package org.team9432.discord.eightbot.commands

import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOneModel
import com.mongodb.client.model.ReplaceOptions
import dev.minn.jda.ktx.generics.getChannel
import dev.minn.jda.ktx.interactions.commands.Command
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.commands.subcommand
import dev.minn.jda.ktx.messages.Embed
import dev.minn.jda.ktx.messages.reply_
import kotlinx.coroutines.flow.toList
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.Channel
import net.dv8tion.jda.api.entities.channel.ChannelType.*
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import net.dv8tion.jda.api.entities.channel.unions.IThreadContainerUnion
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu.SelectTarget
import org.bson.codecs.pojo.annotations.BsonId
import org.team9432.discord.eightbot.CommandBase
import org.team9432.discord.eightbot.database.Database
import org.team9432.discord.eightbot.jda
import org.team9432.discord.eightbot.util.Menu
import java.time.Instant

object AutoAdd: CommandBase {
    data class AutoAddData(@BsonId val containerId: String, val guildId: String, val roleIds: List<String>)

    private val collection = Database.getCollection<AutoAddData>("auto-add")

    override fun commandData() =
        Command(name, "Adds users to all created forum posts and threads depending on their roles") {
            subcommand("add", "Adds new parings of channels to roles")
            subcommand("sneak", "Silently adds a role to the channel this command is used in") {
                option<Role>("role", "The role to add", required = true)
            }
            subcommand("list", "Lists all channels and roles for this server")
            subcommand("remove", "Removes all roles for a given channel") {
                option<Channel>("channel", "The channel to unmap", required = true)
            }

            isGuildOnly = true
            defaultPermissions = DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)
        }

    override suspend fun onChannelCreate(event: ChannelCreateEvent) {
        // Ensure the channel is a new thread or forum post
        val thread = event.channel as? ThreadChannel ?: return

        // Collect all the roles that need to be added to the new thread, return if none are found
        val parentCategoryId = thread.parentChannel.categoryId
        val parentChannelId = thread.parentChannel.id

        val filter = Filters.or(
            Filters.eq(parentChannelId),
            Filters.eq(parentCategoryId),
        )

        val roleIds = collection.find(filter).toList().flatMap { it.roleIds }

        if (roleIds.isEmpty()) return

        // Send a message in the thread then edit it to include the mentions
        val mentionsText = StringBuilder()
        for (roleId in roleIds) mentionsText.append("<@&$roleId> ")

        thread.sendMessage("Adding Users!").queue { message ->
            message.editMessage(mentionsText.toString()).queue { editedMessage ->
                editedMessage.editMessage("Added Users!").queue()
            }
        }
    }

    override suspend fun onSlashCommand(event: SlashCommandInteractionEvent) {
        when (event.subcommandName) {
            "add" -> handleAdd(event)
            "sneak" -> handleSneak(event)
            "list" -> handleList(event)
            "remove" -> handleRemove(event)
        }
    }

    private suspend fun handleRemove(event: SlashCommandInteractionEvent) {
        val channel = event.getOption("channel")!!.asChannel

        val valueChanged = collection.deleteOne(Filters.eq(channel.id)).deletedCount != 0L

        if (valueChanged) {
            event.reply_("Operation Completed!", ephemeral = true).queue()
        } else {
            event.reply_("This channel was not associated with any roles!", ephemeral = true).queue()
        }
    }

    private suspend fun handleList(event: SlashCommandInteractionEvent) {
        val guildData = collection.find(Filters.eq(AutoAddData::guildId.name, event.guild!!.id)).toList()

        if (guildData.isNotEmpty()) {
            val embed = Embed {
                title = "Channels"

                guildData.forEach { data ->
                    val channel = jda.getChannel(data.containerId)
                    val channelText = if (channel?.type == CATEGORY) channel.name else "#${channel?.name}"
                    val roleText = data.roleIds.joinToString(" ") { "<@&$it>" }

                    field {
                        name = channelText
                        inline = true
                        value = roleText
                    }
                }

                color = 0x22462c
                timestamp = Instant.now()
            }

            event.reply_(embeds = listOf(embed), ephemeral = true).queue()
        } else {
            event.reply_("No channels are set in this server!", ephemeral = true).queue()
        }
    }

    private fun handleSneak(event: SlashCommandInteractionEvent) {
        val roleMention = event.getOption("role")?.asRole?.asMention
        event.reply_("Adding users with this role: $roleMention", ephemeral = true).queue()
    }

    private suspend fun handleAdd(event: SlashCommandInteractionEvent) {
        val (channels, roles) = Menu(
            message = "Please select a channel and the roles to be added to it",
            interaction = event,
            id = "autoadd-add"
        ).addEntityMenu(
            name = "channel",
            types = listOf(SelectTarget.CHANNEL),
            placeholder = "Select a channel",
            channelTypes = listOf(CATEGORY, FORUM, TEXT)
        ).addEntityMenu(
            name = "roles",
            types = listOf(SelectTarget.ROLE),
            placeholder = "Select roles",
            valueRange = 1..25,
        ).execute() ?: return

        val guildId = event.guild!!.id

        val data = channels.map { channelId ->
            ReplaceOneModel(
                Filters.eq(channelId),
                AutoAddData(channelId, guildId, roles),
                ReplaceOptions().upsert(true)
            )
        }

        collection.bulkWrite(data)
    }
}

private val IThreadContainerUnion.categoryId
    get() = if (this is ICategorizableChannel) parentCategoryId else null