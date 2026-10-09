param([string]$OutputName = 'preview-v2.png')
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$taskWorkspace = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$taskNames = @('wet_moondust', 'moisturizer', 'tank_core', 'large_water_tank', 'water_as_food', 'large_water_tank_side')
$taskLabels = @('湿润的月之尘', '保湿剂', '储罐核心', '大储罐', '以水为食', '罐体 · 透明窗口')
$taskSheet = [System.Drawing.Bitmap]::new(1200, 236)
$taskGraphics = [System.Drawing.Graphics]::FromImage($taskSheet)
$taskFont = [System.Drawing.Font]::new('Microsoft YaHei', 12)
$taskFormat = [System.Drawing.StringFormat]::new()
$taskFormat.Alignment = [System.Drawing.StringAlignment]::Center
try {
    $taskGraphics.Clear([System.Drawing.Color]::FromArgb(30, 33, 39))
    $taskGraphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $taskGraphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
    for ($taskIndex = 0; $taskIndex -lt $taskNames.Length; $taskIndex++) {
        $taskCategory = if ($taskIndex -eq 5) { 'block' } else { 'item' }
        $taskImage = [System.Drawing.Image]::FromFile((Join-Path $taskWorkspace "src/main/resources/assets/sscfe/textures/$taskCategory/$($taskNames[$taskIndex]).png"))
        try {
            $taskGraphics.DrawImage($taskImage, [System.Drawing.Rectangle]::new($taskIndex * 200 + 20, 15, 160, 160))
            $taskGraphics.DrawString($taskLabels[$taskIndex], $taskFont, [System.Drawing.Brushes]::White,
                [System.Drawing.RectangleF]::new($taskIndex * 200, 190, 200, 36), $taskFormat)
        } finally { $taskImage.Dispose() }
    }
    $taskSheet.Save((Join-Path $PSScriptRoot $OutputName), [System.Drawing.Imaging.ImageFormat]::Png)
} finally {
    $taskFormat.Dispose(); $taskFont.Dispose(); $taskGraphics.Dispose(); $taskSheet.Dispose()
}
