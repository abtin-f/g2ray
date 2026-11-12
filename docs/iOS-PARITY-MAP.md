# TGlass ↔ Telegram-iOS visual parity map

Scope: **look and feel only**, for screens TGlass already has. Each row is a visual difference:
element → iOS value → TGlass value → fix. Anything that already matches is left out.

- iOS values come from `TelegramMessenger/Telegram-iOS` master (path + line), or are measured from the owner's
  photos (`ref2/ios-*.jpg`).
- TGlass paths are under `android/app/src/main/java/com/abtin/tglass/`, at commit `fe5716a`.
- Confidence: **H** = read on both sides; **M** = iOS side read, TGlass side read, but the iOS code path
  (glass vs legacy style) was not traced to the screen; **L** = needs a check on a real iPhone.
- Nothing here was compiled or run. The only TGlass screenshots available were from 29 Oct (older build), used
  only as a hint.

---

## 0. Root cause: the reference photos are from a 440 pt wide iPhone, not 393 pt

This is the single largest difference and it explains several others.

**Evidence (scale-free ratios, so no assumption about pixels per point):**

| Measurement in photo | Ratio to screen width | iOS source value | Implied screen width |
|---|---|---|---|
| Chat-list avatar, `ios-10` (44 px of 326 px) | 0.135 | 60 pt (`ChatListItem.swift:2160`) | 60 / 0.135 = **444 pt** |
| Chat-list row pitch, `ios-10` (55.5 px) | 0.170 | ~76 pt (`ChatListItem.swift:4264-4279`) | **447 pt** |
| Translate bar height, `ios-12` (40 px of 454 px) | 0.088 | 40 pt (`ChatTranslationPanelNode.swift:156`) | **454 pt** |
| Keyboard row pitch, `ios-12` (57.5 px) | 0.127 | 56 pt on Pro Max keyboards | **442 pt** |
| "Translate to English" 17 pt text width, `ios-12` (156 px) | 0.344 | ~150 pt in SF Pro | **437 pt** |

All five agree on ~440 pt (iPhone 16/17 Pro Max). None fits 393 pt.

**What this did to TGlass.** `ProvideUiScale` lays the app out 393 dp wide (`core/design/Theme.kt:717`). Values
were then taken from two sources that disagree by 393/440 = 0.893:

- Values **measured from the photos** assuming 393 pt came out 11 % too small in points (chat row 69 instead of
  76, avatar 55 instead of 60). On screen they have the right proportion.
- Values **taken from the iOS source** (bars 40/44, composer 40, message text 17, settings rows) are correct in
  points, but on a 393-wide layout they are 12 % larger relative to the screen than on the owner's iPhone.
- The font was then squeezed to make text fit the wrong scale: Inter is pinned to `opsz 32` plus an extra
  −0.02 em tracking, calibrated against "Translate to English = 131 pt" (`Theme.kt:283-286, 353-358`). The true
  width is ~147-150 pt. This is why headings look tight ("Contacts", "Appearance").

**Fix (P0, do first, everything else depends on it):**

1. `Theme.kt:717` `ReferenceWidthDp = 393f` → `440f`. Keep the 0.75-1.3 clamp.
2. Re-derive `InterWidthCorrectionEm` against the corrected targets (131 → 147, 61 → 68, 87.5 → 98 pt). Expect
   it to drop to about 0 and `opsz` to return to the text cut.
3. Restore the photo-measured values to source points (section 1 below).
4. Then re-shoot CI screenshots and compare ratios to `ios-10`/`ios-12` again.

---

## 1. Chat list (seen most)

