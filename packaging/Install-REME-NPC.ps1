param(
  [string]$GameDirectory = "",
  [switch]$CheckOnly,
  [switch]$ConfirmInstall
)
$ErrorActionPreference = "Stop"
function Fail([string]$Message) { Write-Host "ERROR: $Message" -ForegroundColor Red; throw $Message }
function Read-ModId([string]$Path) {
  $zip = $null
  try {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($Path)
    $file = $zip.GetEntry("META-INF/neoforge.mods.toml")
    if (-not $file) { return $false }
    $stream = $file.Open()
    try {
      $reader = New-Object System.IO.StreamReader($stream)
      try {
        $text = $reader.ReadToEnd()
        return $text -match '(?ms)\[\[mods\]\]\s*modId\s*=\s*"reme_studio"'
      } finally { $reader.Dispose() }
    } finally { $stream.Dispose() }
  } catch {
    Write-Host "Skipping unreadable jar: $Path"
    return $false
  } finally { if ($zip) { $zip.Dispose() } }
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.Windows.Forms
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$jar = Get-ChildItem -Path $root -Filter "reme_studio-*.jar" -File |
  Where-Object { Read-ModId $_.FullName } | Sort-Object Name -Descending | Select-Object -First 1
if (-not $jar) { Fail "The reme_studio-*.jar is missing from this installer folder. Extract the ZIP completely before running Install-REME-NPC.cmd." }
Write-Host "REME NPC installer: $($jar.Name)" -ForegroundColor Cyan
Write-Host "Package SHA256: $((Get-FileHash -Algorithm SHA256 $jar.FullName).Hash)"
if (-not $GameDirectory) {
  $dialog = New-Object System.Windows.Forms.FolderBrowserDialog
  $dialog.Description = "Choose the CURRENT HMCL Minecraft GAME directory (the folder containing mods and logs). NOT HMCL's install folder."
  $dialog.ShowNewFolderButton = $false
  if ($dialog.ShowDialog() -ne [System.Windows.Forms.DialogResult]::OK) {
    Fail "No directory chosen. Nothing installed."
  }
  $GameDirectory = $dialog.SelectedPath
}
$GameDirectory = (Resolve-Path -LiteralPath $GameDirectory).Path
$mods = Join-Path $GameDirectory "mods"
if (-not (Test-Path -LiteralPath $mods -PathType Container)) {
  Write-Host "There is no mods folder under $GameDirectory" -ForegroundColor Yellow
  $answer = Read-Host "Is this definitely HMCL's selected NeoForge GAME folder? Type YES to create the mods folder"
  if ($answer -cne "YES") { Fail "Installation cancelled. Nothing changed." }
}
$signs = @("options.txt","logs","saves","versions","mods") |
  Where-Object { Test-Path -LiteralPath (Join-Path $GameDirectory $_) }
Write-Host "Selected: $GameDirectory"
Write-Host "Detected: $($signs -join ', ')"
Write-Host "IMPORTANT: If HMCL isolates versions, use that VERSION's game directory, not the parent .minecraft folder." -ForegroundColor Yellow
if ($CheckOnly) { Write-Host "CheckOnly: NO FILES MODIFIED"; exit 0 }
$confirm = if ($ConfirmInstall) { "INSTALL" } else { Read-Host "Type INSTALL to backup old REME NPC jars and install into THIS mods directory" }
if ($confirm -cne "INSTALL") { Fail "Cancelled. No changes made." }
if (-not (Test-Path -LiteralPath $mods)) { New-Item -ItemType Directory -Path $mods | Out-Null }
$old = @(Get-ChildItem -LiteralPath $mods -File -Filter "*.jar" |
  Where-Object { Read-ModId $_.FullName })
$backup = Join-Path $mods ("REME-backup-" + (Get-Date -Format "yyyyMMdd-HHmmss"))
if ($old.Count -gt 0) {
  New-Item -ItemType Directory -Path $backup | Out-Null
  foreach ($f in $old) {
    Move-Item -LiteralPath $f.FullName -Destination (Join-Path $backup $f.Name) -ErrorAction Stop
    Write-Host "Backed up: $($f.Name)"
  }
}
$target = Join-Path $mods $jar.Name
Copy-Item -LiteralPath $jar.FullName -Destination $target -ErrorAction Stop
$inputHash = (Get-FileHash -Algorithm SHA256 $jar.FullName).Hash
$outputHash = (Get-FileHash -Algorithm SHA256 $target).Hash
if ($inputHash -ne $outputHash) { Fail "Hash mismatch after copying. Check disk!" }
$duplicates = @(Get-ChildItem -LiteralPath $mods -File -Filter "*.jar" | Where-Object { Read-ModId $_.FullName })
if ($duplicates.Count -ne 1) { Fail "Expected exactly one REME mod jar; found $($duplicates.Count)" }
Write-Host "SUCCESS: installed to $target" -ForegroundColor Green
Write-Host "Now CLOSE and restart HMCL's selected NeoForge 1.21.1 instance."
Write-Host "On entering a world you should see REME NPC loaded. Type /remecheck first, then /npc spawn with cheats ON."
Write-Host "If /remecheck is unknown, send the CURRENT instance's logs/latest.log and Mods screen."
Write-Host "Backup (if any): $backup"
