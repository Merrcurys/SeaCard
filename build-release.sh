#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Полный release одной командой:  ./build-release.sh
#
#   ru.merrcurys.seacard -> AAB + universal APK + APK-сплиты (x86_64, arm64-v8a, armeabi-v7a)
#   com.example.seacard  -> только AAB
#
# Подписи берутся из keystore.properties в корне проекта.
# ---------------------------------------------------------------------------
set -euo pipefail
cd "$(dirname "$0")"

if [[ ! -f keystore.properties ]]; then
  echo "ВНИМАНИЕ: keystore.properties не найден — release будет подписан debug-ключом." >&2
fi

echo "==> [1/2] App Bundle (AAB): ru.merrcurys.seacard + com.example.seacard"
./gradlew bundleReleaseAll --console=plain

echo "==> [2/2] APK: ru.merrcurys.seacard (universal + x86_64/arm64-v8a/armeabi-v7a)"
./gradlew :app:assembleMerrcurysRelease -PabiSplits=true --console=plain

OUTPUTS="app/build/outputs"
echo
echo "Готовые релизы сохранены в:"
echo "  $(pwd)/$OUTPUTS"
if [[ -d "$OUTPUTS" ]]; then
  find "$OUTPUTS" -type f \( -name '*.aab' -o -name '*.apk' \) | sort | sed 's|^|  |'
else
  echo "  (артефакты не найдены)"
fi
echo
