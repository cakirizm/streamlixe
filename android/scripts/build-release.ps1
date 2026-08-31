<#
    StreamLiveX – Google Play yayın derlemesi (Windows / PowerShell)

    Google Play tek bir uygulamayla telefon, tablet, Android TV ve Google TV'yi
    aynı anda karşılar. Play Console yalnızca Android App Bundle (.aab) kabul eder;
    bu betik imzalı AAB üretir ve isteğe bağlı olarak APK de çıkarır.

    Kullanım (android/ klasöründe veya kökten):
        ./scripts/build-release.ps1
        ./scripts/build-release.ps1 -VersionCode 3 -VersionName "1.0.2"
        ./scripts/build-release.ps1 -WebUrl "https://streamlivex.com/app" -Apk

    İmza bilgileri android/keystore.properties dosyasından veya STREAMLIVEX_*
    ortam değişkenlerinden okunur (bkz. android/PUBLISHING.md). İmza yoksa betik
    durur — Play'e imzasız/debug AAB YÜKLENEMEZ.
#>
[CmdletBinding()]
param(
    [int]    $VersionCode,
    [string] $VersionName,
    [string] $WebUrl,
    [switch] $Apk,
    [switch] $SkipClean
)

$ErrorActionPreference = 'Stop'

# android/ kök klasörünü betiğin konumundan bul (scripts/ bir üst dizin).
$AndroidRoot = Split-Path -Parent $PSScriptRoot
Set-Location $AndroidRoot

$gradlew = Join-Path $AndroidRoot 'gradlew.bat'
if (-not (Test-Path $gradlew)) {
    throw "gradlew.bat bulunamadi: $gradlew"
}

# İmza var mı? (keystore.properties veya ortam degiskeni)
$propsFile = Join-Path $AndroidRoot 'keystore.properties'
$envSigning = $env:STREAMLIVEX_KEYSTORE_FILE -and $env:STREAMLIVEX_KEYSTORE_PASSWORD `
    -and $env:STREAMLIVEX_KEY_ALIAS -and $env:STREAMLIVEX_KEY_PASSWORD
if (-not (Test-Path $propsFile) -and -not $envSigning) {
    Write-Host ""
    Write-Host "  HATA: Yayin imzasi bulunamadi." -ForegroundColor Red
    Write-Host "  android/keystore.properties olusturun (keystore.properties.example'i kopyalayin)"
    Write-Host "  veya STREAMLIVEX_KEYSTORE_* ortam degiskenlerini tanimlayin."
    Write-Host "  Ayrinti: android/PUBLISHING.md"
    Write-Host ""
    throw "Yayin imzasi eksik; Play'e imzasiz paket yuklenemez."
}

# Gradle argümanları
$gradleArgs = @()
if (-not $SkipClean) { $gradleArgs += 'clean' }
$gradleArgs += 'bundleRelease'
if ($Apk) { $gradleArgs += 'assembleRelease' }

if ($VersionCode) { $gradleArgs += "-PSTREAMLIVEX_VERSION_CODE=$VersionCode" }
if ($VersionName) { $gradleArgs += "-PSTREAMLIVEX_VERSION_NAME=$VersionName" }
if ($WebUrl)      { $gradleArgs += "-PSTREAMLIVEX_WEB_URL=$WebUrl" }

Write-Host "==> gradlew $($gradleArgs -join ' ')" -ForegroundColor Cyan
& $gradlew @gradleArgs
if ($LASTEXITCODE -ne 0) { throw "Gradle derlemesi basarisiz (exit $LASTEXITCODE)." }

$aab = Join-Path $AndroidRoot 'app/build/outputs/bundle/release/app-release.aab'
Write-Host ""
Write-Host "  AAB hazir:" -ForegroundColor Green
Write-Host "  $aab"
if (Test-Path $aab) {
    $sizeMb = [math]::Round((Get-Item $aab).Length / 1MB, 2)
    Write-Host "  Boyut: $sizeMb MB"
}
if ($Apk) {
    Write-Host "  APK:  $(Join-Path $AndroidRoot 'app/build/outputs/apk/release/app-release.apk')"
}
Write-Host ""
Write-Host "  Sonraki adim: Play Console > Uretim (veya Kapali test) > Yeni surum > bu .aab dosyasini yukleyin." -ForegroundColor Yellow
Write-Host "  Rehber: android/PUBLISHING.md"
