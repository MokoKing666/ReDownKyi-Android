param(
    [Parameter(Mandatory = $true)][string]$Dir
)

# 汇总 Gradle 单元测试报告。
#
# 存在的理由：「BUILD SUCCESSFUL」并不能证明测试真的跑了 ——
# 测试类没被发现、被过滤掉、或者断言为空，都会给出同样的结果。
# 这个脚本读的是 Gradle 写出的实际结果文件，
# tests 计数为 0 时会被显式标注出来。

if (-not (Test-Path $Dir)) {
    Write-Output "未找到测试结果目录：$Dir"
    exit 1
}

$files = Get-ChildItem -Path $Dir -Filter *.xml
if ($files.Count -eq 0) {
    Write-Output "目录里没有结果文件（测试可能根本没执行）：$Dir"
    exit 1
}

$totalTests = 0
$totalFailures = 0
$totalErrors = 0
$totalSkipped = 0

foreach ($f in $files) {
    $raw = [System.IO.File]::ReadAllText($f.FullName, [System.Text.Encoding]::UTF8)
    $m = [regex]::Match($raw, '<testsuite\s+name="([^"]+)"\s+tests="(\d+)"\s+skipped="(\d+)"\s+failures="(\d+)"\s+errors="(\d+)"')
    if ($m.Success) {
        $name = $m.Groups[1].Value
        $tests = [int]$m.Groups[2].Value
        $skipped = [int]$m.Groups[3].Value
        $failures = [int]$m.Groups[4].Value
        $errors = [int]$m.Groups[5].Value

        $totalTests += $tests
        $totalSkipped += $skipped
        $totalFailures += $failures
        $totalErrors += $errors

        Write-Output ("{0,-58} tests={1,-4} failures={2,-3} errors={3,-3} skipped={4}" -f $name, $tests, $failures, $errors, $skipped)

        # 失败的用例单独列出，否则只有计数看不出是哪个
        foreach ($case in [regex]::Matches($raw, '<testcase name="([^"]+)"[^>]*>\s*<(failure|error)')) {
            Write-Output ("    FAILED: " + $case.Groups[1].Value)
        }
    }
    else {
        Write-Output ($f.Name + "  (无法解析结果文件)")
    }
}

Write-Output ""
Write-Output ("合计：tests={0} failures={1} errors={2} skipped={3}" -f $totalTests, $totalFailures, $totalErrors, $totalSkipped)

if ($totalTests -eq 0) {
    Write-Output "警告：没有任何用例被执行。"
    exit 2
}
if ($totalFailures -gt 0 -or $totalErrors -gt 0) {
    exit 1
}
