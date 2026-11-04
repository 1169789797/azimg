# 递增语义化版本并 VERSION_CODE +1，然后 sync-version.ps1
param(
    [Parameter(Position = 0)]
    [ValidateSet('patch', 'minor', 'major')]
    [string]$Bump = 'patch'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$propFile = Join-Path $root 'version.properties'

if (-not (Test-Path $propFile)) {
    Write-Error "找不到 $propFile，请先创建 VERSION_NAME / VERSION_CODE"
}

$props = @{}
Get-Content $propFile -Encoding UTF8 | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq '' -or $line.StartsWith('#')) { return }
    if ($line -match '^([^=]+)=(.*)$') {
        $props[$Matches[1].Trim()] = $Matches[2].Trim()
    }
}

$oldName = $props['VERSION_NAME']
$oldCode = [int]$props['VERSION_CODE']

if ($oldName -notmatch '^(\d+)\.(\d+)\.(\d+)(.*)$') {
    Write-Error "VERSION_NAME 须为 semver 形如 1.0.0，当前: $oldName"
}

$major = [int]$Matches[1]
$minor = [int]$Matches[2]
$patch = [int]$Matches[3]
$suffix = $Matches[4]

switch ($Bump) {
    'major' { $major++; $minor = 0; $patch = 0 }
    'minor' { $minor++; $patch = 0 }
    'patch' { $patch++ }
}

$newName = "$major.$minor.$patch$suffix"
$newCode = $oldCode + 1

Write-Host "版本 bump ($Bump): $oldName ($oldCode) -> $newName ($newCode)"
Write-Host ""

$lines = Get-Content $propFile -Encoding UTF8
$out = foreach ($line in $lines) {
    if ($line -match '^\s*VERSION_NAME\s*=') { "VERSION_NAME=$newName" }
    elseif ($line -match '^\s*VERSION_CODE\s*=') { "VERSION_CODE=$newCode" }
    else { $line }
}
[System.IO.File]::WriteAllLines($propFile, $out, [System.Text.UTF8Encoding]::new($false))

& (Join-Path $PSScriptRoot 'sync-version.ps1')

Write-Host ""
Write-Host "发版提醒:"
Write-Host "  1. 编辑 CHANGELOG.md 记录本次变更"
Write-Host "  2. 若改了 index.html，同步 pwa / android assets"
Write-Host "  3. Android: cd android; .\gradlew.bat assembleRelease"
