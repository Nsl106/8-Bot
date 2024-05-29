package org.team9432.discord.eightbot.commands

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.generics.getChannel
import dev.minn.jda.ktx.interactions.commands.Command
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.commands.subcommand
import dev.minn.jda.ktx.messages.Embed
import dev.minn.jda.ktx.messages.reply_
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import net.dv8tion.jda.api.entities.channel.attribute.IThreadContainer
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateArchivedEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.session.ReadyEvent
import org.bson.codecs.pojo.annotations.BsonId
import org.team9432.discord.eightbot.CommandBase
import org.team9432.discord.eightbot.database.Database
import org.team9432.discord.eightbot.jda
import org.team9432.discord.eightbot.util.sendUpdate
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.time.Duration.Companion.hours

object KeepAlive: CommandBase {
    data class KeepaliveData(@BsonId val threadId: String, val guildId: String, val channelId: String, val lastUpdate: Long = 0)

    private val collection = Database.getCollection<KeepaliveData>("keepalive")

    override fun commandData() = Command(name, "Keeps threads and forum posts from hiding") {
        subcommand("set", "Sets a thread to be kept alive") {
            option<Boolean>("value", "True if the thread should be kept alive", required = true)
        }
        subcommand("list", "Provides a list of threads being kept alive")

        isGuildOnly = true
    }

    override suspend fun onSlashCommand(event: SlashCommandInteractionEvent) {
        when (event.subcommandName) {
            "set" -> {
                if (event.getOption("value")!!.asBoolean) {
                    startKeepalive(event)
                } else {
                    stopKeepalive(event)
                }
            }

            "list" -> handleList(event)
        }
    }

    private suspend fun handleList(event: SlashCommandInteractionEvent) {
        val allThreads = collection.find(Filters.eq(KeepaliveData::guildId.name, event.guild!!.id))

        val reply = StringBuilder()
        allThreads.collect { reply.appendLine("<#${it.threadId}>") }
        if (reply.isEmpty()) reply.appendLine("No threads are being kept alive in this server!")
        event.reply_(
            ephemeral = true,
            embeds = listOf(Embed {
                title = "Threads"
                description = reply.toString()
                color = 0x22462c
                timestamp = Instant.now()
            })
        ).queue()
    }

    private suspend fun stopKeepalive(event: SlashCommandInteractionEvent) {
        if (!event.channelType.isThread) {
            event.reply_("Only threads and forum posts can archive!", ephemeral = true).queue()
            return
        }

        val thread = event.channel.asThreadChannel()
        val valueChanged = collection.deleteOne(Filters.eq(thread.id)).deletedCount != 0L

        if (valueChanged) {
            event.reply_(Emoji.fromUnicode("U+1F44D").asReactionCode, ephemeral = true).queue()
        } else {
            event.reply_("This channel is already kept alive!", ephemeral = true).queue()
        }
    }

    private suspend fun startKeepalive(event: SlashCommandInteractionEvent) {
        if (!event.channelType.isThread) {
            event.reply_("Only threads and forum posts can archive!", ephemeral = true).queue()
            return
        }

        val thread = event.channel.asThreadChannel()

        if (collection.countDocuments(Filters.eq(thread.id)) == 0L) {
            collection.insertOne(KeepaliveData(thread.id, event.guild!!.id, thread.parentChannel.id))

            event.reply_(Emoji.fromUnicode("U+1F44D").asReactionCode, ephemeral = true).queue()
        } else {
            event.reply_("This channel is already not kept alive!", ephemeral = true).queue()
        }

        setLastUpdateToNow(thread.id)
    }

    override suspend fun onThreadUpdateArchived(event: ChannelUpdateArchivedEvent) {
        val thread = event.channel.asThreadChannel()

        val isInDatabase = collection.countDocuments(Filters.eq(thread.id)) != 0L

        if (thread.isArchived && isInDatabase) {
            thread.manager.setArchived(false).queue()
            setLastUpdateToNow(thread.id)
        }
    }

    override suspend fun onReady(event: ReadyEvent) {
        val items = collection.find().toList()

        for ((channelId, threadData) in items.groupBy { it.channelId }) {
            val keptAliveThreadIds = threadData.map { it.threadId }

            val channel = jda.getChannel(channelId)

            val closedThreads = if (channel is IThreadContainer) {
                channel.retrieveArchivedPublicThreadChannels().await()
            } else continue

            val uncheckedThreads = keptAliveThreadIds.toMutableSet()

            closedThreads.forEach {
                val threadId = it.id
                uncheckedThreads.remove(threadId)

                if (threadId in keptAliveThreadIds) {
                    it.manager.setArchived(false).queue()
                    setLastUpdateToNow(threadId)
                }
            }

            channel.threadChannels.forEach { uncheckedThreads.remove(it.id) }

            uncheckedThreads.forEach { threadId ->
                collection.deleteOne(Filters.eq(threadId))
                sendUpdate("Missing Thread! Removed $threadId from database")
            }
        }
    }

    override suspend fun loop() {
        while (true) {
            val items = collection.find().toList()

            for (threadData in items) {
                val thread = jda.getThreadChannelById(threadData.threadId) ?: continue

                val lastUpdateRecord = getLastUpdateTime(threadData.threadId)

                val now = Instant.now()

                val willHideBetweenChecks = ChronoUnit.MINUTES.between(lastUpdateRecord, now.plus(60, ChronoUnit.MINUTES)) >= thread.autoArchiveDuration.minutes

                if (willHideBetweenChecks) {
                    // Change the thread's auto archive time because this counts as 'activity' and will unhide the thread
                    thread.manager.setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_HOUR).await()
                    thread.manager.setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_WEEK).await()

                    setLastUpdateToNow(thread.id)
                    sendUpdate("~~preemptively~~ unhid ${thread.asMention}!")
                }
            }

            delay(1.hours)
        }
    }

    private suspend fun setLastUpdateToNow(threadId: String) = collection.findOneAndUpdate(Filters.eq(threadId), Updates.set(KeepaliveData::lastUpdate.name, Instant.now().epochSecond))
    private suspend fun getLastUpdateTime(threadId: String) = Instant.ofEpochSecond(collection.find(Filters.eq(threadId)).first().lastUpdate)
}