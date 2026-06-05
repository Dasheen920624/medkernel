#!/usr/bin/env bash
# MedKernel macOS/Linux 一键打包并发布到单机服务器。
# 与 mk-publish.ps1 使用同一个服务端 medkernel-deploy，避免发布逻辑分叉。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
SERVER="193.112.107.134"
USER_NAME="root"
KEY_FILE=""
JAVA_HOME_VALUE=""
MVN_CMD="mvn"
REMOTE_INCOMING="/zoesoft/medkernel/incoming"
REMOTE_DEPLOY="/usr/local/bin/medkernel-deploy"
BACKEND=0
FRONTEND=0
TARGET_SELECTED=0
SKIP_BUILD=0
CLEAN_INSTALL=0
STAGE_ONLY=0
STRICT_HOST_KEY=0
SOURCE=""

info(){ printf '[*]  %s\n' "$*"; }
ok(){ printf '[OK] %s\n' "$*"; }
warn(){ printf '[!]  %s\n' "$*" >&2; }
die(){ printf '[X]  %s\n' "$*" >&2; exit 1; }

usage(){
  cat <<'USAGE'
MedKernel macOS/Linux 一键发布

用法：
  ./mk-publish.sh [选项]

常用：
  ./mk-publish.sh --key-file ~/.ssh/medkernel_deploy
  ./mk-publish.sh --frontend --skip-build --key-file ~/.ssh/medkernel_deploy
  ./mk-publish.sh --server 192.168.8.191 --user root

选项：
  --server HOST          目标服务器，默认 193.112.107.134
  --user USER            SSH 用户，默认 root
  --key-file PATH        SSH 私钥；不传则由 ssh/scp 自行交互认证
  --repo-root PATH       仓库根目录，默认脚本上两级目录
  --java-home PATH       构建后端时设置 JAVA_HOME
  --mvn-cmd CMD          Maven 命令，默认 mvn
  --backend              只发布后端；未指定 backend/frontend 时默认两者都发
  --frontend             只发布前端；未指定 backend/frontend 时默认两者都发
  --skip-build           使用已有 target jar / frontend dist
  --clean-install        前端先 npm ci
  --stage-only           只上传到 incoming，不触发远程发布
  --source TEXT          manifest 来源，默认 git 短哈希
  --strict-host-key      SSH StrictHostKeyChecking=yes；默认 accept-new
  -h, --help             显示帮助
USAGE
}

sh_quote(){
  printf "'%s'" "$(printf '%s' "$1" | sed "s/'/'\\\\''/g")"
}

require_cmd(){
  command -v "$1" >/dev/null 2>&1 || die "缺少命令：$1"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --server) SERVER="${2:-}"; shift 2;;
    --user) USER_NAME="${2:-}"; shift 2;;
    --key-file) KEY_FILE="${2:-}"; shift 2;;
    --repo-root) REPO_ROOT="${2:-}"; shift 2;;
    --java-home) JAVA_HOME_VALUE="${2:-}"; shift 2;;
    --mvn-cmd) MVN_CMD="${2:-}"; shift 2;;
    --backend|--backend-only) BACKEND=1; FRONTEND=0; TARGET_SELECTED=1; shift;;
    --frontend|--frontend-only) FRONTEND=1; BACKEND=0; TARGET_SELECTED=1; shift;;
    --skip-build) SKIP_BUILD=1; shift;;
    --clean-install) CLEAN_INSTALL=1; shift;;
    --stage-only) STAGE_ONLY=1; shift;;
    --source) SOURCE="${2:-}"; shift 2;;
    --strict-host-key) STRICT_HOST_KEY=1; shift;;
    -h|--help) usage; exit 0;;
    *) die "未知参数：$1（--help 查看用法）";;
  esac
done

[[ -d "$REPO_ROOT" ]] || die "找不到仓库目录：$REPO_ROOT"
REPO_ROOT="$(cd "$REPO_ROOT" && pwd)"
if [[ "$TARGET_SELECTED" -eq 0 ]]; then
  BACKEND=1
  FRONTEND=1
fi

require_cmd ssh
require_cmd scp
require_cmd tar
require_cmd git

SSH_ARGS=(-o StrictHostKeyChecking=accept-new)
if [[ "$STRICT_HOST_KEY" -eq 1 ]]; then
  SSH_ARGS=(-o StrictHostKeyChecking=yes)
fi
if [[ -n "$KEY_FILE" ]]; then
  [[ -f "$KEY_FILE" ]] || die "找不到 SSH 私钥：$KEY_FILE"
  SSH_ARGS+=(-i "$KEY_FILE")
fi

if [[ -z "$SOURCE" ]]; then
  SOURCE="$(git -C "$REPO_ROOT" rev-parse --short HEAD 2>/dev/null || true)"
  [[ -n "$SOURCE" ]] || SOURCE="manual"
fi

