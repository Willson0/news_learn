#!/usr/bin/env bash
# Запускается внутри android/ при поднятом эмуляторе.
set -u
status=0
./gradlew :app:connectedDebugAndroidTest --console=plain \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true || status=$?
mkdir -p shots
for f in calendar toast_dialog; do
  adb exec-out run-as app.karta.likvidnosti cat "files/shots/$f.png" > "shots/$f.png" || true
done
ls -la shots
exit $status
