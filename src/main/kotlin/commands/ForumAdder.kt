package commands

import Main
import data.DataFile
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.ChannelType.*
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.entities.channel.unions.IThreadContainerUnion
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent
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
    private val threadToMessage = DataFile("threadToMessage", javaClass.simpleName)

    private var override = false
    private val roleSelection: MutableSet<Role> = mutableSetOf()
    private val channelSelection: MutableSet<GuildChannel> = mutableSetOf()

    private fun saveToFile() {
        val outputMessage = StringBuilder()
        outputMessage.appendLine("Updated forum adder config:")

        channelSelection.forEach { c ->
            val assignedRoleIds = mutableSetOf<String>()
            for (role in roleSelection) {
                assignedRoleIds.add(role.idLong.toString())
            }


            // If not overriding, get the existing roles and add them to the list of new ones
            if (!override) {
                channelToRoles[c.id]?.forEach { assignedRoleIds.add(it) }
            }

            channelToRoles.set(c.id, *assignedRoleIds.toTypedArray())

            // Write to an output message that will be displayed in the update channel
            outputMessage.append("Mapped ${c.asMention} to")
            for (id in assignedRoleIds) {
                outputMessage.append(" <@&$id>")
            }
            outputMessage.appendLine('!')
        }

        Main.update(outputMessage.toString())
    }

    override fun onChannelCreate(event: ChannelCreateEvent) {
        // Ensure the channel is a new thread or forum post
        val thread = event.channel.let { if (it.type.isThread) it.asThreadChannel() else return }

        // Collect all the roles (if any) that need to be added to the new thread
        val parentCategoryId = thread.parentChannel.categoryId
        val parentChannelId = thread.parentChannel.id

        val rawRoleIds = if (channelToRoles.containsKey(parentChannelId)) {
            channelToRoles[parentChannelId] ?: return
        } else if (parentCategoryId != null && channelToRoles.containsKey(parentCategoryId)) {
            channelToRoles[parentCategoryId] ?: return
        } else return


        // Send a message in the thread then edit it to include the mentions
        val mentionsText = StringBuilder()
        for (id in rawRoleIds) mentionsText.append("<@&$id> ")

        thread.sendMessage("Adding Users!").queue { msg ->
            threadToMessage.set(thread.id, msg.id)

            msg.editMessage(mentionsText.toString()).queue { msg2 ->
                msg2.editMessage(
                    "Added Users!"
                ).queue()
            }
        }
    }

    override fun onMessageReceived(event: MessageReceivedEvent) {
        when (event.message.contentDisplay) {
            "!fact" -> {
                event.message.reply("Configuring forum adding command, correct?")
                    .addActionRow(Button.danger("exit", "Cancel"), Button.primary("overrideStep", "Proceed"))
                    .mentionRepliedUser(false).queue()
            }

            "!resetFA" -> {
                event.message.member?.hasPermission(Permission.ADMINISTRATOR).run {
                    channelToRoles.getAllKeys().forEach { channelToRoles.remove(it.toString()) }
                }
            }
        }
    }

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        when (event.componentId) {
            "exit" -> event.editMessage("Canceled!").setComponents().queue()

            "overrideStep" -> {
                val msg = "Would you like to overwrite the existing settings or add to them?"
                event.editMessage(msg).setComponents(
                    ActionRow.of(
                        Button.danger("exit", "Cancel"),
                        Button.primary("enableOverride", "Override"),
                        Button.primary("disableOverride", "Add")
                    )
                ).queue()
            }

            "enableOverride" -> {
                override = true
                val msg = "You selected to overwrite, is this correct?"
                event.editMessage(msg).setComponents(
                    ActionRow.of(Button.danger("overrideStep", "Back"), Button.primary("roleStep", "Next"))
                ).queue()
            }

            "disableOverride" -> {
                override = false
                val msg = "You selected to add, is this correct?"
                event.editMessage(msg).setComponents(
                    ActionRow.of(Button.danger("overrideStep", "Back"), Button.primary("roleStep", "Next"))
                ).queue()
            }

            "roleStep" -> {
                event.editMessage("Select the role to be added!").setComponents(
                    ActionRow.of(EntitySelectMenu.create("roles", SelectTarget.ROLE).setMaxValues(25).build()),
                    ActionRow.of(Button.danger("overrideStep", "Back"))
                ).queue()
            }

            "channelStep" -> {
                event.editMessage("Select the channels that users will be added to!").setComponents(
                    ActionRow.of(
                        EntitySelectMenu.create("channels", SelectTarget.CHANNEL).setChannelTypes(
                            CATEGORY, FORUM, TEXT
                        ).setMaxValues(25).build()
                    ), ActionRow.of(Button.danger("roleStep", "Back"))
                ).queue()
            }

            "confirmFinal" -> {
                val sb = StringBuilder()
                sb.appendLine("**Final Conformation**")
                sb.appendLine("You've selected these roles:")
                for (role in roleSelection) sb.appendLine(role.asMention)

                sb.appendLine("And these channels:")
                for (channel in channelSelection) sb.appendLine(channel.asMention)

                sb.append("Would you like to complete the configuration?")
                event.editMessage(sb.toString()).setComponents(
                    ActionRow.of(Button.danger("channelStep", "Back"), Button.primary("saveToFile", "Finish!"))
                ).queue()
            }

            "saveToFile" -> {
                saveToFile()
                event.editMessage("Operation Completed!").setComponents().queue()
            }
        }
    }

    override fun onEntitySelectInteraction(event: EntitySelectInteractionEvent) {
        when (event.componentId) {
            "roles" -> {
                roleSelection.clear()
                roleSelection.addAll(event.mentions.roles)
                val sb = StringBuilder()
                sb.appendLine("You've selected these roles:")
                roleSelection.forEach { sb.appendLine(it.asMention) }
                sb.append("Would you like to continue?")
                event.editMessage(sb.toString())
                    .setActionRow(Button.danger("roleStep", "Back"), Button.primary("channelStep", "Next")).queue()
            }

            "channels" -> {
                channelSelection.clear()
                channelSelection.addAll(event.mentions.channels)
                val sb = StringBuilder()
                sb.append("You've selected these roles:\n")
                channelSelection.forEach { sb.appendLine(it.asMention) }
                sb.append("Would you like to continue?")
                event.editMessage(sb.toString())
                    .setActionRow(Button.danger("channelStep", "Back"), Button.primary("confirmFinal", "Next")).queue()
            }
        }
    }

    override fun onGuildMemberRoleAdd(event: GuildMemberRoleAddEvent) {
        for (entry in channelToRoles.getEntries()) {
            // Get the list of channels to check
            // This is either one channel, or multiple under a category
            val channelIds: MutableList<String> = mutableListOf()

            val category = Main.jda.getCategoryById(entry.first)

            if (category != null) {
                category.channels.forEach { channelIds.add(it.id) }
            } else {
                channelIds.add(entry.first)
            }

            // For each role that need to be added to the channels
            for (roleId in entry.second) {
                // TODO: does roles[0] mean you can assign more than one at a time?
                // Check if it's the role that was just given
                if (event.roles[0].id != roleId) continue

                for (channelId in channelIds) {
                    // Find all the threads
                    val threads = mutableListOf<ThreadChannel>()

                    val channel = Main.jda.getGuildChannelById(channelId) ?: continue
                    when (channel.type) {
                        TEXT -> {
                            val textChannel = Main.jda.getTextChannelById(channelId) ?: continue
                            threads.addAll(textChannel.threadChannels)
                        }

                        FORUM -> {
                            val forumChannel = Main.jda.getForumChannelById(channelId) ?: continue
                            threads.addAll(forumChannel.threadChannels)
                        }

                        else -> continue
                    }

                    // TODO: Save the message id somewhere so it can add to existing threads
                    // Update the message in each thread
                    for (thread in threads) {
                        val messageId = threadToMessage[thread.id]?.get(0) ?: continue

                        thread.editMessageById(messageId, event.user.asMention).queue {
                            it.editMessage("Added Users!").queue()
                        }
                    }
                }
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