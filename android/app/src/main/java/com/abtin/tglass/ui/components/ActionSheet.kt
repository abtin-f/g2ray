package com.abtin.tglass.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.sp
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.glass.glass
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle

/**
 * One button of an alert / action sheet. [onClickChecked] (when set) is called instead of [onClick]
 * with the state of the request's [SheetRequest.checkbox].
 */
class SheetAction(
    val title: String,
    val destructive: Boolean = false,
    val bold: Boolean = false,
    val onClickChecked: ((Boolean) -> Unit)? = null,
    val onClick: () -> Unit = {},
)

/** An iOS alert check option such as "Also delete for Anna". */
class SheetCheckbox(val title: String, val initial: Boolean = false)

class SheetRequest(
    val title: String? = null,
    val message: String? = null,
    val actions: List<SheetAction>,
    val alert: Boolean = false,
    val cancel: String? = "Cancel",
    /** Alert only: a check row between the message and the buttons. */
    val checkbox: SheetCheckbox? = null,
)

class ActionSheetState {
    var request by mutableStateOf<SheetRequest?>(null)
        private set
    var visible by mutableStateOf(false)
        private set

    /** Current state of [SheetRequest.checkbox]. */
    var checked by mutableStateOf(false)

    fun show(r: SheetRequest) {
        request = r
        checked = r.checkbox?.initial ?: false
        visible = true
    }

    fun dismiss() {
        visible = false
    }
}

val LocalActionSheet = staticCompositionLocalOf<ActionSheetState> { error("ActionSheetState not provided") }

/** UIAlertController in both .actionSheet and .alert styles, drawn as iOS 26 Liquid Glass. */
@Composable
fun ActionSheetHost(state: ActionSheetState) {
    val r = state.request
    RequireRootBackdrop(state.visible)
    BackHandler(enabled = state.visible) { state.dismiss() }
    AnimatedVisibility(state.visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(180))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (r?.alert == true) 0.25f else 0.3f))
                .clickable(remember { MutableInteractionSource() }, null) { if (r?.alert != true) state.dismiss() }
        )
    }
    if (r == null) return
    // The app content recorded by the context-menu host (this host is drawn outside it, so no feedback loop).
    val backdrop = LocalContextMenu.current.backdrop
    fun run(a: SheetAction) {
        state.dismiss()
        val cb = a.onClickChecked
        if (cb != null) cb(state.checked) else a.onClick()
    }
    if (r.alert) {
        AnimatedVisibility(
            state.visible,
            enter = fadeIn(tween(160)) + scaleIn(spring(0.72f, 520f), initialScale = 1.12f),
            exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.94f),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                IosAlert(r, backdrop, state, ::run)
            }
        }
    } else {
        AnimatedVisibility(
            state.visible,
            enter = slideInVertically(spring(0.85f, 420f)) { it } + fadeIn(),
            exit = slideOutVertically(tween(200)) { it } + fadeOut(),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                IosActionSheet(r, backdrop, state, ::run)
            }
        }
    }
}

@Composable
private fun alertSurface(): Color {
    val c = TgTheme.colors
    return if (c.isDark) Color(0xFF1E1E20).copy(alpha = 0.72f) else Color(0xFFF7F7F9).copy(alpha = 0.74f)
}

