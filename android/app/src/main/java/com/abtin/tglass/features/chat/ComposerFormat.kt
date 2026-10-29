package com.abtin.tglass.features.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.glass.GlassBox
import com.abtin.tglass.data.Entity
import com.abtin.tglass.data.EntityType
import com.abtin.tglass.ui.components.T
import com.abtin.tglass.ui.components.fadeClickable
import com.kyant.shapes.Capsule

/** Moves / trims [entities] after the text changed from [old] to [new] (one contiguous edit). */
internal fun adjustEntities(old: String, new: String, entities: List<Entity>): List<Entity> {
    if (entities.isEmpty() || old == new) return entities
    var p = 0
    val maxP = minOf(old.length, new.length)
    while (p < maxP && old[p] == new[p]) p++
    var s = 0
    while (s < maxP - p && old[old.length - 1 - s] == new[new.length - 1 - s]) s++
    val remEnd = old.length - s
    val delta = new.length - old.length
    fun map(o: Int) = when {
        o <= p -> o
        o >= remEnd -> o + delta
        else -> p
    }
    return entities.mapNotNull { e ->
        val ns = map(e.start)
        val ne = map(e.end)
        if (ne > ns) e.copy(start = ns, end = ne) else null
    }
}

/** [x] without the part inside [s, e). */
private fun cut(x: Entity, s: Int, e: Int): List<Entity> {
    if (x.end <= s || x.start >= e) return listOf(x)
    val out = ArrayList<Entity>(2)
    if (x.start < s) out += x.copy(end = s)
    if (x.end > e) out += x.copy(start = e)
    return out
}

/** Toggles [type] over [s, e): removed when the whole range already has it, otherwise added (merged with neighbours). */
internal fun toggleEntity(entities: List<Entity>, s: Int, e: Int, type: EntityType, url: String? = null): List<Entity> {
    if (e <= s) return entities
    val same = entities.filter { it.type == type }
    val rest = entities.filter { it.type != type }
    if (type == EntityType.TextUrl) {
        // A link replaces any link in the range; an empty url removes it.
        val kept = same.flatMap { x -> cut(x, s, e) }
        return rest + kept + (if (url.isNullOrBlank()) emptyList() else listOf(Entity(s, e, type, url = url)))
    }
    if (same.any { it.start <= s && it.end >= e }) return rest + same.flatMap { x -> cut(x, s, e) }
    var ns = s
    var ne = e
    val keep = ArrayList<Entity>()
    for (x in same) {
        if (x.end >= s && x.start <= e) {
            ns = minOf(ns, x.start)
            ne = maxOf(ne, x.end)
        } else keep += x
    }
    return rest + keep + Entity(ns, ne, type)
}

private fun active(entities: List<Entity>, s: Int, e: Int, type: EntityType) =
    entities.any { it.type == type && it.start <= s && it.end >= e }

/** Styles the message field's text with its entities, then hands over to the Apple-emoji transformation. */
internal fun formatTransformation(entities: List<Entity>, accent: Color, spoiler: Color, after: VisualTransformation): VisualTransformation =
    object : VisualTransformation {
        override fun filter(text: AnnotatedString): TransformedText {
            if (entities.isEmpty()) return after.filter(text)
            val b = AnnotatedString.Builder(text)
            for (x in entities) {
                val s = x.start.coerceIn(0, text.length)
                val e = x.end.coerceIn(s, text.length)
                if (s == e) continue
                when (x.type) {
                    EntityType.Bold -> b.addStyle(SpanStyle(fontWeight = FontWeight.SemiBold), s, e)
                    EntityType.Italic -> b.addStyle(SpanStyle(fontStyle = FontStyle.Italic), s, e)
                    EntityType.Underline -> b.addStyle(SpanStyle(textDecoration = TextDecoration.Underline), s, e)
                    EntityType.Strike -> b.addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), s, e)
                    EntityType.Code, EntityType.Pre -> b.addStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 0.88.em, background = accent.copy(alpha = 0.13f)), s, e)
                    EntityType.Spoiler -> b.addStyle(SpanStyle(background = spoiler), s, e)
                    EntityType.Quote -> b.addStyle(SpanStyle(background = accent.copy(alpha = 0.10f)), s, e)
                    EntityType.TextUrl -> b.addStyle(SpanStyle(color = accent), s, e)
                    else -> {}
                }
            }
            return after.filter(b.toAnnotatedString())
        }
    }

