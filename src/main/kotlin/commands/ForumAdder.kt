package commands

import CommandBase
import Main
import data.*
import dev.minn.jda.ktx.interactions.commands.Command
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.commands.subcommand
import dev.minn.jda.ktx.messages.reply_
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.Channel
import net.dv8tion.jda.api.entities.channel.ChannelType.*
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import net.dv8tion.jda.api.entities.channel.unions.IThreadContainerUnion
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu.SelectTarget
import util.Menu

object ForumAdder : CommandBase {
    private val data = JsonFile(name, name)

    override fun commandData() =
        Command(name, "Adds users to all created forum posts and threads depending on their roles") {
            subcommand("add", "Adds new parings of channels to roles")
            subcommand("sneak", "Silently adds a role to the channel this command is used in") {
                option<Role>("role", "The role to add", required = true)
            }
            subcommand("list", "Lists all forumadder mappings for this server")
            subcommand("remove", "Removes all forumadder mappings for a given channel") {
                option<Channel>("channel", "The channel to unmap", required = true)
            }

            isGuildOnly = true
            defaultPermissions = DefaultMemberPermissions.DISABLED
        }

    override suspend fun onChannelCreate(event: ChannelCreateEvent) {
        // Ensure the channel is a new thread or forum post
        val thread = event.channel.let { if (it.type.isThread) it.asThreadChannel() else return }

        // Collect all the roles that need to be added to the new thread, return if none are found
        val parentCategoryId = thread.parentChannel.categoryId
        val parentChannelId = thread.parentChannel.id

        val channelToRoles = data.mainObj.getObjectOrNew("channelToRoles")

        val roleIds = if (channelToRoles.containsKey(parentChannelId)) {
            channelToRoles.getArrayOrNew(parentChannelId)
        } else if (parentCategoryId != null && channelToRoles.containsKey(parentCategoryId)) {
            channelToRoles.getArrayOrNew(parentCategoryId)
        } else return

        // Send a message in the thread then edit it to include the mentions
        val mentionsText = StringBuilder()
        for (id in roleIds) mentionsText.append("<@&${id.asString}> ")

        val threadToMessage = data.mainObj.getObjectOrNew("threadToMessage")
        thread.sendMessage("Adding Users!").queue { msg ->
            threadToMessage.setValue(thread.id, msg.id)
            data.save()
            msg.editMessage(mentionsText.toString()).queue { msg2 ->
                msg2.editMessage(
                    "Added Users!"
                ).queue()
            }
        }
    }

    override suspend fun onRoleAdd(event: GuildMemberRoleAddEvent) {
        val channelToRoles = data.mainObj.getObjectOrNew("channelToRoles")
        val threadToMessage = data.mainObj.getObjectOrNew("threadToMessage")

        for (role in event.roles) {
            val channels = channelToRoles.asArrayMap().filterValues { it!!.contains(role.id) }.keys.toMutableList()

            val iterator = channels.listIterator(channels.size)

            val threads = mutableListOf<ThreadChannel>()

            while (iterator.hasPrevious()) {
                val id = iterator.previous()

                when (Main.jda.getGuildChannelById(id)?.type) {
                    TEXT -> Main.jda.getTextChannelById(id)?.let { threads.addAll(it.threadChannels) }
                    FORUM -> Main.jda.getForumChannelById(id)?.let { threads.addAll(it.threadChannels) }

                    CATEGORY -> {
                        iterator.remove()
                        Main.jda.getCategoryById(id)?.channels?.map { it.id }?.forEach(iterator::add)
                    }

                    else -> continue
                }
            }

            for (thread in threads) {
                val messageId = threadToMessage.getValue(thread.id) ?: continue

                thread.editMessageById(messageId, event.user.asMention).queue {
                    it.editMessage("Added Users!").queue()
                }
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

    private fun handleRemove(event: SlashCommandInteractionEvent) {
        val channelToRoles = data.mainObj.getObjectOrNew("channelToRoles")
        event.getOption("channel")?.asChannel?.id.let {
            channelToRoles.remove(it)
            data.save()
            event.reply_("Operation Completed!", ephemeral = true).queue()
        }
    }

    private fun handleList(event: SlashCommandInteractionEvent) {
        val channelToRoles = data.mainObj.getObjectOrNew("channelToRoles")

        val guildChannels = event.guild!!.channels.map { it.id }

        val mappings = channelToRoles.asMap().filter { guildChannels.contains(it.key) }
        val reply = StringBuilder()
        mappings.forEach {
            val channel = "<#${it.key}>"
            val roleIds = it.value.asJsonArray.asList().map { j -> j.asString }
            val roles = StringBuilder().apply { for (id in roleIds) append("<@&$id> ") }.toString()

            reply.appendLine("$channel to $roles")
        }
        if (reply.isEmpty()) reply.appendLine("No mappings are set in this server!")
        event.reply_(reply.toString(), ephemeral = true).queue()
    }

    private fun handleSneak(event: SlashCommandInteractionEvent) {
        val role = event.getOption("role")?.asRole?.asMention
        event.reply_("Adding users with this role: $role", ephemeral = true).queue()
    }

    private suspend fun handleAdd(event: SlashCommandInteractionEvent) {
        val result = Menu(
            message = "Please select a channel and the roles to be added to it",
            interaction = event,
            id = "forumadderadd"
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


        val channels = result.component1()
        val roles = result.component2()

        val channelToRoles = data.mainObj.getObjectOrNew("channelToRoles")
        for (id in channels) channelToRoles.setArray(id, roles)

        data.save()
    }
}


private val IThreadContainerUnion.categoryId
    get() = when (type) {
        TEXT -> asTextChannel().parentCategoryId
        NEWS -> asNewsChannel().parentCategoryId
        FORUM -> asForumChannel().parentCategoryId
        PRIVATE, UNKNOWN, GROUP, CATEGORY, VOICE, STAGE, GUILD_PUBLIC_THREAD, GUILD_NEWS_THREAD, GUILD_PRIVATE_THREAD -> null
    }