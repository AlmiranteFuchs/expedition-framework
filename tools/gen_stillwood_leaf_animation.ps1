Add-Type -AssemblyName System.Drawing
$textureDir = Join-Path (Split-Path $PSScriptRoot) 'src/main/resources/assets/expeditions/textures/block'
$source = [Drawing.Bitmap]::new((Join-Path $textureDir 'stillwood_leaves.png'))
$frames = [Drawing.Bitmap]::new($source.Width, 4 * $source.Height)
try {
    for ($frame = 0; $frame -lt 4; $frame++) {
        for ($y = 0; $y -lt $source.Height; $y++) {
            for ($x = 0; $x -lt $source.Width; $x++) {
                $pixel = $source.GetPixel($x, $y)
                if ($pixel.A -gt 0 -and ($x + 3 * $y) % 9 -lt 2) {
                    $wave = @(0, 5, 10, 5)[($frame + [int][Math]::Floor(($x + $y) / 6)) % 4]
                    $pixel = [Drawing.Color]::FromArgb($pixel.A,
                        [Math]::Min(255, $pixel.R + $wave), [Math]::Min(255, $pixel.G + $wave),
                        [Math]::Min(255, $pixel.B + [int]($wave / 2)))
                }
                $frames.SetPixel($x, $y + $frame * $source.Height, $pixel)
            }
        }
    }
    $frames.Save((Join-Path $textureDir 'stillwood_leaves_animated.png'), [Drawing.Imaging.ImageFormat]::Png)
} finally {
    $frames.Dispose()
    $source.Dispose()
}
