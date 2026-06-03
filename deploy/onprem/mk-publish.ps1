<#
.SYNOPSIS
  MedKernel 本机一键打包并发布到现场服务器。
.DESCRIPTION
  构建后端 jar（JDK21+Maven）与/或前端 dist（npm）→ 打包 dist.tar.gz →
  上传到服务器 /zoesoft/medkernel/incoming/ → 远程触发 medkernel-deploy
  （备份→替换→重启→健康检查→失败自动回滚）。
  依赖：PowerShell 7、Posh-SSH 模块、JDK21、Maven、Node。
.EXAMPLE
  .\mk-publish.ps1                 # 构建并发布前后端
  .\mk-publish.ps1 -Backend        # 只后端
  .\mk-publish.ps1 -Frontend       # 只前端
  .\mk-publish.ps1 -SkipBuild      # 用已有产物直接发布
  .\mk-publish.ps1 -StageOnly      # 只构建+上传到 incoming，不触发远程发布
  .\mk-publish.ps1 -RepoRoot D:\path\to\medkernel -Source 9a1b2c3
.NOTES
  密码：优先 -Password 参数，其次环境变量 MEDKERNEL_DEPLOY_PASSWORD，否则交互输入。
  生产建议改用 SSH 密钥免密；勿把密码写死进脚本或仓库。
#>
[CmdletBinding()]
param(
  [string]$Server         = "192.168.8.191",
  [string]$User           = "root",
  [string]$Password,
  [string]$RepoRoot       = "D:\vibeCoding\medkernel",
  [string]$JavaHome       = "D:\java\jdk-21",
  [string]$MvnCmd         = "D:\java\apache-maven-3.9.12\bin\mvn.cmd",
  [string]$RemoteIncoming = "/zoesoft/medkernel/incoming",
  [switch]$Backend,
  [switch]$Frontend,
  [switch]$SkipBuild,
  [switch]$CleanInstall,
  [switch]$StageOnly,
  [string]$Source
)
$ErrorActionPreference = 'Stop'
function Info($m){ Write-Host "[*]  $m" -ForegroundColor Cyan }
function Ok($m){ Write-Host "[OK] $m" -ForegroundColor Green }
function Warn($m){ Write-Host "[!]  $m" -ForegroundColor Yellow }
function Die($m){ Write-Host "[X]  $m" -ForegroundColor Red; exit 1 }

# 默认前后端都做
if (-not $Backend -and -not $Frontend) { $Backend = $true; $Frontend = $true }

if (-not (Test-Path $RepoRoot)) { Die "找不到仓库目录：$RepoRoot（用 -RepoRoot 指定）" }
$BackendPom  = Join-Path $RepoRoot 'medkernel-backend\pom.xml'
$FrontendDir = Join-Path $RepoRoot 'frontend'

if (-not (Get-Module -ListAvailable Posh-SSH)) { Die "缺少 Posh-SSH：Install-Module Posh-SSH -Scope CurrentUser" }
Import-Module Posh-SSH -ErrorAction Stop

# 凭据
if (-not $Password) { $Password = $env:MEDKERNEL_DEPLOY_PASSWORD }
if ($Password) { $sec = ConvertTo-SecureString $Password -AsPlainText -Force }
else { $sec = Read-Host "请输入 $User@$Server 的密码" -AsSecureString }
$cred = New-Object System.Management.Automation.PSCredential($User, $sec)

# git 短哈希作为来源标记
if (-not $Source) {
  try { $Source = (& git -C $RepoRoot rev-parse --short HEAD 2>$null).Trim() } catch { }
  if (-not $Source) { $Source = "manual" }
}
Info "目标 $User@$Server   来源 $Source   仓库 $RepoRoot"

$staging = Join-Path $env:TEMP ("mk-publish-" + (Get-Date -Format yyyyMMddHHmmss))
New-Item -ItemType Directory -Force -Path $staging | Out-Null
$jarPath = $null; $distTar = $null

# ---------------- 构建后端 ----------------
if ($Backend) {
  if (-not (Test-Path $BackendPom)) { Die "找不到后端 pom：$BackendPom" }
  if (-not $SkipBuild) {
    Info "构建后端 jar（JDK21，跳过测试）..."
    $env:JAVA_HOME = $JavaHome
    & $MvnCmd -f $BackendPom "-Dmaven.test.skip=true" clean package
    if ($LASTEXITCODE -ne 0) { Die "Maven 构建失败" }
  } else { Warn "跳过后端构建（-SkipBuild），使用已有 jar" }
  $jar = Get-ChildItem (Join-Path $RepoRoot 'medkernel-backend\target') -Filter 'medkernel-backend-*.jar' -ErrorAction SilentlyContinue |
         Where-Object { $_.Name -notlike '*.original' } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
  if (-not $jar) { Die "未找到后端 jar（target\medkernel-backend-*.jar）；去掉 -SkipBuild 先构建" }
  $jarPath = $jar.FullName
  Ok ("后端 jar：{0}  ({1:N1} MB)" -f $jar.Name, ($jar.Length/1MB))
}

