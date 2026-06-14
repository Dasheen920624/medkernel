# MedKernel 产品级 CLI（`@medkernel/cli`）

面向**交付与运维**的受控数据服务客户端（DATASVC-01 FR-3）。不面向临床直接决策。

## 治理边界（不绕治理）

- **走后端 API 鉴权**：仅经 `MEDKERNEL_API_BASE` + `MEDKERNEL_API_TOKEN` 调后端受控合同。
- **不直连数据库、不读本地库连接串**：配置只读 API 基址与令牌，JDBC/DATASOURCE/DATABASE_URL 等一律不读不用。
- **不绕治理**：工具清单/权限/脱敏/审计/降级全由后端裁决；CLI 只是薄客户端。
- **不绕导出审批**：导出审批由后端裁决（当前后端异步导出未实现，见下「诚实缺口」）。

## 配置

| 环境变量 | 说明 |
|---|---|
| `MEDKERNEL_API_BASE` | 后端 API 基址，如 `https://medkernel.example.org` |
| `MEDKERNEL_API_TOKEN` | 后端鉴权令牌（Bearer），须持 `engine-data.read` 权限 |

## 用法

```
medkernel <命令域> <动作> [参数] [--purpose 用途]
```

| 命令域 | 动作 | 后端受控入口 |
|---|---|---|
| `diagnostics` | （默认） | `GET /tools`（受控工具目录与连通自检） |
| `rules` | `usage` / `explain <ruleId>` | `GET /rule-usage` / 工具 `explainRule` |
| `knowledge` | `search <关键词>` / `exists <编码>` / `usage` | 工具 `searchKnowledge`/`checkKnowledgeExistence` / `GET /knowledge-usage` |
| `clinical-signals` | `list` / `summary` | `GET /clinical-signals` / 工具 `summarizeEngineSignals` |
| `privacy` | `validate <D0-D5>` | 工具 `validatePrivacyPolicy` |
| `exports` | `submit` / `list` | **后端未实现（诚实缺口）** |

示例：

```
medkernel diagnostics
medkernel knowledge search 糖尿病 --purpose "交付前知识包核查"
medkernel privacy validate D5
```

## 退出码

`0` 成功；`1` 请求失败（后端错误/不可达）；`2` 用法或配置错误。

## 诚实缺口（DATASVC-01 大卡分期未完）

- `exports`：后端异步导出任务尚未实现（后续切片），CLI 诚实标缺口、**不伪造任务、不绕导出审批**（铁律 #1）。
- `knowledge`/`rules`/`privacy` 部分动作依赖受控工具 `searchKnowledge`/`explainRule`/`validatePrivacyPolicy` 等——运行时需后端已合并对应工具（[PR #612](https://github.com/Dasheen920624/medkernel/pull/612)）。
- 登录/工具调用审计在**后端**完成（FR-6）；CLI 不承担首要脱敏（后端脱敏，FR-2）。

## 测试

```
cd cli && npm test        # node --test test/*.test.mjs（零运行时依赖，fetch 经注入替身）
```
