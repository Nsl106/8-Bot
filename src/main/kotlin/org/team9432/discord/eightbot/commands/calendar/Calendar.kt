package org.team9432.discord.eightbot.commands.calendar

import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.generics.getChannel
import dev.minn.jda.ktx.interactions.commands.Command
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.commands.subcommand
import dev.minn.jda.ktx.messages.Embed
import dev.minn.jda.ktx.messages.edit
import dev.minn.jda.ktx.messages.reply_
import dev.minn.jda.ktx.messages.send
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.requests.ErrorResponse
import org.bson.codecs.pojo.annotations.BsonId
import org.team9432.discord.eightbot.CommandBase
import org.team9432.discord.eightbot.apis.google.CalendarApi
import org.team9432.discord.eightbot.database.Database
import org.team9432.discord.eightbot.jda
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

object Calendar: CommandBase {
    data class CalendarData(@BsonId val messageId: String, val channelId: String, val calendarId: String, val name: String, val guildId: String)

    private val collection = Database.getCollection<CalendarData>("calendar")

    override fun commandData() = Command(name, "Adds events from google calendar to discord") {
        subcommand("add", "Adds a new calendar to be followed in this server") {
            option<String>("name", "The name of the calendar", required = true)
            option<String>("id", "The ID of the google calendar", required = true)
        }
        subcommand("help", "Explains how to get the calendar ID")
        subcommand("list", "Lists all calendars for this server")
        subcommand("update", "Updates the discord calendar on-demand")

        isGuildOnly = true
        defaultPermissions = DefaultMemberPermissions.enabledFor(Permission.MANAGE_EVENTS)
    }

    override suspend fun onSlashCommand(event: SlashCommandInteractionEvent) {
        when (event.subcommandName) {
            "add" -> handleAdd(event)
            "help" -> handleHelp(event)
            "list" -> handleList(event)
            "update" -> handleUpdate(event)
        }
    }

    private fun handleHelp(event: SlashCommandInteractionEvent) {
        event.reply_(HELP_TEXT, ephemeral = true).queue()
    }

    private suspend fun handleUpdate(event: SlashCommandInteractionEvent) {
        update()
        event.reply_("Done!", ephemeral = true).queue()
    }

    private suspend fun handleList(event: SlashCommandInteractionEvent) {
        val calendars = collection.find(Filters.eq(CalendarData::guildId.name, event.guild!!.id))

        val reply = StringBuilder()
        calendars.collect { reply.appendLine("${it.name} https://discord.com/channels/${it.guildId}/${it.channelId}/${it.messageId}") }
        if (reply.isEmpty()) reply.appendLine("No calendars in this server!")
        event.reply_(
            ephemeral = true,
            embeds = listOf(Embed {
                title = "Calendars"
                description = reply.toString()
                color = 0x22462c
                timestamp = Instant.now()
            })
        ).queue()
    }

    private suspend fun handleAdd(event: SlashCommandInteractionEvent) {
        if (event.channel !is GuildMessageChannel) {
            event.reply_("This command cannot be used in this channel!").queue()
            return
        }

        val calendarId = event.getOption("id")!!.asString
        val name = event.getOption("name")!!.asString

        val validId = CalendarApi.testId(calendarId)

        if (validId) {
            val events = CalendarApi.getEvents(calendarId)?.items ?: return
            val importantEvents = CalendarApi.getEvents(calendarId, important = true, daysInAdvance = 365)?.items ?: return
            val message = event.channel.send(embeds = CalendarEmbed.getCalendarEmbed(events, importantEvents, name)).await()

            collection.replaceOne(
                Filters.eq(message.id),
                CalendarData(message.id, event.channel.id, calendarId, name, event.guild!!.id),
                ReplaceOptions().upsert(true)
            )
            event.reply_("success", ephemeral = true).queue()
        } else {
            event.reply_("An error occurred! If you need help finding your calendar id, use /calendar help", ephemeral = true).queue()
        }
    }

    override suspend fun loop() {
        while (true) {
            update()
            delay(5.minutes)
        }
    }

    private suspend fun update() {
        val calendars = collection.find().toList()

        for (calendar in calendars) {
            val channel = jda.getChannel(calendar.channelId) as GuildMessageChannel

            val message: Message
            try {
                message = channel.retrieveMessageById(calendar.messageId).await()
            } catch (e: ErrorResponseException) {
                if (e.errorResponse == ErrorResponse.UNKNOWN_MESSAGE) {
                    collection.deleteOne(Filters.eq(calendar.messageId))
                }
                continue
            }

            val events = CalendarApi.getEvents(calendar.calendarId, 14)?.items ?: continue
            val importantEvents = CalendarApi.getEvents(calendar.calendarId, important = true, daysInAdvance = 365)?.items ?: continue
            message.edit(embeds = CalendarEmbed.getCalendarEmbed(events, importantEvents, calendar.name)).queue()
        }
    }

    private val HELP_TEXT = """
                From the [Google Calendar](<https://calendar.google.com/calendar/u/0/r>) website, click the settings icon in the top bar.
                On the left bar, find the calendar you want to add under **Settings for other calendars**.
                In the dropdown menu, go to **Calendar settings** (it may already be selected).
                Scroll down to the **Integrate calendar** section and copy the calendar id from there.
                It should look something like this:
                `c_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx@group.calendar.google.com`.
                Keep in mind that the calendar needs to be public, or have link sharing on for this to work.
            """.trimIndent()
}