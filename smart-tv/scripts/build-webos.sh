#!/usr/bin/env bash
# StreamLiveX — webOS (.ipk) paketleme
# Kullanım: bash smart-tv/scripts/build-webos.sh
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
app_dir="${root}/smart-tv/webos"
out_dir="${root}/smart-tv/dist"

mkdir -p "${out_dir}"

echo "==> webOS paketi hazırlanıyor: ${app_dir}"
ares-package "${app_dir}" --outdir "${out_dir}"

echo "==> Üretilen paket(ler):"
ls -la "${out_dir}"/*.ipk