# ---------------- 构建前端 ----------------
if ($Frontend) {
  if (-not (Test-Path $FrontendDir)) { Die "找不到前端目录：$FrontendDir" }
  if (-not $SkipBuild) {
    Push-Location $FrontendDir
    try {
      if ($CleanInstall -or -not (Test-Path (Join-Path $FrontendDir 'node_modules'))) {
        Info "安装前端依赖（npm ci）..."; npm ci; if ($LASTEXITCODE -ne 0) { Die "npm ci 失败" }
      }
      Info "构建前端（npm run build）..."; npm run build; if ($LASTEXITCODE -ne 0) { Die "前端构建失败" }
    } finally { Pop-Location }
  } else { Warn "跳过前端构建（-SkipBuild），使用已有 dist" }
  $distDir = Join-Path $FrontendDir 'dist'
  if (-not (Test-Path (Join-Path $distDir 'index.html'))) { Die "未找到 frontend\dist\index.html" }
  $distTar = Join-Path $staging 'dist.tar.gz'
  Info "打包前端 dist.tar.gz ..."
  # 用 Windows 自带 bsdtar（System32\tar.exe，内置 gzip）；GNU/Git tar 从 PowerShell 调会找不到 gzip
  $tarExe = Join-Path $env:SystemRoot 'System32\tar.exe'
  if (-not (Test-Path $tarExe)) { $tarExe = 'tar' }
  & $tarExe -czf $distTar -C $FrontendDir dist
  if ($LASTEXITCODE -ne 0) { Die "打包 dist.tar.gz 失败（tar 退出码 $LASTEXITCODE）" }
  if (-not (Test-Path $distTar) -or (Get-Item $distTar).Length -lt 1024) { Die "dist.tar.gz 异常（缺失或过小），请检查 tar/gzip" }
  $listed = & $tarExe -tzf $distTar 2>$null
  if (-not ($listed -match 'dist/index\.html')) { Die "dist.tar.gz 内未发现 dist/index.html" }
  Ok ("前端包：dist.tar.gz  ({0:N2} MB, {1} 项)" -f ((Get-Item $distTar).Length/1MB), (@($listed).Count))
}

# ---------------- 上传 ----------------
Info "上传到 $Server : $RemoteIncoming ..."
$rjar = $null; $rtar = $null
if ($jarPath) { Set-SCPItem -ComputerName $Server -Credential $cred -Path $jarPath -Destination $RemoteIncoming -AcceptKey; $rjar = "$RemoteIncoming/" + (Split-Path $jarPath -Leaf); Ok "已上传 $(Split-Path $jarPath -Leaf)" }
if ($distTar) { Set-SCPItem -ComputerName $Server -Credential $cred -Path $distTar -Destination $RemoteIncoming -AcceptKey; $rtar = "$RemoteIncoming/dist.tar.gz"; Ok "已上传 dist.tar.gz" }

# ---------------- 触发远程发布 ----------------
if ($StageOnly) {
  Warn "按 -StageOnly 只上传不发布。登录服务器执行： sudo medkernel-deploy"
  try { [System.IO.Directory]::Delete($staging, $true) } catch { }
  exit 0
}
$cmd = "/usr/local/bin/medkernel-deploy --source $Source"
if ($rjar) { $cmd += " --jar $rjar" }
if ($rtar) { $cmd += " --frontend $rtar" }
Info "远程执行：$cmd"
$s = New-SSHSession -ComputerName $Server -Credential $cred -AcceptKey
$res = Invoke-SSHCommand -SessionId $s.SessionId -Command $cmd -TimeOut 300
$res.Output
if ($res.Error) { Write-Host "--- 远程 stderr ---" -ForegroundColor Yellow; $res.Error }
$code = $res.ExitStatus
Remove-SSHSession -SessionId $s.SessionId | Out-Null
try { [System.IO.Directory]::Delete($staging, $true) } catch { }
if ($code -eq 0) { Ok "==== 发布完成（健康检查已通过）====" }
else { Die "发布失败（exit=$code）；若已进入重启阶段脚本会自动回滚，详见上面输出与服务器 /zoesoft/medkernel/logs/{deploy,stdout}.log" }
