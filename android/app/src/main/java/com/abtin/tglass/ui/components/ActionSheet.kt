package com.abtin.tglass.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.abtin.tglass.core.design.TgTheme
import com.kyant.shapes.RoundedRectangle

class SheetAction(val title: String, val destructive: Boolean = false, val bold: Boolean = false, val onClick: () -> Unit = {})

class SheetRequest(
    val title: String? = null,
    val message: String? = null,
    val actions: List<SheetAction>,
    val alert: Boolean = false,
    val cancel: String? = "Cancel",
)

class ActionSheetState {
    var request by mutableStateOf<SheetRequest?>(null)
        private set
    var visible by mutableStateOf(false)
        private set

    fun show(r: SheetRequest) {
        request = r
        visible = true
    }

    fun dismiss() {
        visible = false
    }
}

val LocalActionSheet = staticCompositionLocalOf<ActionSheetState> { error("ActionSheetState not provided") }

/** UIAlertController in both .actionSheet and .alert styles. */
@Composable
fun ActionSheetHost(state: ActionSheetState) {
    val c = TgTheme.colors
    val r = state.request
    BackHandler(enabled = state.visible) { state.dismiss() }
    AnimatedVisibility(state.visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
                .clickable(remember { MutableInteractionSource() }, null) { state.dismiss() }
        )
    }
    if (r == null) return
    val fill = if (c.isDark) Color(0xFF2C2C2E) else Color(0xFFF9F9F9)
    if (r.alert) {
        AnimatedVisibility(state.visible, enter = fadeIn() + scaleIn(initialScale = 1.15f), exit = fadeOut() + scaleOut(targetScale = 0.9f)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    Modifier
                        .width(290.dp)
                        .clip(RoundedRectangle(28.dp))
                        .background(fill),
                ) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        r.title?.let { T(it, TgTheme.type.headline, c.text, align = TextAlign.Center) }
                        r.message?.let {
                            Spacer(Modifier.height(4.dp))
                            T(it, TgTheme.type.footnote, c.text, align = TextAlign.Center)
                        }
                    }
                    val all = r.actions + listOfNotNull(r.cancel?.let { SheetAction(it, bold = true) })
                    if (all.size == 2) {
                        Separator()
                        Row(Modifier.height(48.dp)) {
                            all.reversed().forEachIndexed { i, a ->
                                if (i > 0) Box(Modifier.width(0.33.dp).height(48.dp).background(c.separator))
                                SheetButton(a, Modifier.weight(1f)) { state.dismiss(); a.onClick() }
                            }
                        }
                    } else all.forEach { a ->
                        Separator()
                        SheetButton(a, Modifier.fillMaxWidth()) { state.dismiss(); a.onClick() }
                    }
                }
            }
        }
    } else {
        AnimatedVisibility(
            state.visible,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Column(Modifier.navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp)) {
                    Column(Modifier.fillMaxWidth().clip(RoundedRectangle(20.dp)).background(fill)) {
                        if (r.title != null || r.message != null) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                r.title?.let { T(it, TgTheme.type.footnote, c.secondaryText, weight = FontWeight.SemiBold, align = TextAlign.Center) }
                                r.message?.let { T(it, TgTheme.type.footnote, c.secondaryText, align = TextAlign.Center) }
                            }
                        }
                        r.actions.forEachIndexed { i, a ->
                            if (i > 0 || r.title != null || r.message != null) Separator()
                            SheetButton(a, Modifier.fillMaxWidth()) { state.dismiss(); a.onClick() }
                        }
                    }
                    if (r.cancel != null) {
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth().clip(RoundedRectangle(20.dp)).background(fill)) {
                            SheetButton(SheetAction(r.cancel, bold = true), Modifier.fillMaxWidth()) { state.dismiss() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetButton(a: SheetAction, modifier: Modifier, onClick: () -> Unit) {
    val c = TgTheme.colors
    Box(
        modifier
            .iosClickable(onClick = onClick)
            .height(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        T(
            a.title, TgTheme.type.body.copy(fontSize = TgTheme.type.body.fontSize * 1.06f),
            if (a.destructive) c.destructive else c.accent,
            weight = if (a.bold) FontWeight.SemiBold else null,
            maxLines = 1,
        )
    }
}
