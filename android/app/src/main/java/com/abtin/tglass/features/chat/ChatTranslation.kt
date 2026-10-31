package com.abtin.tglass.features.chat

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.abtin.tglass.data.Message
import com.abtin.tglass.data.MessageContent
import com.abtin.tglass.data.TelegramRepository
import com.abtin.tglass.data.TranslatedText

/** Language the chat translation bar translates into. */
const val TranslateTarget = "en"

/** The text of [m] can be translated: text, link previews and captions of photos / files. */
fun isTranslatable(m: Message): Boolean =
    !m.text.isNullOrBlank() && (m.content is MessageContent.Text || m.content is MessageContent.Link ||
        m.content is MessageContent.Photo || m.content is MessageContent.File)

/** Same message with its text / caption replaced by [t]. */
fun Message.withTranslation(t: TranslatedText): Message = when (val c = content) {
    is MessageContent.Text -> copy(content = c.copy(text = t.text, entities = t.entities))
    is MessageContent.Link -> copy(content = c.copy(text = t.text, entities = t.entities))
    is MessageContent.Photo -> copy(content = c.copy(caption = t.text, captionEntities = t.entities))
    is MessageContent.File -> copy(content = c.copy(caption = t.text, captionEntities = t.entities))
    else -> this
}

/**
 * Per-chat translation state: translations are fetched lazily for the messages on screen (TDLib translateMessageText),
 * cached by message id, and shown in place of the original while [enabled] (the bar's "Show Original" switches it off).
 * A single message can be forced either way from its long-press menu ([forced]).
 */
@Stable
class ChatTranslation {
    /** The bar's switch: translate every incoming message in view. */
    var enabled by mutableStateOf(false)
    val cache = mutableStateMapOf<Long, TranslatedText>()
    /** message id → true (translated) / false (original), overriding [enabled]. */
    val forced = mutableStateMapOf<Long, Boolean>()
    private val requested = HashSet<Long>()

    /** Whether any translation could currently be on screen (cheap check before mapping the whole list). */
    val anyShown: Boolean get() = cache.isNotEmpty() && (enabled || forced.isNotEmpty())

    fun showsTranslation(id: Long): Boolean = cache.containsKey(id) && (forced[id] ?: enabled)

    fun apply(m: Message): Message {
        if (!showsTranslation(m.id)) return m
        return m.withTranslation(cache[m.id] ?: return m)
    }

    /** Asks for the translation of [m] unless it is cached or already on its way. [onError] gets the readable failure (once per request). */
    fun request(repo: TelegramRepository, m: Message, onError: (String) -> Unit) {
        if (cache.containsKey(m.id) || !requested.add(m.id)) return
        repo.translateMessage(m.chatId, m.id, TranslateTarget) { t, err ->
            if (t != null) {
                cache[m.id] = t
            } else {
                requested.remove(m.id)
                onError(err ?: "Translation failed.")
            }
        }
    }

    /** Back to the originals (the bar's "Show Original"); the cache stays so translating again is instant. */
    fun showOriginals() {
        enabled = false
        forced.clear()
    }
}
