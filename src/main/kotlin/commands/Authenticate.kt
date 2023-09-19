package commands

import CommandBase
import apis.google.GoogleAuth
import dev.minn.jda.ktx.interactions.commands.Command
import dev.minn.jda.ktx.interactions.commands.subcommand
import dev.minn.jda.ktx.interactions.components.link
import dev.minn.jda.ktx.messages.into
import dev.minn.jda.ktx.messages.reply_
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import kotlin.time.Duration.Companion.seconds

object Authenticate : CommandBase {
    override fun commandData(): SlashCommandData {
        return Command(name, "Signs in to google") {
            subcommand("login", "Logs in")
            subcommand("logout", "Logs out")
            isGuildOnly = true
        }
    }

    override suspend fun onSlashCommand(event: SlashCommandInteractionEvent) {
        when (event.subcommandName) {
            "login" -> handleLogin(event)
            "logout" -> handleLogout(event)
        }
    }

    private fun handleLogout(event: SlashCommandInteractionEvent) {
        GoogleAuth.removeCredentials(event.user.id)
        event.reply_("Success!", ephemeral = true).queue()
    }

    private suspend fun handleLogin(event: SlashCommandInteractionEvent) {
        val userId = event.user.id
        if (GoogleAuth.hasSavedCredentials(userId)) {
            event.reply_("You already are signed in!", ephemeral = true).queue()
            return
        }

        val url = GoogleAuth.generateCredentialUrl()
        event.reply_("Click the link below to sign in", ephemeral = true, components = link(url, "Sign In").into())
            .queue()


        val success = coroutineScope {
            val job = launch {
                GoogleAuth.waitForCredentials(event.user.id)
            }

            val timeOut = withTimeoutOrNull(60.seconds) timeout@{
                while (isActive && !job.isCompleted) {
                    // do nothing
                }

                if (!job.isCompleted) {
                    GoogleAuth.cancelWait()
                    return@timeout true
                }
                return@timeout false
            }

            return@coroutineScope timeOut == false
        }

        if (success) event.hook.editOriginal("Success!").setComponents().queue()
        else event.hook.editOriginal("Timed out!").setComponents().queue()
    }
}