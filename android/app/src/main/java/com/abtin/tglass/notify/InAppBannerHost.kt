package com.abtin.tglass.notify

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.glass.GlassBox
import com.abtin.tglass.ui.components.Avatar
import com.abtin.tglass.ui.components.Haptics
import com.abtin.tglass.ui.components.T
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.delay

/** iOS-style in-app notification: a glass card that drops in from the top for a few seconds. */
@Composable
fun InAppBannerHost(onOpen: (Long) -> Unit) {
    val view = LocalView.current
    var current by remember { mutableStateOf<Banner?>(null) }
    var visible by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        InAppAlerts.attach(view.context)
        InAppBanners.flow.collect { b ->
            current = b
            visible = true
            shown++
            if (InAppAlerts.vibrate) Haptics.tick(view)
            InAppAlerts.playSound(view.context)
        }
    }
    LaunchedEffect(shown) {
        if (shown > 0) {
            delay(3_500)
            visible = false
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible && current != null,
            enter = slideInVertically(spring(dampingRatio = 0.75f, stiffness = 400f)) { -it * 2 } + fadeIn(),
            exit = slideOutVertically { -it * 2 } + fadeOut(),
        ) {
            val b = current ?: return@AnimatedVisibility
            GlassBox(
                onClick = { visible = false; onOpen(b.chatId) },
                shape = RoundedRectangle(26.dp),
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .fillMaxWidth()
                    .pointerInput(Unit) { detectVerticalDragGestures { _, dy -> if (dy < -8f) visible = false } },
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(b.title, b.peerId, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        T(b.title, TgTheme.type.headline, TgTheme.colors.text, maxLines = 1)
                        T(b.text, TgTheme.type.subheadline, TgTheme.colors.secondaryText, maxLines = 2)
                    }
                }
            }
        }
    }
}
