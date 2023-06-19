package commands

import Main
import data.DataFile
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.channel.ChannelType.*
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import net.dv8tion.jda.api.entities.channel.unions.IThreadContainerUnion
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu.SelectTarget

object ForumAdder : ListenerAdapter() {
    private val channelToRoles = DataFile("channelToRoles", javaClass.simpleName)
    private val rolesToChannels = DataFile("rolesToChannels", javaClass.simpleName)
    private val threadToMessage = DataFile("threadToMessage", javaClass.simpleName)

    private var selectedRoleIds: List<String> = mutableListOf()
    private var selectedChannelIds: List<String> = mutableListOf()

    private fun saveToFile() {
        val outputMessage = StringBuilder()
        outputMessage.appendLine("Updated forum adder config")
        outputMessage.appendLine("Mapped these channels:")

        for (channelId in selectedChannelIds) {
            channelToRoles.set(channelId, *selectedRoleIds.toTypedArray())
            outputMessage.appendLine("<#$channelId>")
        }

        outputMessage.appendLine("To these roles:")

        for (roleId in selectedRoleIds) {
            rolesToChannels.set(roleId, *selectedChannelIds.toTypedArray())
            outputMessage.appendLine("<@&$roleId>")
        }

        Main.report(outputMessage.toString())
    }

    override fun onChannelCreate(event: ChannelCreateEvent) {
        // Ensure the channel is a new thread or forum post
        val thread = event.channel.let { if (it.type.isThread) it.asThreadChannel() else return }

        // Collect all the roles that need to be added to the new thread, return if none are found
        val parentCategoryId = thread.parentChannel.categoryId
        val parentChannelId = thread.parentChannel.id

        val roleIds = if (channelToRoles.containsKey(parentChannelId)) {
            channelToRoles[parentChannelId] ?: return
        } else if (parentCategoryId != null && channelToRoles.containsKey(parentCategoryId)) {
            channelToRoles[parentCategoryId] ?: return
        } else return

        // Send a message in the thread then edit it to include the mentions
        val mentionsText = StringBuilder()
        for (id in roleIds) mentionsText.append("<@&$id> ")

        thread.sendMessage("Adding Users!").queue { msg ->
            threadToMessage.set(thread.id, msg.id)

            msg.editMessage(mentionsText.toString()).queue { msg2 ->
                msg2.editMessage(
                    "Added Users!"
                ).queue()
            }
        }
    }

    override fun onGuildMemberRoleAdd(event: GuildMemberRoleAddEvent) {
        for (role in event.roles) {
            val channels = (rolesToChannels[role.id] ?: continue).toMutableList()

            val iterator = channels.listIterator(channels.size)

            val threads = mutableListOf<ThreadChannel>()

            while (iterator.hasPrevious()) {
                val id = iterator.previous()

                when (Main.jda.getGuildChannelById(id)?.type) {
                    TEXT -> {
                        val textChannel = Main.jda.getTextChannelById(id) ?: continue
                        threads.addAll(textChannel.threadChannels)
                    }

                    FORUM -> {
                        val forumChannel = Main.jda.getForumChannelById(id) ?: continue
                        threads.addAll(forumChannel.threadChannels)
                    }

                    CATEGORY -> {
                        iterator.remove()
                        Main.jda.getCategoryById(id)?.channels?.map { it.id }?.forEach(iterator::add)
                    }

                    else -> continue
                }
            }

            for (thread in threads) {
                val messageId = threadToMessage[thread.id]?.get(0) ?: continue

                thread.editMessageById(messageId, event.user.asMention).queue {
                    it.editMessage("Added Users!").queue()
                }
            }
        }
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != "forumadder") return

        when (event.subcommandName) {
            "add" -> {
                event.reply("Configuring forum adding command!").mentionRepliedUser(false).setComponents(
                    ActionRow.of(
                        EntitySelectMenu.create("roles", SelectTarget.ROLE).setMaxValues(25)
                            .setPlaceholder("Select the roles!").build()
                    ), ActionRow.of(
                        EntitySelectMenu.create("channels", SelectTarget.CHANNEL).setChannelTypes(CATEGORY, FORUM, TEXT)
                            .setMaxValues(25).setPlaceholder("Select the channels!").build()
                    ), ActionRow.of(Button.danger("exit", "Exit"))
                ).queue()
            }
        }
    }

    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (event.message.contentDisplay == "!resetFA") {
            event.message.member?.hasPermission(Permission.ADMINISTRATOR).run {
                channelToRoles.getAllKeys().forEach(channelToRoles::remove)
                rolesToChannels.getAllKeys().forEach(channelToRoles::remove)
                threadToMessage.getAllKeys().forEach(channelToRoles::remove)
            }
        }
    }

    override fun onEntitySelectInteraction(event: EntitySelectInteractionEvent) {
        event.deferEdit().queue()

        when (event.componentId) {
            "roles" -> selectedRoleIds = event.mentions.roles.map { it.id }
            "channels" -> selectedChannelIds = event.mentions.channels.map { it.id }
        }

        if (selectedChannelIds.isNotEmpty() && selectedRoleIds.isNotEmpty()) {
            val components = event.message.components.toMutableList()
            components.removeLast()
            components.add(ActionRow.of(Button.danger("exit", "Exit"), Button.success("save", "Confirm")))
            event.message.editMessageComponents(components).queue()
        }
    }

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        when (event.componentId) {
            "exit" -> event.editMessage("Canceled!").setComponents().queue()
            "save" -> {
                saveToFile()
                selectedRoleIds = mutableListOf()
                selectedChannelIds = mutableListOf()
                event.editMessage("Operation Completed!").setComponents().queue()
            }
        }
    }

    private val IThreadContainerUnion.categoryId
        get() = when (type) {
            TEXT -> asTextChannel().parentCategoryId
            NEWS -> asNewsChannel().parentCategoryId
            FORUM -> asForumChannel().parentCategoryId
            PRIVATE, UNKNOWN, GROUP, CATEGORY, VOICE, STAGE, GUILD_PUBLIC_THREAD, GUILD_NEWS_THREAD, GUILD_PRIVATE_THREAD -> null
        }
}