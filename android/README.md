# TGlass — Telegram with the iPhone (iOS 26 Liquid Glass) design, for Android

A Kotlin + Jetpack Compose Android client whose UI mirrors Telegram for iOS, built from
`Telegram_iOS_Exact_UI_UX_Design_Spec.md`.

## What's implemented

| Area | Details |
|---|---|
| Liquid Glass | Real backdrop blur + lens refraction via [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) (Android 13+ for refraction, 12+ for blur). Levels Full / Blur / Translucent / Off in **Settings → Power Saving**. |
| Tab bar | Floating glass tab bar (Contacts, Calls, Chats, Settings) with the draggable "gel" selection lens. |
| Navigation | iOS push/pop with parallax + interactive swipe-back from the left edge; modal screens slide up. |
| Chat list | Pinned/muted/unread/mention/draft/typing states, stories row, folder tabs, archive, search, edit mode, swipe actions (Read/Pin ← → Mute/Delete/Archive, full-swipe), long-press **peek preview** with blurred background. |
| Chat | Wallpaper with doodles, iOS bubbles with tails and grouping, inline time + ✓✓, replies, reactions, date pills, pinned bar, swipe-to-reply, double-tap 👍, **long-press → haptic → blur + dim → sharp message → reactions + context menu** (spec §6/§67), multi-select, forward, copy, edit, delete. |
| Composer | Glass field, send/mic morph, hold-to-record voice with *slide to cancel* and *slide up to lock*, emoji / stickers / GIF panel, attachment sheet (gallery multi-select, file, location, poll, contact, music). |
| Content types | Text, photo, voice, sticker, file, location, contact, poll (voting), link preview, service messages, channel posts with views. |
| Other screens | Profile (user/group/channel + shared media), Contacts, Calls + active call, New Message, Stories viewer, Media viewer (pinch/double-tap zoom, swipe-down), Settings and sub-pages (Appearance with live preview/text size/corners/wallpaper, Power Saving, Notifications, Privacy, Data, Language, Devices, Folders, Premium, Edit Profile), Welcome → API setup → Phone → Code → Password login. |
| System | Light/Dark/System theme, action sheets & alerts, toasts, haptics, edge-to-edge insets, keyboard handling. |

## Data

The UI only talks to `TelegramRepository`, which has two implementations:

- **`TdRepository`** (`data/td/`) — a real Telegram account through [TDLib](https://core.telegram.org/tdlib)
  (via the prebuilt Android build in [tdl-coroutines](https://github.com/g000sha256/tdl-coroutines)).
  TDLib updates are folded into Compose state, so every screen updates live: chat list with folders,
  archive, pins, unread/mentions, drafts, typing; message history with paging, sending, replies, edits,
  reactions, pins, polls, forwarding; profile photos, photos, stickers; contacts, calls, active sessions.
  Sign-in: phone → code → two-step password (or registration).
- **`DemoRepository`** — local sample data ("Explore the Demo" on the welcome screen, and CI screenshots).

To sign in you need your own `api_id` / `api_hash` from <https://my.telegram.org> → *API development tools*.
They are entered once in the app, or baked into a build with `-PTG_API_ID=… -PTG_API_HASH=…`
(or the environment variables of the same name).

## Build

GitHub Actions (`.github/workflows/android.yml`) builds `app-debug.apk` / `app-release.apk`
(artifact **tglass-apk**) and captures emulator screenshots into `android/screenshots/`.

Locally: open `android/` in Android Studio, or run `./gradlew assembleDebug` (JDK 21, Android SDK 36).

## Credits

Glass components in `core/glass` are adapted from Kyant0/AndroidLiquidGlass (Apache-2.0).
"Telegram" is a trademark of Telegram FZ-LLC; this is an unofficial personal project.
