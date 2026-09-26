package com.abtin.tglass.features.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import com.abtin.tglass.core.glass.GlassBox
import com.abtin.tglass.core.glass.LocalBackdrop
import com.abtin.tglass.features.chat.ChatWallpaper
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.Capsule
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abtin.tglass.core.design.LocalAppSettings
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.navigation.LocalNavigator
import com.abtin.tglass.core.navigation.Route
import com.abtin.tglass.ui.components.GlassTopBar
import com.abtin.tglass.ui.components.PrimaryButton
import com.abtin.tglass.ui.components.Separator
import com.abtin.tglass.ui.components.T
import com.abtin.tglass.ui.components.LottieLoop
import com.abtin.tglass.ui.components.TgAnimations
import com.abtin.tglass.ui.components.TextButton
import com.abtin.tglass.ui.components.fadeClickable
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.delay

/** Telegram-like paper plane logo drawn in code. */
@Composable
fun PlaneLogo(size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Brush.verticalGradient(listOf(Color(0xFF37AEE2), Color(0xFF1E96C8)))),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size * 0.55f)) {
            val w = this.size.width
            val h = this.size.height
            val p = Path().apply {
                moveTo(w * 0.04f, h * 0.47f)
                lineTo(w * 0.93f, h * 0.1f)
                lineTo(w * 0.78f, h * 0.88f)
                lineTo(w * 0.52f, h * 0.68f)
                lineTo(w * 0.36f, h * 0.84f)
                lineTo(w * 0.36f, h * 0.62f)
                lineTo(w * 0.78f, h * 0.25f)
                lineTo(w * 0.28f, h * 0.57f)
                close()
            }
            drawPath(p, Color.White)
        }
    }
}

/** Spec §47 Welcome: Telegram's animated gradient behind a glass logo, content on a sheet. */
@Composable
fun WelcomeScreen() {
    val nav = LocalNavigator.current
    val c = TgTheme.colors
    val t = rememberInfiniteTransition(label = "logo")
    val bob by t.animateFloat(-6f, 6f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "bob")
    var phase by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1600)
            phase++
        }
    }
    val backdrop = rememberLayerBackdrop()
    var page by remember { mutableIntStateOf(0) }
    val pages = listOf(
        "TGlass" to "The world's fastest messaging app.\nIt is free and secure.",
        "Fast" to "TGlass delivers messages faster\nthan any other application.",
        "Liquid Glass" to "A living interface that refracts\nand reacts to your touch.",
        "Powerful" to "No limits on the size of your\nmedia and chats.",
    )
    LaunchedEffect(Unit) {
        while (true) {
            delay(3500)
            page = (page + 1) % pages.size
        }
    }
    CompositionLocalProvider(LocalBackdrop provides backdrop) {
        Box(Modifier.fillMaxSize().background(c.background)) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.62f).layerBackdrop(backdrop)) {
                ChatWallpaper(phase = phase)
            }
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.62f), contentAlignment = Alignment.Center) {
                // Telegram-iOS intro animations (PlaneLogo / IntroMessage / IntroPhone / IntroLetter).
                GlassBox(
                    onClick = {},
                    shape = Capsule(),
                    modifier = Modifier.size(196.dp).graphicsLayer { translationY = bob * density },
                ) {
                    AnimatedContent(
                        targetState = page,
                        transitionSpec = { (fadeIn(tween(350)) + scaleIn(initialScale = 0.8f)) togetherWith (fadeOut(tween(200)) + scaleOut(targetScale = 0.8f)) },
                        label = "introAnim",
                    ) { p ->
                        val anim = listOf(TgAnimations.PlaneLogo, TgAnimations.IntroMessage, TgAnimations.IntroPhone, TgAnimations.IntroLetter)[p]
                        LottieLoop(anim, if (p == 0) 150.dp else 160.dp)
                    }
                }
            }
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.42f)
                    .clip(RoundedRectangle(34.dp))
                    .background(c.background)
                    .navigationBarsPadding()
                    .padding(horizontal = 28.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedContent(
                    targetState = page,
                    transitionSpec = { (fadeIn(tween(300)) + slideInHorizontally { it / 4 }) togetherWith (fadeOut(tween(200)) + slideOutHorizontally { -it / 4 }) },
                    label = "welcomePage",
                ) { p ->
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        T(pages[p].first, TgTheme.type.largeTitle.copy(fontSize = 30.sp), c.text, align = TextAlign.Center)
                        Spacer(Modifier.height(10.dp))
                        T(pages[p].second, TgTheme.type.body, c.secondaryText, align = TextAlign.Center)
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    repeat(pages.size) { i ->
                        val w by animateDpAsState(if (i == page) 18.dp else 7.dp, label = "dot")
                        Box(Modifier.size(width = w, height = 7.dp).clip(CircleShape).background(if (i == page) c.accent else c.tertiaryText))
                    }
                }
                Spacer(Modifier.weight(1f))
                PrimaryButton("Start Messaging", { nav.push(Route.Phone) })
            }
        }
    }
}

