package com.abtin.tglass.features.auth

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abtin.tglass.core.design.LocalAppSettings
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.navigation.LocalNavigator
import com.abtin.tglass.core.navigation.Route
import com.abtin.tglass.data.td.AuthStep
import com.abtin.tglass.data.td.TdConfig
import com.abtin.tglass.data.td.TdRepository
import com.abtin.tglass.features.main.LocalRepository
import com.abtin.tglass.ui.components.GlassTopBar
import com.abtin.tglass.ui.components.Haptics
import com.abtin.tglass.ui.components.Icon
import com.abtin.tglass.ui.components.IosIcons
import com.abtin.tglass.ui.components.LottieLoop
import com.abtin.tglass.ui.components.PrimaryButton
import com.abtin.tglass.ui.components.Separator
import com.abtin.tglass.ui.components.T
import com.abtin.tglass.ui.components.TextButton
import com.abtin.tglass.ui.components.TgAnimations
import com.abtin.tglass.ui.components.fadeClickable
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The live TDLib repository, or null in demo mode. */
@Composable
private fun liveRepo(): TdRepository? = LocalRepository.current as? TdRepository

/** Shared layout of the sign-in pages: illustration, title, explanation, content, bottom button. */
@Composable
private fun AuthPage(
    title: String,
    subtitle: String,
    art: @Composable () -> Unit,
    error: String?,
    button: (@Composable () -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = TgTheme.colors
    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                art()
                Spacer(Modifier.height(14.dp))
                T(title, TgTheme.type.title1.copy(fontSize = 26.sp, lineHeight = 32.sp), c.text, weight = FontWeight.SemiBold, align = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                T(subtitle, TgTheme.type.body, c.secondaryText, align = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
                Spacer(Modifier.height(28.dp))
                content()
                AnimatedVisibility(error != null, enter = fadeIn(), exit = fadeOut()) {
                    T(error ?: "", TgTheme.type.subheadline, c.destructive, align = TextAlign.Center, modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp))
                }
            }
            if (button != null) Box(Modifier.padding(horizontal = 24.dp, vertical = 16.dp).navigationBarsPadding()) { button() }
        }
        val live = liveRepo()
        val nav = LocalNavigator.current
        GlassTopBar(
            title = null,
            fade = Color.Transparent,
            // Telegram iOS shows proxy settings on the login screens: often the only way to connect.
            right = if (live != null) ({ com.abtin.tglass.ui.components.GlassTextButton("Proxy", { nav.push(Route.Proxy) }) }) else null,
        )
    }
}

