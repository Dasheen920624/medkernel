<#
.SYNOPSIS
  MedKernel Windows/PowerShell 一键打包并发布到单机服务器。
.DESCRIPTION
  构建后端 jar 与/或前端 dist，上传到 /zoesoft/medkernel/incoming/，
  再远程调用 medkernel-deploy 完成备份、替换、重启、健康检查与失败回滚。
  前端-only 发布不会改写运行 manifest；manifest/JAR 来源仍以最近一次完整或后端发布为准，
  前端版本以 index/assets 与本命令来源追溯。

  默认目标为当前腾讯云轻量服务器；老 Oracle 现场可通过 -Server 覆盖。
  推荐使用 -KeyFile 走 OpenSSH 密钥登录，避免在命令行或脚本中出现口令。
.EXAMPLE
  .\mk-publish.ps1 -KeyFile C:\tmp\medkernel_deploy_ed25519
.EXAMPLE
  .\mk-publish.ps1 -Server 192.168.8.191 -User root -Password $env:MEDKERNEL_DEPLOY_PASSWORD
#>
[CmdletBinding()]
param(
  [string]$Server = "193.112.107.134",
  [string]$User = "root",
  [string]$Password,
  [string]$KeyFile,
  [string]$RepoRoot,
  [string]$JavaHome,
  [string]$MvnCmd = "mvn",
  [string]$RemoteIncoming = "/zoesoft/medkernel/incoming",
  [string]$RemoteDeploy = "/usr/local/bin/medkernel-deploy",
  [switch]$Backend,
  [switch]$Frontend,
  [switch]$CleanInstall,
  [switch]$StageOnly,
  [switch]$StrictHostKeyChecking,
  [string]$Source
)

$ErrorActionPreference = 'Stop'

function Info($message) { Write-Host "[*]  $message" -ForegroundColor Cyan }
function Ok($message) { Write-Host "[OK] $message" -ForegroundColor Green }
function Warn($message) { Write-Host "[!]  $message" -ForegroundColor Yellow }
function Die($message) { Write-Host "[X]  $message" -ForegroundColor Red; exit 1 }