| # | Element | iOS | TGlass | Fix | Conf |
|---|---|---|---|---|---|
| 1.1 | Row height (default) | ~76 pt: `4·lineH − 8`, `ChatListItem.swift:4264-4279` | 69 (`Theme.kt:416` Compact, default) | Make default 76; drop or rename "Compact" | H |
| 1.2 | Avatar | 60 (`ChatListItem.swift:2728`) | 55 (`Theme.kt:416`) | 60 | H |
| 1.3 | Avatar left inset | 16 (`:2729`) | 10 (`ChatRow.kt:203`, row padding) | 16 | H |
| 1.4 | Avatar → text gap | 8 (`:2739`, text x = 16+8+60 = 84) | 10 (`ChatRow.kt:154`) | 8 | H |
| 1.5 | Separator left inset | 84 (= text x) | 75 (`ChatRow.kt:203`: 10+55+10) | follows 1.2-1.4 | H |
| 1.6 | "Regular" preset | n/a (iOS scales row with text size: 17 → 76/60) | 82/60, title 17, preview 16 (`Theme.kt:417`) | Regular = 76/60/16/15; Large from text size 19 | H |
| 1.7 | Unread badge text | 12 semibold, monospaced digits, Ø 20 (`:2293, :2578` in older numbering) | 13 regular (`ui/components/Components.kt:175`) | 12 semibold | H |
| 1.8 | Folder tab title | medium 15, side inset 16, badge medium 12 with insets 1/5/2/5 (`HorizontalTabsComponent.swift:1311-1404`) | subheadline 15 medium, badge 12 semibold (`ChatListScreen.kt:572, 579`) | badge weight medium | H |
| 1.9 | Folder strip height | lens inset 3 (`HorizontalTabsComponent.swift:151`); strip is 44 with glass buttons | 38 strip, 30 selected pill (`ChatListScreen.kt:550-566`) | re-measure after step 0; likely 40-44 / 34-38 | L |
| 1.10 | Search pill side inset | ~20 pt at 440 (`ios-10`: 15 px margin of 326) | 12 (`ChatListScreen.kt:528`) | 16-20 after step 0 | M |

## 2. Chat screen

### 2.1 Header and panels

| # | Element | iOS | TGlass | Fix | Conf |
|---|---|---|---|---|---|
| 2.1 | Title subtitle (status) | regular 13 (`ChatTitleView.swift:27`) | 12 / line 14 (`ChatScreen.kt:858`) | 13 | H |
| 2.2 | Header avatar hit box | 44 × 44 (`ChatAvatarNavigationNode.swift:82, 349`) | 40 (`ChatChrome.kt:52` `ChatBarHeight`) | keep 40 visual, 44 touch | M |
| 2.3 | Pinned panel height | 50 (`ChatPinnedMessageTitlePanelNode.swift:258`) | 48 (`ChatChrome.kt:58`) | 50 | H |
| 2.4 | Pinned title font | rounded semibold 15 for the action; title 15 | medium 15 (`ChatChrome.kt:263`) | check weight on device | L |
| 2.5 | Whole chrome vs screen | 40 pt bars on a 440 pt screen = 9.1 % | 40 dp on 393 = 10.2 % | fixed by step 0 | H |

### 2.2 Bubbles

| # | Element | iOS | TGlass | Fix | Conf |
|---|---|---|---|---|---|
| 2.6 | Max bubble width | `width − 36` (then edge insets and avatar inset), `ChatMessageItemCommon.swift` `ChatMessageItemWidthFill` + `ChatMessageBubbleItemNode.swift:2222-2237`. On 440 pt ≈ 385 pt ≈ 88 % | 78 % of width, capped at 326 (`ChatScreen.kt:272`) | `screenW − 36 − 9 − avatarInset`, no 326 cap | H |
| 2.7 | Spacing between separate bubbles | 2 + 1 px (`defaultSpacing`), merged 0 | 3 dp top+bottom = 6 between, 1 dp merged = 2 (`ChatScreen.kt:1582`) | 1.2 dp / 0 dp halves → ~2.3 and 0 | H |
| 2.8 | Date header | 34 pt tall row, pill 22 (`ChatMessageDateHeader.swift:99, 454`) | pill 22 with `vertical = 5` → 32 (`Bubble.kt:1389`) | 6 dp → 34 | H |
| 2.9 | Round video message | 240 × 240 (compact-width layout: 212), `ChatMessageItemCommon.swift` `instantVideo` | not verified in this pass | check `VideoNote.kt` against 212/240 | L |
| 2.10 | Poll text | percentage bold 14.5 / 12.5, count regular 12.5 (`ChatMessagePollBubbleContentNode.swift:385-387`) | 14 (`BubbleAudioPoll.kt:470, 552, 631`) | 14.5 bold for %, 12.5 for counts | M |
| 2.11 | Swipe-to-reply circle | 33 (`ChatMessageSwipeToReplyNode.swift:9`) | not verified | check `MessageGestures.kt` | L |

