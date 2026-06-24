# MedKernel 产品级 CLI（`@medkernel/cli`）

面向**交付与运维**的受控数据服务客户端（DATASVC-01 FR-3）。不面向临床直接决策。

## 治理边界（不绕治理）

- **走后端 API 鉴权**：仅经 `MEDKERNEL_API_BASE` + `MEDKERNEL_API_TOKEN` 调后端受控合同。
- **不直连数据库、不读本地库连接串**：配置只读 API 基址与访问凭证，JDBC/DATASOURCE/DATABASE_URL 等一律不读不用。
- **不绕治理**：工具清单/权限/脱敏/审计/降级全由后端裁决；CLI 只是薄客户端。
- **导出范围确认**：CLI 只使用后端冻结并留证的范围，提交、查询和登记真实导出任务。

## 配置

| 环境变量 | 说明 |
|---|---|
| `MEDKERNEL_API_BASE` | 后端 API 基址，如 `https://medkernel.example.org` |
| `MEDKERNEL_API_TOKEN` | 后端访问凭证（Bearer）；读工具须 `engine-data.read`，Agent 取数/回写须 `knowledge.write`，导出动作须相应导出权限 |

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
| `agent` | `submit-candidate <payloadJson>` / `fetch-public-material <payloadJson>` | 工具 `submitProductionCandidate` / `fetchPublicMaterial` |
| `privacy` | `validate <D0-D5>` | 工具 `validatePrivacyPolicy` |
| `exports` | `submit <exportType> <confirmationId> <idempotencyKey>` / `status <jobCode>` / `list` / `cancel <jobCode>` / `complete <confirmationId> <jobCode>` | 范围确认后的导出任务与真实产物登记入口 |

示例：

```
medkernel diagnostics
medkernel knowledge search 糖尿病 --purpose "交付前知识资产核查"
medkernel agent fetch-public-material '{"sourceCode":"NHC-HTN","url":"https://guideline.example.org/htn.txt","versionNo":"v2026","format":"STRUCTURED_TEXT","dataLevel":"D1"}' --purpose "Agent 受控获取公域资料"
medkernel privacy validate D5
```

## 退出码

`0` 成功；`1` 请求失败（后端错误/不可达）；`2` 用法或配置错误。

## 诚实分寸

- `agent fetch-public-material` 只触发后端 `fetchPublicMaterial` 受控工具；是否允许抓取由后端生产中心形态、allowlist、许可和 robots 门禁裁决，失败返回真实原因，不伪造资料 URI 或候选。
- `knowledge`/`rules`/`privacy`/`agent` 动作依赖后端受控工具目录；运行时以 `diagnostics` 返回的真实工具和权限为准。
- 登录/工具调用审计在**后端**完成（FR-6）；CLI 不承担首要脱敏（后端脱敏，FR-2）。

## 测试

```
cd cli && npm test        # node --test test/*.test.mjs（零运行时依赖，fetch 经注入替身）
```