/** Spec §47 Phone number. */
@Composable
fun PhoneScreen() {
    val nav = LocalNavigator.current
    val c = TgTheme.colors
    var code by rememberSaveable { mutableStateOf("98") }
    var number by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().padding(top = 70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            T("Your Phone", TgTheme.type.title1, c.text)
            Spacer(Modifier.height(10.dp))
            T("Please confirm your country code\nand enter your phone number.", TgTheme.type.body, c.secondaryText, align = TextAlign.Center)
            Spacer(Modifier.height(30.dp))
            Separator()
            Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                T("🇮🇷  Iran", TgTheme.type.body, c.accent, modifier = Modifier.weight(1f))
            }
            Separator(startPadding = 20.dp)
            Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                T("+", TgTheme.type.body.copy(fontSize = 20.sp), c.text)
                BasicTextField(code, { code = it.filter(Char::isDigit).take(4) }, Modifier.width(48.dp), textStyle = TgTheme.type.body.copy(fontSize = 20.sp, color = c.text), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), cursorBrush = SolidColor(c.accent))
                Box(Modifier.width(0.5.dp).height(30.dp).background(c.separator))
                Spacer(Modifier.width(14.dp))
                Box(Modifier.weight(1f)) {
                    if (number.isEmpty()) T("Phone number", TgTheme.type.body.copy(fontSize = 20.sp), c.tertiaryText)
                    BasicTextField(number, { number = it.filter(Char::isDigit).take(12) }, Modifier.fillMaxWidth().focusRequester(focus),
                        textStyle = TgTheme.type.body.copy(fontSize = 20.sp, color = c.text), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), cursorBrush = SolidColor(c.accent))
                }
            }
            Separator()
            Spacer(Modifier.weight(1f))
            PrimaryButton("Continue", { nav.push(Route.Code("+$code $number")) }, Modifier.padding(horizontal = 24.dp, vertical = 16.dp), enabled = number.length >= 7)
        }
        GlassTopBar(title = null, fade = Color.Transparent)
    }
}

/** Spec §47 verification code. Demo mode accepts any 5 digits. */
@Composable
fun CodeScreen(phone: String) {
    val nav = LocalNavigator.current
    val settings = LocalAppSettings.current
    val c = TgTheme.colors
    var code by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(code) {
        if (code.length == 5) {
            delay(300)
            settings.updateLoggedIn(true)
            nav.resetTo(Route.Main)
        }
    }
    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 70.dp, start = 24.dp, end = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            T("💬", TgTheme.type.largeTitle.copy(fontSize = 70.sp, lineHeight = 80.sp))
            Spacer(Modifier.height(12.dp))
            T(phone, TgTheme.type.title1, c.text)
            Spacer(Modifier.height(10.dp))
            T("We've sent the code to the Telegram app\non your other device.\n(Demo mode: enter any 5 digits.)", TgTheme.type.body, c.secondaryText, align = TextAlign.Center)
            Spacer(Modifier.height(30.dp))
            Box {
                BasicTextField(code, { code = it.filter(Char::isDigit).take(5) }, Modifier.size(1.dp).focusRequester(focus),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(5) { i ->
                        val ch = code.getOrNull(i)
                        Box(
                            Modifier
                                .size(48.dp, 56.dp)
                                .clip(RoundedRectangle(12.dp))
                                .background(c.searchField)
                                .border(if (i == code.length) 2.dp else 0.dp, if (i == code.length) c.accent else Color.Transparent, RoundedRectangle(12.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            T(ch?.toString() ?: "", TgTheme.type.title2, c.text, weight = FontWeight.SemiBold)
                        }
                    }
                }
                Box(Modifier.matchParentSize().fadeClickable { focus.requestFocus() })
            }
            Spacer(Modifier.height(24.dp))
            TextButton("Didn't get the code?", {})
        }
        GlassTopBar(title = null, fade = Color.Transparent)
    }
}
