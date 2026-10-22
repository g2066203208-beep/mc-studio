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
# The main mc-studio project also uses modId=reme_studio yet contains NO
# NPC commands. A valid mod ID alone is NOT evidence that this is an NPC JAR.
function Has-Npc-Commands([string]$Path) {
  $zip = $null
  try {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($Path)
    return ($null -ne $zip.GetEntry("dev/reme/mcstudio/npc/NpcCommands.class") -and
            $null -ne $zip.GetEntry("dev/reme/mcstudio/npc/NpcItems.class") -and
            $null -ne $zip.GetEntry("dev/reme/mcstudio/StudioMod.class"))
  } catch {
    return $false
  } finally {
    if ($zip) { $zip.Dispose() }
  }
}
Add-Type -AssemblyName System.Windows.Forms
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$jar = Get-ChildItem -Path $root -Filter "reme_studio-*.jar" -File |
  Where-Object { (Read-ModId $_.FullName) -and (Has-Npc-Commands $_.FullName) } | Sort-Object Name -Descending | Select-Object -First 1
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
if ($CheckOnly) {
  Write-Host "CheckOnly: NO FILES MODIFIED" -ForegroundColor Cyan
  $installed = @()
  if (Test-Path -LiteralPath $mods -PathType Container) {
    $installed = @(Get-ChildItem -LiteralPath $mods -File -Filter "*.jar" |
      Where-Object { Read-ModId $_.FullName })
  }
  Write-Host "Selected game mods directory: $mods"
  Write-Host "REME jars found here: $($installed.Count)"
  foreach ($candidate in $installed) {
    Write-Host "Present: $($candidate.Name)"
    if (-not (Has-Npc-Commands $candidate.FullName)) {
      Write-Host "DIAGNOSIS: This JAR uses the REME mod ID but has NO NpcCommands.class. It is the wrong studio/main build, NOT a usable NPC Mod." -ForegroundColor Red
    } else {
      Write-Host "Verified: actual NpcCommands.class and NpcItems.class present." -ForegroundColor Green
    }
    Write-Host "SHA256: $((Get-FileHash -LiteralPath $candidate.FullName -Algorithm SHA256).Hash)"
  }
  if ($installed.Count -eq 0) {
    Write-Host "DIAGNOSIS: No REME NPC jar in this selected GAME directory." -ForegroundColor Red
  }
  if ($installed.Count -gt 1) {
    Write-Host "DIAGNOSIS: Multiple REME jar copies may prevent loading." -ForegroundColor Red
  }
  $logPath = Join-Path $GameDirectory "logs/latest.log"
  if (Test-Path -LiteralPath $logPath -PathType Leaf) {
    $runlog = Get-Content -LiteralPath $logPath -Raw -ErrorAction Stop
    Write-Host "Last game log: $logPath"
    Write-Host "Log last modified: $((Get-Item -LiteralPath $logPath).LastWriteTime)"
    if ($runlog -match 'REME NPC ([0-9.]+-dev) CONSTRUCTED') {
      Write-Host "Previous launch loaded REME: $($Matches[0])" -ForegroundColor Green
    } elseif ($runlog -match 'REME NPC ([0-9.]+-dev): mod loaded') {
      Write-Host "Previous launch loaded REME: $($Matches[0])" -ForegroundColor Green
    } else {
      Write-Host "DIAGNOSIS: Latest game log contains NO REME mod entrypoint." -ForegroundColor Red
      Write-Host "Possible wrong HMCL game directory or mod dependency rejection."
    }
    if ($runlog -match 'REME NPC.*COMMANDS REGISTERED') {
      Write-Host "Previous log proves command dispatcher registration." -ForegroundColor Green
    } elseif ($runlog -match 'REME NPC: /npc present on dispatcher') {
      Write-Host "Previous log proves /npc present on player login." -ForegroundColor Green
    } else {
      Write-Host "Previous log has NO /npc registration evidence."
    }
  } else {
    Write-Host "No logs/latest.log here. Confirm the active HMCL GAME DIRECTORY." -ForegroundColor Yellow
  }
  Write-Host "0.6.7 requires MC 1.21.1 + NeoForge 21.1.262 or later 21.1.x + Java 21."
  exit 0
}
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
Write-Host "Now CLOSE and restart HMCL's selected MC 1.21.1 NeoForge 21.1.262+ instance (Java 21)."
Write-Host "On entering a world you should see REME NPC loaded. Type /remecheck first, then /npc spawn with cheats ON."
Write-Host "If /remecheck is unknown, first inspect Mods > REME MC Studio v0.6.7-dev; absence is NOT a permissions error."
Write-Host "Rerun with -CheckOnly and the EXACT active HMCL game directory to compare files and logs."
Write-Host "Backup (if any): $backup"
