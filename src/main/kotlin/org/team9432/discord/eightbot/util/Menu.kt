package org.team9432.discord.eightbot.util

import dev.minn.jda.ktx.events.CoroutineEventListener
import dev.minn.jda.ktx.events.awaitButton
import dev.minn.jda.ktx.events.onEntitySelect
import dev.minn.jda.ktx.events.onStringSelect
import dev.minn.jda.ktx.interactions.components.EntitySelectMenu
import dev.minn.jda.ktx.interactions.components.StringSelectMenu
import dev.minn.jda.ktx.interactions.components.row
import dev.minn.jda.ktx.interactions.components.success
import dev.minn.jda.ktx.messages.reply_
import kotlinx.coroutines.withTimeoutOrNull
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu
import net.dv8tion.jda.api.interactions.components.selections.SelectOption
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu
import org.team9432.discord.eightbot.jda
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class Menu(
    private val message: String,
    private val interaction: IReplyCallback,
    private val id: String,
    private val ephemeral: Boolean = true,
    private val successMessage: String = "Operation Completed!",
    private val timeoutMessage: String = "Timed out!",
    private val timeout: Duration = 2.minutes,
) {
    private data class MenuOption(val id: String, val menu: SelectMenu, var result: List<String>? = null)

    private val userId = interaction.user.id
    private val menus = mutableListOf<MenuOption>()
    private val buttonId = "$userId:confirm:$id"

    suspend fun execute(): List<List<String>>? {
        val button = success(buttonId, "Confirm")

        val actionRows = mutableListOf<ActionRow>()
        actionRows.addAll(menus.map { row(it.menu) })
        actionRows.add(row(button))

        interaction.reply_(
            content = message,
            ephemeral = ephemeral,
            components = actionRows,
        ).queue()

        val listeners = mutableListOf<CoroutineEventListener>()
        menus.forEach { menu ->
            val listener = when (menu.menu) {
                is EntitySelectMenu -> {
                    jda.onEntitySelect(menu.id) { event ->
                        event.deferEdit().queue()
                        menu.result = event.values.map { it.id }
                    }
                }

                is StringSelectMenu -> {
                    jda.onStringSelect(menu.id) { event ->
                        event.deferEdit().queue()
                        menu.result = event.values
                    }
                }

                else -> throw Exception("Unsupported menu type")
            }
            listeners.add(listener)
        }

        val result = withTimeoutOrNull(timeout) {
            interaction.user.awaitButton(buttonId) { event ->
                event.deferEdit().queue()
                menus.map { it.result }.none { it.isNullOrEmpty() }
            }
            interaction.hook.editOriginal(successMessage).setComponents().queue()
            return@withTimeoutOrNull menus.map { it.result ?: emptyList() }
        }

        if (result == null) interaction.hook.editOriginal(timeoutMessage).setComponents().queue()

        listeners.forEach { it.cancel() }

        return result
    }

    fun addStringMenu(
        name: String,
        placeholder: String? = null,
        valueRange: IntRange = 1..1,
        disabled: Boolean = false,
        options: Collection<SelectOption> = emptyList(),
    ): Menu {
        val id = "$userId:$name:$id"
        val menu = StringSelectMenu(id, placeholder, valueRange, disabled, options)
        menus.add(MenuOption(id, menu))
        return this
    }

    fun addEntityMenu(
        name: String,
        types: Collection<EntitySelectMenu.SelectTarget>,
        placeholder: String? = null,
        valueRange: IntRange = 1..1,
        disabled: Boolean = false,
        channelTypes: List<ChannelType> = emptyList(),
    ): Menu {
        val id = "$userId:$name:$id"
        val menu = EntitySelectMenu(id, types, placeholder, valueRange, disabled) {
            setChannelTypes(channelTypes)
        }
        menus.add(MenuOption(id, menu))
        return this
    }
}