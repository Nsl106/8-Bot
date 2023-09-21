package commands

import CommandBase
import Main.jda
import apis.google.Calendar
import apis.google.GoogleAuth
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.model.Event
import data.*
import dev.minn.jda.ktx.interactions.commands.Command
import dev.minn.jda.ktx.interactions.commands.subcommand
import dev.minn.jda.ktx.messages.reply_
import net.dv8tion.jda.api.entities.channel.ChannelType.STAGE
import net.dv8tion.jda.api.entities.channel.ChannelType.VOICE
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu.SelectTarget
import net.dv8tion.jda.api.interactions.components.selections.SelectOption
import util.Menu
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.concurrent.fixedRateTimer
import kotlin.time.Duration.Companion.minutes

object CalendarSync : CommandBase {
    private val data = JsonFile(name, name)

    override fun commandData() = Command(name, "Adds events from google calendar to discord") {
        subcommand("addcolor", "Set which channel will be the location of events by color")
        subcommand("addcalendar", "Adds a new calendar to be followed in this server")
        subcommand("listcolors", "Lists all color mappings for this server")
        subcommand("listcalendars", "Lists all calendars for this server")
        subcommand("removecalendars", "Remove calendars from this server")
        subcommand("update", "Updates the discord calendar on-demand")

        isGuildOnly = true
        defaultPermissions = DefaultMemberPermissions.DISABLED
    }

    override suspend fun onSlashCommand(event: SlashCommandInteractionEvent) {
        when (event.subcommandName) {
            "addcalendar" -> handleAddCalendar(event)
            "addcolor" -> handleAddColor(event)
            "listcolors" -> handleListColors(event)
            "listcalendars" -> handleListCalendars(event)
            "removecalendars" -> handleRemoveCalendars(event)
            "update" -> handleUpdate(event)
        }
    }

    private fun handleUpdate(event: SlashCommandInteractionEvent) {
        copyToDiscord()
        event.reply_("Done!", ephemeral = true).queue()
    }

    private suspend fun handleRemoveCalendars(event: SlashCommandInteractionEvent) {
        val userId = event.user.id
        val credentials = GoogleAuth.getSavedCredentials(userId)

        if (credentials == null) {
            event.reply_("Sign in with the **/authenticate** command to remove calendars!", ephemeral = true).queue()
            return
        }

        val calendarNames = Calendar.getCalender(credentials).calendarList().list().execute().items
        val nameOptions = calendarNames.map { SelectOption.of(it.summary, it.id) }

        val result = Menu(
            message = "Select a calendar",
            interaction = event,
            id = "calendarsyncremovecalendar"
        ).addStringMenu(
            name = "calendar",
            placeholder = "Select a calendar",
            options = nameOptions
        ).execute() ?: return

        val calendar = result.component1()[0]

        data.mainObj.getObjectOrNew(event.guild!!.id).getObjectOrNew("calendarToUser").remove(calendar)
        data.save()
    }

    private fun handleListCalendars(event: SlashCommandInteractionEvent) {
        val guildObj = data.mainObj.getObject(event.guild!!.id) ?: return
        val calendars = guildObj.getObjectOrNew("calendarToUser").getAsMap()

        val reply = StringBuilder()
        for (pair in calendars) {
            val userId = pair.value
            val credentials = GoogleAuth.getSavedCredentials(userId)
            if (credentials == null) {
                Exception("Missing authentication!").printStackTrace()
                continue
            }
            val calendar = Calendar.getCalender(credentials)
            reply.appendLine(calendar.calendars().get(pair.key).execute().summary)
        }

        if (reply.isEmpty()) reply.appendLine("No calendars are followed in this server!")
        event.reply_(reply.toString(), ephemeral = true).queue()
    }

    private fun handleListColors(event: SlashCommandInteractionEvent) {
        val mappings = data.mainObj.getObjectOrNew(event.guild!!.id).getObjectOrNew("colorsToChannels").asMap()

        val reply = StringBuilder()
        mappings.forEach {
            val channel = Calendar.calendarColors[it.key.toInt()]
            val color = "<#${it.value.asString}>"

            reply.appendLine("$channel to $color")
        }
        if (reply.isEmpty()) reply.appendLine("No mappings are set in this server!")
        event.reply_(reply.toString(), ephemeral = true).queue()
    }