### 2.3 Composer

| # | Element | iOS | TGlass | Fix | Conf |
|---|---|---|---|---|---|
| 2.12 | Field min height | 33 + 5 + 4 = 42 (`ChatTextInputPanelNode.swift:670, 2378-2383`); side buttons 40 (`:2521`) | field 40 (`ChatChrome.kt:52`) | 42 field, 40 buttons, bottom-aligned | M |
| 2.13 | Field text insets | top 5, left 12, bottom 4, right 11 (`:670`) | start 12 / end 2 (`Composer.kt:365`) | match | M |
| 2.14 | Panel side inset | 8 (`:1474`), button gap 6 (`:2442`) | 8 and 6 | none | – |

## 3. Tab bar

| # | Element | iOS | TGlass | Fix | Conf |
|---|---|---|---|---|---|
| 3.1 | Bar height | 56 + 2·4 = 64 (`TabBarComponent.swift:673, 693`) | 62 (`core/glass/LiquidBottomTabs.kt:183`) | 64 | H |
| 3.2 | Selection lens height | 56 | 54 (`LiquidBottomTabs.kt:226, 290, 307`) | 56 | H |
| 3.3 | Search circle | = bar height, 64 (`:892`); gap 8 | 62 (`features/main/MainScreen.kt:159`); gap 8 | 64 | H |
| 3.4 | Label | semibold 10 (`:1296`) | 10.5 semibold (`MainScreen.kt:197`) | 10 | H |
| 3.5 | Badge text | regular 13 (`:1324`) | 11 semibold in a 16 pt pill (`MainScreen.kt:211-223`) | 13 regular, pill ~18-20 | H |

## 4. Navigation transitions

| # | Element | iOS | TGlass | Fix | Conf |
|---|---|---|---|---|---|
| 4.1 | Card shadow | black, radius 12, opacity 0.04, offset (0, −3) (`NavigationTransitionCoordinator.swift:156-163`) | elevation 12, ambient 0.15 / spot 0.2 (`core/navigation/Navigation.kt:267-269`) | drop to ~0.04-0.06 | H |
| 4.2 | Dim over the lower screen | UIKit parallax dim ≈ 0.1 black | 0.5 in dark, 0.12 in light (`Navigation.kt:202`) | 0.1-0.15 in both | M |
| 4.3 | Parallax of lower screen | −30 % | −0.3 (`Navigation.kt:274`) | none | – |

## 5. Profile

| # | Element | iOS | TGlass | Fix | Conf |
|---|---|---|---|---|---|
| 5.1 | Action buttons (call, mute, search, more) | Equal-width **rounded-rect tiles**, height 58, corner 16, gap 8, side inset 16; 40 pt icon with the lowercase 11 pt label **inside** the tile, 9 pt from the bottom (`PeerInfoHeaderNode.swift:2237-2298`, `PeerInfoHeaderButtonNode.swift:178, 301, 316, 327`) | 48 pt glass **circles** with the label underneath, row 66 (`features/profile/ProfileHeader.kt:176-178, 440-470`) | Tiles 58 high, r 16, label inside | H |
| 5.2 | Title | medium 28 (`PeerInfoHeaderNode.swift:1310`) | 26 / line 31 (`ProfileHeader.kt:353`) | 28 medium | H |
| 5.3 | Subtitle | regular 17, 16 when collapsed (`:1320-1330`) | not verified | check | L |
| 5.4 | Info row label | regular 14 (`PeerInfoScreenLabeledValueItem.swift:589`) | subheadline 15 (`features/profile/ProfileInfo.kt:101`) | 14 | H |
| 5.5 | Pane tabs | medium 15, tabs height 40 (`PeerInfoPaneContainerNode.swift:104, 1011`) | not verified | check `ProfileTabs.kt` | L |