/** One row of an iOS grouped input: optional prefix, text field, bottom separator. */
@Composable
private fun AuthField(
    value: String,
    onValue: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    secure: Boolean = false,
    focus: FocusRequester? = null,
    fontSize: Int = 20,
    prefix: (@Composable () -> Unit)? = null,
) {
    val c = TgTheme.colors
    val style = TgTheme.type.body.copy(fontSize = fontSize.sp, color = c.text)
    Row(modifier.fillMaxWidth().height(54.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        prefix?.invoke()
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) T(placeholder, style, c.tertiaryText, maxLines = 1)
            BasicTextField(
                value, onValue,
                Modifier.fillMaxWidth().then(if (focus != null) Modifier.focusRequester(focus) else Modifier),
                textStyle = style,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                visualTransformation = if (secure) PasswordVisualTransformation() else VisualTransformation.None,
                cursorBrush = SolidColor(c.accent),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// API credentials
// ---------------------------------------------------------------------------------------------

/** One-time setup: the user's own api_id / api_hash from my.telegram.org. */
@Composable
fun ApiSetupScreen() {
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val td = liveRepo()
    val c = TgTheme.colors
    val config = remember { TdConfig(context) }
    var id by rememberSaveable { mutableStateOf(config.apiId.takeIf { it != 0 }?.toString() ?: "") }
    var hash by rememberSaveable { mutableStateOf(config.apiHash) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val auth = td?.auth
    var submitted by remember { mutableStateOf(false) }
    LaunchedEffect(auth, submitted) {
        if (submitted && auth == AuthStep.WaitPhone) {
            // Opened from the phone screen ("Change API ID") → go back to it; otherwise continue to it.
            if (nav.stack.getOrNull(nav.stack.size - 2)?.route == Route.Phone) nav.pop() else nav.replaceTop(Route.Phone)
        }
    }
    LaunchedEffect(td?.authError) { td?.authError?.let { error = it } }

    AuthPage(
        title = "Connect to Telegram",
        subtitle = "TGlass is your own Telegram client. To sign in it needs an API ID from Telegram — create one at my.telegram.org → API development tools. It never leaves this phone.",
        art = { Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) { LottieLoop(TgAnimations.PlaneLogo, 140.dp) } },
        error = error,
        button = {
            PrimaryButton(
                "Continue",
                {
                    val apiId = id.toIntOrNull()
                    if (apiId == null || hash.trim().length < 16) {
                        error = "Please enter a valid API ID and API hash."
                    } else {
                        error = null
                        config.save(apiId, hash)
                        submitted = true
                        if (td != null) td.credentialsChanged() else nav.replaceTop(Route.Phone)
                    }
                },
                enabled = id.isNotBlank() && hash.isNotBlank(),
                loading = submitted && td != null && td.auth != AuthStep.NeedCredentials,
            )
        },
    ) {
        Separator()
        AuthField(id, { id = it.filter(Char::isDigit).take(12) }, "API ID", keyboard = KeyboardType.Number, focus = focus, fontSize = 18)
        Separator(startPadding = 20.dp)
        AuthField(hash, { hash = it.trim().take(64) }, "API hash", fontSize = 18)
        Separator()
        Spacer(Modifier.height(18.dp))
        TextButton("Open my.telegram.org", {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://my.telegram.org/apps")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        })
    }
}

// ---------------------------------------------------------------------------------------------
// Phone
// ---------------------------------------------------------------------------------------------

private val Countries = mapOf(
    "98" to "🇮🇷 Iran", "1" to "🇺🇸 USA", "44" to "🇬🇧 United Kingdom", "49" to "🇩🇪 Germany", "90" to "🇹🇷 Turkey",
    "7" to "🇷🇺 Russia", "971" to "🇦🇪 UAE", "33" to "🇫🇷 France", "39" to "🇮🇹 Italy", "34" to "🇪🇸 Spain",
    "91" to "🇮🇳 India", "86" to "🇨🇳 China", "81" to "🇯🇵 Japan", "82" to "🇰🇷 South Korea", "61" to "🇦🇺 Australia",
    "31" to "🇳🇱 Netherlands", "46" to "🇸🇪 Sweden", "47" to "🇳🇴 Norway", "41" to "🇨🇭 Switzerland", "43" to "🇦🇹 Austria",
    "32" to "🇧🇪 Belgium", "20" to "🇪🇬 Egypt", "964" to "🇮🇶 Iraq", "93" to "🇦🇫 Afghanistan", "92" to "🇵🇰 Pakistan",
    "966" to "🇸🇦 Saudi Arabia", "974" to "🇶🇦 Qatar", "380" to "🇺🇦 Ukraine", "48" to "🇵🇱 Poland", "55" to "🇧🇷 Brazil",
    "52" to "🇲🇽 Mexico", "374" to "🇦🇲 Armenia", "994" to "🇦🇿 Azerbaijan", "995" to "🇬🇪 Georgia", "965" to "🇰🇼 Kuwait",
    "968" to "🇴🇲 Oman", "973" to "🇧🇭 Bahrain", "962" to "🇯🇴 Jordan", "961" to "🇱🇧 Lebanon", "963" to "🇸🇾 Syria",
    "992" to "🇹🇯 Tajikistan", "993" to "🇹🇲 Turkmenistan", "998" to "🇺🇿 Uzbekistan", "60" to "🇲🇾 Malaysia", "62" to "🇮🇩 Indonesia",
    "358" to "🇫🇮 Finland", "45" to "🇩🇰 Denmark", "351" to "🇵🇹 Portugal", "30" to "🇬🇷 Greece", "36" to "🇭🇺 Hungary",
)

/** Spec §47 phone number entry. */
@Composable
fun PhoneScreen() {
    val nav = LocalNavigator.current
    val td = liveRepo()
    val c = TgTheme.colors
    var code by rememberSaveable { mutableStateOf("98") }
    var number by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val country = Countries[code]
    val ready = td == null || td.auth == AuthStep.WaitPhone

    AuthPage(
        title = "Your Phone",
        subtitle = "Please confirm your country code\nand enter your phone number.",
        art = { Spacer(Modifier.height(20.dp)) },
        error = td?.authError,
        button = {
            PrimaryButton(
                "Continue",
                { if (td != null) td.submitPhone("+$code$number") else nav.push(Route.Code("+$code $number")) },
                enabled = number.length >= 5 && code.isNotEmpty(),
                loading = td != null && (td.busy || !ready),
            )
        },
    ) {
        Separator()
        Row(Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            T(country ?: if (code.isEmpty()) "Choose a country" else "Invalid country code", TgTheme.type.body.copy(fontSize = 18.sp), if (country != null) c.text else c.secondaryText, modifier = Modifier.weight(1f))
            Icon(IosIcons.ChevronRight, c.tertiaryText, 14.dp)
        }
        Separator(startPadding = 20.dp)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.width(92.dp).padding(start = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                T("+", TgTheme.type.body.copy(fontSize = 20.sp), c.text)
                BasicTextField(
                    code, { code = it.filter(Char::isDigit).take(4) }, Modifier.fillMaxWidth(),
                    textStyle = TgTheme.type.body.copy(fontSize = 20.sp, color = c.text), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), cursorBrush = SolidColor(c.accent),
                )
            }
            Box(Modifier.width(0.5.dp).height(30.dp).background(c.separator))
            AuthField(number, { number = it.filter(Char::isDigit).take(14) }, "Phone number", Modifier.weight(1f), KeyboardType.Phone, focus = focus)
        }
        Separator()
        if (td != null && td.authError?.contains("API ID") == true) {
            Spacer(Modifier.height(12.dp))
            TextButton("Change API ID", { nav.push(Route.ApiSetup) })
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Code
// ---------------------------------------------------------------------------------------------

/** Spec §47 verification code, auto-submitted once all digits are in. */
@Composable
fun CodeScreen(phone: String) {
    val nav = LocalNavigator.current
    val settings = LocalAppSettings.current
    val td = liveRepo()
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val c = TgTheme.colors
    val step = td?.auth as? AuthStep.WaitCode
    val length = step?.length ?: 5
    var code by rememberSaveable { mutableStateOf("") }
    val shake = remember { Animatable(0f) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(code) {
        if (code.length == length) {
            if (td != null) td.submitCode(code)
            else {
                delay(300)
                settings.updateLoggedIn(true)
                nav.resetTo(Route.Main)
            }
        }
    }
    // Wrong code: shake the boxes (Telegram-iOS) and clear them.
    val error = td?.authError
    LaunchedEffect(error) {
        if (error != null && code.length == length) {
            Haptics.reject(view)
            scope.launch {
                for (x in listOf(14f, -12f, 9f, -6f, 3f, 0f)) shake.animateTo(x, spring(stiffness = 4000f))
            }
            code = ""
        }
    }

    AuthPage(
        title = step?.phone ?: phone,
        subtitle = when {
            td == null -> "We've sent the code to the Telegram app\non your other device.\n(Demo: enter any 5 digits.)"
            step?.viaApp == true -> "We've sent the code to the Telegram app\non your other device."
            else -> "We've sent you an SMS with the code."
        },
        art = { Box(Modifier.size(130.dp), contentAlignment = Alignment.Center) { LottieLoop(TgAnimations.IntroMessage, 120.dp) } },
        error = error,
        button = null,
    ) {
        Box(Modifier.graphicsLayer { translationX = shake.value * density }) {
            BasicTextField(
                code, { code = it.filter(Char::isDigit).take(length) }, Modifier.size(1.dp).focusRequester(focus),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(if (length > 5) 8.dp else 10.dp)) {
                val box: Dp = if (length > 5) 42.dp else 48.dp
                repeat(length) { i ->
                    val ch = code.getOrNull(i)
                    val active = i == code.length
                    Box(
                        Modifier
                            .size(box, 56.dp)
                            .clip(RoundedRectangle(12.dp))
                            .background(c.searchField)
                            .border(if (active) 2.dp else 0.dp, if (active) c.accent else Color.Transparent, RoundedRectangle(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        T(ch?.toString() ?: "", TgTheme.type.title2, c.text, weight = FontWeight.SemiBold)
                    }
                }
            }
            Box(Modifier.matchParentSize().fadeClickable { focus.requestFocus() })
        }
        Spacer(Modifier.height(24.dp))
        if (td?.busy == true) com.abtin.tglass.ui.components.ActivityIndicator(22.dp)
        else if (td == null || step?.canResend == true) TextButton("Didn't get the code?", { td?.resendCode() })
    }
}

// ---------------------------------------------------------------------------------------------
// Two-step verification
// ---------------------------------------------------------------------------------------------

@Composable
fun PasswordScreen() {
    val td = liveRepo()
    val step = td?.auth as? AuthStep.WaitPassword
    var password by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val c = TgTheme.colors

    AuthPage(
        title = "Two-Step Verification",
        subtitle = "Your account is protected with\nan additional password.",
        art = {
            Box(Modifier.size(96.dp).clip(RoundedRectangle(28.dp)).background(c.accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(IosIcons.Lock, c.accent, 48.dp)
            }
        },
        error = td?.authError,
        button = {
            PrimaryButton("Continue", { td?.submitPassword(password) }, enabled = password.isNotEmpty(), loading = td?.busy == true)
        },
    ) {
        Separator()
        AuthField(password, { password = it }, step?.hint?.takeIf { it.isNotBlank() } ?: "Password", secure = true, focus = focus, fontSize = 18)
        Separator()
    }
}

// ---------------------------------------------------------------------------------------------
// New account
// ---------------------------------------------------------------------------------------------

@Composable
fun RegisterScreen() {
    val td = liveRepo()
    var first by rememberSaveable { mutableStateOf("") }
    var last by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AuthPage(
        title = "Profile Info",
        subtitle = "Enter your name and add\na profile photo.",
        art = { Box(Modifier.size(130.dp), contentAlignment = Alignment.Center) { LottieLoop(TgAnimations.IntroLetter, 120.dp) } },
        error = td?.authError,
        button = {
            PrimaryButton("Continue", { td?.register(first.trim(), last.trim()) }, enabled = first.isNotBlank(), loading = td?.busy == true)
        },
    ) {
        Separator()
        AuthField(first, { first = it.take(64) }, "First Name", focus = focus, fontSize = 18)
        Separator(startPadding = 20.dp)
        AuthField(last, { last = it.take(64) }, "Last Name", fontSize = 18)
        Separator()
    }
}
