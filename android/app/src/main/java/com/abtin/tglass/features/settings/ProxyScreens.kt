package com.abtin.tglass.features.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.glass.LocalBackdrop
import com.abtin.tglass.core.navigation.LocalNavigator
import com.abtin.tglass.core.navigation.Route
import com.abtin.tglass.data.ProxyItem
import com.abtin.tglass.data.ProxyKind
import com.abtin.tglass.features.main.LocalRepository
import com.abtin.tglass.ui.components.Cell
import com.abtin.tglass.ui.components.GlassTopBar
import com.abtin.tglass.ui.components.IOSSwitch
import com.abtin.tglass.ui.components.LocalActionSheet
import com.abtin.tglass.ui.components.LocalToast
import com.abtin.tglass.ui.components.PrimaryButton
import com.abtin.tglass.ui.components.Section
import com.abtin.tglass.ui.components.SegmentedControl
import com.abtin.tglass.ui.components.Separator
import com.abtin.tglass.ui.components.SheetAction
import com.abtin.tglass.ui.components.SheetRequest
import com.abtin.tglass.ui.components.T
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay

private fun ProxyItem.status(): String = when (ping) {
    null -> "Checking…"
    -1 -> "Unavailable"
    else -> if (enabled) "Connected, ping: $ping ms" else "Available, ping: $ping ms"
}

