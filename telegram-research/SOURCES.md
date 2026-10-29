# Sources

## 1. Official code: primary reference

| Source | Use it for | Notes |
|---|---|---|
| [TelegramMessenger/Telegram-iOS](https://github.com/TelegramMessenger/Telegram-iOS) | **UI truth**: layout constants, colors, fonts, animations, gestures | GPL-2.0. Key modules are listed below |
| [DrKLO/Telegram](https://github.com/DrKLO/Telegram) / [TelegramOrg/Telegram-Android](https://github.com/TelegramOrg/Telegram-Android) | How Telegram implements the same features on Android (lists, media, calls) | GPL-2.0. Compare against it, but don't copy its look |
| [TDLib](https://core.telegram.org/tdlib) · [docs](https://core.telegram.org/tdlib/docs/) | **Data layer**: auth, chats, messages, media, reactions, stories, calls | Replaces `DemoRepository` |
| [Telegram API](https://core.telegram.org/api) · [API terms](https://core.telegram.org/api/terms) | Data model, MTProto, and the rules for third-party clients | Needs `api_id`/`api_hash` from my.telegram.org |

### Telegram-iOS modules that matter

| Area | Path |
|---|---|
| Chat list row | `submodules/ChatListUI/Sources/Node/ChatListItem.swift` |
| Message layout constants | `submodules/TelegramUI/Components/Chat/ChatMessageItemCommon/Sources/ChatMessageItemCommon.swift` |
| Bubble shapes / tail | `submodules/TelegramPresentationData/Sources/ChatMessageBubbleImages.swift` |
| Bubble corner defaults | `submodules/TelegramUIPreferences/Sources/PresentationThemeSettings.swift` |
| Time + checks | `submodules/TelegramUI/Components/Chat/ChatMessageDateAndStatusNode/…` |
| Composer | `submodules/TelegramUI/Components/Chat/ChatTextInputPanelNode/…` |
| Context menu | `submodules/TelegramUI/Components/ContextControllerImpl/…` |
| Liquid Glass surface | `submodules/TelegramUI/Components/GlassBackgroundComponent/…` (`GlassBackgroundComponent`, `LegacyGlassView`, `TouchEffect`) |
| Tab bar | `submodules/TelegramUI/Components/TabBarComponent/Sources/TabBarComponent.swift` |
| Day / Night theme | `submodules/TelegramPresentationData/Sources/DefaultDayPresentationTheme.swift`, `DefaultDarkPresentationTheme.swift` |
| Fonts | `submodules/Display/Source/Font.swift` |

## 2. Official announcements: what changed and when

| Source | Use it for |
|---|---|
| [Telegram Blog](https://telegram.org/blog) | Feature and UI change log (Liquid Glass, AI summaries, stories, gifts, …) |
| [New design + AI summaries](https://telegram.org/blog/new-design-ai-summaries) | Liquid Glass across the whole iOS app, and the Power Saving → interface effects setting |

## 3. Android implementation helpers

| Source | Use it for |
|---|---|
| [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) (`io.github.kyant0:backdrop`) | Blur, lens refraction, highlight, and glass tab bar/toggle for Compose (Apache-2.0). **Used in the app** |
| [chrisbanes/haze](https://github.com/chrisbanes/haze) | Simpler backdrop blur (fallback option) |
| [TelegramExample](https://github.com/indritbashkimi/TelegramExample), [Kotlin-Telegram-Client](https://github.com/OTR/Kotlin-Telegram-Client) | TDLib + Compose wiring examples |

## 4. Secondary references: structure only, never the source of numbers

| Source | Why it's secondary |
|---|---|
| [Telegram Free iOS UI Kit (Dribbble)](https://dribbble.com/shots/11087011-Telegram-Free-iOS-UI-Kit) | Pre-2026 design, useful for overall structure only |
| [telegram-mini-apps-dev/TelegramUI](https://github.com/telegram-mini-apps-dev/TelegramUI) | Unofficial Mini App components |

## Legal

Telegram-iOS and Telegram-Android are **GPL-2.0**. This dataset stores measurements (numbers and
behaviour descriptions), not copied code. Copying their code into the app would make the app GPL too.
"Telegram" is a trademark, so the app uses its own name (TGlass). Follow the
[API terms](https://core.telegram.org/api/terms) when connecting real accounts.
