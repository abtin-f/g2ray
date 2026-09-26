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
| Other screens | Profile (user/group/channel + shared media), Contacts, Calls + active call, New Message, Stories viewer, Media viewer (pinch/double-tap zoom, swipe-down), Settings and sub-pages (Appearance with live preview/text size/corners/wallpaper, Power Saving, Notifications, Privacy, Data, Language, Devices, Folders, Premium, Edit Profile), Welcome → Phone → Code login. |
| System | Light/Dark/System theme, action sheets & alerts, toasts, haptics, edge-to-edge insets, keyboard handling. |

## Data

The UI only talks to `TelegramRepository`. Right now `DemoRepository` provides local sample data
(messages you send get simulated read receipts, typing and replies). To connect a real account,
implement `TelegramRepository` on top of [TDLib](https://core.telegram.org/tdlib) with your own
`api_id`/`api_hash` from <https://my.telegram.org>.

## Build

GitHub Actions (`.github/workflows/android.yml`) builds `app-debug.apk` / `app-release.apk`
(artifact **tglass-apk**) and captures emulator screenshots into `android/screenshots/`.

Locally: open `android/` in Android Studio, or run `./gradlew assembleDebug` (JDK 21, Android SDK 36).

## Credits

Glass components in `core/glass` are adapted from Kyant0/AndroidLiquidGlass (Apache-2.0).
"Telegram" is a trademark of Telegram FZ-LLC; this is an unofficial personal project.
