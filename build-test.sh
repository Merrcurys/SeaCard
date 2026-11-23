#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Тестовая сборка только под arm64-v8a (v8a):  ./build-test.sh [--install]
#
#   По умолчанию собирается debug APK flavor merrcurys.
#   --install — сразу установить собранный APK на подключённое устройство (adb).
#
# Release-вариант: ./build-test.sh --release
# Подпись берётся из keystore.properties в корне проекта.
# ---------------------------------------------------------------------------
set -euo pipefail
cd "$(dirname "$0")"

ABI="arm64-v8a"
BUILD_TYPE="debug"
GRADLE_TASK=":app:assembleMerrcurysDebug"
DO_INSTALL=0

for arg in "$@"; do
  case "$arg" in
    --release)
      BUILD_TYPE="release"
      GRADLE_TASK=":app:assembleMerrcurysRelease"
      ;;
    --install)
      DO_INSTALL=1
      ;;
    -h|--help)
      sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *)
      echo "Неизвестный аргумент: $arg" >&2
      exit 1
      ;;
  esac
done

if [[ "$BUILD_TYPE" == "release" && ! -f keystore.properties ]]; then
  echo "ВНИМАНИЕ: keystore.properties не найден — release будет подписан debug-ключом." >&2
fi

echo "==> Сборка $BUILD_TYPE APK только под $ABI"
./gradlew "$GRADLE_TASK" -PabiSplits=true -PabiFilter="$ABI" --console=plain

APK_DIR="app/build/outputs/apk/merrcurys/$BUILD_TYPE"
APK="$(find "$APK_DIR" -type f -name "*-$ABI-*.apk" | sort | tail -n 1 || true)"

if [[ -z "$APK" ]]; then
  echo "ОШИБКА: APK для $ABI не найден в $APK_DIR" >&2
  exit 1
fi

echo
echo "Готовый APK ($ABI, $BUILD_TYPE):"
echo "  $(pwd)/$APK"

if [[ "$DO_INSTALL" -eq 1 ]]; then
  echo
  echo "==> Установка на подключённое устройство"
  adb install -r "$APK"
fi
echo
