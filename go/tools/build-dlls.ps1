# 构建 MDRenderer Go 版全部 DLL（c-shared）
# 用法：powershell -File tools\build-dlls.ps1 [-Only name1,name2]
param([string[]]$Only = @())

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
$env:CGO_ENABLED = "1"
$env:CC = "C:\wpjc\mingw64\bin\gcc.exe"
$out = Join-Path $root "build\dll"
New-Item -ItemType Directory -Force -Path $out | Out-Null
$log = Join-Path $out "build.log"
if (Test-Path $log) { Remove-Item $log }
$mods = @("idp", "compress", "log", "config", "file", "single",
          "doc", "markdown", "render", "convert-html", "convert-text", "convert-txt2md",
          "quicksort", "tmmf", "lerf", "cmd", "threadstat", "visualize",
          "logmain", "core", "docpack", "toolspack", "controller")
if ($Only.Count -gt 0) { $mods = $Only }

$fail = 0
foreach ($m in $mods) {
    $name = $m
    Write-Host "building $name ..."
    $args = @("build", "-buildmode=c-shared", "-trimpath", "-ldflags", "-s -w", "-o", (Join-Path $out "$name.dll"), ".\dll\$m")
    $result = & "C:\wpjc\go\bin\go.exe" @args 2>&1
    $code = $LASTEXITCODE
    Add-Content -LiteralPath $log -Value ("===== $name (exit=$code) =====")
    Add-Content -LiteralPath $log -Value ($result | Out-String)
    if ($code -ne 0) {
        Write-Host "FAILED $name"
        $result | ForEach-Object { Write-Host $_ }
        $fail++
    } else {
        $dll = Join-Path $out "$name.dll"
        Write-Host ("  ok  {0:N0} bytes" -f (Get-Item $dll).Length)
    }
}
Write-Host "DONE fail=$fail"
exit $fail