/** iOS 26 alert: centered glass card, big corner radius, capsule buttons (side by side for two, stacked otherwise). */
@Composable
private fun IosAlert(r: SheetRequest, backdrop: Backdrop?, state: ActionSheetState, run: (SheetAction) -> Unit) {
    val c = TgTheme.colors
    val cancel = r.cancel?.let { SheetAction(it) { } }
    val all = r.actions + listOfNotNull(cancel)
    Column(
        Modifier
            .widthIn(max = 300.dp)
            .fillMaxWidth(0.78f)
            // Swallow taps so the dim layer behind doesn't get them.
            .clickable(remember { MutableInteractionSource() }, null) {}
            .glass(shape = RoundedRectangle(34.dp), backdrop = backdrop, surface = alertSurface(), blurRadius = 16.dp, lensHeight = 18.dp, lensAmount = 26.dp)
            .padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()).padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            r.title?.let { T(it, TgTheme.type.headline.copy(fontSize = 17.sp), c.text, weight = FontWeight.SemiBold, align = TextAlign.Center) }
            r.message?.let {
                Spacer(Modifier.height(if (r.title != null) 6.dp else 0.dp))
                T(it, TgTheme.type.subheadline.copy(fontSize = 15.sp), c.text.copy(alpha = 0.86f), align = TextAlign.Center)
            }
        }
        r.checkbox?.let { cb ->
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .clip(Capsule())
                    .clickable(remember { MutableInteractionSource() }, null) { state.checked = !state.checked }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CheckCircle(state.checked)
                Spacer(Modifier.width(10.dp))
                T(cb.title, TgTheme.type.body.copy(fontSize = 16.sp), c.text, maxLines = 2)
            }
        }
        Spacer(Modifier.height(18.dp))
        if (all.size == 2) {
            // Cancel on the left, the action on the right (UIAlertController order).
            val ordered = if (cancel != null) listOf(cancel, r.actions.first()) else all
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ordered.forEach { a -> AlertButton(a, isCancel = a === cancel, Modifier.weight(1f)) { run(a) } }
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                all.forEach { a -> AlertButton(a, isCancel = a === cancel, Modifier.fillMaxWidth()) { run(a) } }
            }
        }
    }
}

@Composable
private fun CheckCircle(checked: Boolean) {
    val c = TgTheme.colors
    Box(
        Modifier
            .size(22.dp)
            .clip(Capsule())
            .then(
                if (checked) Modifier.background(c.accent)
                else Modifier.border(1.5.dp, c.secondaryText.copy(alpha = 0.6f), Capsule())
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(IosIcons.Checkmark, Color.White, 14.dp)
    }
}

@Composable
private fun AlertButton(a: SheetAction, isCancel: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = TgTheme.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    // iOS 26: the preferred (bold, non-destructive) action is a filled accent capsule, the rest are gray capsules.
    val primary = a.bold && !a.destructive && !isCancel
    val base = when {
        primary -> c.accent
        c.isDark -> Color.White.copy(alpha = 0.12f)
        else -> Color.Black.copy(alpha = 0.06f)
    }
    val fill = if (pressed) (if (primary) c.accent.copy(alpha = 0.8f) else if (c.isDark) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.12f)) else base
    val textColor = when {
        primary -> Color.White
        a.destructive -> c.destructive
        else -> c.text
    }
    Box(
        modifier
            .height(48.dp)
            .clip(Capsule())
            .background(fill)
            .clickable(source, null, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        T(a.title, TgTheme.type.body.copy(fontSize = 17.sp), textColor, weight = if (a.bold || primary) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, align = TextAlign.Center)
    }
}

/** iOS 26 action sheet: a glass card of actions with a separate glass Cancel capsule. */
@Composable
private fun IosActionSheet(r: SheetRequest, backdrop: Backdrop?, state: ActionSheetState, run: (SheetAction) -> Unit) {
    val c = TgTheme.colors
    val surface = alertSurface()
    Column(Modifier.navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(remember { MutableInteractionSource() }, null) {}
                .glass(shape = RoundedRectangle(28.dp), backdrop = backdrop, surface = surface, blurRadius = 16.dp, lensHeight = 16.dp, lensAmount = 22.dp)
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (r.title != null || r.message != null) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    r.title?.let { T(it, TgTheme.type.footnote, c.secondaryText, weight = FontWeight.SemiBold, align = TextAlign.Center) }
                    r.message?.let { T(it, TgTheme.type.footnote, c.secondaryText, align = TextAlign.Center) }
                }
            }
            r.actions.forEachIndexed { i, a ->
                if (i > 0 || r.title != null || r.message != null) Separator()
                SheetButton(a, Modifier.fillMaxWidth()) { run(a) }
            }
        }
        if (r.cancel != null) {
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .glass(shape = RoundedRectangle(28.dp), backdrop = backdrop, surface = surface, blurRadius = 16.dp, lensHeight = 16.dp, lensAmount = 22.dp)
            ) {
                SheetButton(SheetAction(r.cancel, bold = true), Modifier.fillMaxWidth()) { state.dismiss() }
            }
        }
    }
}

@Composable
private fun SheetButton(a: SheetAction, modifier: Modifier, onClick: () -> Unit) {
    val c = TgTheme.colors
    Box(
        modifier
            .iosClickable(highlight = if (c.isDark) Color.White.copy(0.1f) else Color.Black.copy(0.06f), onClick = onClick)
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