## 6. Settings and grouped lists

iOS 26 uses the `.glass` item-list style, which is taller than the legacy one TGlass copied.

| # | Element | iOS | TGlass | Fix | Conf |
|---|---|---|---|---|---|
| 6.1 | Settings main rows | text height + 32 ≈ 53-54 (`PeerInfoScreenDisclosureItem.swift:237`) | min 44 (`core/design/Dimens.kt:41`, `Components.kt:311`) | 54 | H |
| 6.2 | Sub-page disclosure rows | vertical inset 15 (13 with a detail line) → ~52; legacy was 11 → 44 (`ItemListDisclosureItem.swift:519-553`) | 44 | 52 | M |
| 6.3 | Switch rows | 52 in glass style (`ItemListSwitchItem.swift:336-339`) | 44 | 52 | M |
| 6.4 | Icon tile | 30 (`PeerInfoScreenDisclosureItem.swift:258`) | 29 (`Components.kt:265`) | 30 | H |
| 6.5 | Text start with icon | 16 + 29 + 16 = 61 (`:177`) | 60 (`Components.kt:305`) | 61-62 | H |
| 6.6 | Separator right inset | 16 in glass style (`ItemListSwitchItem.swift:292`) | 0, runs to the edge (`ui/components/Basics.kt:174-181`) | add `endPadding = 16` | M |
| 6.7 | Row subtitle | 14/17 of base = 14 (`ItemListSwitchItem.swift:298`), 15 for disclosure detail (`ItemListDisclosureItem.swift:470`) | subheadline 15 everywhere (`Components.kt:325`) | 14 under switches | M |
| 6.8 | Footer text insets | left 15, top/bottom 7 (`ItemListTextItem.swift:162-164`) | start 16, top 7 (`Components.kt:258`) | none worth doing | – |
| 6.9 | Section corner radius | 26 (`ItemListItem.swift:191`) | 26 (`Components.kt:253`) | none | – |

## 7. Alerts, action sheets, menus

| # | Element | iOS | TGlass | Fix | Conf |
|---|---|---|---|---|---|
| 7.1 | Alert content side inset | 30; top 22, bottom 21; content gap 8; button side inset 16, gap 8 (`AlertComponent.swift:353-359`) | 18 + 4 = 22 side, top 22, bottom 16; button gap 10 (`ui/components/ActionSheet.kt:170-201`) | 30 side, gap 8 | H |
| 7.2 | Alert corner | 35 (`AlertComponent.swift:602`) | 34 (`ActionSheet.kt:169`) | 35 | H |
| 7.3 | Action sheet row | 57 tall, regular 20 (medium 20 for bold) (`ActionSheetButtonItem.swift:65-66, 172`) | 56, ~18 (17 × 1.06) (`ActionSheet.kt:303-307`, `Dimens.kt:32`) | 57 / 20 | M |
| 7.4 | Action sheet group corner, inset | 16, inset 10 (`ActionSheetItemGroupNode.swift:44`, `ActionSheetControllerNode.swift:5`) | 28, inset 10 (`ActionSheet.kt:264-289`) | Legacy file; iOS 26 may round more. Check on device before changing | L |
| 7.5 | Context menu icon | 24, in a 32 slot, side inset 18, vertical 11 (`ContextControllerActionsStackNode.swift:213-218, 400`) | 22 for vector icons, 24 for drawables, start 14 + 6 (`ui/components/ContextMenu.kt:420-428`) | all 24 | H |

## 8. Contacts and Calls

