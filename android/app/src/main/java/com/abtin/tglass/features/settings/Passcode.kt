package com.abtin.tglass.features.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.navigation.LocalNavigator
import com.abtin.tglass.core.navigation.Route
import com.abtin.tglass.ui.components.Cell
import com.abtin.tglass.ui.components.GlassTopBar
import com.abtin.tglass.ui.components.Haptics
import com.abtin.tglass.ui.components.Icon
import com.abtin.tglass.ui.components.IosIcons
import com.abtin.tglass.ui.components.LocalActionSheet
import com.abtin.tglass.ui.components.LocalToast
import com.abtin.tglass.ui.components.Section
import com.abtin.tglass.ui.components.SheetAction
import com.abtin.tglass.ui.components.SheetRequest
import com.abtin.tglass.ui.components.T
import com.abtin.tglass.ui.components.fadeClickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Local app lock (Settings → Privacy and Security → Passcode Lock): a 4-digit passcode stored as a salted
 * SHA-256 hash in SharedPreferences. The app locks on a cold start and when it comes back after being in the
 * background for at least [autoLockSeconds].
 */
object PasscodeLock {
    const val LENGTH = 4
    private const val MAX_ATTEMPTS = 5
    private const val BLOCK_MS = 30_000L

    /** Auto-lock choices in seconds with their labels. */
    val AutoLockOptions: List<Pair<Int, String>> = listOf(0 to "Immediately", 60 to "If away for 1 min", 300 to "If away for 5 min", 3600 to "If away for 1 hour")

    private var prefs: SharedPreferences? = null

    var enabled by mutableStateOf(false)
        private set
    var autoLockSeconds by mutableIntStateOf(3600)
        private set
    var locked by mutableStateOf(false)
        private set
    /** Uptime until which unlocking is refused after too many wrong attempts. */
    var blockedUntil by mutableLongStateOf(0L)
        private set

    private var backgroundAt = 0L
    private var failed = 0

    /** Loads the settings once per process; a cold start with a passcode set begins locked. */
    fun attach(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("tglass_passcode", Context.MODE_PRIVATE)
        prefs = p
        enabled = p.getString("hash", null) != null
        autoLockSeconds = p.getInt("autoLock", 3600)
        locked = enabled
    }

    fun autoLockLabel(seconds: Int = autoLockSeconds): String =
        AutoLockOptions.firstOrNull { it.first == seconds }?.second ?: "If away for ${seconds / 60} min"

    fun onBackground() {
        if (enabled && !locked) backgroundAt = SystemClock.elapsedRealtime()
    }

    fun onForeground() {
        val at = backgroundAt
        backgroundAt = 0L
        if (!enabled || locked || at == 0L) return
        if (SystemClock.elapsedRealtime() - at >= autoLockSeconds * 1000L) locked = true
    }

    fun lockNow() {
        if (enabled) locked = true
    }

    private fun hash(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("$salt:$pin".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { b -> String.format("%02x", b.toInt() and 0xFF) }
    }

    /** Sets (or changes) the passcode. */
    fun set(pin: String) {
        val p = prefs ?: return
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val salt = bytes.joinToString("") { b -> String.format("%02x", b.toInt() and 0xFF) }
        p.edit().putString("salt", salt).putString("hash", hash(pin, salt)).apply()
        enabled = true
        failed = 0
    }

    fun disable() {
        prefs?.edit()?.remove("salt")?.remove("hash")?.apply()
        enabled = false
        locked = false
    }

    fun setAutoLock(seconds: Int) {
        autoLockSeconds = seconds
        prefs?.edit()?.putInt("autoLock", seconds)?.apply()
    }

    /** True if [pin] is the current passcode (counts failed attempts). */
    fun check(pin: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now < blockedUntil) return false
        val p = prefs ?: return false
        val stored = p.getString("hash", null) ?: return true
        val ok = MessageDigest.isEqual(hash(pin, p.getString("salt", "") ?: "").toByteArray(), stored.toByteArray())
        if (ok) {
            failed = 0
        } else if (++failed >= MAX_ATTEMPTS) {
            failed = 0
            blockedUntil = now + BLOCK_MS
        }
        return ok
    }

    fun unlock(pin: String): Boolean {
        val ok = check(pin)
        if (ok) locked = false
        return ok
    }
}

