$ErrorActionPreference = 'Stop'
$runningJar = $null
try {
    Set-Location -LiteralPath $PSScriptRoot
    $launcherSettings = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'run-settings.json') -Raw | ConvertFrom-Json
    $heapPercent = 0
    if (-not [int]::TryParse([string]$launcherSettings.maximumHeapPercent, [ref]$heapPercent) -or
        $heapPercent -lt 10 -or $heapPercent -gt 80) {
        throw 'maximumHeapPercent in run-settings.json must be an integer from 10 to 80.'
    }
    $javaExecutable = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }
    # Java loads classes lazily. Never run directly from the JAR Maven replaces.
    $runningJar = Join-Path $PSScriptRoot ( 'target\rem-running-' + [guid]::NewGuid().ToString('N') + '.jar' )
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'target\rem.jar') -Destination $runningJar
    Write-Host "Starting REM Framework with saved settings; Java heap ceiling: $heapPercent% of system RAM."
    & $javaExecutable "-XX:MaxRAMPercentage=$heapPercent" '-jar' $runningJar
    exit $LASTEXITCODE
} catch {
    Write-Error $_ -ErrorAction Continue
    exit 1
} finally {
    if ($runningJar -and (Test-Path -LiteralPath $runningJar)) {
        Remove-Item -LiteralPath $runningJar -ErrorAction SilentlyContinue
    }
}
