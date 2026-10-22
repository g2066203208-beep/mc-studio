param(
  [string]$GameDirectory = "",
  [switch]$CheckOnly,
  [switch]$ConfirmInstall
)
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.Windows.Forms
function Is-NpcJar([string]$Path) {
  $z = $null
  try {
    $z = [System.IO.Compression.ZipFile]::OpenRead($Path)
    $meta = $z.GetEntry("META-INF/neoforge.mods.toml")
    if ($null -eq $meta) { return $false }
    $reader = New-Object System.IO.StreamReader($meta.Open())
    try { $text = $reader.ReadToEnd() } finally { $reader.Dispose() }
    return ($text -match 'modId="reme_npc"' -and
      $null -ne $z.GetEntry("dev/reme/npcstudio/npc/NpcCommands.class") -and
      $null -ne $z.GetEntry("dev/reme/npcstudio/npc/NpcItems.class") -and
      $null -ne $z.GetEntry("assets/reme_npc/lang/zh_cn.json"))
  } catch {
    Write-Host "Skipping invalid or unreadable third-party JAR: $Path"
    return $false
  } finally { if ($z) { $z.Dispose() } }
}
$src = Split-Path -Parent $MyInvocation.MyCommand.Path
$jars = @(Get-ChildItem -LiteralPath $src -Filter 'reme_npc-*.jar' -File)
$jars = @($jars | Where-Object { Is-NpcJar $_.FullName })
if ($jars.Count -ne 1) { throw "Installer must contain exactly one verified reme_npc-*.jar" }
$jar = $jars[0]
if (-not $GameDirectory) {
  $dialog = New-Object System.Windows.Forms.FolderBrowserDialog
  $dialog.Description = "Select HMCL CURRENT game directory (has mods and logs), NOT launcher folder"
  if ($dialog.ShowDialog() -ne [System.Windows.Forms.DialogResult]::OK) {
    throw "Installation cancelled: no HMCL game directory selected"
  }
  $GameDirectory = $dialog.SelectedPath
}
$GameDirectory = (Resolve-Path -LiteralPath $GameDirectory).Path
$mods = Join-Path $GameDirectory 'mods'
if (-not (Test-Path -LiteralPath $mods -PathType Container)) {
  throw "Selected directory has no mods folder. Choose the actual isolated HMCL NeoForge game directory"
}
Write-Host "HMCL GAME DIRECTORY: $GameDirectory"
Write-Host "NPC JAR: $($jar.Name) SHA256: $((Get-FileHash -Algorithm SHA256 $jar.FullName).Hash)"
$existing = @(Get-ChildItem -LiteralPath $mods -File -Filter '*.jar' | Where-Object { Is-NpcJar $_.FullName })
Write-Host "Existing standalone NPC JARs: $($existing.Count)"
foreach ($old in $existing) { Write-Host "  Found: $($old.Name)" }
$log = Join-Path $GameDirectory 'logs/latest.log'
if (Test-Path -LiteralPath $log) {
  $text = Get-Content -LiteralPath $log -Raw
  if ($text -match 'REME NPC.*COMMANDS REGISTERED') {
    Write-Host 'Latest launch had real server /npc registration evidence.'
  } else {
    Write-Host 'No /npc registration in latest game log; ensure selected HMCL game folder actually runs this Mod.'
  }
} else { Write-Host "No logs/latest.log. Is this the CURRENT HMCL GAME directory?" }
if ($CheckOnly) { Write-Host 'READ-ONLY CHECK. NO FILES MODIFIED.'; exit 0 }
$choice = if ($ConfirmInstall) { 'INSTALL' } else { Read-Host 'Type INSTALL to replace only previous reme_npc jars in THIS mods folder' }
if ($choice -cne 'INSTALL') { throw 'Cancelled: no files modified' }
$backup = Join-Path $mods ("REME-NPC-backup-" + (Get-Date -Format "yyyyMMdd-HHmmss"))
if ($existing.Count -gt 0) {
  New-Item -ItemType Directory -Path $backup | Out-Null
  foreach ($old in $existing) { Move-Item -LiteralPath $old.FullName -Destination $backup -ErrorAction Stop }
}
$dst = Join-Path $mods $jar.Name
Copy-Item -LiteralPath $jar.FullName -Destination $dst -ErrorAction Stop
if ((Get-FileHash -Algorithm SHA256 $dst).Hash -ne (Get-FileHash -Algorithm SHA256 $jar.FullName).Hash) {
  throw 'JAR SHA256 mismatch after copy'
}
if (@(Get-ChildItem -LiteralPath $mods -File -Filter '*.jar' | Where-Object { Is-NpcJar $_.FullName }).Count -ne 1) {
  throw 'Expected exactly one independent reme_npc JAR after install'
}
Write-Host 'INSTALLED standalone reme_npc. Do not delete PEAK/worldseam/rpmotion jars.' -ForegroundColor Green
Write-Host 'Launch MC1.21.1 + NeoForge21.1.262+ + Java21, enter NEW test world, then type /remecheck.'
Write-Host 'OLD SAVES: reme_studio:player_npc to reme_npc:player_npc has no migration yet. Do not use unbacked old worlds.'
