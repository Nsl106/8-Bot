package commands

import CommandBase
import Main
import com.google.gson.JsonPrimitive
import data.JsonFile
import data.getArrayOrNew
import dev.minn.jda.ktx.interactions.commands.Command
import dev.minn.jda.ktx.interactions.commands.subcommand
import dev.minn.jda.ktx.messages.reply_
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel.AutoArchiveDuration
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateArchivedEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.utils.TimeUtil
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.concurrent.fixedRateTimer
import kotlin.time.Duration.Companion.minutes

object KeepAlive : CommandBase {
    private val data = JsonFile(name, name)
    override fun commandData() = Command(name, "Keeps threads and forum posts from hiding") {
        subcommand("keepalive", "Sets a thread to be kept alive")
        subcommand("diepls", "Stops keeping a thread alive")
        subcommand("list", "Provides a list of threads being kept alive")

        isGuildOnly = true
    }

    override suspend fun onSlashCommand(event: SlashCommandInteractionEvent) {
        when (event.subcommandName) {
            "keepalive" -> handleKeepalive(event)
            "diepls" -> handleDiepls(event)
            "list" -> handleList(event)
        }
    }

    private fun handleList(event: SlashCommandInteractionEvent) {
        val guildThreads = event.guild!!.threadChannels.map { it.id }
        val allThreads = data.mainObj.getArrayOrNew("channels").toList().map { it.asString }

        val matchingThreads = allThreads.filter { guildThreads.contains(it) }
        val reply = StringBuilder()
        matchingThreads.forEach { reply.appendLine("<#$it>") }
        if (reply.isEmpty()) reply.appendLine("No threads are being kept alive in this server!")
        event.reply_(reply.toString(), ephemeral = true).queue()
    }

    private fun handleDiepls(event: SlashCommandInteractionEvent) {
        if (!event.channelType.isThread) {
            event.reply_("Only threads and forum posts can archive!", ephemeral = true).queue(); return
        }
        val channelId = JsonPrimitive(event.channel.id)

        if (!data.mainObj.getArrayOrNew("channels").contains(channelId)) {
            event.reply_("This channel is already set to archive!", ephemeral = true).queue()
            return
        }
        data.mainObj.getArrayOrNew("channels").remove(channelId)
        data.save()
        event.reply_(Emoji.fromUnicode("U+1F44D").asReactionCode, ephemeral = true).queue()
        Main.report("no longer keeping alive ${event.channel.asMention}!")
    }

    private fun handleKeepalive(event: SlashCommandInteractionEvent) {
        if (!event.channelType.isThread) {
            event.reply_("Only threads and forum posts can archive!", ephemeral = true).queue(); return
        }
        val channelId = JsonPrimitive(event.channel.id)

        if (data.mainObj.getArrayOrNew("channels").contains(channelId)) {
            event.reply_("This channel is already set to not archive!", ephemeral = true).queue()
            return
        }
        data.mainObj.getArrayOrNew("channels").add(channelId)
        data.save()
        event.reply_(Emoji.fromUnicode("U+1F44D").asReactionCode, ephemeral = true).queue()
        Main.report("keeping alive ${event.channel.asMention}!")
    }

    override suspend fun onThreadUpdateArchived(event: ChannelUpdateArchivedEvent) {
        val thread = event.channel.asThreadChannel()
        if (data.mainObj.getArrayOrNew("channels").contains(JsonPrimitive(thread.id)) && thread.isArchived) {
            thread.manager.setArchived(false).queue()
            Main.report("Unarchived ${thread.asMention}!")
        }
    }

    private fun unhideThreads() {
        val array = data.mainObj.getArrayOrNew("channels")
        for (id in array.asList()) {
            val thread = Main.jda.getThreadChannelById(id.asString)
            if (thread == null) {
                array.remove(id)
                data.save()
                Main.report("Missing Thread?!?! Removed $id from database")
                continue
            }
            val lastMessageTime = TimeUtil.getTimeCreated(thread.latestMessageIdLong).toEpochSecond()

            val lastUpdateTime = thread.timeArchiveInfoLastModified.toEpochSecond()
            val lastActive = Instant.ofEpochSecond(Math.max(lastMessageTime, lastUpdateTime))
            val now = Instant.now()

            val willHideBetweenChecks = ChronoUnit.MINUTES.between(
                lastActive, now.plus(60, ChronoUnit.MINUTES)
            ) >= thread.autoArchiveDuration.minutes

            if (willHideBetweenChecks) {
                val manager = thread.manager

                // Change the thread's auto archive time because this counts as 'activity' and will unhide the thread
                manager.setAutoArchiveDuration(AutoArchiveDuration.TIME_1_HOUR).queue {
                    manager.setAutoArchiveDuration(AutoArchiveDuration.TIME_1_WEEK).queue()
                }
                Main.report("~~preemptively~~ unhid ${thread.asMention}!")
            }
        }
    }

    override suspend fun onReady(event: ReadyEvent) {
        fixedRateTimer(startAt = Date.from(Instant.now()), period = 60.minutes.inWholeMilliseconds) { unhideThreads() }
    }
}