function Resolve-RepoRoot {
  param([string]$Value)
  if ($Value) {
    if (-not (Test-Path $Value)) { Die "找不到仓库目录：$Value（用 -RepoRoot 指定）" }
    return (Resolve-Path $Value).Path
  }
  return (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
}

function Is-UnixPowerShell {
  return ($PSVersionTable.ContainsKey('Platform') -and $PSVersionTable.Platform -eq 'Unix')
}

function Get-NpmCommand {
  if (Is-UnixPowerShell) { return 'npm' }
  return 'npm.cmd'
}

function Get-TarCommand {
  if (Is-UnixPowerShell) { return (Get-Command tar -ErrorAction Stop).Source }
  $windowsTar = Join-Path $env:SystemRoot 'System32\tar.exe'
  if (Test-Path $windowsTar) { return $windowsTar }
  return (Get-Command tar -ErrorAction Stop).Source
}

function Quote-ShArg {
  param([string]$Value)
  return "'" + ($Value -replace "'", "'\''") + "'"
}

function Invoke-Native {
  param(
    [string]$File,
    [string[]]$Arguments,
    [string]$FailureMessage
  )
  & $File @Arguments
  if ($LASTEXITCODE -ne 0) { Die "$FailureMessage（退出码 $LASTEXITCODE）" }
}

function Get-SshArgs {
  $hostKeyMode = if ($StrictHostKeyChecking) { 'yes' } else { 'accept-new' }
  $args = @('-o', "StrictHostKeyChecking=$hostKeyMode")
  if ($KeyFile) {
    if (-not (Test-Path $KeyFile)) { Die "找不到 SSH 私钥：$KeyFile" }
    $args += @('-i', (Resolve-Path $KeyFile).Path)
  }
  return $args
}

function Upload-WithNativeOpenSsh {
  param(
    [string]$Path,
    [string]$Destination
  )
  $scp = (Get-Command scp -ErrorAction Stop).Source
  $args = @(Get-SshArgs) + @($Path, "${User}@${Server}:$Destination")
  Invoke-Native -File $scp -Arguments $args -FailureMessage "上传失败：$Path"
}

function Invoke-RemoteWithNativeOpenSsh {
  param([string]$Command)
  $ssh = (Get-Command ssh -ErrorAction Stop).Source
  $args = @(Get-SshArgs) + @("${User}@${Server}", $Command)
  Invoke-Native -File $ssh -Arguments $args -FailureMessage '远程发布失败'
}

function New-PasswordCredential {
  if (-not $Password) { $Password = $env:MEDKERNEL_DEPLOY_PASSWORD }
  if ($Password) { $secure = ConvertTo-SecureString $Password -AsPlainText -Force }
  else { $secure = Read-Host "请输入 $User@$Server 的密码" -AsSecureString }
  return New-Object System.Management.Automation.PSCredential($User, $secure)
}

if (-not $Backend -and -not $Frontend) {
  $Backend = $true
  $Frontend = $true
}

$RepoRoot = Resolve-RepoRoot $RepoRoot
$BackendPom = Join-Path $RepoRoot 'medkernel-backend\pom.xml'
$FrontendDir = Join-Path $RepoRoot 'frontend'
$UseNativeOpenSsh = [bool]$KeyFile

if (-not $UseNativeOpenSsh -and -not (Get-Module -ListAvailable Posh-SSH)) {
  Warn "未安装 Posh-SSH，将使用系统 ssh/scp；如需密码登录会由 ssh/scp 自行交互提示。"
  $UseNativeOpenSsh = $true
}

$headSource = (& git -C $RepoRoot rev-parse HEAD 2>$null).Trim()
if ($LASTEXITCODE -ne 0 -or $headSource -notmatch '^[0-9a-f]{40}$') {
  Die '仓库 HEAD 不是可验证的完整提交哈希'
}
if (-not $Source) { $Source = $headSource }
if ($Source -notmatch '^[0-9a-f]{40}$' -or $Source -cne $headSource) {
  Die "发布来源必须是当前 HEAD 的完整 40 位提交哈希：$headSource"
}
$dirtyWorktree = @(& git -C $RepoRoot status --porcelain --untracked-files=normal)
if ($LASTEXITCODE -ne 0) { Die '无法核验 Git 工作树状态' }
if ($dirtyWorktree.Count -gt 0) {
  Die '工作树存在未提交改动，禁止构建和发布不可追溯制品'
}

Info "目标 $User@$Server   来源 $Source   仓库 $RepoRoot"

$staging = Join-Path ([System.IO.Path]::GetTempPath()) ("mk-publish-" + (Get-Date -Format yyyyMMddHHmmss))
New-Item -ItemType Directory -Force -Path $staging | Out-Null
$jarPath = $null
$distTar = $null

try {
  if ($Backend) {
    if (-not (Test-Path $BackendPom)) { Die "找不到后端 pom：$BackendPom" }
    Info "从当前提交重新构建后端 jar（跳过测试，完整验证请在发布前单独跑 mvn test）..."
    if ($JavaHome) {
      if (-not (Test-Path $JavaHome)) { Die "找不到 JavaHome：$JavaHome" }
      $env:JAVA_HOME = (Resolve-Path $JavaHome).Path
    }
    Invoke-Native -File $MvnCmd -Arguments @('-f', $BackendPom, '-Dmaven.test.skip=true', 'clean', 'package') -FailureMessage 'Maven 构建失败'

    $jar = Get-ChildItem (Join-Path $RepoRoot 'medkernel-backend\target') -Filter 'medkernel-backend-*.jar' -ErrorAction SilentlyContinue |
      Where-Object { $_.Name -notlike '*.original' } |
      Sort-Object LastWriteTime -Descending |
      Select-Object -First 1
    if (-not $jar) { Die '后端构建完成后未找到 jar（target\medkernel-backend-*.jar）' }
    $jarPath = $jar.FullName
    Ok ("后端 jar：{0}  ({1:N1} MB)" -f $jar.Name, ($jar.Length / 1MB))
  }

  if ($Frontend) {
    if (-not (Test-Path $FrontendDir)) { Die "找不到前端目录：$FrontendDir" }
    Push-Location $FrontendDir
    try {
      $npmCmd = Get-NpmCommand
      if ($CleanInstall -or -not (Test-Path (Join-Path $FrontendDir 'node_modules'))) {
        Info '安装前端依赖（npm ci）...'
        Invoke-Native -File $npmCmd -Arguments @('ci') -FailureMessage 'npm ci 失败'
      }
      Info '从当前提交重新构建前端（npm run build）...'
      Invoke-Native -File $npmCmd -Arguments @('run', 'build') -FailureMessage '前端构建失败'
    } finally {
      Pop-Location
    }

    $distDir = Join-Path $FrontendDir 'dist'
    if (-not (Test-Path (Join-Path $distDir 'index.html'))) { Die '未找到 frontend\dist\index.html' }
    $distTar = Join-Path $staging 'dist.tar.gz'
    Info '打包前端 dist.tar.gz ...'
    $tarExe = Get-TarCommand
    Invoke-Native -File $tarExe -Arguments @('-czf', $distTar, '-C', $FrontendDir, 'dist') -FailureMessage '打包 dist.tar.gz 失败'
    if (-not (Test-Path $distTar) -or (Get-Item $distTar).Length -lt 1024) { Die 'dist.tar.gz 异常（缺失或过小）' }
    $listed = & $tarExe -tzf $distTar 2>$null
    if (-not ($listed -match 'dist/index\.html')) { Die 'dist.tar.gz 内未发现 dist/index.html' }
    Ok ("前端包：dist.tar.gz  ({0:N2} MB, {1} 项)" -f ((Get-Item $distTar).Length / 1MB), (@($listed).Count))
  }

  Info "上传到 $Server : $RemoteIncoming ..."
  $remoteJar = $null
  $remoteFrontend = $null

  if ($UseNativeOpenSsh) {
    if ($jarPath) {
      Upload-WithNativeOpenSsh $jarPath "$RemoteIncoming/"
      $remoteJar = "$RemoteIncoming/$(Split-Path $jarPath -Leaf)"
      Ok "已上传 $(Split-Path $jarPath -Leaf)"
    }
    if ($distTar) {
      Upload-WithNativeOpenSsh $distTar "$RemoteIncoming/dist.tar.gz"
      $remoteFrontend = "$RemoteIncoming/dist.tar.gz"
      Ok '已上传 dist.tar.gz'
    }
  } else {
    Import-Module Posh-SSH -ErrorAction Stop
    $credential = New-PasswordCredential
    if ($jarPath) {
      Set-SCPItem -ComputerName $Server -Credential $credential -Path $jarPath -Destination $RemoteIncoming -AcceptKey
      $remoteJar = "$RemoteIncoming/$(Split-Path $jarPath -Leaf)"
      Ok "已上传 $(Split-Path $jarPath -Leaf)"
    }
    if ($distTar) {
      Set-SCPItem -ComputerName $Server -Credential $credential -Path $distTar -Destination $RemoteIncoming -AcceptKey
      $remoteFrontend = "$RemoteIncoming/dist.tar.gz"
      Ok '已上传 dist.tar.gz'
    }
  }

  if ($StageOnly) {
    Warn "按 -StageOnly 只上传不发布。登录服务器执行：sudo medkernel-deploy --source $(Quote-ShArg $Source)"
    exit 0
  }

  $remoteCommand = "$(Quote-ShArg $RemoteDeploy) --source $(Quote-ShArg $Source)"
  if ($remoteJar) { $remoteCommand += " --jar $(Quote-ShArg $remoteJar)" }
  if ($remoteFrontend) { $remoteCommand += " --frontend $(Quote-ShArg $remoteFrontend)" }
  Info "远程执行：$remoteCommand"

  if ($UseNativeOpenSsh) {
    Invoke-RemoteWithNativeOpenSsh $remoteCommand
  } else {
    $session = New-SSHSession -ComputerName $Server -Credential $credential -AcceptKey
    $result = Invoke-SSHCommand -SessionId $session.SessionId -Command $remoteCommand -TimeOut 300
    $result.Output
    if ($result.Error) {
      Write-Host '--- 远程 stderr ---' -ForegroundColor Yellow
      $result.Error
    }
    $exitCode = $result.ExitStatus
    Remove-SSHSession -SessionId $session.SessionId | Out-Null
    if ($exitCode -ne 0) { Die "发布失败（exit=$exitCode）；详见服务器 /zoesoft/medkernel/logs/{deploy,stdout}.log" }
  }

  Ok '==== 发布完成（健康检查已通过）===='
} finally {
  try { [System.IO.Directory]::Delete($staging, $true) } catch { }
}