| # | Element | iOS | TGlass | Fix | Conf |
|---|---|---|---|---|---|
| 8.1 | Contact avatar | 40 (`ContactsPeerItem.swift:833`) | 42 (`features/contacts/ContactsScreen.kt:164`) | 40 | H |
| 8.2 | Contact name | regular 17 with the sort-key part in **medium** 17 (`:821-822, 964-998`) | headline = semibold 17 for the whole name (`ContactsScreen.kt:167`) | regular + medium | H |
| 8.3 | Contact status | regular 13 (`:824-830`) | subheadline 15 (`ContactsScreen.kt:168`) | 13 | H |
| 8.4 | Text x / separator inset | 65 (`:838`) | 14 + 42 + 12 = 68, separator 70 (`ContactsScreen.kt:161-171`) | 65 | H |
| 8.5 | Contact row height | ~50-52 with status | 58 (`ContactsScreen.kt:161`) | ~52 | M |
| 8.6 | Call row title | medium 17 (`CallListCallItem.swift:308`) | semibold 17 (`features/calls/CallsScreen.kt:162`) | medium | H |
| 8.7 | Call row status | regular 14 (`:309`); date regular 15 (`:310`) | 15 and 15 (`CallsScreen.kt:167, 170`) | status 14 | H |
| 8.8 | Call row avatar / height | 40, vertical inset 8 → ~56 (`:311, 537-542`) | 42, 62 (`CallsScreen.kt:148, 159`) | 40 / 56 | H |

## 9. Not compared in this pass

Media viewer, emoji panel, login screens, in-app banner and the media editor were not compared line by line.
The iOS files to use are `GalleryUI/Sources/ChatItemGalleryFooterContentNode.swift`,
`TelegramUI/Components/EntityKeyboard/Sources/EntityKeyboard.swift`,
`AuthorizationUI/Sources/AuthorizationSequencePhoneEntryControllerNode.swift`,
`TelegramUI/Sources/NotificationItemContainerNode.swift`. Do them after step 0, because step 0 changes every
proportion on those screens.

---

## Prioritized fix list

**P0 (most visible, in this order)**

1. Reference width 393 → 440 (`core/design/Theme.kt:717`).
2. Undo the font squeeze: re-derive `InterWidthCorrectionEm` and `opsz` (`Theme.kt:283-286, 353-358`).
3. Chat list row 76 / avatar 60 / insets 16 + 8 (`Theme.kt:416-418`, `features/chatlist/ChatRow.kt:154, 203`).
4. Bubble max width `screenW − 36` instead of 78 % / 326 (`features/chat/ChatScreen.kt:272`).
5. Bubble spacing 2.3 / 0 instead of 6 / 2 (`ChatScreen.kt:1582`).
6. Tab bar 64 / lens 56 / search 64 / label 10 / badge 13 regular (`LiquidBottomTabs.kt`, `MainScreen.kt`).
7. Settings and list rows 52-54 instead of 44, separator right inset 16 (`Dimens.kt:41`, `Components.kt`, `Basics.kt`).

**P1**

8. Profile action buttons as 58 pt tiles with the label inside (`features/profile/ProfileHeader.kt`).
9. Contacts row: avatar 40, name regular + medium, status 13, inset 65 (`ContactsScreen.kt`).
10. Calls row: medium title, status 14, avatar 40, height 56 (`CallsScreen.kt`).
11. Chat title status 13; pinned panel 50; composer field 42 (`ChatScreen.kt:858`, `ChatChrome.kt:52, 58`).
12. Push transition: shadow 0.04-0.06, dim 0.1-0.15 in dark (`core/navigation/Navigation.kt:202, 267-269`).
13. Unread badge 12 semibold (`Components.kt:175`); profile title 28 medium; info label 14.

**P2**

14. Alert insets 30 / corner 35 / gap 8; action sheet 57 / 20 pt; menu icons 24.
15. Date header 34; poll fonts 14.5 / 12.5; icon tile 30.
16. Section 9 screens, after re-shooting screenshots.

**Quick wins (under an hour each):** 1, 5, 6, 11, 12, 13, 15, and the font-size rows 2.1, 5.4, 8.3, 8.6, 8.7.

**Risk note for item 1:** it shrinks everything by 10.7 % at once. Every value that was measured from the
photos under the 393 assumption then becomes too small and must be raised in the same change: the chat-list
rows (section 1) for certain, and possibly others whose comments say "measured" or "pixel-scanned". Search the
code for those words before shipping.
