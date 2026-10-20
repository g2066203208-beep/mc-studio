param()
$ErrorActionPreference = 'Stop'
$studioRoot = Split-Path $PSScriptRoot -Parent
$config = Get-Content -LiteralPath (Join-Path $studioRoot 'local/environment.json') -Raw | ConvertFrom-Json
$modsPath = Join-Path $config.instance 'mods'
if (-not (Test-Path -LiteralPath $modsPath)) { throw "Mods directory missing: $modsPath" }
$outputPath = Join-Path $studioRoot ('local/mods-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff') + '.csv')
Get-ChildItem -LiteralPath $modsPath -Filter '*.jar' | Sort-Object Name | ForEach-Object {
    [pscustomobject]@{
        file = $_.Name
        bytes = $_.Length
        sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
    }
} | Export-Csv -LiteralPath $outputPath -NoTypeInformation -Encoding utf8
Write-Output $outputPath
