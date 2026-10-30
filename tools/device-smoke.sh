#!/usr/bin/env bash
set -euo pipefail
capture() {
  mkdir -p device-smoke/screenshots
  adb pull /sdcard/Android/data/space.vexo.music.radio/files/device-smoke/. device-smoke/screenshots/ || true
  adb logcat -d -t 2000 > device-smoke/logcat.txt || true
}
trap capture EXIT
adb shell wm size 960x1920
adb shell wm density 480
adb shell settings put system font_scale 1.0
bash gradlew --no-daemon connectedDebugAndroidTest
mkdir -p device-smoke/normal-text
cp -R app/build/outputs/androidTest-results/connected device-smoke/normal-text/
adb shell settings put system font_scale 1.6
bash gradlew --no-daemon connectedDebugAndroidTest
mkdir -p device-smoke/large-text
cp -R app/build/outputs/androidTest-results/connected device-smoke/large-text/
