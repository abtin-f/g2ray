package com.abtin.tglass.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.abtin.tglass.core.design.TgTheme
import com.kyant.shapes.Capsule
import kotlinx.coroutines.delay

class ToastState {
    var text by mutableStateOf<String?>(null)
        private set
    var icon by mutableStateOf<ImageVector>(Icons.Rounded.CheckCircle)
        private set
    var visible by mutableStateOf(false)
        private set
    internal var serial by mutableIntStateOf(0)

    fun show(text: String, icon: ImageVector = Icons.Rounded.CheckCircle) {
        this.text = text
        this.icon = icon
        visible = true
        serial++
    }

    internal fun hide() {
        visible = false
    }
}

val LocalToast = staticCompositionLocalOf<ToastState> { error("ToastState not provided") }

@Composable
fun ToastHost(state: ToastState) {
    val c = TgTheme.colors
    LaunchedEffect(state.serial) {
        if (state.visible) {
            delay(2000)
            state.hide()
        }
    }
    Box(Modifier.fillMaxSize().statusBarsPadding().padding(top = 60.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(state.visible, enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
            Row(
                Modifier
                    .shadow(20.dp, Capsule(), clip = false, ambientColor = Color.Black.copy(0.2f), spotColor = Color.Black.copy(0.2f))
                    .clip(Capsule())
                    .background(if (c.isDark) Color(0xFF2C2C2E) else Color(0xFF333333).copy(alpha = 0.92f))
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(state.icon, Color.White, 20.dp)
                Spacer(Modifier.width(8.dp))
                T(state.text ?: "", TgTheme.type.subheadline, Color.White, maxLines = 2)
            }
        }
    }
}
