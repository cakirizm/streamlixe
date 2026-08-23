#!/usr/bin/env bash
# StreamLiveX — webOS (.ipk) derleme + paketleme
# 1) TV SPA'yı derler (vite.tv.config.ts)
# 2) appinfo.json + ikonlar + SPA çıktısını staging'de birleştirir
# 3) ares-package ile imzasız .ipk üretir → smart-tv/dist/
# Kullanım: bash smart-tv/scripts/build-webos.sh
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
spa_dist="${root}/smart-tv/spa/dist"
stage="${root}/smart-tv/build/webos"
out_dir="${root}/smart-tv/dist"

echo "==> [1/3] TV SPA derleniyor..."
( cd "${root}" && npx vite build --config vite.tv.config.ts )

echo "==> [2/3] webOS staging hazırlanıyor: ${stage}"
rm -rf "${stage}"
mkdir -p "${stage}"
cp -r "${spa_dist}/." "${stage}/"                 # index.html + assets/
cp "${root}/smart-tv/webos/appinfo.json" "${stage}/"
cp "${root}/smart-tv/webos/icon.png" "${stage}/"
cp "${root}/smart-tv/webos/largeIcon.png" "${stage}/"

echo "==> [3/3] .ipk paketleniyor..."
mkdir -p "${out_dir}"
ares-package "${stage}" --outdir "${out_dir}"

echo "==> Üretilen paket(ler):"
ls -la "${out_dir}"/*.ipk
