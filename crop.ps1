param(
    [Parameter(Mandatory = $true)][string]$Source,
    [Parameter(Mandatory = $true)][string]$Dest,
    [int]$X = 0,
    [int]$Y = 0,
    [int]$W = 400,
    [int]$H = 300,
    [double]$Scale = 1.0
)

Add-Type -AssemblyName System.Drawing

$img = [System.Drawing.Image]::FromFile($Source)

$outW = [int]($W * $Scale)
$outH = [int]($H * $Scale)

$bmp = New-Object System.Drawing.Bitmap($outW, $outH)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
$g.DrawImage(
    $img,
    (New-Object System.Drawing.Rectangle(0, 0, $outW, $outH)),
    (New-Object System.Drawing.Rectangle($X, $Y, $W, $H)),
    [System.Drawing.GraphicsUnit]::Pixel
)

$g.Dispose()
$bmp.Save($Dest, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
$img.Dispose()

Write-Output ("saved: " + $Dest + "  (" + $outW + "x" + $outH + ")")
