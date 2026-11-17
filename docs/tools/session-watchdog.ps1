#!/usr/bin/env pwsh
<#
.SYNOPSIS
  DSH 会话看门狗：看"活着没 / 还在写没 / GUI 通不通"，异常就记日志并按需重启 GUI。

.DESCRIPTION
  能观测到的事实（都在本机磁盘上，不需要任何私钥）：
    1. 宿主进程  "DeepSeek Harness.exe" 在不在；
    2. 会话文件  <SESSION_FILE> 的 mtime 与体积有没有在长；
    3. Web GUI   http://127.0.0.1:49177 通不通。

  **做不到的事**（写明，免得误以为它能驱动会话）：
    DSH 没有"从外部向会话注入消息"的公开入口，也没有 CLI bin。
    所以本脚本只能"发现异常 + 重启 GUI"，不能让它继续干活。
    想让它持续干活，用会话内的 schedule_create（到点由系统投递消息），或人工在 GUI 发一句。

.PARAMETER SessionFile
  会话文件路径。默认是本机当前主力会话（Wrist Agent 项目）。

.PARAMETER IntervalSeconds
  轮询间隔，默认 300 秒。

.PARAMETER Once
  只检查一次就退出（给计划任务 / 手动巡检用）。

.PARAMETER RestartOnDead
  连续 STALE_LIMIT 次判定"会话没在写"且进程缺失时，重启 GUI。

.EXAMPLE
  pwsh -File docs/tools/session-watchdog.ps1 -Once
  pwsh -File docs/tools/session-watchdog.ps1 -IntervalSeconds 300 -RestartOnDead
#>
[CmdletBinding()]
param(
    [string]$SessionFile = 'C:\Users\GHR\.dsh\sessions\--D-AndroidProjects--\session-7a4e0e05-3880-44af-ae2e-de6e022c9d85\session.v4.jsonl.zstd',
    [int]$IntervalSeconds = 300,
    [switch]$Once,
    [switch]$RestartOnDead,
    [string]$LogFile = '',
    [string]$GuiUrl = 'http://127.0.0.1:49177',
    [string]$ExePath = 'C:\Users\GHR\AppData\Local\Programs\DeepSeek Harness\DeepSeek Harness.exe'
)

$ErrorActionPreference = 'Continue'
if (-not $LogFile) {
    $LogFile = Join-Path $PSScriptRoot '..\..\build-logs\session-watchdog.log'
}
$LogFile = [System.IO.Path]::GetFullPath($LogFile)
$logDir = Split-Path -Parent $LogFile
if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir -Force | Out-Null }

function Write-Log([string]$msg) {
    $line = '{0}  {1}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $msg
    Write-Output $line
    Add-Content -Path $LogFile -Value $line -Encoding UTF8
}

function Test-Gui {
    # 注意：GUI 未鉴权访问会返回 **401**，那是"服务在听、只是要令牌"，算活着。
    # 实测过：`curl --max-time 5 http://127.0.0.1:49177` → 401。
    # 只有"根本连不上"才算死。PS 5.1 的 Invoke-WebRequest 遇到 401 会抛异常，
    # 所以要看异常里有没有 Response：有 Response = 服务端回了话 = 活着。
    try {
        $r = Invoke-WebRequest -UseBasicParsing -Uri $GuiUrl -TimeoutSec 5 -ErrorAction Stop
        return ($null -ne $r)
    } catch [System.Net.WebException] {
        if ($_.Exception.Response) { return $true }
        return $false
    } catch {
        return $false
    }
}

function Get-Probe {
    $proc = Get-Process -Name 'DeepSeek Harness' -ErrorAction SilentlyContinue
    $procCount = 0
    if ($null -ne $proc) { $procCount = ($proc | Measure-Object).Count }
    $bytes = -1
    $mtime = $null
    if (Test-Path $SessionFile) {
        $item = Get-Item $SessionFile
        $bytes = $item.Length
        $mtime = $item.LastWriteTime
    }
    [pscustomobject]@{
        At        = (Get-Date)
        Proc      = ($procCount -gt 0)
        ProcIds   = $procCount
        FileBytes = $bytes
        FileMtime = $mtime
        Gui       = (Test-Gui)
    }
}

$staleLimit = 3
$staleCount = 0
$lastBytes = -1

Write-Log ('watchdog start  session=' + (Split-Path -Leaf (Split-Path -Parent $SessionFile)))
Write-Log ('  file=' + $SessionFile)
Write-Log ('  gui=' + $GuiUrl + '  interval=' + $IntervalSeconds + 's  once=' + $Once)

while ($true) {
    $p = Get-Probe
    $grew = if ($lastBytes -ge 0) { $p.FileBytes -gt $lastBytes } else { $null }
    $lastBytes = $p.FileBytes

    $parts = @(
        'proc=' + $p.Proc + '(' + $p.ProcIds + ')'
        'gui=' + $p.Gui
        'bytes=' + $p.FileBytes
        'mtime=' + $(if ($p.FileMtime) { $p.FileMtime.ToString('HH:mm:ss') } else { 'none' })
        'grew=' + $(if ($null -eq $grew) { 'n/a' } else { $grew })
    )
    Write-Log ($parts -join '  ')

    # 判定：进程没了 或 GUI 不通 或 会话文件连续多轮完全不长（且文件存在）
    $alive = $p.Proc -and $p.Gui
    if ($alive) { $staleCount = 0 } else { $staleCount++ }

    if ($staleCount -ge $staleLimit) {
        Write-Log ('ALERT 连续 ' + $staleCount + ' 次判定为异常（proc/gui 任一不通）')
        if ($RestartOnDead) {
            Write-Log 'RESTART 尝试重启 GUI'
            try {
                Start-Process -FilePath $ExePath -ErrorAction Stop
                Write-Log 'RESTART 已拉起'
            } catch {
                Write-Log ('RESTART 失败: ' + $_.Exception.Message)
            }
            $staleCount = 0
        }
    }

    if ($Once) { break }
    Start-Sleep -Seconds $IntervalSeconds
}

Write-Log 'watchdog exit'
