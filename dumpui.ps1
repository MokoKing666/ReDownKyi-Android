param(
    [Parameter(Mandatory = $true)][string]$Path,
    [string]$Filter = ""
)

# 读取 uiautomator dump 出来的控件树，列出有文字的节点及其屏幕边界。
# 用于确认真机上的可点区域坐标 —— 凭截图估算的坐标经常差几十像素，
# 而 input tap 又不会给出任何失败反馈。

[xml]$doc = [System.IO.File]::ReadAllText($Path, [System.Text.Encoding]::UTF8)

$nodes = $doc.SelectNodes('//node') | Where-Object { $_.text }

if ($Filter -ne "") {
    $nodes = $nodes | Where-Object { $_.text -like "*$Filter*" }
}

foreach ($n in $nodes) {
    Write-Output ($n.text + '  ->  ' + $n.bounds)
}