    private suspend fun handleAddColor(event: SlashCommandInteractionEvent) {
        val colorOptions = Calendar.calendarColors.map { SelectOption.of(it.value, it.key.toString()) }

        val result = Menu(
            message = "Select colors and the channel they will be added to",
            interaction = event,
            id = "calendarsynccolor"
        ).addStringMenu(
            name = "color",
            placeholder = "Select colors",
            valueRange = 1..25,
            options = colorOptions
        ).addEntityMenu(
            name = "channel",
            types = listOf(SelectTarget.CHANNEL),
            placeholder = "Select a channel",
            channelTypes = listOf(VOICE, STAGE)
        ).execute() ?: return

        val colors = result.component1()
        val channel = result.component2()

        val colorsToChannel = colors.associateWith { channel[0] }

        data.mainObj.getObjectOrNew(event.guild!!.id).getObjectOrNew("colorsToChannels").setProperties(colorsToChannel)
        data.save()
    }

    private suspend fun handleAddCalendar(event: SlashCommandInteractionEvent) {
        val userId = event.user.id
        val credentials = GoogleAuth.getSavedCredentials(userId)

        if (credentials == null) {
            event.reply_("Sign in with the **/authenticate** command to add calendars!", ephemeral = true).queue()
            return
        }

        val calendarNames = Calendar.getCalender(credentials).calendarList().list().execute().items
        val nameOptions = calendarNames.map { SelectOption.of(it.summary, it.id) }

        val result = Menu(
            message = "Select a calendar",
            interaction = event,
            id = "calendarsyncadd"
        ).addStringMenu(
            name = "calendar",
            placeholder = "Select a calendar",
            options = nameOptions
        ).execute() ?: return

        val calendar = result.component1()[0]

        data.mainObj.getObjectOrNew(event.guild!!.id).getObjectOrNew("calendarToUser").addProperty(calendar, userId)
        data.save()
    }

    private fun getEvents(calendar: com.google.api.services.calendar.Calendar, id: String): List<Event> {
        val minTime = Instant.now().toDateTime()
        val maxTime = Instant.now().plus(14, ChronoUnit.DAYS).toDateTime()

        return calendar.events().list(id).setTimeMax(maxTime).setTimeMin(minTime).setOrderBy("startTime").setSingleEvents(true).execute().items
    }

    private fun copyToDiscord() {
        val guilds = data.mainObj.keys.mapNotNull { jda.getGuildById(it) }
        for (guild in guilds) {
            // Collect all the events
            val events = mutableListOf<Event>()
            val guildObj = data.mainObj.getObject(guild.id) ?: continue
            val calendars = guildObj.getObjectOrNew("calendarToUser").getAsMap()

            for (pair in calendars) {
                val userId = pair.value
                val credentials = GoogleAuth.getSavedCredentials(userId)
                if (credentials == null) {
                    Exception("Missing authentication!").printStackTrace()
                    continue
                }
                val calendar = Calendar.getCalender(credentials)
                events.addAll(getEvents(calendar, pair.key))
            }

            // Copy all the events into discord
            for (event in events) {
                if (event.start.dateTime == null) continue

                val name = event.summary ?: "Unnamed Event"
                val color: String? = event.colorId

                val startTime = OffsetDateTime.ofInstant(event.start.dateTime.toInstant(), ZoneId.of("UTC"))
                val endTime = OffsetDateTime.ofInstant(event.end.dateTime.toInstant(), ZoneId.of("UTC"))

                if (event.start.dateTime.toInstant().isBefore(Instant.now())) continue
                if (guild.scheduledEvents.find { it.name == name && it.startTime == startTime } != null) continue

                val colorsToChannels = guildObj.getObjectOrNew("colorsToChannels").getAsMap()
                if (color == null || !colorsToChannels.containsKey(color)) {
                    val location = event.location ?: "Unknown Location"
                    guild.createScheduledEvent(name, location, startTime, endTime).queue()
                } else {
                    val channelId = colorsToChannels.get(color) ?: continue
                    val channel = jda.getGuildChannelById(channelId) ?: continue
                    guild.createScheduledEvent(name, channel, startTime).queue()
                }
            }
        }
    }

    override suspend fun onReady(event: ReadyEvent) {
        fixedRateTimer(startAt = Date.from(Instant.now()), period = 60.minutes.inWholeMilliseconds) { copyToDiscord() }
    }

    private fun Instant.toDateTime() = DateTime(Date.from(this))
    private fun DateTime.toInstant() = Instant.ofEpochMilli(this.value)
}