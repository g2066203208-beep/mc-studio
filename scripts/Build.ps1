param()
$ErrorActionPreference = 'Stop'
$studioRoot = Split-Path $PSScriptRoot -Parent
$config = Get-Content -LiteralPath (Join-Path $studioRoot 'local/environment.json') -Raw | ConvertFrom-Json
$javaExecutable = Join-Path $config.javaHome 'bin/java.exe'
if (-not (Test-Path -LiteralPath $javaExecutable)) { throw "JDK missing: $javaExecutable" }
$previousJavaHome = $env:JAVA_HOME
try {
    $env:JAVA_HOME = $config.javaHome
    $proxyArgs = @()
    $internetSettings = Get-ItemProperty 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings' -ErrorAction SilentlyContinue
    if ($internetSettings.ProxyEnable -eq 1 -and $internetSettings.ProxyServer) {
        $proxyText = [string]$internetSettings.ProxyServer
        if ($proxyText -notmatch '://') { $proxyText = 'http://' + $proxyText }
        $proxyUri = $null
        if ([uri]::TryCreate($proxyText, [UriKind]::Absolute, [ref]$proxyUri) -and
            $proxyUri.Host -and $proxyUri.Port -gt 0 -and $proxyText -notmatch '[;=]') {
            $proxyArgs = @(
                "-Dhttp.proxyHost=$($proxyUri.Host)", "-Dhttp.proxyPort=$($proxyUri.Port)",
                "-Dhttps.proxyHost=$($proxyUri.Host)", "-Dhttps.proxyPort=$($proxyUri.Port)"
            )
        }
    }
    & (Join-Path $studioRoot 'mod/gradlew.bat') -p (Join-Path $studioRoot 'mod') @proxyArgs build --no-daemon --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
    Get-ChildItem -LiteralPath (Join-Path $studioRoot 'mod/build/libs') -Filter '*.jar' |
        Get-FileHash -Algorithm SHA256
} finally {
    $env:JAVA_HOME = $previousJavaHome
}
