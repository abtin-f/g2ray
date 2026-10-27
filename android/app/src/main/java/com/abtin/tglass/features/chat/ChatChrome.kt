package com.abtin.tglass.features.chat

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.glass.GlassBox
import com.abtin.tglass.data.Message
import com.abtin.tglass.ui.components.Icon
import com.abtin.tglass.ui.components.IosIcons
import com.abtin.tglass.ui.components.T
import com.abtin.tglass.ui.components.TgIcons
import com.abtin.tglass.ui.components.fadeClickable
import com.abtin.tglass.ui.components.formatCount
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle

/** Height of the chat screen's glass bars and header controls (Telegram-iOS: 40 pt). */
val ChatBarHeight = 40.dp

/**
 * Back control of the chat header: a 40 dp glass capsule with the chevron and, when other chats have unread
 * messages, a light inner pill with the count (iOS "‹ 256"). Without a count it is a 40 dp circle.
 */
@Composable
fun ChatBackPill(onClick: () -> Unit, badge: Int, modifier: Modifier = Modifier) {
    val c = TgTheme.colors
    if (badge <= 0) {
        GlassBox(onClick = onClick, modifier = modifier.size(ChatBarHeight), shape = Capsule()) {
            Icon(IosIcons.ChevronLeft, c.text, 22.dp)
        }
        return
    }
    GlassBox(onClick = onClick, modifier = modifier.height(ChatBarHeight), shape = Capsule()) {
        Row(Modifier.padding(start = 8.dp, end = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(IosIcons.ChevronLeft, c.text, 22.dp)
            Spacer(Modifier.width(3.dp))
            Box(
                Modifier.height(24.dp).clip(Capsule()).background(c.text.copy(alpha = 0.14f)).padding(horizontal = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                T(formatCount(badge), TgTheme.type.body.copy(fontSize = 13.sp, lineHeight = 16.sp), c.text, weight = FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

/** Text pill in glass at the chat bar height ("Cancel"). */
@Composable
fun ChatPillButton(text: String, onClick: (() -> Unit)?, modifier: Modifier = Modifier, color: Color = TgTheme.colors.text, bold: Boolean = false) {
    GlassBox(onClick = onClick, modifier = modifier.height(ChatBarHeight), shape = Capsule()) {
        T(
            text, TgTheme.type.body, color,
            weight = if (bold) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp), maxLines = 1,
        )
    }
}

// ---- Translate bar ----

/** Whether [c] contains Arabic-script letters (Arabic, Persian, Urdu...). */
private fun isArabicScript(cp: Int): Boolean =
    cp in 0x0600..0x06FF || cp in 0x0750..0x077F || cp in 0x08A0..0x08FF || cp in 0xFB50..0xFDFF || cp in 0xFE70..0xFEFF

/** True when [text] contains at least one Arabic-script letter. */
fun hasArabicScript(text: String?): Boolean {
    if (text.isNullOrEmpty()) return false
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        if (isArabicScript(cp) && Character.isLetter(cp)) return true
        i += Character.charCount(cp)
    }
    return false
}

/** The system language is one whose speakers read Arabic script (no point offering to translate it). */
fun systemReadsArabicScript(): Boolean = java.util.Locale.getDefault().language.lowercase() in setOf("fa", "ar", "ur")

/**
 * Telegram offers to translate when the last incoming messages are in a language other than the user's:
 * here a message counts when it contains Arabic-script text; at least 2 of the last 12 incoming messages.
 */
fun shouldOfferTranslation(messages: List<Message>): Boolean {
    if (systemReadsArabicScript()) return false
    var seen = 0
    var hits = 0
    var i = messages.lastIndex
    while (i >= 0 && seen < 12) {
        val m = messages[i]
        if (!m.outgoing) {
            seen++
            if (hasArabicScript(m.text)) hits++
        }
        i--
    }
    return hits >= 2
}

/** Chats whose translation bar the user hid (kept in memory and in preferences). */
object TranslateBarPrefs {
    private const val FILE = "tglass_chat"
    private const val KEY = "translate_hidden"
    private var prefs: android.content.SharedPreferences? = null
    private var hiddenIds by mutableStateOf(emptySet<Long>())

    fun init(context: Context) {
        if (prefs != null) return
        val p = runCatching { context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE) }.getOrNull() ?: return
        prefs = p
        hiddenIds = hiddenIds + (runCatching { p.getStringSet(KEY, emptySet()) }.getOrNull().orEmpty().mapNotNull { it.toLongOrNull() })
    }

    fun isHidden(chatId: Long): Boolean = chatId in hiddenIds

    fun hide(chatId: Long) {
        hiddenIds = hiddenIds + chatId
        prefs?.edit()?.putStringSet(KEY, hiddenIds.map { it.toString() }.toSet())?.apply()
    }
}

/** Pinned-message bars the user closed (private / bot / Saved chats): chat id to the pinned message id; in memory only. */
object PinnedBarPrefs {
    private var hidden by mutableStateOf(emptyMap<Long, Long>())
    fun isHidden(chatId: Long, messageId: Long): Boolean = hidden[chatId] == messageId
    fun hide(chatId: Long, messageId: Long) { hidden = hidden + (chatId to messageId) }
}

private val TranslateGlyph: ImageVector by lazy {
    ImageVector.Builder("translate", 24.dp, 24.dp, 24f, 24f).apply {
        listOf(
            "M3 6h9M7.5 4v2M5.2 6c.6 3.2 2.8 5.8 6.3 7.4M10 6c-.6 3.2-3 6-6.8 7.6",
            "M12.8 20l4.2-10 4.2 10M14.3 16.4h5.4",
        ).forEach { d ->
            addPath(
                addPathNodes(d), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()
}

/** 24 dp ring with three dots ("…") — the more button of the translate bar. */
@Composable
private fun DotsRing(color: Color) {
    Canvas(Modifier.size(24.dp)) {
        drawCircle(color, radius = size.minDimension / 2f - 0.75.dp.toPx(), style = Stroke(width = 1.5.dp.toPx()))
        val r = 1.5.dp.toPx()
        val cy = size.height / 2f
        val gap = 4.5.dp.toPx()
        drawCircle(color, r, Offset(size.width / 2f - gap, cy))
        drawCircle(color, r, Offset(size.width / 2f, cy))
        drawCircle(color, r, Offset(size.width / 2f + gap, cy))
    }
}

/** Full-width glass capsule under the header: translate glyph, "Translate to English" and a "…" menu. */
@Composable
fun TranslateBar(onTranslate: () -> Unit, onMore: () -> Unit, modifier: Modifier = Modifier) {
    val c = TgTheme.colors
    GlassBox(onClick = null, shape = Capsule(), modifier = modifier.fillMaxWidth().height(38.dp)) {
        Box(Modifier.fillMaxWidth().fillMaxHeight()) {
            Row(
                Modifier.align(Alignment.Center).fillMaxHeight().fadeClickable(onClick = onTranslate).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(TranslateGlyph, c.text, 20.dp)
                Spacer(Modifier.width(8.dp))
                T("Translate to English", TgTheme.type.subheadline.copy(fontSize = 15.sp, lineHeight = 20.sp), c.text, weight = FontWeight.Medium, maxLines = 1)
            }
            Box(
                Modifier.align(Alignment.CenterEnd).size(38.dp).fadeClickable(onClick = onMore),
                contentAlignment = Alignment.Center,
            ) { DotsRing(c.secondaryText) }
        }
    }
}

// ---- Pinned message bar ----

/**
 * Pinned-message bar (40 dp, 16 dp radius): accent vertical bar, "Pinned Message" and the text; a pin glyph at
 * the right, or an X ([closable], private / bot / Saved chats) that hides the bar.
 */
@Composable
fun PinnedBar(preview: String, closable: Boolean, onClick: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val c = TgTheme.colors
    GlassBox(
        onClick = onClick,
        shape = RoundedRectangle(16.dp),
        modifier = modifier.fillMaxWidth().height(ChatBarHeight),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(2.dp).height(30.dp).clip(Capsule()).background(c.accent))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                T("Pinned Message", TgTheme.type.footnote.copy(fontSize = 13.sp, lineHeight = 16.sp), c.accent, weight = FontWeight.SemiBold, maxLines = 1)
                T(preview, TgTheme.type.footnote.copy(fontSize = 14.sp, lineHeight = 17.sp, textDirection = TextDirection.Content), c.text, maxLines = 1)
            }
            if (closable) {
                Box(Modifier.size(ChatBarHeight).fadeClickable(onClick = onClose), contentAlignment = Alignment.Center) {
                    Icon(IosIcons.Close, c.secondaryText, 14.dp)
                }
            } else {
                Box(Modifier.heightIn(min = ChatBarHeight).widthIn(min = 36.dp), contentAlignment = Alignment.Center) {
                    Icon(TgIcons.MsgPinned, c.secondaryText, 16.dp)
                }
            }
        }
    }
}
