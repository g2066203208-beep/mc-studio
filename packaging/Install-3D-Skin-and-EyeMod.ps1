param(
  [string]$GameDirectory = ""
)
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.Windows.Forms

function Read-RemodId([string]$File) {
  try {
    $z = [System.IO.Compression.ZipFile]::OpenRead($File)
    try {
      $entry = $z.GetEntry("META-INF/neoforge.mods.toml")
      if (-not $entry) { $entry = $z.GetEntry("META-INF/mods.toml") }
      if (-not $entry) { return "" }
      $r = New-Object System.IO.StreamReader($entry.Open())
      try {
        $body = $r.ReadToEnd()
        $m = [regex]::Match($body, '(?m)^\s*modId\s*=\s*"([^"]+)"')
        if ($m.Success) { return $m.Groups[1].Value }
        return ""
      } finally { $r.Dispose() }
    } finally { $z.Dispose() }
  } catch { return "" }
}
function Resolve-OfficialModrinth([string]$Project, [string]$Release) {
  $base = "https://api.modrinth.com/v2/project/$Project/version"
  $arg = "?loaders=%5B%22neoforge%22%5D&game_versions=%5B%221.21.1%22%5D"
  $all = @(Invoke-RestMethod -Uri ($base + $arg) -Headers @{ "User-Agent" = "REME-NPC-cosmetics-installer/1.0" })
  $matches = @($all | Where-Object {
    $_.version_number -eq $Release -and $_.version_type -eq "release" -and
    $_.loaders -contains "neoforge" -and $_.game_versions -contains "1.21.1"
  })
  if ($matches.Count -lt 1) { throw "No NeoForge 1.21.1 release $Release found for $Project" }
  $v = $matches[0]
  if (@($v.dependencies | Where-Object { $_.dependency_type -eq "required" }).Count -gt 0) {
    throw "$Project requires extra dependencies. Refusing incomplete installation."
  }
  $files = @($v.files | Where-Object { $_.filename -match '\.jar$' })
  $primary = @($files | Where-Object { $_.primary })
  if ($primary.Count -eq 1) { $f = $primary[0] }
  elseif ($files.Count -eq 1) { $f = $files[0] }
  else { throw "Ambiguous artifacts for $Project" }
  if (([uri]$f.url).Scheme -ne "https" -or
      ([uri]$f.url).Host -notin @("cdn.modrinth.com", "cdn-raw.modrinth.com", "github.com", "objects.githubusercontent.com")) {
    throw "Unsafe file origin: $($f.url)"
  }
  if ($f.filename.Contains("/") -or $f.filename.Contains("\")) { throw "Unsafe filename" }
  return $f
}
function Download-Verified([string]$Project, [string]$Release, [string]$ExpectedId, [string]$Temp) {
  $f = Resolve-OfficialModrinth $Project $Release
  $dst = Join-Path $Temp $f.filename
  Invoke-WebRequest -Uri $f.url -OutFile $dst -UseBasicParsing
  if ($null -ne $f.size -and (Get-Item -LiteralPath $dst).Length -ne [long]$f.size) {
    throw "Download size mismatch for $($f.filename)"
  }
  foreach ($algo in @("SHA512", "SHA1")) {
    $expected = if ($algo -eq "SHA512") { $f.hashes.sha512 } else { $f.hashes.sha1 }
    if (-not $expected) { throw "Missing Modrinth $algo hash" }
    $actual = (Get-FileHash -LiteralPath $dst -Algorithm $algo).Hash.ToLowerInvariant()
    if ($actual -ne $expected.ToLowerInvariant()) { throw "$algo mismatch on $($f.filename)" }
  }
  $actualId = Read-RemodId $dst
  if ($actualId -ne $ExpectedId) {
    throw "Actual NeoForge mod ID mismatch: expected $ExpectedId, got $actualId"
  }
  Write-Host "Verified $($f.filename), Mod ID $actualId, SHA512 OK" -ForegroundColor Green
  return $dst
}

Write-Host "REME NPC 3D Skin Layers + moving eyes installer — NeoForge 1.21.1" -ForegroundColor Cyan
Write-Host "Only downloads original mod JAR files directly from Modrinth, never redistributes them."
if (-not $GameDirectory) {
  $d = New-Object System.Windows.Forms.FolderBrowserDialog
  $d.Description = "Select your CURRENT HMCL NeoForge 1.21.1 GAME folder containing mods"
  if ($d.ShowDialog() -ne [System.Windows.Forms.DialogResult]::OK) {
    throw "No game folder selected. Nothing installed."
  }
  $GameDirectory = $d.SelectedPath
}
$root = (Resolve-Path -LiteralPath $GameDirectory).Path
$mods = Join-Path $root "mods"
if (-not (Test-Path $mods -PathType Container)) {
  throw "No mods folder exists: $mods. Wrong HMCL instance? Nothing changed."
}
Write-Host "Selected mods folder: $mods"
$answer = Read-Host "Type INSTALL to download both visual mods into this folder"
if ($answer -cne "INSTALL") { throw "Cancelled. No changes made." }

$tmp = Join-Path $env:TEMP ("reme_cosmetics_" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $tmp | Out-Null
try {
  # Download and verify BOTH before changing any installed game files.
  $downloaded = @(
    (Download-Verified "3dskinlayers" "1.11.4" "skinlayers3d" $tmp),
    (Download-Verified "eye-mod" "1.1.0" "eyemod" $tmp)
  )
  $old = @(Get-ChildItem -LiteralPath $mods -File -Filter "*.jar" |
    Where-Object { (Read-RemodId $_.FullName) -in @("skinlayers3d", "eyemod") })
  $backup = Join-Path $mods ("REME-visual-backup-" + (Get-Date -Format "yyyyMMdd-HHmmss"))
  if ($old.Count -gt 0) {
    New-Item -ItemType Directory -Path $backup | Out-Null
    foreach ($f in $old) {
      Move-Item -LiteralPath $f.FullName -Destination (Join-Path $backup $f.Name) -ErrorAction Stop
      Write-Host "Backed up old visual mod: $($f.Name)"
    }
  }
  foreach ($f in $downloaded) {
    Copy-Item -LiteralPath $f -Destination (Join-Path $mods (Split-Path $f -Leaf)) -ErrorAction Stop
  }
  $installed = @(Get-ChildItem -LiteralPath $mods -File -Filter "*.jar" |
    Where-Object { (Read-RemodId $_.FullName) -in @("skinlayers3d", "eyemod") })
  if ($installed.Count -ne 2) { throw "Expected exactly 2 cosmetic mod JARs; found $($installed.Count)" }
  Write-Host "Success: 3D Skin Layers 1.11.4 and EyeMod 1.1.0 installed." -ForegroundColor Green
  Write-Host "Restart HMCL NeoForge 1.21.1. Mods menu should list both."
} finally { Remove-Item -LiteralPath $tmp -Recurse -Force -ErrorAction SilentlyContinue }
