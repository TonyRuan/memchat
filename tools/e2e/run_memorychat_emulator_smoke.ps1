[CmdletBinding()]
param(
    [string]$AdbPath = "adb",
    [string]$DeviceId = "",
    [string]$ApkPath = "app/build/outputs/apk/debug/MemoryChat-v1.0.67-debug.apk",
    [string]$PackageName = "com.memorychat.app",
    [string]$Receiver = "com.memorychat.app/.AdbInputReceiver",
    [string]$Action = "com.memorychat.app.SEND_MESSAGE",
    [string]$PythonPath = "python",
    [string]$Message = "MemoryChat adb smoke: please reply with one short sentence.",
    [int]$WaitSeconds = 60,
    [string]$OutDir = ""
)

$ErrorActionPreference = "Stop"

function Resolve-RepoPath {
    param([Parameter(Mandatory = $true)][string]$Path)

    if ([System.IO.Path]::IsPathRooted($Path)) {
        return $Path
    }

    return (Join-Path -Path (Get-Location) -ChildPath $Path)
}

function Get-AdbArgs {
    param([string[]]$Args)

    $allArgs = @()
    if (-not [string]::IsNullOrWhiteSpace($DeviceId)) {
        $allArgs += @("-s", $DeviceId)
    }
    $allArgs += $Args
    return $allArgs
}

function Invoke-Adb {
    param([Parameter(Mandatory = $true)][string[]]$Args)

    $fullArgs = Get-AdbArgs -Args $Args
    & $AdbPath @fullArgs
    if ($LASTEXITCODE -ne 0) {
        throw "adb failed with exit code ${LASTEXITCODE}: $($fullArgs -join ' ')"
    }
}

function Join-ProcessArguments {
    param([string[]]$Arguments)

    $quotedArgs = foreach ($item in $Arguments) {
        $arg = [string]$item
        if ($arg.Length -gt 0 -and $arg -notmatch '[\s"]') {
            $arg
            continue
        }

        $escaped = $arg -replace '(\\*)"', '${1}${1}\"'
        $escaped = $escaped -replace '(\\+)$', '${1}${1}'
        '"' + $escaped + '"'
    }

    return ($quotedArgs -join " ")
}

function Invoke-ProcessBinaryToFile {
    param(
        [Parameter(Mandatory = $true)][string]$FileName,
        [Parameter(Mandatory = $true)][string[]]$Args,
        [Parameter(Mandatory = $true)][string]$OutputPath
    )

    $psi = [System.Diagnostics.ProcessStartInfo]::new()
    $psi.FileName = $FileName
    $psi.UseShellExecute = $false
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true

    if ($psi.PSObject.Properties["ArgumentList"]) {
        foreach ($arg in $Args) {
            [void]$psi.ArgumentList.Add($arg)
        }
    } else {
        $psi.Arguments = Join-ProcessArguments -Arguments $Args
    }

    $proc = [System.Diagnostics.Process]::new()
    $proc.StartInfo = $psi
    [void]$proc.Start()

    $outputStream = [System.IO.File]::Open($OutputPath, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write)
    try {
        $proc.StandardOutput.BaseStream.CopyTo($outputStream)
    } finally {
        $outputStream.Dispose()
    }

    $stderr = $proc.StandardError.ReadToEnd()
    $proc.WaitForExit()

    if ($proc.ExitCode -ne 0) {
        throw "process failed with exit code $($proc.ExitCode): $FileName $($Args -join ' ')`n$stderr"
    }
}

function ConvertTo-ShellSingleQuoted {
    param([Parameter(Mandatory = $true)][string]$Value)

    return "'" + ($Value -replace "'", "'\''") + "'"
}

function Invoke-PythonLatestConversationId {
    param(
        [Parameter(Mandatory = $true)][string]$DatabasePath,
        [Parameter(Mandatory = $true)][string]$WorkDir
    )

    $queryScript = Join-Path -Path $WorkDir -ChildPath "latest_conversation.py"
    @'
import sqlite3
import sys

db_path = sys.argv[1]
conn = sqlite3.connect(db_path)
try:
    row = conn.execute(
        "SELECT id, title, updatedAt FROM conversations ORDER BY updatedAt DESC LIMIT 1"
    ).fetchone()
finally:
    conn.close()

if not row:
    print("No conversations found. Create/open a conversation in the app, then run this script again.", file=sys.stderr)
    sys.exit(2)

print(row[0])
'@ | Set-Content -LiteralPath $queryScript -Encoding UTF8

    $output = & $PythonPath $queryScript $DatabasePath 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "python failed to read latest conversation id: $($output -join [Environment]::NewLine)"
    }

    $conversationId = ($output | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -First 1)
    if ([string]::IsNullOrWhiteSpace($conversationId)) {
        throw "python returned an empty conversation id"
    }

    return [string]$conversationId
}

