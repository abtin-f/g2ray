package com.abtin.tglass.core.perf

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Extra context for the overlay label (e.g. the selected main tab), set by screens with a SideEffect. */
object PerfLabel {
    var detail by mutableStateOf("")
}

/**
 * Optional frame-rate readout (Settings → Power Saving → Performance Overlay), off by default.
 *
 * Uses the window's frame metrics (the same data as `dumpsys gfxinfo`): one tiny callback per *drawn* frame, nothing
 * while the screen is idle, so it reports the real cost of scrolling and animations. Once a second it shows the
 * number of frames drawn, the slowest frame (UI work + render thread hand-off) and how many frames missed the
 * display's frame budget in that second.
 */
@Composable
fun PerfOverlay(screen: () -> String = { "" }) {
    val view = LocalView.current
    var text by remember { mutableStateOf("-- fps") }
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        if (window == null) {
            onDispose { }
        } else {
            val handler = Handler(Looper.getMainLooper())
            val budgetNs = (1_000_000_000f / (view.display?.refreshRate ?: 60f).coerceAtLeast(30f)).toLong() + 1_500_000L
            var frames = 0
            var slow = 0
            var worst = 0L
            var windowStart = 0L
            val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
                val now = System.nanoTime()
                val total = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
                if (windowStart == 0L) windowStart = now
                frames++
                if (total > worst) worst = total
                if (total > budgetNs) slow++
                if (now - windowStart >= 1_000_000_000L) {
                    val fps = (frames * 1_000_000_000L / (now - windowStart)).toInt()
                    text = "$fps fps  worst ${worst / 1_000_000} ms  slow $slow"
                    frames = 0
                    slow = 0
                    worst = 0L
                    windowStart = now
                }
            }
            window.addOnFrameMetricsAvailableListener(listener, handler)
            onDispose { window.removeOnFrameMetricsAvailableListener(listener) }
        }
    }
    Box(Modifier.fillMaxSize()) {
        BasicText(
            // The route label is read here only, so a screen change recomposes just this text.
            screen().let { s -> val d = PerfLabel.detail; if (s.isEmpty()) text else if (d.isEmpty() || s != "Main") "$s\n$text" else "$s/$d\n$text" },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 2.dp, end = 6.dp)
                .background(Color(0xB3000000))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            style = TextStyle(color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
        )
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
