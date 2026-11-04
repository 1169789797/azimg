# 从 version.properties 同步到各端 version.json，并更新 PWA Service Worker 缓存名
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$propFile = Join-Path $root 'version.properties'

if (-not (Test-Path $propFile)) {
    Write-Error "找不到 $propFile"
}

$props = @{}
Get-Content $propFile -Encoding UTF8 | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq '' -or $line.StartsWith('#')) { return }
    if ($line -match '^([^=]+)=(.*)$') {
        $props[$Matches[1].Trim()] = $Matches[2].Trim()
    }
}

$name = $props['VERSION_NAME']
$code = $props['VERSION_CODE']
if (-not $name -or -not $code) {
    Write-Error 'version.properties 需包含 VERSION_NAME 与 VERSION_CODE'
}

$json = @{
    versionName = $name
    versionCode = [int]$code
} | ConvertTo-Json -Compress

$targets = @(
    (Join-Path $root 'version.json')
    (Join-Path $root 'pwa\version.json')
    (Join-Path $root 'android\app\src\main\assets\www\version.json')
)

foreach ($t in $targets) {
    $dir = Split-Path $t -Parent
    if (-not (Test-Path $dir)) {
        Write-Warning "跳过（目录不存在）: $t"
        continue
    }
    [System.IO.File]::WriteAllText($t, $json + "`n", [System.Text.UTF8Encoding]::new($false))
    Write-Host "已写入 $t"
}

$swFile = Join-Path $root 'pwa\sw.js'
if (Test-Path $swFile) {
    $sw = Get-Content $swFile -Raw -Encoding UTF8
    $newCache = "az-img-pwa-v$code"
    $sw = $sw -replace "const CACHE_NAME = 'az-img-pwa-v\d+';", "const CACHE_NAME = '$newCache';"
    if ($sw -notmatch [regex]::Escape($newCache)) {
        $sw = $sw -replace "const CACHE_NAME = '[^']+';", "const CACHE_NAME = '$newCache';"
    }
    if ($sw -notmatch 'version\.json') {
        $sw = $sw -replace "('./icon-512\.png')", "`$1,`n  './version.json'"
    }
    [System.IO.File]::WriteAllText($swFile, $sw, [System.Text.UTF8Encoding]::new($false))
    Write-Host "已更新 PWA 缓存名: $newCache"
}

Write-Host ""
Write-Host "当前版本 VERSION_NAME = $name （用户可见 / versionName）"
Write-Host "当前版本 VERSION_CODE = $code （Android 整数 versionCode，发 APK 须递增）"