/** Shows the lock screen over the whole app while [PasscodeLock.locked]; put it last in the root Box. */
@Composable
fun PasscodeLockHost() {
    val context = LocalContext.current
    PasscodeLock.attach(context)
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, e ->
            when (e) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> PasscodeLock.onBackground()
                androidx.lifecycle.Lifecycle.Event.ON_START -> PasscodeLock.onForeground()
                else -> {}
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    if (PasscodeLock.locked) {
        BackHandler { (context as? android.app.Activity)?.moveTaskToBack(true) }
        val c = TgTheme.colors
        Box(
            Modifier
                .fillMaxSize()
                .background(c.background)
                // Swallow every touch so nothing underneath can be used.
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
        ) {
            PasscodeEntry(
                title = "Enter Passcode",
                onEntered = { PasscodeLock.unlock(it) },
                showLock = true,
            )
        }
    }
}

private val KeyLetters = mapOf('2' to "ABC", '3' to "DEF", '4' to "GHI", '5' to "JKL", '6' to "MNO", '7' to "PQRS", '8' to "TUV", '9' to "WXYZ")

/**
 * iOS passcode entry: title, four dots and a round number pad. [onEntered] gets the full code and returns
 * whether it was accepted; a wrong code shakes the dots and clears them. Changing [resetKey] clears the code.
 */
@Composable
internal fun PasscodeEntry(
    title: String,
    onEntered: (String) -> Boolean,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    resetKey: Any? = null,
    showLock: Boolean = false,
) {
    val c = TgTheme.colors
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var code by remember(resetKey) { mutableStateOf("") }
    var busy by remember(resetKey) { mutableStateOf(false) }
    var wrong by remember(resetKey) { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    val blocked = PasscodeLock.blockedUntil > SystemClock.elapsedRealtime()

    fun press(d: Char) {
        if (busy || code.length >= PasscodeLock.LENGTH) return
        Haptics.tap(view)
        code += d
        wrong = false
        if (code.length == PasscodeLock.LENGTH) {
            val entered = code
            busy = true
            scope.launch {
                delay(120)
                val ok = onEntered(entered)
                if (!ok) {
                    Haptics.reject(view)
                    wrong = true
                    for (x in listOf(16f, -14f, 11f, -8f, 5f, -2f, 0f)) shake.animateTo(x, tween(45))
                }
                code = ""
                busy = false
            }
        }
    }

    Column(
        modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (showLock) {
            Icon(IosIcons.Lock, c.accent, 34.dp)
            Spacer(Modifier.height(14.dp))
        }
        T(title, TgTheme.type.title3, c.text, align = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        val hint = when {
            blocked -> "Too many attempts. Try again in 30 seconds."
            wrong -> "Wrong passcode"
            else -> subtitle ?: " "
        }
        T(hint, TgTheme.type.subheadline, if (wrong || blocked) c.destructive else c.secondaryText, align = TextAlign.Center)
        Spacer(Modifier.height(22.dp))
        Row(
            Modifier.graphicsLayer { translationX = shake.value.dp.toPx() },
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            repeat(PasscodeLock.LENGTH) { i ->
                val filled = i < code.length
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(if (filled) c.text else Color.Transparent)
                        .border(1.5.dp, c.text, CircleShape)
                )
            }
        }
        Spacer(Modifier.height(44.dp))
        val rows = listOf("123", "456", "789")
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { d -> PadKey(d, KeyLetters[d]) { press(d) } }
            }
            Spacer(Modifier.height(16.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(78.dp))
            PadKey('0', null) { press('0') }
            Box(Modifier.size(78.dp), contentAlignment = Alignment.Center) {
                if (code.isNotEmpty() && !busy) {
                    T("Delete", TgTheme.type.body, c.text, modifier = Modifier.fadeClickable { code = code.dropLast(1) })
                }
            }
        }
    }
}

@Composable
private fun PadKey(digit: Char, letters: String?, onClick: () -> Unit) {
    val c = TgTheme.colors
    Box(
        Modifier
            .size(78.dp)
            .clip(CircleShape)
            .background(c.searchField)
            .fadeClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            T(digit.toString(), TgTheme.type.title1.copy(fontSize = 32.sp, lineHeight = 36.sp), c.text, weight = FontWeight.Normal)
            if (letters != null) T(letters, TgTheme.type.caption2.copy(fontSize = 10.sp, letterSpacing = 2.sp), c.text, weight = FontWeight.SemiBold)
        }
    }
}

