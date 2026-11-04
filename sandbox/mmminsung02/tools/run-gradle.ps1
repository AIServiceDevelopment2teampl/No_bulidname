# Run Gradle for the sandbox Android app and open a progress window next to it.
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File tools\run-gradle.ps1 [-Tasks "ktlintCheck testDebugUnitTest assembleDebug"] [-Title "App check"]
# Prints the last lines of the Gradle log and exits with Gradle's exit code.
param(
    [string]$Tasks = "ktlintCheck testDebugUnitTest assembleDebug",
    [string]$Title = "App check",
    [string]$ExtraArgs = ""
)

$ErrorActionPreference = "Stop"
$projectDir = Join-Path $PSScriptRoot "..\android" | Resolve-Path
$workDir = Join-Path $env:TEMP "gradle-progress"
New-Item -ItemType Directory -Force $workDir | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$log = Join-Path $workDir "$stamp.log"
$done = "$log.done"
$history = Join-Path $workDir "durations.txt"

# Usual duration = average of the last 5 runs of the same tasks (120s if unknown)
$usual = 120
if (Test-Path $history) {
    $past = Get-Content $history | Where-Object { $_ -like "$Tasks|*" } | Select-Object -Last 5 | ForEach-Object { [double]($_.Split('|')[1]) }
    if ($past) {
        $sum = 0.0
        foreach ($p in $past) { $sum += $p }
        $usual = [Math]::Round($sum / @($past).Count)
    }
}

# Android Studio's bundled JDK. Override with JAVA_HOME if it is set to something that exists
if (-not $env:JAVA_HOME -or -not (Test-Path $env:JAVA_HOME)) {
    $candidates = @("$env:ProgramFiles\Android\Android Studio\jbr", "$env:LOCALAPPDATA\Programs\Android Studio\jbr")
    $env:JAVA_HOME = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
}

$resultsDir = Join-Path $projectDir "app\build\test-results\testDebugUnitTest"
if ($Tasks -match "test") { Remove-Item -Recurse -Force $resultsDir -ErrorAction SilentlyContinue }

New-Item -ItemType File $log | Out-Null
Start-Process powershell -ArgumentList @(
    '-NoExit', '-NoProfile', '-ExecutionPolicy', 'Bypass',
    '-File', "`"$(Join-Path $PSScriptRoot 'progress-window.ps1')`"",
    '-LogFile', "`"$log`"", '-DoneFile', "`"$done`"", '-Title', "`"$Title`"",
    '-ResultsDir', "`"$resultsDir`"", '-UsualSeconds', $usual
)

$startTime = Get-Date
# Write each line as it comes so the progress window sees it live.
# Not 'cmd /c "... > log"': PowerShell 5.1 mangles the inner quotes and cd fails.
# Not '> log': PowerShell 5.1 writes UTF-16. stderr lines arrive as ErrorRecords, so do not stop on them.
$ErrorActionPreference = "Continue"
$gradleArgs = @($Tasks.Split(' ', [StringSplitOptions]::RemoveEmptyEntries)) + @($ExtraArgs.Split(' ', [StringSplitOptions]::RemoveEmptyEntries)) + '--console=plain'
Push-Location $projectDir
try {
    & .\gradlew.bat @gradleArgs 2>&1 | ForEach-Object { Add-Content -Path $log -Value "$_" -Encoding UTF8 }
    $code = $LASTEXITCODE
} finally {
    Pop-Location
}
$seconds = [Math]::Round(((Get-Date) - $startTime).TotalSeconds)
Set-Content -Path $done -Value $code -Encoding ascii
Add-Content -Path $history -Value "$Tasks|$seconds" -Encoding ascii

Get-Content $log -Tail 15
Write-Output "exit=$code seconds=$seconds log=$log"
exit $code
