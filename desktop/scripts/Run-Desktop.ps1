param(
    [string]$WebUrl = "http://localhost:5175"
)

$ErrorActionPreference = "Stop"

$streamLiveXUri = $null
if (-not [Uri]::TryCreate($WebUrl, [UriKind]::Absolute, [ref]$streamLiveXUri) -or
    $streamLiveXUri.Scheme -notin @("http", "https")) {
    throw "Geçerli bir HTTP veya HTTPS adresi girilmelidir."
}

$env:STREAMLIVEX_WEB_URL = $streamLiveXUri.AbsoluteUri
dotnet run --project "$PSScriptRoot\..\StreamLiveX.Desktop\StreamLiveX.Desktop.csproj"
