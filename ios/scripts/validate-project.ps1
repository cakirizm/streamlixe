$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$required = @("project.yml", "Podfile", "StreamLiveX.xcodeproj/project.pbxproj", "StreamLiveX.xcodeproj/xcshareddata/xcschemes/StreamLiveX.xcscheme", "StreamLiveX/App/StreamLiveXApp.swift", "StreamLiveX/Resources/Info.plist", "StreamLiveX/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png", "StreamLiveX/Downloads/NativeDownloadManager.swift", "StreamLiveX/Security/SecurePinStore.swift")
foreach ($file in $required) { if (-not (Test-Path (Join-Path $root $file))) { throw "Missing: $file" } }
$content = (Get-ChildItem $root -Recurse -File | Where-Object Extension -in ".swift", ".yml", ".plist" | Get-Content -Raw) -join "`n"
if ($content -match "TODO|FIXME|NODE_TLS_REJECT_UNAUTHORIZED") { throw "Unsafe or unfinished marker found" }
if ($content -match "tvOS|appletv|TARGETED_DEVICE_FAMILY:\s*3") { throw "tvOS target/reference found" }
if ((Get-Content (Join-Path $root "Podfile") -Raw) -notmatch "MobileVLCKit") { throw "MobileVLCKit dependency missing" }
$plistText = Get-Content (Join-Path $root "StreamLiveX/Resources/Info.plist") -Raw
[xml]$plistText | Out-Null
if ($plistText -match "NSAllowsArbitraryLoads\s*</key>\s*<true" -and $plistText -notmatch "NSAllowsArbitraryLoadsForMedia\s*</key>\s*<true") { throw "Broad ATS exception is not limited by the media compatibility declaration" }
Write-Host "iOS static validation: PASS"
Write-Host "TVOS TARGET PRESENT: NO"
