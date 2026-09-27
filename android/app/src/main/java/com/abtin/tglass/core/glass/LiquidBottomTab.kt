// Adapted from Kyant0/AndroidLiquidGlass 1.0.6 catalog (Apache License 2.0).
// https://github.com/Kyant0/AndroidLiquidGlass
package com.abtin.tglass.core.glass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule

internal val LocalLiquidBottomTabScale =
    staticCompositionLocalOf { { 1f } }

/**
 * Which copy of the tabs is being composed: [Base] is the visible row, [Tinted] the accent-tinted copy that
 * shows through the selection pill, [Overlay] a row drawn above the pill for things that must not be tinted
 * (unread badges). Tab content reads it to decide what to draw.
 */
enum class LiquidBottomTabLayer { Base, Tinted, Overlay }

val LocalLiquidBottomTabLayer =
    staticCompositionLocalOf { LiquidBottomTabLayer.Base }

@Composable
fun RowScope.LiquidBottomTab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val scale = LocalLiquidBottomTabScale.current
    val overlay = LocalLiquidBottomTabLayer.current == LiquidBottomTabLayer.Overlay
    Column(
        modifier
            .then(
                // The overlay copy must not take touches from the pill below it.
                if (overlay) Modifier
                else Modifier
                    .clip(Capsule())
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        role = Role.Tab,
                        onClick = onClick
                    )
            )
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val scale = scale()
                scaleX = scale
                scaleY = scale
            },
        verticalArrangement = Arrangement.spacedBy(1f.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}
