#!/usr/bin/env bash
# Captures screenshots of the main screens on a running emulator/device.
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=screenshots
mkdir -p "$OUT"
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell settings put system screen_off_timeout 1800000 || true
adb shell svc power stayon true || true
adb shell input keyevent KEYCODE_WAKEUP || true
adb shell wm dismiss-keyguard || true
sleep 2

adb logcat -c || true
# Warm-up: first launch compiles/JITs the app on the emulator.
adb shell am start -W -n com.abtin.tglass/.MainActivity --es screen chats --es theme light >/dev/null || true
sleep 20

shot() {
  local screen=$1 theme=$2 wait=${3:-5}
  adb shell am force-stop com.abtin.tglass
  adb shell input keyevent KEYCODE_WAKEUP || true
  adb shell am start -W -n com.abtin.tglass/.MainActivity --es screen "$screen" --es theme "$theme" >/dev/null
  sleep "$wait"
  adb exec-out screencap -p > "$OUT/${screen}_${theme}.png"
  {
    echo "=== ${screen}_${theme}"
    adb shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity" | head -2
    adb logcat -d -b crash
  } >> "$OUT/log.txt" 2>&1 || true
  echo "captured ${screen}_${theme}"
}

for theme in light dark; do
  shot chats "$theme"
  shot chat "$theme"
  shot chat_menu "$theme" 7
  shot settings_tab "$theme"
  shot profile "$theme"
done
shot welcome light
shot group light
shot channel light
shot appearance light
shot power light
shot contacts light
shot calls light
shot devices dark

adb logcat -d -t 2000 "*:W" > "$OUT/logcat_warn.txt" 2>&1 || true