STAGING="$(mktemp -d "${TMPDIR:-/tmp}/mk-publish.XXXXXX")"
cleanup(){ rm -rf "$STAGING"; }
trap cleanup EXIT

info "目标 ${USER_NAME}@${SERVER}   来源 ${SOURCE}   仓库 ${REPO_ROOT}"

JAR_PATH=""
DIST_TAR=""

if [[ "$BACKEND" -eq 1 ]]; then
  BACKEND_POM="$REPO_ROOT/medkernel-backend/pom.xml"
  [[ -f "$BACKEND_POM" ]] || die "找不到后端 pom：$BACKEND_POM"
  if [[ "$SKIP_BUILD" -eq 0 ]]; then
    require_cmd "$MVN_CMD"
    if [[ -n "$JAVA_HOME_VALUE" ]]; then
      [[ -d "$JAVA_HOME_VALUE" ]] || die "找不到 JAVA_HOME：$JAVA_HOME_VALUE"
      export JAVA_HOME="$JAVA_HOME_VALUE"
    fi
    info "构建后端 jar（跳过测试，完整验证请在发布前单独跑 mvn test）..."
    "$MVN_CMD" -f "$BACKEND_POM" -Dmaven.test.skip=true clean package
  else
    warn "跳过后端构建（--skip-build），使用已有 jar"
  fi
  JAR_PATH="$(ls -t "$REPO_ROOT"/medkernel-backend/target/medkernel-backend-*.jar 2>/dev/null | grep -v '\.original$' | head -1 || true)"
  [[ -n "$JAR_PATH" && -f "$JAR_PATH" ]] || die "未找到后端 jar；去掉 --skip-build 先构建"
  ok "后端 jar：$(basename "$JAR_PATH")"
fi

if [[ "$FRONTEND" -eq 1 ]]; then
  FRONTEND_DIR="$REPO_ROOT/frontend"
  [[ -d "$FRONTEND_DIR" ]] || die "找不到前端目录：$FRONTEND_DIR"
  if [[ "$SKIP_BUILD" -eq 0 ]]; then
    require_cmd npm
    pushd "$FRONTEND_DIR" >/dev/null
    if [[ "$CLEAN_INSTALL" -eq 1 || ! -d node_modules ]]; then
      info "安装前端依赖（npm ci）..."
      npm ci
    fi
    info "构建前端（npm run build）..."
    npm run build
    popd >/dev/null
  else
    warn "跳过前端构建（--skip-build），使用已有 dist"
  fi
  [[ -f "$FRONTEND_DIR/dist/index.html" ]] || die "未找到 frontend/dist/index.html"
  DIST_TAR="$STAGING/dist.tar.gz"
  info "打包前端 dist.tar.gz ..."
  tar -czf "$DIST_TAR" -C "$FRONTEND_DIR" dist
  [[ "$(tar -tzf "$DIST_TAR" | grep -c '^dist/index.html$')" -gt 0 ]] || die "dist.tar.gz 内未发现 dist/index.html"
  ok "前端包：dist.tar.gz"
fi

info "上传到 ${SERVER}:${REMOTE_INCOMING} ..."
REMOTE_JAR=""
REMOTE_FRONTEND=""
if [[ -n "$JAR_PATH" ]]; then
  scp "${SSH_ARGS[@]}" "$JAR_PATH" "${USER_NAME}@${SERVER}:${REMOTE_INCOMING}/"
  REMOTE_JAR="${REMOTE_INCOMING}/$(basename "$JAR_PATH")"
  ok "已上传 $(basename "$JAR_PATH")"
fi
if [[ -n "$DIST_TAR" ]]; then
  scp "${SSH_ARGS[@]}" "$DIST_TAR" "${USER_NAME}@${SERVER}:${REMOTE_INCOMING}/dist.tar.gz"
  REMOTE_FRONTEND="${REMOTE_INCOMING}/dist.tar.gz"
  ok "已上传 dist.tar.gz"
fi

if [[ "$STAGE_ONLY" -eq 1 ]]; then
  warn "按 --stage-only 只上传不发布。登录服务器执行：sudo medkernel-deploy --source $(sh_quote "$SOURCE")"
  exit 0
fi

REMOTE_COMMAND="$(sh_quote "$REMOTE_DEPLOY") --source $(sh_quote "$SOURCE")"
if [[ -n "$REMOTE_JAR" ]]; then
  REMOTE_COMMAND+=" --jar $(sh_quote "$REMOTE_JAR")"
fi
if [[ -n "$REMOTE_FRONTEND" ]]; then
  REMOTE_COMMAND+=" --frontend $(sh_quote "$REMOTE_FRONTEND")"
fi

info "远程执行：$REMOTE_COMMAND"
ssh "${SSH_ARGS[@]}" "${USER_NAME}@${SERVER}" "$REMOTE_COMMAND"
ok "==== 发布完成（健康检查已通过）===="