$resolvedApkPath = Resolve-RepoPath -Path $ApkPath
if (-not (Test-Path -LiteralPath $resolvedApkPath)) {
    throw "APK not found: $resolvedApkPath"
}

if ([string]::IsNullOrWhiteSpace($OutDir)) {
    $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $OutDir = Join-Path -Path ([System.IO.Path]::GetTempPath()) -ChildPath "memorychat-smoke-$stamp"
} else {
    $OutDir = Resolve-RepoPath -Path $OutDir
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$dbPath = Join-Path -Path $OutDir -ChildPath "memorychat.db"
$logPath = Join-Path -Path $OutDir -ChildPath "memorychat-smoke-logcat.txt"

Write-Host "ADB smoke output: $OutDir"
Write-Host "Installing APK: $resolvedApkPath"
Invoke-Adb -Args @("install", "-r", $resolvedApkPath)

Write-Host "Starting app package: $PackageName"
Invoke-Adb -Args @("shell", "monkey", "-p", $PackageName, "-c", "android.intent.category.LAUNCHER", "1")

Write-Host ""
Write-Host "Manual step required:"
Write-Host "  1. In the emulator, create or open a MemoryChat conversation."
Write-Host "  2. Make sure real-model settings were configured in the app before this run."
Write-Host "  3. Do not paste or enter API keys in this terminal."
[void](Read-Host "Press Enter after the conversation exists")

Write-Host "Exporting app database with run-as..."
$dbExportArgs = Get-AdbArgs -Args @("exec-out", "run-as", $PackageName, "cat", "databases/memorychat.db")
Invoke-ProcessBinaryToFile -FileName $AdbPath -Args $dbExportArgs -OutputPath $dbPath
if ((Get-Item -LiteralPath $dbPath).Length -le 0) {
    throw "exported database is empty: $dbPath"
}

$conversationId = Invoke-PythonLatestConversationId -DatabasePath $dbPath -WorkDir $OutDir
Write-Host "Latest conversation id: $conversationId"

Write-Host "Clearing logcat..."
Invoke-Adb -Args @("logcat", "-c")

Write-Host "Sending smoke message through broadcast receiver..."
$encodedMessage = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($Message))
$broadcastCommand = @(
    "am broadcast",
    "-a $(ConvertTo-ShellSingleQuoted -Value $Action)",
    "-n $(ConvertTo-ShellSingleQuoted -Value $Receiver)",
    "--es conv_id $(ConvertTo-ShellSingleQuoted -Value $conversationId)",
    "--es msg_b64 $(ConvertTo-ShellSingleQuoted -Value $encodedMessage)"
) -join " "
Invoke-Adb -Args @("shell", $broadcastCommand)

Write-Host "Waiting $WaitSeconds seconds for model call and memory recall logs..."
Start-Sleep -Seconds $WaitSeconds

Write-Host "Exporting related logcat..."
$logcatArgs = Get-AdbArgs -Args @(
    "logcat",
    "-d",
    "-v",
    "time",
    "AdbInput:I",
    "ChatVM:I",
    "MemoryEngine:I",
    "LlmProvider:I",
    "AndroidRuntime:E",
    "*:S"
)
& $AdbPath @logcatArgs 2>&1 | Set-Content -LiteralPath $logPath -Encoding UTF8
if ($LASTEXITCODE -ne 0) {
    throw "adb logcat export failed with exit code $LASTEXITCODE"
}

$logText = Get-Content -LiteralPath $logPath -Raw
if ($logText -match "AdbInput\\(.*\\): Error:" -or
    $logText -match "LlmProvider\\(.*\\): complete\\(\\) failed" -or
    $logText -match "Unable to resolve host" -or
    $logText -match "HTTP \\d{3}") {
    throw "smoke failed: model/network error found in $logPath"
}
if ($logText -notmatch "\\[4/4\\] API response" -or $logText -notmatch "=== DONE") {
    throw "smoke failed: missing successful API response or completion marker in $logPath"
}

Write-Host ""
Write-Host "Smoke command finished."
Write-Host "Database copy: $dbPath"
Write-Host "Related logcat: $logPath"
Write-Host "Verified AdbInput '[4/4] API response' and completion marker."
