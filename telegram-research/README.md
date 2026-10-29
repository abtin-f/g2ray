# Telegram UI Research Dataset

Reference data for building an Android client that looks and behaves like **Telegram for iOS (2026, Liquid Glass)**.
Every numeric value here is taken from the **official Telegram-iOS source code** and cites the file it came from,
so the Android app (`../android`) can be checked against it instead of against guesses or screenshots.

- Source snapshot: `TelegramMessenger/Telegram-iOS` @ `6ad963e5` (2026-07-18)
- Units: iOS points (pt). On Android 1 pt ≈ 1 dp; font pt ≈ sp.
- Default text size = 17 pt (`baseDisplaySize`). Most sizes scale as `value * baseDisplaySize / 17`.

| File | Contents |
|---|---|
| [`SOURCES.md`](SOURCES.md) | All sources, grouped by what each one is used for |
| [`MASTER_SPEC.md`](MASTER_SPEC.md) | One table with every extracted value, its source, and whether the app matches it |
| [`visual/colors.yaml`](visual/colors.yaml) | Day and Night theme colors |
| [`visual/typography.yaml`](visual/typography.yaml) | Font sizes and weights |
| [`components/*.yaml`](components) | One record per component (trigger, layout, animation, haptics, source) |

## How values were extracted

1. Blobless clone of Telegram-iOS, then `git checkout` of only the relevant modules.
2. Read the layout code (e.g. `ChatListItem.swift` `asyncLayout`) and the theme files.
3. Copy constants as-is; derived values are marked `derived:` with the formula.

## Updating

Re-run the extraction against a newer commit and diff `MASTER_SPEC.md`. UI changes are also announced on
<https://telegram.org/blog>. Check it before a big update.