/** Settings → Proxy (Telegram iOS "Proxy Settings"): use-proxy switch, saved servers with ping, add/share/delete. */
@Composable
fun ProxyListScreen() {
    val repo = LocalRepository.current
    val nav = LocalNavigator.current
    val sheet = LocalActionSheet.current
    val toast = LocalToast.current
    val context = LocalContext.current
    val c = TgTheme.colors
    val backdrop = rememberLayerBackdrop()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    LaunchedEffect(Unit) {
        repo.loadProxies()
        // Re-ping periodically while the screen is open.
        while (true) {
            delay(600)
            repo.pingProxies()
            delay(10_000)
        }
    }
    val proxies = repo.proxies
    val active = proxies.firstOrNull { it.enabled }
    CompositionLocalProvider(LocalBackdrop provides backdrop) {
        Box(Modifier.fillMaxSize().background(c.groupedBackground)) {
            LazyColumn(Modifier.fillMaxSize().layerBackdrop(backdrop), contentPadding = PaddingValues(top = top + 70.dp, bottom = 40.dp)) {
                item {
                    Section(footer = "A proxy lets you connect to Telegram when it is blocked or slow in your network.") {
                        Cell("Use Proxy", chevron = false, divider = false, trailing = {
                            IOSSwitch(active != null, { on ->
                                if (!on) repo.disableProxy()
                                else proxies.firstOrNull()?.let { repo.enableProxy(it.id) } ?: nav.push(Route.ProxyEdit(null))
                            })
                        })
                    }
                    Spacer(Modifier.height(24.dp))
                }
                item {
                    Section(header = "Saved Proxies") {
                        Cell("Add Proxy", titleColor = c.accent, chevron = false, divider = proxies.isNotEmpty(), onClick = { nav.push(Route.ProxyEdit(null)) })
                        proxies.forEachIndexed { i, p ->
                            Cell(
                                "${p.server}:${p.port}",
                                subtitle = "${p.kind.title} · ${p.status()}",
                                checked = p.enabled,
                                chevron = false,
                                divider = i != proxies.lastIndex,
                                onClick = {
                                    sheet.show(SheetRequest(
                                        title = "${p.server}:${p.port}",
                                        actions = listOfNotNull(
                                            if (!p.enabled) SheetAction("Connect") { repo.enableProxy(p.id) } else SheetAction("Disconnect") { repo.disableProxy() },
                                            SheetAction("Edit") { nav.push(Route.ProxyEdit(p.id)) },
                                            SheetAction("Share Link") {
                                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, p.link), "Share Proxy").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                            },
                                            SheetAction("Copy Link") {
                                                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("proxy", p.link))
                                                toast.show("Link copied")
                                            },
                                            SheetAction("Delete", destructive = true) { repo.removeProxy(p.id) },
                                        ),
                                    ))
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
                item {
                    Section(footer = "You can also open a proxy link (t.me/proxy or t.me/socks) to add it.") {
                        Cell("Paste Proxy Link", titleColor = c.accent, chevron = false, divider = false, onClick = {
                            val text = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.text?.toString()
                            if (text != null && ProxyItem.fromLink(text) != null) nav.push(Route.ProxyLink(text))
                            else toast.error("Copy a t.me/proxy or t.me/socks link first")
                        })
                    }
                }
            }
            GlassTopBar("Proxy", fade = c.groupedBackground)
        }
    }
}

/** Add / edit a proxy server. */
@Composable
fun ProxyEditScreen(id: Int?, prefill: ProxyItem? = null) {
    val repo = LocalRepository.current
    val nav = LocalNavigator.current
    val toast = LocalToast.current
    val c = TgTheme.colors
    val existing = id?.let { pid -> repo.proxies.firstOrNull { it.id == pid } } ?: prefill
    var kind by rememberSaveable { mutableStateOf((existing?.kind ?: ProxyKind.MTProto).ordinal) }
    var server by rememberSaveable { mutableStateOf(existing?.server ?: "") }
    var port by rememberSaveable { mutableStateOf(existing?.port?.toString() ?: "") }
    var secret by rememberSaveable { mutableStateOf(existing?.secret ?: "") }
    var user by rememberSaveable { mutableStateOf(existing?.username ?: "") }
    var pass by rememberSaveable { mutableStateOf(existing?.password ?: "") }
    var saving by remember { mutableStateOf(false) }
    val kinds = listOf(ProxyKind.MTProto, ProxyKind.Socks5)
    val selected = kinds.getOrElse(kind.coerceAtMost(kinds.lastIndex)) { ProxyKind.MTProto }
    val valid = server.isNotBlank() && (port.toIntOrNull() ?: 0) in 1..65535 && (selected != ProxyKind.MTProto || secret.isNotBlank())
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    fun save() {
        if (!valid || saving) return
        saving = true
        val item = ProxyItem(0, server.trim(), port.toInt(), selected, secret.trim(), user.trim(), pass)
        repo.saveProxy(id, item, enable = true) { err ->
            saving = false
            if (err != null) toast.error(err) else { toast.show("Proxy connected"); nav.pop() }
        }
    }

    Box(Modifier.fillMaxSize().background(c.groupedBackground)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = top + 70.dp, bottom = 40.dp)) {
            item {
                SegmentedControl(listOf("MTProto", "SOCKS5"), kinds.indexOf(selected), { kind = it }, Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                Section(header = "Connection") {
                    ProxyField(server, { input ->
                        // A whole t.me/proxy or tg://socks link pasted here fills in every field.
                        val link = ProxyItem.fromLink(input.trim())
                        if (link != null) {
                            kind = kinds.indexOf(link.kind).coerceAtLeast(0)
                            server = link.server; port = link.port.toString(); secret = link.secret
                            user = link.username; pass = link.password
                        } else server = input.trim()
                    }, "Server or proxy link")
                    ProxyField(port, { port = it.filter(Char::isDigit).take(5) }, "Port", keyboard = KeyboardType.Number, divider = false)
                }
                Spacer(Modifier.height(24.dp))
                if (selected == ProxyKind.MTProto) {
                    Section(header = "Secret") { ProxyField(secret, { secret = it.trim() }, "Secret", divider = false) }
                } else {
                    Section(header = "Authentication (optional)") {
                        ProxyField(user, { user = it }, "Username")
                        ProxyField(pass, { pass = it }, "Password", secure = true, divider = false)
                    }
                }
                Spacer(Modifier.height(28.dp))
                PrimaryButton(if (id == null) "Save and Connect" else "Save", { save() }, Modifier.padding(horizontal = 16.dp), enabled = valid, loading = saving)
            }
        }
        GlassTopBar(if (id == null) "Add Proxy" else "Edit Proxy", fade = c.groupedBackground)
    }
}

/** Confirmation for an opened proxy link (tg://proxy, t.me/socks…), like Telegram's proxy alert. */
@Composable
fun ProxyLinkScreen(link: String) {
    val repo = LocalRepository.current
    val nav = LocalNavigator.current
    val toast = LocalToast.current
    val c = TgTheme.colors
    val proxy = remember(link) { ProxyItem.fromLink(link) }
    var saving by remember { mutableStateOf(false) }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(Modifier.fillMaxSize().background(c.groupedBackground)) {
        Column(Modifier.fillMaxSize().padding(top = top + 70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (proxy == null) {
                T("This proxy link is not valid.", TgTheme.type.body, c.secondaryText, align = TextAlign.Center, modifier = Modifier.padding(32.dp))
                return@Column
            }
            T("Proxy Server", TgTheme.type.title2, c.text)
            Spacer(Modifier.height(8.dp))
            T("Connect to this proxy? You can turn it off in Settings → Proxy.", TgTheme.type.subheadline, c.secondaryText, align = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
            Spacer(Modifier.height(24.dp))
            Section {
                Cell("Server", value = proxy.server, chevron = false)
                Cell("Port", value = proxy.port.toString(), chevron = false)
                Cell("Type", value = proxy.kind.title, chevron = false, divider = proxy.kind == ProxyKind.MTProto || proxy.username.isNotEmpty())
                if (proxy.kind == ProxyKind.MTProto) Cell("Secret", value = proxy.secret.take(10) + "…", chevron = false, divider = false)
                else if (proxy.username.isNotEmpty()) Cell("Username", value = proxy.username, chevron = false, divider = false)
            }
            Spacer(Modifier.height(28.dp))
            PrimaryButton("Connect Proxy", {
                saving = true
                repo.saveProxy(null, proxy, enable = true) { err ->
                    saving = false
                    if (err != null) toast.error(err) else { toast.show("Proxy connected"); nav.replaceTop(Route.Proxy) }
                }
            }, Modifier.padding(horizontal = 16.dp), loading = saving)
        }
        GlassTopBar("Proxy", fade = c.groupedBackground)
    }
}

@Composable
private fun ProxyField(
    value: String,
    onValue: (String) -> Unit,
    placeholder: String,
    keyboard: KeyboardType = KeyboardType.Uri,
    secure: Boolean = false,
    divider: Boolean = true,
) {
    val c = TgTheme.colors
    Box {
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) T(placeholder, TgTheme.type.body, c.tertiaryText)
                BasicTextField(
                    value, onValue, Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = TgTheme.type.body.copy(color = c.text),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                    visualTransformation = if (secure) PasswordVisualTransformation() else VisualTransformation.None,
                    cursorBrush = SolidColor(c.accent),
                )
            }
        }
        if (divider) Separator(Modifier.align(Alignment.BottomStart), startPadding = 16.dp)
    }
}

/** Small "Proxy" status used in lists: connected server or Off. */
@Composable
fun proxySummary(): String {
    val repo = LocalRepository.current
    LaunchedEffect(Unit) { repo.loadProxies() }
    return repo.proxies.firstOrNull { it.enabled }?.server ?: "Off"
}
