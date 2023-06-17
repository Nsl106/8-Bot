package commands

import Main
import data.DataFile
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel.AutoArchiveDuration
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateArchivedEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.utils.TimeUtil
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object KeepAlive : ListenerAdapter() {
    private val data = DataFile(javaClass.simpleName)

    override fun onMessageReceived(event: MessageReceivedEvent) {
        val msg = event.message

        if (msg.author.isBot) return

        val channelId = msg.channel.id
        val command = msg.contentDisplay

        if (!(command.equals("!keepalive", true) || command.equals("!diepls", true))) return

        if (!msg.channelType.isThread) {
            msg.reply("Only threads and forum posts can archive!").mentionRepliedUser(false).queue()
            return
        }

        when (command) {
            "!keepalive" -> {
                if (data.containsKey(channelId)) {
                    msg.reply("This channel is already set to not archive!").mentionRepliedUser(false).queue()
                    return
                }
                data.set(channelId, "alive")
                msg.addReaction(Emoji.fromUnicode("U+1F44D")).queue() // Thumbs up emoji
                Main.update("keeping alive ${msg.channel.asMention}!")
            }

            "!diepls" -> {
                if (!data.containsKey(channelId)) {
                    msg.reply("This channel is already set to archive!").mentionRepliedUser(false).queue()
                    return
                }
                data.remove(channelId)
                msg.addReaction(Emoji.fromUnicode("U+1F44D")).queue() // Thumbs up emoji
                Main.update("no longer keeping alive ${msg.channel.asMention}!")
            }
        }
    }

    override fun onChannelUpdateArchived(event: ChannelUpdateArchivedEvent) {
        val thread = event.channel.asThreadChannel()
        if (data.containsKey(thread.id) && thread.isArchived) {
            thread.manager.setArchived(false).queue()
        }
        Main.update("Unarchived ${thread.asMention}!")

    }

    private fun unhideThreads() {
        Main.update("Checking for threads to unhide!")
        for (id in data.getAllKeys()) {
            val thread = Main.jda.getThreadChannelById(id.toString())
            if (thread == null) {
                data.remove(id.toString())
                Main.update("Missing Thread?!?! Removed $id from database")
                return
            }
            val lastMessageTime = TimeUtil.getTimeCreated(thread.latestMessageIdLong).toEpochSecond()

            val lastUpdateTime = thread.timeArchiveInfoLastModified .toEpochSecond()
            val lastActive = Instant.ofEpochSecond(Math.max(lastMessageTime, lastUpdateTime))
            val now = Instant.now()

            /* If the time between the last activity and now is less than the archive period. Instead of checking if it needs to be unhid now, it checks to see if it needs to be unhid before the next check. */
            val willHideBetweenChecks = ChronoUnit.MINUTES.between(
                lastActive, now.plus(60, ChronoUnit.MINUTES)
            ) >= thread.autoArchiveDuration.minutes

            Main.update(
                "${thread.asMention} " + ChronoUnit.MINUTES.between(
                    lastActive, now.plus(60, ChronoUnit.MINUTES)
                ) + " " + thread.autoArchiveDuration.minutes
            )

            if (willHideBetweenChecks) {
                val manager = thread.manager

                // Change the thread's auto archive time because this counts as 'activity' and will unhide the thread
                manager.setAutoArchiveDuration(AutoArchiveDuration.TIME_1_HOUR).queue {
                    manager.setAutoArchiveDuration(AutoArchiveDuration.TIME_1_WEEK).queue()
                }
                Main.update("~~preemptively~~ unhid ${thread.asMention}!")
            }
        }
    }

    override fun onReady(event: ReadyEvent) {
        val scheduler = Executors.newScheduledThreadPool(1)
        scheduler.scheduleAtFixedRate(this::unhideThreads, 0, 60, TimeUnit.MINUTES)
    }
}