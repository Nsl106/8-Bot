package org.team9432.discord.eightbot.commands.calendar

import dev.minn.jda.ktx.messages.EmbedBuilder
import dev.minn.jda.ktx.messages.InlineEmbed
import net.dv8tion.jda.api.entities.MessageEmbed
import org.team9432.discord.eightbot.apis.google.CalendarApi
import java.time.Instant

object CalendarEmbed {

    fun getCalendarEmbed(events: List<CalendarApi.CalendarEvent>, importantEvents: List<CalendarApi.CalendarEvent>, calendarName: String): List<MessageEmbed> {
        val formatter = CalendarTextFormatter()

        val (inProgressEvents, normalEvents) = events.take(25).partition { it.start.date.before(formatter.today) }
        val (reminders, futureEvents) = normalEvents.partition { it.description.contains("reminder") }

        val output = mutableListOf<InlineEmbed>()
        output += EmbedBuilder {
            description = "# $calendarName"
            color = 0x22462c
        }

        if (futureEvents.isNotEmpty()) {
            output += EmbedBuilder {
                title = "Upcoming Events"
                futureEvents.forEach { event ->
                    field {
                        value = formatter.getText(event)
                        name = event.summary
                        inline = false
                    }
                }
                color = 0x22462c
            }
        }

        if (importantEvents.isNotEmpty()) {
            output += EmbedBuilder {
                title = "Important Events"
                importantEvents.forEach { event ->
                    field {
                        value = formatter.getText(event)
                        name = event.summary
                        inline = false
                    }
                }
                color = 0x22462c
            }
        }

        if (reminders.isNotEmpty()) {
            output += EmbedBuilder {
                title = "Reminders"
                reminders.forEach { event ->
                    field {
                        value = formatter.getText(event)
                        name = event.summary
                        inline = false
                    }
                }
                color = 0x22462c
            }
        }

        if (inProgressEvents.isNotEmpty()) {
            output += EmbedBuilder {
                title = ":green_square: In Progress"
                inProgressEvents.forEach { event ->
                    field {
                        value = formatter.getText(event)
                        name = event.summary
                        inline = false
                    }
                }
                color = 0x22462c
            }
        }

        output.last().apply {
            footer("Last update")
            timestamp = Instant.now()
        }

        return output.map { it.build() }
    }
}