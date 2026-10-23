#!/usr/bin/env bash
# Drives PixelPocket on an emulator and captures Play Store screenshots into ./shots.
set -u
OUT=shots
mkdir -p "$OUT" samples
PKG=com.pixelpocket.gallery

log() { echo "::group::$*"; echo "::endgroup::"; }
shot() { sleep 2; adb exec-out screencap -p > "$OUT/$1.png"; echo "captured $1"; }

dump() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb shell cat /sdcard/ui.xml > ui.xml
}
# Prints "x y" for the centre of the first node whose attributes match $1.
center() {
  dump
  local b
  b=$(grep -o "<node[^>]*$1[^>]*>" ui.xml | head -1 | grep -o 'bounds="[^"]*"')
  [ -z "$b" ] && { echo "node not found: $1" >&2; return 1; }
  set -- $(echo "$b" | grep -oE '[0-9]+' | tr '\n' ' ')
  echo "$(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))"
}
tap() { local c; c=$(center "$1") && adb shell input tap $c; sleep 1.5; }
longpress() { local c; c=$(center "$1") && adb shell input swipe $c $c 900; sleep 1.5; }

# ---- sample media -------------------------------------------------------
for i in $(seq 1 14); do
  curl -sSL --retry 3 -o "samples/photo_$(printf %02d "$i").jpg" "https://picsum.photos/seed/pixelpocket$i/1200/900" || true
done
if command -v ffmpeg >/dev/null; then
  ffmpeg -loglevel error -y -loop 1 -i samples/photo_03.jpg -t 8 -vf "scale=720:-2,zoompan=z='min(zoom+0.0015,1.3)':d=200:s=720x540" \
    -pix_fmt yuv420p samples/clip_beach.mp4 || true
fi
adb shell mkdir -p /sdcard/Pictures/Holidays /sdcard/DCIM/Camera /sdcard/Movies
n=0
for f in samples/*; do
  name=$(basename "$f")
  case $((n % 3)) in
    0) dest=/sdcard/DCIM/Camera ;;
    1) dest=/sdcard/Pictures/Holidays ;;
    2) dest=/sdcard/DCIM/Camera ;;
  esac
  [[ $name == *.mp4 ]] && dest=/sdcard/Movies
  adb push "$f" "$dest/$name" >/dev/null
  # Spread items over a few days so the date headers show.
  day=$(( n / 4 ))
  adb shell touch -d "$(date -u -d "-$day day" +%Y-%m-%dT%H:%M:%S)" "$dest/$name" || true
  n=$((n + 1))
done
adb shell content call --method scan_volume --uri content://media --arg external_primary >/dev/null 2>&1 || true
sleep 5

# ---- clean status bar ---------------------------------------------------
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter >/dev/null
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1000 >/dev/null
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false >/dev/null
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 >/dev/null
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false >/dev/null

# ---- app flow -------------------------------------------------------------
adb install -r PixelPocket.apk
adb shell pm grant $PKG android.permission.READ_MEDIA_IMAGES
adb shell pm grant $PKG android.permission.READ_MEDIA_VIDEO
adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 6
shot 01_registration

tap 'text="Full name"';   adb shell input text "Nagesh"
tap 'text="Email"';       adb shell input text "nagesh@example.com"
adb shell input keyevent 111
sleep 1
tap 'text="Register &amp; continue"'
sleep 6
shot 02_gallery

tap 'content-desc="Sort"'
shot 03_sort_options
adb shell input keyevent 4
sleep 1

longpress 'content-desc="photo_01.jpg"'
tap 'content-desc="photo_02.jpg"'
tap 'content-desc="photo_05.jpg"'
shot 04_multi_select
tap 'content-desc="Move"'
shot 05_move_to_album
adb shell input keyevent 4
sleep 1
tap 'content-desc="Select all"'
shot 06_select_all
tap 'content-desc="Exit selection"'

tap 'content-desc="photo_04.jpg"'
sleep 3
shot 07_viewer
tap 'text="Crop"'
sleep 4
tap 'text="1:1"'
shot 08_crop
tap 'text="Save"'
shot 09_save_preferences
adb shell input keyevent 4
sleep 1
adb shell input keyevent 4
sleep 1
adb shell input keyevent 4
sleep 1
tap 'content-desc="Settings"'
shot 10_settings

# Fail loudly if the app crashed at any point.
if adb logcat -d | grep -q "FATAL EXCEPTION"; then
  adb logcat -d | grep -A 30 "FATAL EXCEPTION"
  exit 1
fi
ls -la "$OUT"