private fun normalizeUrl(raw: String): String {
    val u = raw.trim()
    return if (u.isNotEmpty() && !u.contains("://") && !u.startsWith("tg:")) "https://$u" else u
}

/**
 * The iOS "Format" menu as a glass capsule over the composer while text is selected:
 * Bold, Italic, Underline, Strikethrough, Monospace, Spoiler, Quote and Link.
 */
@Composable
internal fun FormatBar(
    entities: List<Entity>,
    selStart: Int,
    selEnd: Int,
    onToggle: (EntityType) -> Unit,
    onLink: (String) -> Unit,
    linkMode: Boolean,
    onLinkMode: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = TgTheme.colors
    var url by remember { mutableStateOf("") }
    val linkFocus = remember { FocusRequester() }
    GlassBox(onClick = null, shape = Capsule(), modifier = modifier) {
        if (linkMode) {
            LaunchedEffect(Unit) { runCatching { linkFocus.requestFocus() } }
            Row(Modifier.height(40.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(210.dp), contentAlignment = Alignment.CenterStart) {
                    if (url.isEmpty()) T("Enter URL", TgTheme.type.callout, c.secondaryText, maxLines = 1)
                    BasicTextField(
                        value = url,
                        onValueChange = { url = it },
                        singleLine = true,
                        textStyle = TgTheme.type.callout.copy(color = c.text),
                        cursorBrush = SolidColor(c.accent),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            onLink(normalizeUrl(url))
                            url = ""
                            onLinkMode(false)
                        }),
                        modifier = Modifier.focusRequester(linkFocus),
                    )
                }
                Box(
                    Modifier.height(40.dp).padding(start = 10.dp).fadeClickable {
                        onLink(normalizeUrl(url))
                        url = ""
                        onLinkMode(false)
                    },
                    contentAlignment = Alignment.Center,
                ) { T("Done", TgTheme.type.callout, c.accent, weight = FontWeight.SemiBold, maxLines = 1) }
            }
        } else {
            Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                @Composable
                fun item(label: String, type: EntityType, weight: FontWeight = FontWeight.Medium, italic: Boolean = false, mono: Boolean = false, deco: TextDecoration? = null) {
                    val on = active(entities, selStart, selEnd, type)
                    Box(Modifier.height(40.dp).width(38.dp).fadeClickable { onToggle(type) }, contentAlignment = Alignment.Center) {
                        T(
                            label,
                            TgTheme.type.body.copy(
                                fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
                                fontFamily = if (mono) FontFamily.Monospace else TgTheme.type.body.fontFamily,
                                textDecoration = deco,
                            ),
                            if (on) c.accent else c.text,
                            weight = weight,
                            maxLines = 1,
                        )
                    }
                }
                item("B", EntityType.Bold, FontWeight.Bold)
                item("I", EntityType.Italic, italic = true)
                item("U", EntityType.Underline, deco = TextDecoration.Underline)
                item("S", EntityType.Strike, deco = TextDecoration.LineThrough)
                item("</>", EntityType.Code, mono = true)
                item("■", EntityType.Spoiler)
                item("❝", EntityType.Quote)
                val linked = active(entities, selStart, selEnd, EntityType.TextUrl)
                Box(Modifier.height(40.dp).width(50.dp).fadeClickable { if (linked) onLink("") else onLinkMode(true) }, contentAlignment = Alignment.Center) {
                    T("Link", TgTheme.type.subheadline, if (linked) c.accent else c.text, weight = FontWeight.Medium, maxLines = 1)
                }
            }
        }
    }
}
