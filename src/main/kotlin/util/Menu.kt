package util

import Main.jda
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
import net.dv8tion.jda.internal.utils.tuple.MutablePair
import kotlin.time.Duration.Companion.seconds

class Menu(
    private val message: String,
    private val interaction: IReplyCallback,
    private val id: String,
    private val ephemeral: Boolean = true,
    private val successMessage: String = "Operation Completed!",
    private val timeoutMessage: String = "Timed out!",
    private val timeout: Int = 60
) {
    private val userId = interaction.user.id
    private val menus = mutableMapOf<String, MutablePair<SelectMenu, List<String>?>>()
    private val buttonId = "$userId:confirm:$id"

    fun addStringMenu(
        name: String,
        placeholder: String? = null,
        valueRange: IntRange = 1..1,
        disabled: Boolean = false,
        options: Collection<SelectOption> = emptyList()
    ): Menu {
        val id = "$userId:$name:$id"
        menus[id] = MutablePair(StringSelectMenu(id, placeholder, valueRange, disabled, options), null)
        return this
    }

    fun addEntityMenu(
        name: String,
        types: Collection<EntitySelectMenu.SelectTarget>,
        placeholder: String? = null,
        valueRange: IntRange = 1..1,
        disabled: Boolean = false,
        channelTypes: List<ChannelType> = emptyList()
    ): Menu {
        val id = "$userId:$name:$id"
        menus[id] = MutablePair(EntitySelectMenu(id, types, placeholder, valueRange, disabled) {
            setChannelTypes(channelTypes)
        }, null)
        return this
    }

    suspend fun execute(): List<List<String>>? {
        val button = success(buttonId, "Confirm")

        val actionRows = mutableListOf<ActionRow>()
        actionRows.addAll(menus.values.map { row(it.left) })
        actionRows.add(row(button))

        interaction.reply_(
            content = message,
            ephemeral = ephemeral,
            components = actionRows,
        ).queue()

        val listeners = mutableListOf<CoroutineEventListener>()
        menus.forEach {
            when (it.value.left) {
                is EntitySelectMenu -> {
                    val l = jda.onEntitySelect(it.key) { i ->
                        i.deferEdit().queue()
                        it.value.right = i.values.map { c -> c.id }
                    }
                    listeners.add(l)
                }

                is StringSelectMenu -> {
                    val l = jda.onStringSelect(it.key) { i ->
                        i.deferEdit().queue()
                        it.value.right = i.values
                    }
                    listeners.add(l)
                }
            }
        }

        val result = withTimeoutOrNull(timeout.seconds) {
            interaction.user.awaitButton(buttonId) {
                it.deferEdit().queue()
                menus.values.all { pair -> !pair.right.isNullOrEmpty() }
            }
            interaction.hook.editOriginal(successMessage).setComponents().queue()
            return@withTimeoutOrNull menus.values.map { it.right!! }
        }

        if (result == null) interaction.hook.editOriginal(timeoutMessage).setComponents().queue()

        listeners.forEach { it.cancel() }

        return result
    }
}