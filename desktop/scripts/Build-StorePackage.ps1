param(
    [Parameter(Mandatory = $true)]
    [string]$IdentityName,

    [Parameter(Mandatory = $true)]
    [string]$Publisher,

    [ValidatePattern('^\d+\.\d+\.\d+\.\d+$')]
    [string]$Version = "1.0.0.0",

    [ValidateSet("x64", "arm64")]
    [string]$Architecture = "x64"
)

$ErrorActionPreference = "Stop"
$desktopRoot = (Resolve-Path "$PSScriptRoot\..").Path
$project = Join-Path $desktopRoot "StreamLiveX.Desktop\StreamLiveX.Desktop.csproj"
$artifactRoot = Join-Path $desktopRoot "artifacts\$Architecture"
$publishRoot = Join-Path $artifactRoot "publish"
$layoutRoot = Join-Path $artifactRoot "layout"
$packagePath = Join-Path $artifactRoot "StreamLiveX-$Version-$Architecture.msix"
$runtime = "win-$Architecture"

$resolvedArtifactRoot = [IO.Path]::GetFullPath($artifactRoot)
$resolvedDesktopRoot = [IO.Path]::GetFullPath($desktopRoot) + [IO.Path]::DirectorySeparatorChar
if (-not $resolvedArtifactRoot.StartsWith($resolvedDesktopRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Paket çıktı klasörü masaüstü proje klasörünün dışında olamaz."
}

Remove-Item -LiteralPath $artifactRoot -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $publishRoot, $layoutRoot | Out-Null

dotnet publish $project `
    --configuration Release `
    --runtime $runtime `
    --self-contained true `
    --output $publishRoot `
    -p:PublishReadyToRun=true

if ($LASTEXITCODE -ne 0) {
    throw "Windows uygulaması yayımlanamadı."
}

Copy-Item -Path "$publishRoot\*" -Destination $layoutRoot -Recurse
Copy-Item -Path "$desktopRoot\packaging\Assets" -Destination $layoutRoot -Recurse

$libVlcRoot = Join-Path $layoutRoot "libvlc"
if (Test-Path -LiteralPath $libVlcRoot) {
    Get-ChildItem -LiteralPath $libVlcRoot -Directory |
        Where-Object Name -ne $runtime |
        Remove-Item -Recurse -Force
}

$manifestTemplate = Get-Content "$desktopRoot\packaging\Package.appxmanifest.template" -Raw
$manifest = $manifestTemplate.Replace("{{IDENTITY_NAME}}", $IdentityName)
$manifest = $manifest.Replace("{{PUBLISHER}}", $Publisher)
$manifest = $manifest.Replace("{{VERSION}}", $Version)
$manifest = $manifest.Replace("{{ARCHITECTURE}}", $Architecture)
Set-Content -LiteralPath "$layoutRoot\AppxManifest.xml" -Value $manifest -Encoding UTF8

$makeAppx = Get-ChildItem "${env:ProgramFiles(x86)}\Windows Kits\10\bin" -Filter MakeAppx.exe -Recurse -ErrorAction SilentlyContinue |
    Sort-Object FullName -Descending |
    Select-Object -First 1

if (-not $makeAppx) {
    throw "MakeAppx.exe bulunamadı. Visual Studio Installer üzerinden MSIX Packaging Tools ve Windows SDK yüklenmelidir."
}

& $makeAppx.FullName pack /d $layoutRoot /p $packagePath /o
if ($LASTEXITCODE -ne 0) {
    throw "MSIX paketi oluşturulamadı."
}

Write-Host "Paket oluşturuldu: $packagePath" -ForegroundColor Green
