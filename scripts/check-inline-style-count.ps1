# check-inline-style-count.ps1 — 前端内联样式收敛守门
#
# 统计 frontend/src 下 `style={{` 出现次数（即 JSX 内联 style 对象数）。
# 当数量 > baseline（保存在本文件 $script:Baseline 常量）时退出码 1，CI FAIL。
# 当前 baseline 固定为 0，实现「inline style 不得新增」的硬门禁。
#
# 触发时机：
#   - scripts/verify-pr.ps1 在每次 PR 前调用
#   - GitHub Actions guard-rules job 调用
#
# 更新 baseline：
#   抽取完毕后跑 ./scripts/check-inline-style-count.ps1 -UpdateBaseline
#   会把新值写回本文件（仅本地，需手工 commit）。
#
# 当前目标：内联 `style={{}}` 归零。新增样式必须使用 CSS Modules、统一 `mk-*` 样式类和设计 token。

param(
    [switch]$UpdateBaseline,
    [switch]$Verbose
)

$ErrorActionPreference = 'Stop'

# 当前基线
$script:Baseline = 0

# 根路径推断（脚本在 scripts/ 下，仓库根在上一级）
$repoRoot = Split-Path -Parent $PSScriptRoot
$frontendSrc = Join-Path $repoRoot 'frontend/src'

if (-not (Test-Path $frontendSrc)) {
    Write-Error "frontend/src 不存在: $frontendSrc"
    exit 1
}

# 扫描所有 .tsx/.ts 中的 `style={{` 出现次数
$files = Get-ChildItem -Path $frontendSrc -Recurse -Include *.tsx, *.ts -File
$total = 0
$perFile = @{}

foreach ($file in $files) {
    $content = Get-Content -Path $file.FullName -Raw -Encoding UTF8
    if ($null -eq $content) { continue }
    $matches = [regex]::Matches($content, 'style=\{\{')
    if ($matches.Count -gt 0) {
        $perFile[$file.FullName.Substring($repoRoot.Length + 1)] = $matches.Count
        $total += $matches.Count
    }
}

if ($Verbose) {
    Write-Host "== inline style 分布 ==" -ForegroundColor Cyan
    $perFile.GetEnumerator() | Sort-Object Value -Descending | Select-Object -First 20 | ForEach-Object {
        Write-Host ("  {0,4}  {1}" -f $_.Value, $_.Key)
    }
    Write-Host ""
}

Write-Host "inline style 当前: $total / baseline: $($script:Baseline)" -ForegroundColor White

if ($UpdateBaseline) {
    $scriptPath = $PSCommandPath
    $scriptContent = Get-Content -Path $scriptPath -Raw -Encoding UTF8
    $newContent = $scriptContent -replace '\$script:Baseline = \d+', "`$script:Baseline = $total"
    Set-Content -Path $scriptPath -Value $newContent -Encoding UTF8 -NoNewline
    Write-Host "Baseline 已更新为 $total，请 git commit 本脚本。" -ForegroundColor Green
    exit 0
}

if ($total -gt $script:Baseline) {
    Write-Host ""
    Write-Host "❌ FAIL：inline style 数量上升（$total > baseline $($script:Baseline)）。" -ForegroundColor Red
    Write-Host "   前端视觉债原则：『归零后不得新增』。" -ForegroundColor Red
    Write-Host "   请把新增的 style={{}} 抽取到同名 .module.css，或使用 var(--mk-*) className。" -ForegroundColor Red
    Write-Host "   详见 docs/EXPERIENCE_CONTRACT.md 和 frontend/README.md。" -ForegroundColor Red
    exit 1
} elseif ($total -lt $script:Baseline) {
    Write-Host "✅ PASS：inline style 减少 $($script:Baseline - $total) 处（$total < $($script:Baseline)）。" -ForegroundColor Green
    Write-Host "   请记得跑 ./scripts/check-inline-style-count.ps1 -UpdateBaseline 把 baseline 下调。" -ForegroundColor Yellow
    exit 0
} else {
    Write-Host "✅ PASS：inline style 已归零（$total == $($script:Baseline)）。" -ForegroundColor Green
    exit 0
}
