param(
    [Parameter(Mandatory = $true)][string]$Dir,
    [int]$MaxLength = 500
)

# 提取 Gradle 测试报告里的失败详情。
#
# testreport.ps1 只给计数，定位问题时需要的是**断言消息与异常类型** ——
# 尤其是这类"11 个测试同时失败"的情况，第一条消息通常就能说明共同原因。

Get-ChildItem -Path $Dir -Filter *.xml | ForEach-Object {
    $raw = [System.IO.File]::ReadAllText($_.FullName, [System.Text.Encoding]::UTF8)

    $matches = [regex]::Matches(
        $raw,
        '<testcase name="([^"]+)"[^>]*>\s*<(?:failure|error)[^>]*message="([^"]*)"'
    )

    foreach ($m in $matches) {
        $name = $m.Groups[1].Value
        $message = $m.Groups[2].Value

        # XML 实体还原，否则消息里全是 &quot; &#10;
        $message = $message.Replace('&quot;', '"').Replace('&#10;', "`n").Replace('&apos;', "'")
        $message = $message.Replace('&lt;', '<').Replace('&gt;', '>').Replace('&amp;', '&')

        if ($message.Length -gt $MaxLength) {
            $message = $message.Substring(0, $MaxLength) + '…'
        }

        Write-Output ("FAILED: " + $name)
        Write-Output ("   " + $message)
        Write-Output ""
    }
}
