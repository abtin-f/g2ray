package com.abtin.tglass.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abtin.tglass.core.design.TgTheme
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle

/** Telegram's 7 avatar gradients (top → bottom). */
private val AvatarGradients = listOf(
    Color(0xFFFF885E) to Color(0xFFFF516A),
    Color(0xFFFFCD6A) to Color(0xFFFFA85C),
    Color(0xFF82B1FF) to Color(0xFF665FFF),
    Color(0xFFA0DE7E) to Color(0xFF54CB68),
    Color(0xFF53EDD6) to Color(0xFF28C9B7),
    Color(0xFF72D5FD) to Color(0xFF2A9EF1),
    Color(0xFFE0A2F3) to Color(0xFFD669ED),
)

fun avatarColors(seed: Long): Pair<Color, Color> = AvatarGradients[(abs(seed) % AvatarGradients.size).toInt()]

private fun abs(v: Long) = if (v < 0) -v else v

fun initials(name: String): String {
    val parts = name.split(' ').filter { it.isNotBlank() && it.first().isLetterOrDigit() }
    return when {
        parts.isEmpty() -> name.take(1)
        parts.size == 1 -> parts[0].take(1)
        else -> parts[0].take(1) + parts[1].take(1)
    }.uppercase()
}

@Composable
fun Avatar(
    name: String,
    seed: Long,
    size: Dp,
    modifier: Modifier = Modifier,
    saved: Boolean = false,
    icon: ImageVector? = null,
    iconColors: Pair<Color, Color>? = null,
    online: Boolean = false,
    storyRing: StoryRing = StoryRing.None,
) {
    val colors = when {
        saved -> Color(0xFF72D5FD) to Color(0xFF2A9EF1)
        iconColors != null -> iconColors
        else -> avatarColors(seed)
    }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        val inner = if (storyRing != StoryRing.None) size - 7.dp else size
        if (storyRing != StoryRing.None) {
            val brush = if (storyRing == StoryRing.Unseen)
                Brush.linearGradient(listOf(Color(0xFF34C76F), Color(0xFF3DA1FD)))
            else SolidColor(TgTheme.colors.tertiaryText)
            Box(
                Modifier
                    .size(size)
                    .border(if (storyRing == StoryRing.Unseen) 2.dp else 1.5.dp, brush, CircleShape)
            )
        }
        Box(
            Modifier
                .size(inner)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(colors.first, colors.second))),
            contentAlignment = Alignment.Center,
        ) {
            when {
                saved -> Icon(Icons.Rounded.Bookmark, Color.White, inner * 0.5f)
                icon != null -> Icon(icon, Color.White, inner * 0.52f)
                else -> T(
                    initials(name),
                    TgTheme.type.body.copy(fontSize = (inner.value * 0.4f).sp, lineHeight = (inner.value * 0.44f).sp),
                    Color.White,
                    weight = FontWeight.SemiBold,
                )
            }
        }
        if (online) {
            val dot = (size.value * 0.27f).dp
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-1).dp, y = (-1).dp)
                    .size(dot)
                    .clip(CircleShape)
                    .background(TgTheme.colors.background)
                    .padding(dot * 0.18f)
                    .clip(CircleShape)
                    .background(TgTheme.colors.onlineDot)
            )
        }
    }
}

enum class StoryRing { None, Unseen, Seen }

/** Unread counter capsule. */
@Composable
fun Badge(count: Int, muted: Boolean = false, modifier: Modifier = Modifier, mention: Boolean = false) {
    val c = TgTheme.colors
    Box(
        modifier
            .defaultMinSize(minWidth = 20.dp)
            .height(20.dp)
            .clip(Capsule())
            .background(if (muted) c.mutedBadge else c.accent)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        T(
            if (mention) "@" else formatCount(count),
            TgTheme.type.footnote.copy(fontSize = 13.sp, lineHeight = 16.sp),
            Color.White,
            weight = FontWeight.SemiBold,
        )
    }
}

fun formatCount(n: Int): String = when {
    n >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", n / 1_000_000f).replace(".0M", "M")
    n >= 10_000 -> "${n / 1000}K"
    n >= 1_000 -> String.format(java.util.Locale.US, "%.1fK", n / 1000f).replace(".0K", "K")
    else -> n.toString()
}

/** iOS search field (spec §9). */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    focusRequester: FocusRequester = remember { FocusRequester() },
    onFocus: (Boolean) -> Unit = {},
    background: Color = TgTheme.colors.searchField,
) {
    val c = TgTheme.colors
    Row(
        modifier
            .height(36.dp)
            .clip(RoundedRectangle(10.dp))
            .background(background)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, c.secondaryText, 20.dp)
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) T(placeholder, TgTheme.type.body, c.secondaryText, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TgTheme.type.body.copy(color = c.text),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { onFocus(it.isFocused) },
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Rounded.Cancel, c.secondaryText, 18.dp,
                Modifier.clickable(remember { MutableInteractionSource() }, null) { onValueChange("") },
            )
        }
    }
}

