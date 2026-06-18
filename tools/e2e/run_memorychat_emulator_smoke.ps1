[CmdletBinding()]
param(
    [string]$AdbPath = "adb",
    [string]$DeviceId = "",
    [string]$ApkPath = "app/build/outputs/apk/debug/MemoryChat-v1.0.73-debug.apk",
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
    param([string[]]$CommandArgs)

    $allArgs = @()
    if (-not [string]::IsNullOrWhiteSpace($DeviceId)) {
        $allArgs += @("-s", $DeviceId)
    }
    $allArgs += $CommandArgs
    return $allArgs
}

function Invoke-Adb {
    param([Parameter(Mandatory = $true)][string[]]$CommandArgs)

    $fullArgs = Get-AdbArgs -CommandArgs $CommandArgs
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
        [Parameter(Mandatory = $true)][string[]]$ProcessArgs,
        [Parameter(Mandatory = $true)][string]$OutputPath
    )

    $psi = [System.Diagnostics.ProcessStartInfo]::new()
    $psi.FileName = $FileName
    $psi.UseShellExecute = $false
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true

    if ($psi.PSObject.Properties["ArgumentList"]) {
        foreach ($arg in $ProcessArgs) {
            [void]$psi.ArgumentList.Add($arg)
        }
    } else {
        $psi.Arguments = Join-ProcessArguments -Arguments $ProcessArgs
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
        throw "process failed with exit code $($proc.ExitCode): $FileName $($ProcessArgs -join ' ')`n$stderr"
    }
}

function ConvertTo-ShellSingleQuoted {
    param([Parameter(Mandatory = $true)][string]$Value)

    return "'" + ($Value -replace "'", "'\''") + "'"
}

function Export-AppDatabase {
    param([Parameter(Mandatory = $true)][string]$DatabasePath)

    $dbExportArgs = Get-AdbArgs -CommandArgs @("exec-out", "run-as", $PackageName, "cat", "databases/memorychat.db")
    Invoke-ProcessBinaryToFile -FileName $AdbPath -ProcessArgs $dbExportArgs -OutputPath $DatabasePath
    if ((Get-Item -LiteralPath $DatabasePath).Length -le 0) {
        throw "exported database is empty: $DatabasePath"
    }

    foreach ($suffix in @("-wal", "-shm")) {
        $sidecarPath = "${DatabasePath}${suffix}"
        $remoteName = "memorychat.db${suffix}"
        $sidecarArgs = Get-AdbArgs -CommandArgs @(
            "exec-out",
            "run-as",
            $PackageName,
            "sh",
            "-c",
            "if [ -f databases/$remoteName ]; then cat databases/$remoteName; fi"
        )
        Invoke-ProcessBinaryToFile -FileName $AdbPath -ProcessArgs $sidecarArgs -OutputPath $sidecarPath
        if ((Get-Item -LiteralPath $sidecarPath).Length -le 0) {
            Remove-Item -LiteralPath $sidecarPath -Force
        }
    }
}

