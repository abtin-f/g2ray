package com.abtin.tglass.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.glass.GlassBox
import com.abtin.tglass.core.glass.GlassIconButton
import com.abtin.tglass.core.navigation.LocalNavigator
import com.kyant.shapes.Capsule

/**
 * iOS 26 navigation bar: floating glass controls over a soft fade instead of an opaque bar (spec §11).
 */
@Composable
fun GlassTopBar(
    title: String?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    fade: Color = TgTheme.colors.background,
    showBack: Boolean = true,
    onBack: (() -> Unit)? = null,
    left: (@Composable RowScope.() -> Unit)? = null,
    right: (@Composable RowScope.() -> Unit)? = null,
    center: (@Composable BoxScope.() -> Unit)? = null,
) {
    val nav = LocalNavigator.current
    Box(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(fade, fade.copy(alpha = 0.85f), fade.copy(alpha = 0f))))
            .statusBarsPadding()
            .padding(bottom = 10.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 12.dp)) {
            Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
                if (left != null) left()
                else if (showBack) GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBackIos, onClick = onBack ?: { nav.pop() }, iconSize = 20.dp, tint = TgTheme.colors.text, modifier = Modifier.padding(start = 0.dp))
            }
            Box(Modifier.align(Alignment.Center).padding(horizontal = 72.dp)) {
                if (center != null) center()
                else if (title != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    T(title, TgTheme.type.headline, TgTheme.colors.text, maxLines = 1, align = TextAlign.Center)
                    if (subtitle != null) T(subtitle, TgTheme.type.footnote, TgTheme.colors.secondaryText, maxLines = 1)
                }
            }
            Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                if (right != null) right()
            }
        }
    }
}

/** Text pill in glass ("Edit", "Done"). */
@Composable
fun GlassTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = TgTheme.colors.text, bold: Boolean = false) {
    GlassBox(onClick = onClick, modifier = modifier.height(44.dp), shape = Capsule()) {
        T(
            text, TgTheme.type.body, color,
            weight = if (bold) androidx.compose.ui.text.font.FontWeight.SemiBold else null,
            modifier = Modifier.padding(horizontal = 16.dp), maxLines = 1,
        )
    }
}
