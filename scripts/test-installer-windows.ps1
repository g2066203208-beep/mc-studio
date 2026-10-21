$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression.FileSystem
function New-FakeModJar($File, $ModId) {
  $archive = [System.IO.Compression.ZipFile]::Open($File, [System.IO.Compression.ZipArchiveMode]::Create)
  try {
    $entry = $archive.CreateEntry("META-INF/neoforge.mods.toml")
    $stream = $entry.Open()
    try {
      $writer = New-Object System.IO.StreamWriter($stream)
      try {
        $meta = 'modLoader="javafml"' + [Environment]::NewLine + '[[mods]]' +
            [Environment]::NewLine + 'modId="' + $ModId + '"' + [Environment]::NewLine
        $writer.Write($meta)
      } finally { $writer.Dispose() }
    } finally { $stream.Dispose() }
  } finally { $archive.Dispose() }
}
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$temp = Join-Path $env:RUNNER_TEMP "REME-Installer-Actual-Windows-Tests"
$pkg = Join-Path $temp "package"
$game = Join-Path $temp "hmcl-version-game"
$mods = Join-Path $game "mods"
New-Item -ItemType Directory -Path $pkg,$mods,(Join-Path $game "logs") -Force | Out-Null
Copy-Item (Join-Path $root "packaging/Install-REME-NPC.ps1") $pkg
New-FakeModJar (Join-Path $pkg "reme_studio-0.5.4-dev.jar") "reme_studio"
New-FakeModJar (Join-Path $mods "custom-named-previous-npc.jar") "reme_studio"
New-FakeModJar (Join-Path $mods "ordinary-other-mod.jar") "other_mod"
$installer = Join-Path $pkg "Install-REME-NPC.ps1"
& $installer -GameDirectory $game -CheckOnly
if ((Get-ChildItem $mods -Filter "*.jar").Count -ne 2) { throw "Dry run incorrectly modified mods folder" }
& $installer -GameDirectory $game -ConfirmInstall
if (-not (Test-Path (Join-Path $mods "reme_studio-0.5.4-dev.jar"))) { throw "New REME jar missing" }
if (-not (Test-Path (Join-Path $mods "ordinary-other-mod.jar"))) { throw "Touched unrelated mod" }
if ((Get-ChildItem $mods -Filter "*.jar").Count -ne 2) { throw "Duplicate REME or missing other mod" }
if ((Get-ChildItem $mods -Directory -Filter "REME-backup-*").Count -ne 1) { throw "No backup folder" }
$backup = Get-ChildItem $mods -Directory -Filter "REME-backup-*" | Select-Object -First 1
if (-not (Test-Path (Join-Path $backup.FullName "custom-named-previous-npc.jar"))) {
  throw "Old REME with arbitrary filename was not moved to backup"
}
$hashIn = (Get-FileHash (Join-Path $pkg "reme_studio-0.5.4-dev.jar") -Algorithm SHA256).Hash
$hashOut = (Get-FileHash (Join-Path $mods "reme_studio-0.5.4-dev.jar") -Algorithm SHA256).Hash
if ($hashIn -ne $hashOut) { throw "Copy corrupt" }
Write-Host "PASS: Windows PowerShell installer backed up old mod, kept other mod, copied and SHA-verified new mod."
