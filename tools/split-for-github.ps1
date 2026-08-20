# Compresses the offline bundle into per-component archives, split to fit GitHub.
#
#   .\tools\split-for-github.ps1 -Source C:\pronunciation-offline -Destination C:\pronunciation-offline-zips
#
# One archive per top-level folder, so you can choose what to upload rather than being forced
# to take all of it. Anything over the part size is split into .partNNN files with a rejoin
# script and SHA-256 checksums.
#
# Part size defaults to 95 MB, under GitHub's hard 100 MB limit for files committed to a
# repository. Release assets allow 2 GB, but 95 MB parts are valid in both places, so this
# size works wherever you decide to put them.

param(
    [string]$Source = "C:\pronunciation-offline",
    [string]$Destination = "C:\pronunciation-offline-zips",
    [int]$PartSizeMB = 95
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Step($m) { Write-Host "==> $m" -ForegroundColor Cyan }

if (-not (Test-Path $Source)) { throw "Source not found: $Source" }
New-Item -ItemType Directory -Force $Destination | Out-Null
$partBytes = $PartSizeMB * 1MB
$report = @()

# Most of the payload is already-compressed archives and model weights, so Fastest gives
# nearly the same size as Optimal in a fraction of the time.
$level = [System.IO.Compression.CompressionLevel]::Fastest

function Split-File($path, $chunk) {
    $name = Split-Path $path -Leaf
    $in = [IO.File]::OpenRead($path)
    $buffer = New-Object byte[] (4MB)
    $index = 0
    try {
        while ($in.Position -lt $in.Length) {
            $index++
            $partPath = "{0}.part{1:D3}" -f $path, $index
            $out = [IO.File]::Create($partPath)
            try {
                $written = 0
                while ($written -lt $chunk -and $in.Position -lt $in.Length) {
                    $want = [Math]::Min($buffer.Length, $chunk - $written)
                    $read = $in.Read($buffer, 0, $want)
                    if ($read -le 0) { break }
                    $out.Write($buffer, 0, $read)
                    $written += $read
                }
            } finally { $out.Dispose() }
        }
    } finally { $in.Dispose() }
    Remove-Item $path -Force
    return $index
}

foreach ($dir in Get-ChildItem $Source -Directory | Sort-Object Name) {
    $zip = Join-Path $Destination ($dir.Name + ".zip")
    if (Test-Path $zip) { Remove-Item $zip -Force }

    $raw = (Get-ChildItem $dir.FullName -Recurse -File | Measure-Object Length -Sum).Sum
    Step ("{0}  ({1:N0} MB raw)" -f $dir.Name, ($raw / 1MB))
    [System.IO.Compression.ZipFile]::CreateFromDirectory($dir.FullName, $zip, $level, $true)

    $size = (Get-Item $zip).Length
    $hash = (Get-FileHash $zip -Algorithm SHA256).Hash

    if ($size -gt $partBytes) {
        $n = Split-File $zip $partBytes
        Write-Host ("    {0:N0} MB -> {1} parts" -f ($size / 1MB), $n)
        $report += [pscustomobject]@{ Name = $dir.Name; MB = [math]::Round($size / 1MB); Parts = $n; Sha256 = $hash }
    } else {
        Write-Host ("    {0:N0} MB, single file" -f ($size / 1MB))
        $report += [pscustomobject]@{ Name = $dir.Name; MB = [math]::Round($size / 1MB); Parts = 1; Sha256 = $hash }
    }
}

# Loose files at the top of the bundle (installer, README) go in as-is.
Get-ChildItem $Source -File | ForEach-Object { Copy-Item $_.FullName $Destination -Force }

# --- rejoin script -----------------------------------------------------------------------
$rejoin = @'
# Reassembles any split archives in this folder, then verifies them against CHECKSUMS.txt.
#
#   .\rejoin.ps1
#
# Run this after downloading every part. It concatenates NAME.zip.partNNN back into NAME.zip
# and deletes the parts once the checksum matches.

$ErrorActionPreference = "Stop"
$here = $PSScriptRoot

$expected = @{}
if (Test-Path "$here\CHECKSUMS.txt") {
    foreach ($line in Get-Content "$here\CHECKSUMS.txt") {
        if ($line -match '^([0-9A-Fa-f]{64})\s+(.+)$') { $expected[$Matches[2].Trim()] = $Matches[1].ToUpper() }
    }
}

$groups = Get-ChildItem "$here\*.part*" -File |
    Group-Object { ($_.Name -replace '\.part\d+$', '') }

foreach ($g in $groups) {
    $target = Join-Path $here $g.Name
    Write-Host "==> $($g.Name)  ($($g.Count) parts)" -ForegroundColor Cyan

    $parts = $g.Group | Sort-Object { [int]($_.Name -replace '^.*\.part', '') }
    $out = [IO.File]::Create($target)
    try {
        foreach ($p in $parts) {
            $bytes = [IO.File]::OpenRead($p.FullName)
            try { $bytes.CopyTo($out) } finally { $bytes.Dispose() }
        }
    } finally { $out.Dispose() }

    if ($expected.ContainsKey($g.Name)) {
        $actual = (Get-FileHash $target -Algorithm SHA256).Hash
        if ($actual -ne $expected[$g.Name]) {
            throw "Checksum mismatch for $($g.Name). A part is missing or truncated; do not use this file."
        }
        Write-Host "    checksum OK" -ForegroundColor Green
    } else {
        Write-Host "    no checksum on record" -ForegroundColor Yellow
    }
    $parts | Remove-Item -Force
}

Write-Host ""
Write-Host "Done. Extract every .zip HERE - each already contains its own folder, so you" -ForegroundColor Green
Write-Host "end up with 01-jdk\, 03-gradle\, 04-python\ and so on beside this script:"
Write-Host ""
Write-Host '  Get-ChildItem *.zip | ForEach-Object { Expand-Archive $_.FullName -DestinationPath . -Force }'
Write-Host ""
Write-Host "Then run:  .\offline-install.ps1 -ProjectDir C:\pronunciation-app"
'@
Set-Content -Path (Join-Path $Destination "rejoin.ps1") -Value $rejoin -Encoding utf8

$report | ForEach-Object { "{0}  {1}.zip" -f $_.Sha256, $_.Name } |
    Set-Content (Join-Path $Destination "CHECKSUMS.txt") -Encoding ascii

Write-Host ""
$report | Format-Table -AutoSize
$total = (Get-ChildItem $Destination -File | Measure-Object Length -Sum).Sum
Write-Host ("Total: {0:N2} GB across {1} files in {2}" -f ($total / 1GB),
    (Get-ChildItem $Destination -File).Count, $Destination) -ForegroundColor Green

$global:LASTEXITCODE = 0
exit 0
