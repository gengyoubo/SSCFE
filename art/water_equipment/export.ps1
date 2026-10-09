# Export the ImageGen source art as Minecraft textures. No interpolation is applied.
param([int]$TextureSize = 16, [string]$SourceDirectory = 'source-v2')
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
if (-not ('TextureAlphaBounds' -as [type])) {
    Add-Type -ReferencedAssemblies @([System.Drawing.Bitmap].Assembly.Location,
        [System.Drawing.Rectangle].Assembly.Location, [System.Runtime.InteropServices.Marshal].Assembly.Location) -TypeDefinition @'
using System.Drawing;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;
public static class TextureAlphaBounds {
    public static Rectangle Find(Bitmap bitmap) {
        var data = bitmap.LockBits(new Rectangle(0, 0, bitmap.Width, bitmap.Height),
            ImageLockMode.ReadOnly, PixelFormat.Format32bppArgb);
        try {
            byte[] pixels = new byte[data.Stride * data.Height];
            Marshal.Copy(data.Scan0, pixels, 0, pixels.Length);
            int left = bitmap.Width, top = bitmap.Height, right = -1, bottom = -1;
            for (int y = 0; y < bitmap.Height; y++)
                for (int x = 0; x < bitmap.Width; x++) {
                    if (pixels[y * data.Stride + x * 4 + 3] < 128) continue;
                    left = System.Math.Min(left, x); top = System.Math.Min(top, y);
                    right = System.Math.Max(right, x); bottom = System.Math.Max(bottom, y);
                }
            return right < left ? new Rectangle(0, 0, bitmap.Width, bitmap.Height)
                : Rectangle.FromLTRB(left, top, right + 1, bottom + 1);
        } finally { bitmap.UnlockBits(data); }
    }
}
'@
}
$taskWorkspace = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$taskTextures = Join-Path $taskWorkspace 'src/main/resources/assets/sscfe/textures'
$taskNames = @('wet_moondust', 'moisturizer', 'tank_core', 'large_water_tank', 'water_as_food', 'large_water_tank_side')
foreach ($taskName in $taskNames) {
    $taskCategory = if ($taskName -eq 'large_water_tank_side') { 'block' } else { 'item' }
    $taskDirectory = Join-Path $taskTextures $taskCategory
    [System.IO.Directory]::CreateDirectory($taskDirectory) | Out-Null
    $taskImage = [System.Drawing.Bitmap]::new((Join-Path $PSScriptRoot "$SourceDirectory/$taskName.png"))
    $taskCrop = [System.Drawing.Rectangle]::new(0, 0, $taskImage.Width, $taskImage.Height)
    $taskTarget = [System.Drawing.Rectangle]::new(0, 0, $TextureSize, $TextureSize)
    if ($taskCategory -eq 'item') {
        # Remove generated canvas padding, keeping the silhouette and its aspect ratio.
        $taskCrop = [TextureAlphaBounds]::Find($taskImage)
        $taskScale = ($TextureSize - 2) / [double][Math]::Max($taskCrop.Width, $taskCrop.Height)
        $taskWidth = [Math]::Max(1, [int][Math]::Round($taskCrop.Width * $taskScale))
        $taskHeight = [Math]::Max(1, [int][Math]::Round($taskCrop.Height * $taskScale))
        $taskTarget = [System.Drawing.Rectangle]::new([int][Math]::Floor(($TextureSize - $taskWidth) / 2),
            [int][Math]::Floor(($TextureSize - $taskHeight) / 2), $taskWidth, $taskHeight)
    }
    $taskOutput = [System.Drawing.Bitmap]::new($TextureSize, $TextureSize, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $taskGraphics = [System.Drawing.Graphics]::FromImage($taskOutput)
    try {
        $taskGraphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
        $taskGraphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
        $taskGraphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
        $taskGraphics.DrawImage($taskImage, $taskTarget, $taskCrop.X, $taskCrop.Y,
            $taskCrop.Width, $taskCrop.Height, [System.Drawing.GraphicsUnit]::Pixel)
        $taskOutput.Save((Join-Path $taskDirectory "$taskName.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $taskGraphics.Dispose()
        $taskOutput.Dispose()
        $taskImage.Dispose()
    }
}
