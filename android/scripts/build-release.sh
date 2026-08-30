#!/usr/bin/env bash
#
# StreamLiveX – Google Play yayın derlemesi (Linux / macOS / CI)
#
# Google Play tek uygulamayla telefon, tablet, Android TV ve Google TV'yi karşılar.
# Play Console yalnızca Android App Bundle (.aab) kabul eder; bu betik imzalı AAB üretir.
#
# Kullanım (android/ klasöründen veya kökten):
#   ./scripts/build-release.sh
#   VERSION_CODE=3 VERSION_NAME=1.0.2 ./scripts/build-release.sh
#   WEB_URL="https://streamlivex.com/app" APK=1 ./scripts/build-release.sh
#
# İmza bilgileri android/keystore.properties veya STREAMLIVEX_* ortam
# değişkenlerinden okunur (bkz. android/PUBLISHING.md). İmza yoksa betik durur.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_ROOT="$(dirname "$SCRIPT_DIR")"
cd "$ANDROID_ROOT"

if [[ ! -x ./gradlew ]]; then
  chmod +x ./gradlew 2>/dev/null || true
fi

# İmza kontrolü
if [[ ! -f keystore.properties ]] \
  && [[ -z "${STREAMLIVEX_KEYSTORE_FILE:-}" || -z "${STREAMLIVEX_KEYSTORE_PASSWORD:-}" \
     || -z "${STREAMLIVEX_KEY_ALIAS:-}"    || -z "${STREAMLIVEX_KEY_PASSWORD:-}" ]]; then
  echo ""
  echo "  HATA: Yayın imzası bulunamadı."
  echo "  android/keystore.properties oluşturun (keystore.properties.example kopyalayın)"
  echo "  veya STREAMLIVEX_KEYSTORE_* ortam değişkenlerini tanımlayın."
  echo "  Ayrıntı: android/PUBLISHING.md"
  echo "" >&2
  exit 1
fi

ARGS=()
[[ "${SKIP_CLEAN:-}" == "1" ]] || ARGS+=(clean)
ARGS+=(bundleRelease)
[[ "${APK:-}" == "1" ]] && ARGS+=(assembleRelease)

[[ -n "${VERSION_CODE:-}" ]] && ARGS+=("-PSTREAMLIVEX_VERSION_CODE=${VERSION_CODE}")
[[ -n "${VERSION_NAME:-}" ]] && ARGS+=("-PSTREAMLIVEX_VERSION_NAME=${VERSION_NAME}")
[[ -n "${WEB_URL:-}" ]]      && ARGS+=("-PSTREAMLIVEX_WEB_URL=${WEB_URL}")

echo "==> ./gradlew ${ARGS[*]}"
./gradlew "${ARGS[@]}"

AAB="app/build/outputs/bundle/release/app-release.aab"
echo ""
echo "  AAB hazır: ${ANDROID_ROOT}/${AAB}"
[[ -f "$AAB" ]] && echo "  Boyut: $(du -h "$AAB" | cut -f1)"
[[ "${APK:-}" == "1" ]] && echo "  APK:  ${ANDROID_ROOT}/app/build/outputs/apk/release/app-release.apk"
echo ""
echo "  Sonraki adım: Play Console > Üretim (veya Kapalı test) > Yeni sürüm > bu .aab dosyasını yükleyin."
echo "  Rehber: android/PUBLISHING.md"