/** Grouped (inset) section like UITableView.insetGrouped (spec §37). */
@Composable
fun Section(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = TgTheme.colors
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (header != null) {
            T(
                header.uppercase(), TgTheme.type.footnote, c.secondaryText,
                modifier = Modifier.padding(start = 16.dp, bottom = 7.dp, top = 4.dp),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedRectangle(26.dp))
                .background(c.cell),
            content = content,
        )
        if (footer != null) {
            T(footer, TgTheme.type.footnote, c.secondaryText, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 7.dp))
        }
    }
}

/** Colored rounded-square settings icon. */
@Composable
fun SettingsIcon(icon: ImageVector, color: Color, size: Dp = 30.dp) {
    Box(
        Modifier.size(size).clip(RoundedRectangle(8.dp)).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, Color.White, size * 0.66f)
    }
}

/** A row in a grouped section. */
@Composable
fun Cell(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconColor: Color = TgTheme.colors.accent,
    value: String? = null,
    subtitle: String? = null,
    titleColor: Color = TgTheme.colors.text,
    chevron: Boolean = true,
    checked: Boolean = false,
    divider: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = TgTheme.colors
    val textStart = if (icon != null || leading != null) 60.dp else 16.dp
    Box(Modifier.fillMaxWidth()) {
        Row(
            modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.iosClickable(onClick = onClick) else Modifier)
                .heightIn(min = if (subtitle != null) 58.dp else 50.dp)
                .padding(start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(14.dp))
            } else if (icon != null) {
                SettingsIcon(icon, iconColor)
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                T(title, TgTheme.type.body, titleColor, maxLines = 1)
                if (subtitle != null) T(subtitle, TgTheme.type.subheadline, c.secondaryText, maxLines = 2)
            }
            if (value != null) {
                T(value, TgTheme.type.body, c.secondaryText, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            } else if (checked) {
                Icon(Icons.Rounded.Check, c.accent, 22.dp)
            } else if (chevron && onClick != null) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, c.tertiaryText, 14.dp)
            }
        }
        if (divider) Separator(Modifier.align(Alignment.BottomStart), startPadding = textStart)
    }
}

/** UISegmentedControl. */
@Composable
fun SegmentedControl(
    items: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = TgTheme.colors
    BoxWithConstraints(
        modifier
            .height(34.dp)
            .clip(Capsule())
            .background(c.searchField)
            .padding(2.dp)
    ) {
        val segment = maxWidth / items.size
        val x by animateFloatAsState(selected.toFloat(), label = "seg")
        Box(
            Modifier
                .graphicsLayer { translationX = x * segment.toPx() }
                .width(segment)
                .fillMaxHeight()
                .clip(Capsule())
                .background(if (c.isDark) Color(0xFF636366) else Color.White)
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            items.forEachIndexed { i, s ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(remember { MutableInteractionSource() }, null) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    T(s, TgTheme.type.footnote.copy(fontSize = 14.sp), c.text, weight = if (i == selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
                }
            }
        }
    }
}

/** Horizontal chip tabs (chat folders, search filters, shared media). */
@Composable
fun ChipTabs(
    items: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = TgTheme.colors
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(10.dp))
        items.forEachIndexed { i, s ->
            val sel = i == selected
            Box(
                Modifier
                    .height(32.dp)
                    .clip(Capsule())
                    .background(if (sel) c.accent.copy(alpha = if (c.isDark) 0.28f else 0.12f) else Color.Transparent)
                    .clickable(remember { MutableInteractionSource() }, null) { onSelect(i) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                T(s, TgTheme.type.subheadline, if (sel) c.accent else c.secondaryText, weight = FontWeight.SemiBold, maxLines = 1)
            }
        }
        Spacer(Modifier.width(10.dp))
    }
}

/** Primary filled button (Start Messaging, Continue...). */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = TgTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(Capsule())
            .background(if (enabled) c.accent else c.accent.copy(alpha = 0.4f))
            .then(if (enabled) Modifier.bounceClickable(onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        T(text, TgTheme.type.headline, Color.White, align = TextAlign.Center)
    }
}

/** Empty state placeholder (spec §53). */
@Composable
fun EmptyState(emoji: String, title: String, subtitle: String, modifier: Modifier = Modifier) {
    val c = TgTheme.colors
    Column(modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        T(emoji, TgTheme.type.largeTitle.copy(fontSize = 64.sp, lineHeight = 72.sp))
        Spacer(Modifier.height(12.dp))
        T(title, TgTheme.type.title3, c.text, align = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        T(subtitle, TgTheme.type.subheadline, c.secondaryText, align = TextAlign.Center)
    }
}