function Get-LocalConversationTitle {
    param([Parameter(Mandatory = $true)][string]$Value)

    $normalized = [regex]::Replace($Value, "\s+", " ").Trim()
    $trimChars = [char[]]@(0x3002, 0x002e, 0xff0c, 0x002c, 0xff01, 0x0021, 0xff1f, 0x003f, 0x003a, 0xff1a, 0x0022, 0x0027, 0x201c, 0x201d)
    $normalized = $normalized.Trim($trimChars)
    if ([string]::IsNullOrWhiteSpace($normalized)) {
        return "新会话"
    }
    if ($normalized.Length -le 16) {
        return $normalized
    }
    return $normalized.Substring(0, 16) + [string][char]0x2026
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

function Invoke-PythonConversationTitle {
    param(
        [Parameter(Mandatory = $true)][string]$DatabasePath,
        [Parameter(Mandatory = $true)][string]$ConversationId,
        [Parameter(Mandatory = $true)][string]$WorkDir
    )

    $queryScript = Join-Path -Path $WorkDir -ChildPath "conversation_title.py"
    @'
import sqlite3
import sys
import base64

db_path = sys.argv[1]
conversation_id = sys.argv[2]
conn = sqlite3.connect(db_path)
try:
    row = conn.execute(
        "SELECT title FROM conversations WHERE id = ?",
        (conversation_id,),
    ).fetchone()
finally:
    conn.close()

if not row:
    print(f"Conversation not found: {conversation_id}", file=sys.stderr)
    sys.exit(2)

print(base64.b64encode(row[0].encode("utf-8")).decode("ascii"))
'@ | Set-Content -LiteralPath $queryScript -Encoding UTF8

    $output = & $PythonPath $queryScript $DatabasePath $ConversationId 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "python failed to read conversation title: $($output -join [Environment]::NewLine)"
    }

    $title = ($output | Select-Object -First 1)
    if ($null -eq $title) {
        return ""
    }
    return [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String([string]$title))
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
Invoke-Adb -CommandArgs @("install", "-r", $resolvedApkPath)

Write-Host "Starting app package: $PackageName"
Invoke-Adb -CommandArgs @("shell", "monkey", "-p", $PackageName, "-c", "android.intent.category.LAUNCHER", "1")

Write-Host ""
Write-Host "Manual step required:"
Write-Host "  1. In the emulator, create or open a MemoryChat conversation."
Write-Host "  2. Make sure real-model settings were configured in the app before this run."
Write-Host "  3. Do not paste or enter API keys in this terminal."
[void](Read-Host "Press Enter after the conversation exists")

Write-Host "Exporting app database with run-as..."
Export-AppDatabase -DatabasePath $dbPath

$conversationId = Invoke-PythonLatestConversationId -DatabasePath $dbPath -WorkDir $OutDir
Write-Host "Latest conversation id: $conversationId"

Write-Host "Clearing logcat..."
Invoke-Adb -CommandArgs @("logcat", "-c")

Write-Host "Sending smoke message through broadcast receiver..."
$encodedMessage = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($Message))
$broadcastCommand = @(
    "am broadcast",
    "-a $(ConvertTo-ShellSingleQuoted -Value $Action)",
    "-n $(ConvertTo-ShellSingleQuoted -Value $Receiver)",
    "--es conv_id $(ConvertTo-ShellSingleQuoted -Value $conversationId)",
    "--es msg_b64 $(ConvertTo-ShellSingleQuoted -Value $encodedMessage)"
) -join " "
Invoke-Adb -CommandArgs @("shell", $broadcastCommand)

Write-Host "Waiting $WaitSeconds seconds for model call and memory recall logs..."
Start-Sleep -Seconds $WaitSeconds

Write-Host "Exporting related logcat..."
$logcatArgs = Get-AdbArgs -CommandArgs @(
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
Invoke-ProcessBinaryToFile -FileName $AdbPath -ProcessArgs $logcatArgs -OutputPath $logPath

$logText = Get-Content -LiteralPath $logPath -Raw
if ($logText -match "API key is empty") {
    throw "smoke failed: API key is empty in app settings; configure real-model settings before running this smoke"
}
if ($logText -match "AdbInput\(.*\): Error:" -or
    $logText -match "LlmProvider\(.*\): complete\(\) failed" -or
    $logText -match "Unable to resolve host" -or
    $logText -match "HTTP \d{3}") {
    throw "smoke failed: model/network error found in $logPath"
}
if ($logText -notmatch "\[4/4\] API response" -or $logText -notmatch "=== DONE") {
    throw "smoke failed: missing successful API response or completion marker in $logPath"
}

$postDbPath = Join-Path -Path $OutDir -ChildPath "memorychat-after.db"
Write-Host "Exporting app database after broadcast..."
Export-AppDatabase -DatabasePath $postDbPath
$expectedTitle = Get-LocalConversationTitle -Value $Message
$actualTitle = Invoke-PythonConversationTitle -DatabasePath $postDbPath -ConversationId $conversationId -WorkDir $OutDir
if ($actualTitle -ne $expectedTitle) {
    throw "smoke failed: conversation title mismatch. expected '$expectedTitle', got '$actualTitle'"
}

Write-Host ""
Write-Host "Smoke command finished."
Write-Host "Database copy: $dbPath"
Write-Host "Database copy after broadcast: $postDbPath"
Write-Host "Related logcat: $logPath"
Write-Host "Verified AdbInput '[4/4] API response' and completion marker."
Write-Host "Verified conversation title auto-update: $actualTitle"