/** Settings → Passcode Lock. Asks for the current passcode first when one is set. */
@Composable
internal fun PasscodeSettingsScreen() {
    val context = LocalContext.current
    PasscodeLock.attach(context)
    val c = TgTheme.colors
    var verified by rememberSaveable { mutableStateOf(!PasscodeLock.enabled) }
    Box(Modifier.fillMaxSize()) {
        SettingsScaffold(Page.Passcode.title) { passcodeItems() }
        if (!verified) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(c.groupedBackground)
                    .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
            ) {
                PasscodeEntry(title = "Enter Passcode", onEntered = { pin ->
                    val ok = PasscodeLock.check(pin)
                    if (ok) verified = true
                    ok
                })
                GlassTopBar(Page.Passcode.title, fade = c.groupedBackground)
            }
        }
    }
}

private fun LazyListScope.passcodeItems() {
    item {
        val c = TgTheme.colors
        Column(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(90.dp).clip(CircleShape).background(Color(0xFFFF9500)), contentAlignment = Alignment.Center) {
                Icon(IosIcons.Lock, Color.White, 50.dp)
            }
            Spacer(Modifier.height(14.dp))
            T(
                "Lock TGlass with a 4-digit passcode. You will be asked for it when you open the app and when you come back to it.",
                TgTheme.type.subheadline, c.secondaryText, align = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp),
            )
        }
    }
    item {
        val c = TgTheme.colors
        val nav = LocalNavigator.current
        val sheet = LocalActionSheet.current
        val toast = LocalToast.current
        if (!PasscodeLock.enabled) {
            Section {
                Cell("Turn Passcode On", titleColor = c.accent, chevron = false, divider = false, onClick = { nav.push(Route.SettingsPage(Page.PasscodeSetup)) })
            }
        } else {
            Section {
                Cell("Turn Passcode Off", titleColor = c.destructive, chevron = false, onClick = {
                    sheet.show(SheetRequest(
                        title = "Turn off the passcode?",
                        message = "Anyone with access to this device will be able to open TGlass.",
                        alert = true,
                        actions = listOf(SheetAction("Turn Off", destructive = true) {
                            PasscodeLock.disable()
                            toast.show("Passcode turned off")
                        }),
                    ))
                })
                Cell("Change Passcode", titleColor = c.accent, chevron = false, divider = false, onClick = { nav.push(Route.SettingsPage(Page.PasscodeSetup)) })
            }
            Spacer(Modifier.height(24.dp))
            Section(footer = "The app will ask for the passcode after it has been in the background for this long.") {
                Cell("Auto-Lock", value = PasscodeLock.autoLockLabel(), divider = false, onClick = {
                    sheet.show(SheetRequest(
                        title = "Auto-Lock",
                        actions = PasscodeLock.AutoLockOptions.map { (sec, label) -> SheetAction(label, bold = sec == PasscodeLock.autoLockSeconds) { PasscodeLock.setAutoLock(sec) } },
                    ))
                })
            }
            Spacer(Modifier.height(24.dp))
            Section {
                Cell("Lock Now", titleColor = c.accent, chevron = false, divider = false, onClick = { PasscodeLock.lockNow() })
            }
        }
    }
}

/** New / changed passcode: enter it twice. */
@Composable
internal fun PasscodeSetupScreen() {
    val context = LocalContext.current
    PasscodeLock.attach(context)
    val c = TgTheme.colors
    val nav = LocalNavigator.current
    val toast = LocalToast.current
    val changing = remember { PasscodeLock.enabled }
    var first by remember { mutableStateOf<String?>(null) }
    var mismatch by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(c.groupedBackground)) {
        PasscodeEntry(
            title = if (first == null) (if (changing) "Enter a new passcode" else "Enter a passcode") else "Re-enter your passcode",
            subtitle = if (mismatch) "Passcodes didn't match. Try again." else null,
            resetKey = first,
            onEntered = { pin ->
                val f = first
                when {
                    f == null -> {
                        first = pin
                        mismatch = false
                        true
                    }
                    f == pin -> {
                        PasscodeLock.set(pin)
                        toast.show(if (changing) "Passcode changed" else "Passcode enabled")
                        nav.pop()
                        true
                    }
                    else -> {
                        first = null
                        mismatch = true
                        false
                    }
                }
            },
        )
        GlassTopBar("Passcode", fade = c.groupedBackground)
    }
}
