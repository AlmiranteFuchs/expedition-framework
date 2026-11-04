# Rebuild the hand-drawn 16px inventory sprites and an optional preview board.
param([switch]$Preview)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$repo = Split-Path $PSScriptRoot
$assets = Join-Path $repo 'src/main/resources/assets/expeditions'
$source = Get-Content (Join-Path $PSScriptRoot 'recent_item_sprites.json') -Raw |
    ConvertFrom-Json -AsHashtable
$palette = [System.Collections.Generic.Dictionary[string, System.Drawing.Color]]::new([StringComparer]::Ordinal)
foreach ($entry in $source.palette.GetEnumerator()) {
    $palette.Add($entry.Key, [Drawing.ColorTranslator]::FromHtml($entry.Value))
}

$board = $null
if ($Preview) {
    $board = [Drawing.Bitmap]::new(640, 148 * [Math]::Ceiling($source.icons.Count / 4))
    $canvas = [Drawing.Graphics]::FromImage($board)
    $canvas.Clear([Drawing.Color]::FromArgb(34, 31, 29))
    $canvas.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $canvas.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::Half
}

$index = 0
foreach ($entry in $source.icons.GetEnumerator()) {
    $name = $entry.Key
    $rows = $entry.Value
    if ($rows.Count -ne 16 -or @($rows | Where-Object Length -ne 16).Count) {
        throw "Sprite $name must be 16 by 16"
    }
    $image = [Drawing.Bitmap]::new(16, 16)
    for ($y = 0; $y -lt 16; $y++) {
        for ($x = 0; $x -lt 16; $x++) {
            $pixel = [string]$rows[$y][$x]
            if ($pixel -ne '.') { $image.SetPixel($x, $y, $palette[$pixel]) }
        }
    }
    $image.Save((Join-Path $assets "textures/item/$name.png"), [Drawing.Imaging.ImageFormat]::Png)
    if ($board) {
        $left = 160 * ($index % 4)
        $top = 148 * [Math]::Floor($index / 4)
        $canvas.DrawImage($image, [Drawing.Rectangle]::new($left + 31, $top + 10, 96, 96))
        $canvas.DrawImageUnscaled($image, $left + 14, $top + 111)
        $canvas.DrawString($name.Replace('_', ' '), [Drawing.SystemFonts]::DefaultFont,
            [Drawing.Brushes]::Wheat, $left + 34, $top + 112)
    }
    $image.Dispose()
    $index++
}

if ($board) {
    $canvas.Dispose()
    $board.Save((Join-Path $repo 'build/recent-item-sprites.png'), [Drawing.Imaging.ImageFormat]::Png)
    $board.Dispose()
}
