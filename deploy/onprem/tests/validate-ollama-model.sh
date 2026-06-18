#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
model_file="$repo_root/deploy/onprem/ollama/MedKernel.Qwen25-1.5B.Modelfile"

fail() {
  echo "Ollama 生产模型定义校验失败：$1" >&2
  exit 1
}

[[ -f "$model_file" ]] || fail "缺少受版本控制的 Modelfile"

grep -Fxq 'FROM qwen2.5:1.5b' "$model_file" || fail "基础模型必须固定为 qwen2.5:1.5b"
grep -Fxq 'PARAMETER temperature 0' "$model_file" || fail "必须关闭随机采样"
grep -Eq '^PARAMETER seed [0-9]+$' "$model_file" || fail "必须固定随机种子"
grep -Fxq 'PARAMETER num_ctx 2048' "$model_file" || fail "上下文预算必须匹配 134 资源上限"
grep -Eq '^SYSTEM .*Never invent a source, diagnosis, order, prescription, threshold, or medical fact\.' "$model_file" \
  || fail "系统约束必须禁止编造医学事实"
grep -Eq '^SYSTEM .*independent human review\.$' "$model_file" \
  || fail "系统约束必须要求独立人工审核"

if grep -Eqi '(api[_-]?key|password|secret|token|https?://)' "$model_file"; then
  fail "模型定义不得包含凭据或现场端点"
fi

echo "Ollama 生产模型定义校验通过"
