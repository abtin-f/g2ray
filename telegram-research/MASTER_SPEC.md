# MASTER SPEC: Telegram iOS → TGlass (Android)

Every row: the value used by Telegram-iOS (`6ad963e5`, 2026-07-18), where it comes from, and whether the Android app matches it.
✅ = matches · 🟡 = close / approximated · ❌ = not implemented yet

Paths are shortened: `CLI` = `submodules/ChatListUI/Sources/Node/ChatListItem.swift`,
`MIC` = `submodules/TelegramUI/Components/Chat/ChatMessageItemCommon/Sources/ChatMessageItemCommon.swift`,
`CTX` = `submodules/TelegramUI/Components/ContextControllerImpl/Sources/ContextControllerActionsStackNode.swift`,
`GLS` = `submodules/TelegramUI/Components/GlassBackgroundComponent/Sources/`,
`TAB` = `submodules/TelegramUI/Components/TabBarComponent/Sources/TabBarComponent.swift`,
`DAY`/`DRK` = `submodules/TelegramPresentationData/Sources/Default{Day,Dark}PresentationTheme.swift`.

## Chat list row

| Property | Telegram-iOS | Source | App |
|---|---|---|---|
| Avatar diameter | 60 | CLI `avatarDiameter = min(60, base*60/17)` | ✅ |
| Avatar left edge | 16 | CLI `avatarLeftEdgeInset` | ✅ |
| Text left inset | 24 + 60 (+2) | CLI `avatarLeftInset` | ✅ (86) |
| Row height | ≈72 (derived) | CLI `itemHeight` formula | ✅ |
| Title | 16 semibold | CLI `titleFont` | ✅ |
| Message | 15 regular | CLI `textFont` | ✅ |
| Date | 14 regular | CLI `dateFont` | ✅ |
| Badge | Ø20, 12 semibold, monospaced digits | CLI `badgeDiameter`, `badgeFont` | 🟡 13sp, no monospace |
| Badge colors | active = accent, muted = #B6B6BB (night #666666) | DAY/DRK `unreadBadge*` | ✅ |
| Read checkmarks | accent (blue) | DAY `checkmarkColor` | ✅ |
| Pinned row bg | #F7F7F7 / #1C1C1D | DAY/DRK `pinnedItemBackgroundColor` | ✅ |
| Online dot | #4CC91F | DAY `onlineDotColor` | ✅ |
| Swipe right | Read/Unread, Pin | CLI `revealOptions` | ✅ |
| Swipe left | Mute, Delete, Archive | CLI `revealOptions` | ✅ |
| Archive avatar | #DEDEE5→#C5C6CC (unpinned) | DAY `unpinnedArchiveAvatarColor` | ✅ |

## Message bubble

| Property | Telegram-iOS | Source | App |
|---|---|---|---|
| Main corner radius | 16 | `PresentationThemeSettings.swift` `mainRadius` | ✅ (default, adjustable) |
| Grouped corner radius | 8 | same, `auxiliaryRadius` | ✅ (= main/2) |
| Tail | ellipse-cut tail (27×17 fill, 23×21 cut) | `ChatMessageBubbleImages.swift` | ✅ exact port (path ops) |
| Text insets | 6 / 11 / 6 / 11 | MIC `text.bubbleInsets` | ✅ |
| Spacing | 2 (grouped 0) | MIC `bubble.defaultSpacing` | 🟡 2 / 6 |
| Max width | screen − 36 | MIC `compactInset` | ✅ (− 38 more in groups) |
| Min size | 40 × 35 | MIC `minimumSize` | ✅ |
| Image insets / corner | 2 / 15 (merged 7) | MIC `image` | 🟡 2 / radius−2 |
| Image max / min | 300×380 / 170×74 | MIC `image` | 🟡 fixed 250 width |
| Group avatar | 34 (+4) | MIC `avatarInset` | ✅ |
| Date header height | 34 | MIC `timestampHeaderHeight` | 🟡 |
| Time font | 11 | `ChatMessageDateAndStatusNode.swift` `dateFont` | ✅ |
| Day incoming | #FFFFFF, meta #525252@0.6 | DAY | ✅ |
| Day outgoing | #E1FFC7, meta #008C09@0.8, accent #00A700 | DAY | ✅ |
| Night incoming | #1D1D1D | DRK | ✅ |
| Night outgoing | gradient #61BCF9→#0088FF, white text, meta white@0.5 | DRK | ✅ |
| Links | #004BAD (day) | DAY `linkTextColor` | ✅ |
| Service pill | black @ 0.2 | DAY `defaultServiceBackgroundColor` | ✅ |
| Reactions | inactive accent@0.1, active accent, 12pt | DAY `reaction*` | 🟡 13sp |

