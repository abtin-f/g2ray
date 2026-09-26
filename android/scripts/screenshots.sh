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

shot() {
  local screen=$1 theme=$2 wait=${3:-5}
  adb shell am force-stop com.abtin.tglass
  adb shell input keyevent KEYCODE_WAKEUP || true
  adb shell am start -W -n com.abtin.tglass/.MainActivity --es screen "$screen" --es theme "$theme" >/dev/null
  sleep "$wait"
  adb exec-out screencap -p > "$OUT/${screen}_${theme}.png"
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
