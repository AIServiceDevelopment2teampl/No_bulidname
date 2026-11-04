# Read-only progress window for a Gradle run (experience repo: general/collaboration.md).
# It only reads the log file, the done file, test result XML files and the process list.
# Text is English on purpose: Korean breaks in BOM-less .ps1 files on PowerShell 5.1.
param(
    [Parameter(Mandatory = $true)][string]$LogFile,
    [Parameter(Mandatory = $true)][string]$DoneFile,
    [string]$Title = "Gradle",
    [string]$ResultsDir = "",
    [double]$UsualSeconds = 120,
    [string]$Stages = "ktlint=:app:ktlint;compile=:app:compileDebugKotlin;test=:app:testDebugUnitTest;apk=:app:assembleDebug"
)

$Host.UI.RawUI.WindowTitle = "[PROGRESS] $Title"
$start = Get-Date
$stageList = @()
foreach ($pair in $Stages.Split(';')) {
    $kv = $pair.Split('=')
    $stageList += [pscustomobject]@{ Name = $kv[0]; Pattern = $kv[1] }
}

function Get-GradleProcessCount {
    $n = 0
    Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue | ForEach-Object {
        if ($_.CommandLine -match 'GradleDaemon|GradleWrapperMain|GradleWorkerMain|Gradle Test Executor') { $n++ }
    }
    return $n
}

function Get-TestSummary {
    if (-not $ResultsDir -or -not (Test-Path $ResultsDir)) { return $null }
    $total = 0; $failed = 0; $names = @()
    Get-ChildItem $ResultsDir -Filter *.xml -ErrorAction SilentlyContinue | ForEach-Object {
        try {
            [xml]$x = Get-Content $_.FullName -Raw -Encoding UTF8
            $suite = $x.testsuite
            $total += [int]$suite.tests
            $failed += [int]$suite.failures + [int]$suite.errors
            foreach ($case in $suite.testcase) {
                if ($case.failure -or $case.error) { $names += "$($suite.name.Split('.')[-1]).$($case.name)" }
            }
        } catch {}
    }
    return [pscustomobject]@{ Total = $total; Failed = $failed; Names = $names }
}

while ($true) {
    $log = ""
    if (Test-Path $LogFile) { $log = Get-Content $LogFile -Raw -ErrorAction SilentlyContinue }
    if ($null -eq $log) { $log = "" }
    $done = Test-Path $DoneFile
    $elapsed = ((Get-Date) - $start).TotalSeconds

    # Stage state: a stage is done when a later stage has started (or the run ended)
    $started = @()
    foreach ($s in $stageList) { $started += ($log.Contains($s.Pattern)) }
    $lastStarted = -1
    for ($i = 0; $i -lt $stageList.Count; $i++) { if ($started[$i]) { $lastStarted = $i } }

    if ($done) {
        $pct = 100.0
    } else {
        $pct = [Math]::Min([double]99, [double]($elapsed / $UsualSeconds * 100))
    }
    $width = 30
    $filled = [int]([Math]::Floor($pct / 100 * $width))
    $bar = ('#' * $filled) + ('-' * ($width - $filled))

    Clear-Host
    Write-Host "  $Title" -ForegroundColor Cyan
    Write-Host ""
    $note = ""
    if (-not $done -and $elapsed -gt $UsualSeconds) { $note = "  (longer than usual)" }
    Write-Host ("  [{0}] {1,3:N0}%   {2:N0}s / usual {3:N0}s{4}" -f $bar, $pct, $elapsed, $UsualSeconds, $note)
    Write-Host ""
    for ($i = 0; $i -lt $stageList.Count; $i++) {
        $name = $stageList[$i].Name
        $failedHere = $log -match [regex]::Escape($stageList[$i].Pattern) + '\S* FAILED'
        if ($failedHere) {
            Write-Host "  [FAILED ] $name" -ForegroundColor Red
        } elseif ($started[$i] -and ($i -lt $lastStarted -or $done)) {
            Write-Host "  [done   ] $name" -ForegroundColor Green
        } elseif ($started[$i]) {
            Write-Host "  [running] $name" -ForegroundColor Yellow
        } elseif ($done) {
            Write-Host "  [skipped] $name" -ForegroundColor DarkGray
        } else {
            Write-Host "  [wait   ] $name" -ForegroundColor DarkGray
        }
    }
    Write-Host ""

    if (-not $done) {
        $procs = Get-GradleProcessCount
        if ($procs -eq 0) {
            Write-Host "  Gradle processes: 0  <- nothing is running. The job may have died." -ForegroundColor Red
        } else {
            Write-Host "  Gradle processes: $procs"
        }
        Write-Host ""
        Write-Host "  If the window title shows 'Select', press Esc or right-click to resume updates." -ForegroundColor DarkGray
        Start-Sleep -Seconds 2
        continue
    }

    # Finished: summary once, then stop updating
    $code = (Get-Content $DoneFile -Raw).Trim()
    if ($code -eq "0") {
        Write-Host "  BUILD SUCCESSFUL" -ForegroundColor Green
    } else {
        Write-Host "  BUILD FAILED (exit $code)" -ForegroundColor Red
        $log -split "`n" | Where-Object { $_ -match 'FAILED|^e: |\.kt:\d+:\d+ ' } | Select-Object -First 8 | ForEach-Object { Write-Host "    $($_.Trim())" -ForegroundColor Red }
    }
    $t = Get-TestSummary
    if ($t) {
        $color = 'Green'
        if ($t.Failed -gt 0) { $color = 'Red' }
        Write-Host ("  Tests: {0} passed, {1} failed (of {2})" -f ($t.Total - $t.Failed), $t.Failed, $t.Total) -ForegroundColor $color
        $t.Names | Select-Object -First 10 | ForEach-Object { Write-Host "    failed: $_" -ForegroundColor Red }
    }
    Write-Host ""
    Write-Host ("  Finished in {0:N0}s. This window stopped updating. You can close it." -f $elapsed)
    $Host.UI.RawUI.WindowTitle = "[DONE] $Title"
    break
}
