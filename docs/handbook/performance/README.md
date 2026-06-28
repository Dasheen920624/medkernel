# 性能验证

## 脚本

| 脚本 | 用途 |
|---|---|
| `k6-1000-concurrent.js` | 核心接口并发与稳定性 |
| `k6-llm-degradation.js` | 模型不可用、切换和降级链 |

## 执行

```bash
BASE_URL=https://target.example k6 run docs/handbook/performance/k6-1000-concurrent.js
BASE_URL=https://target.example k6 run docs/handbook/performance/k6-llm-degradation.js
```

目标地址、测试账号和令牌通过环境变量注入，不写入脚本或仓库。脚本读取
`MEDKERNEL_AUTH_TOKEN` 或 `AUTH_TOKEN` 后附加 `Bearer` 令牌；未注入令牌时只适用于本地或已放行的
受控压测环境。

脚本只覆盖当前产品模型下的医疗引擎、知识生产、质量管理、平台管理和模型能力入口；不得回流旧
LLM 路径、历史四域分组或固定患者病例文本。

## 结果

每次验证记录版本、环境、数据规模、并发模型、P50/P95/P99、错误率、资源使用和失败样本。原始日志、JFR、监控快照和可能含业务数据的文件保存到受控运行目录；仓库只提交脱敏结论。

上线阈值与失败处置以[质量基线](../../audit/质量基线.md)为准。
