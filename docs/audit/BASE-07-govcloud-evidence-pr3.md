# BASE-07 国产化 smoke 证据包门禁 PR3

> 日期：2026-06-01
> 分支：`codex/base-07-runtime-finalization`
> 范围：BASE-07 第三批。只补国产化自托管验收证据门禁，不宣称达梦 / 人大金仓真实连通已在本机完成；按当前阶段口径，真实国产化运行环境登记为 `DEFER-001`，不阻塞 PostgreSQL / Oracle 范围和 BASE-08。

## 目标

BASE-07 原 FR-5 / AC-3 曾要求国产化 profile 在达梦 / 人大金仓真实环境下通过，并包含国密 SM2/SM3/SM4 smoke。按当前项目节奏修正，BASE-07 当前阶段只保障 PostgreSQL + Oracle，达梦 / 人大金仓真实环境进入 [待处理问题清单](deferred-issues.md) `DEFER-001`。PR2 已提供 fail-closed 的 `govcloud-smoke.sh`，本批把内网执行结果固化成可归档的证据包，避免后续 AI 或执行人用“脚本跑过”替代真实证据。

## 改动

- `deploy/docker/scripts/govcloud-smoke.sh` 新增 `MEDKERNEL_GOV_EVIDENCE_DIR`，未设置时默认写入仓库根目录 `/runtime/govcloud-smoke/`。
- 证据文件名为 `govcloud-smoke-<UTC时间>.txt`，包含方言、驱动类、JDBC jar 文件名、JDBC jar SHA-256、国密 smoke、数据库 smoke、通过 / 失败状态和证据文件路径。
- 脚本仍然缺任一真实连接条件即失败，不写入口令，不把“未连接”伪装为通过。
- `validate-deployment-assets.sh` 与 `RuntimeConfigurationContractTest` 增加证据包合同，阻断脚本退化为无证据 smoke。

## 已验证

- TDD 红灯：新增 `RuntimeConfigurationContractTest.govcloudSmokeScriptFailsClosedWithoutRealDomesticConnection` 对证据字段的断言后，`mvn -B -q -Dtest=RuntimeConfigurationContractTest test` 失败，缺 `MEDKERNEL_GOV_EVIDENCE_DIR` 等证据口径。
- TDD 绿灯：补脚本、部署资产校验与 README 后，`mvn -B -q -Dtest=RuntimeConfigurationContractTest test` 通过。
- 部署资产合同：`bash deploy/docker/tests/validate-deployment-assets.sh` 通过。
- 失败场景实跑：未提供 `MEDKERNEL_GOV_DATABASE_DIALECT` 时，`govcloud-smoke.sh` 退出失败并生成含 `status=FAIL` 的证据文件。
- 后端全量：`mvn -B -q test` 通过。
- 前端全量：`npm test`、`npm run typecheck`、`npm run build`、`npm run lint`、`npm run format:check` 通过。
- T-GATE：`node scripts/authenticity-guard.mjs --mode=inventory` 与 `node --test scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/authenticity-guard.test.mjs` 通过。
- 空白检查：`git diff --check` 通过。

## 诚实边界

本机 Docker 镜像与仓库内未包含达梦 / 人大金仓闭源 JDBC 驱动，也没有真实国产库实例；因此不得宣称国产化真实环境已通过。按当前阶段修正，BASE-07 当前范围为 PostgreSQL + Oracle，可继续收口；达梦 / 人大金仓 AC 进入 `DEFER-001`，由 D6 `DOMCHK-01` 与 GA `QA-02` / `INFRA-10` 最终适配阶段提交真实连接证据后关闭。