## Context menu (long press)

| Property | Telegram-iOS | Source | App |
|---|---|---|---|
| Background | blur + dim, message extracted & sharp | `ContextControllerExtractedPresentationNode.swift` | ✅ |
| Item font | 17 regular | CTX `titleFont` | ✅ |
| Item insets | side 18, vertical 11 (≈44pt) | CTX | ✅ |
| Icon | 24pt, **left**, slot 32 at x=20 | CTX `iconSideInset`, `standardIconWidth` | ✅ |
| Title x (with icon) | 60 | CTX `iconSideInset + 40` | ✅ |
| Subtitle | 14 regular | CTX `subtitleFont` | ❌ (no subtitles yet) |
| Reactions | bar above the message | `ReactionPreviewView.swift` | ✅ |

## Liquid Glass

| Property | Telegram-iOS | Source | App |
|---|---|---|---|
| Renderer | `UIGlassEffect(.regular)` on iOS 26 | GLS `GlassBackgroundComponent.swift` | ✅ backdrop lib: blur + lens + vibrancy |
| Fallback fill | white@0.70 / #1C1C1C@0.85 | GLS legacy path | ✅ (Translucent level) |
| Fallback backdrop | blur 2 + saturation matrix | GLS `LegacyGlassView.swift` | 🟡 blur + vibrancy |
| Shadow | blur 40, alpha 0.04, y +1 | GLS `generateLegacyShadowImage` | 🟡 |
| Press growth | +20pt per axis | GLS `TouchEffect.pressedSizeIncrease` | ✅ |
| Press spring | on: m1.36 k568 c39.7 · off: m2.0 k460 c21.8 | GLS `TouchEffect` | 🟡 library spring |
| Power-saving levels | interface effects toggle | Telegram blog | ✅ 4 levels |

## Tab bar

| Property | Telegram-iOS | Source | App |
|---|---|---|---|
| Height | 64 (56 + 2×4) | TAB `barHeight` | ✅ |
| Max width | 500 | TAB | ✅ |
| Title | 10 semibold | TAB | ✅ |
| Search circle | 64, 8pt gap, right side | TAB | ✅ |
| Selection lens | ≥ itemHeight × 1.2, draggable, liftable | TAB `liquidLensView` | ✅ (library lens) |

## Composer

| Property | Telegram-iOS | Source | App |
|---|---|---|---|
| Input font | 17 regular | `ChatTextInputPanelNode.swift` | ✅ |
| Internal insets | 5 / 12 / 4 / 11 | same | 🟡 11 / 16 |
| Min text height | 33 | same | 🟡 44 field |
| Glass panel | yes | same (`glass: true`) | ✅ |

## Theme

| Property | Day | Night | App |
|---|---|---|---|
| Accent | #0088FF | #3E88F7 | ✅ |
| Grouped bg | #EFEFF4 | #000000 | ✅ |
| Cell | #FFFFFF | #1C1C1D | ✅ |
| Secondary text | #8E8E93 | #98989E | ✅ |
| Separator | #C8C7CC | #545458@0.55 | ✅ |

## Assets & motion taken from Telegram-iOS

| Item | Source | App |
|---|---|---|
| 82 UI icons (tab bar, context menu, settings, profile, attach, calls) | `submodules/TelegramUI/Images.xcassets` (PDF → VectorDrawable) | ✅ |
| Chat wallpaper: 4-color gradient, 8 base positions, swirl, advances on send | `submodules/GradientBackground/Sources/SoftwareGradientBackground.swift` | ✅ exact algorithm |
| Glass context menu & reaction bar | `ContextControllerImpl`, `GlassBackgroundComponent` | ✅ |
| Stories collapsed into the chat list title | Chat list header | ✅ pull-down to expand |

## Next steps

1. Port `TouchEffect` springs (stretch toward the drag direction) into `GlassBox`.
2. Menu item subtitles and animated icons.
3. TDLib repository implementation.
