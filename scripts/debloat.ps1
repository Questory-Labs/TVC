#Requires -Version 7
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string] $Target,
    [ValidateSet("list", "disable", "restore", "dump")] [string] $Action = "list",
    [ValidateSet("safe", "optional", "risky", "all")] [string] $Tier = "safe",
    [string] $Catalog = (Join-Path $PSScriptRoot "..\debloat\packages.json"),
    [switch] $Confirm
)

$ErrorActionPreference = "Stop"

function Find-Adb {
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $candidates = @()
    if ($env:ANDROID_HOME) { $candidates += Join-Path $env:ANDROID_HOME "platform-tools\adb.exe" }
    if ($env:LOCALAPPDATA) { $candidates += Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe" }
    foreach ($c in $candidates) { if (Test-Path $c) { return $c } }
    throw "adb not found. Install Android platform-tools or add adb to PATH."
}

$adb = Find-Adb
$catalog = Get-Content -Raw $Catalog | ConvertFrom-Json

& $adb connect $Target | Out-Null
$serial = (& $adb devices | Select-String -Pattern "^$([regex]::Escape($Target))\S*\s+device" | Select-Object -First 1)
if (-not $serial) {
    throw "Not connected to $Target. Enable Wireless debugging on the device and pair first:`n  adb pair <ip>:<pairing-port>  (code shown on screen)`nThen re-run with the connect port, e.g. -Target 192.168.1.50:5555 or the port shown under Wireless debugging."
}
$serial = ($serial.ToString() -split "\s+")[0]

function Invoke-Shell([string] $cmd) {
    & $adb -s $serial shell $cmd
}

$disabled = @(Invoke-Shell "pm list packages -d" | ForEach-Object { ($_ -replace "^package:", "").Trim() } | Where-Object { $_ })
$installed = @(Invoke-Shell "pm list packages" | ForEach-Object { ($_ -replace "^package:", "").Trim() } | Where-Object { $_ })

$selected = $catalog.packages | Where-Object { $Tier -eq "all" -or $_.tier -eq $Tier.ToUpper() }

$table = $selected | ForEach-Object {
    [pscustomobject]@{
        Package   = $_.pkg
        Label     = $_.label
        Tier      = $_.tier
        Installed = $installed -contains $_.pkg
        Disabled  = $disabled -contains $_.pkg
    }
}

switch ($Action) {
    "list" {
        $table | Format-Table -AutoSize
        return
    }
    "dump" {
        Invoke-Shell "pm list packages -f" | Sort-Object
        return
    }
    "disable" {
        $targets = $table | Where-Object { $_.Installed -and -not $_.Disabled }
        if (-not $targets) { Write-Host "Nothing to disable for tier '$Tier'."; return }
        Write-Host "Would disable:"; $targets | Format-Table Package, Label, Tier -AutoSize
        if (-not $Confirm) { Write-Host "Dry run. Re-run with -Confirm to apply."; return }
        foreach ($t in $targets) {
            Write-Host "disable $($t.Package)"
            Invoke-Shell "pm disable-user --user 0 $($t.Package)"
        }
    }
    "restore" {
        $targets = $table | Where-Object { $_.Disabled }
        if (-not $targets) { Write-Host "Nothing from the catalog is disabled."; return }
        Write-Host "Would restore:"; $targets | Format-Table Package, Label, Tier -AutoSize
        if (-not $Confirm) { Write-Host "Dry run. Re-run with -Confirm to apply."; return }
        foreach ($t in $targets) {
            Write-Host "enable $($t.Package)"
            Invoke-Shell "pm enable $($t.Package)"
        }
    }
}
