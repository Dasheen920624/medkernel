# 全系统产品信息架构门禁验收报告

> 日期：2026-06-12  
> 阶段：P2 全系统产品门禁  
> 结论：通过，可在提交后进入 P3 演练前发布准备；本报告生成时尚未对 `193.112.107.134` 执行外向操作。
> P3 追加：`2026-06-12` 已完成 134 首轮现场核验、前置备份和隔离恢复验证，最新状态见 [P3 演练前发布准备验收报告](p3-release-prep-acceptance.md)。
> P4 追加：`2026-06-12` 已在最终备份恢复通过后完成清库、V1-V116 从零迁移与全新候选部署，最新状态见 [P4 首次全新部署验收报告](p4-first-fresh-deployment-acceptance.md)。
> 新增硬约束：`193.112.107.134` 是后续主平台知识管理服务器；任何平台知识生成必须等全功能完美验收、结构冻结和第一阶段正式验收通过后才能开始，避免生成知识后反复改结构。
> 追加存储门禁：正式文献资料不得落本地 `tmp`。系统配置页可查看维护“平台知识文献资料库根地址”；初始状态必须为“未配置”，管理员可维护 COS/S3/OSS/OBS/MinIO/HTTPS 网关等受管 URI，但产品不得预设厂商地址。

## 1. 范围与裁决

本阶段已完成前端路由、页面、后端菜单、控制器、批量任务、第三方接口和专家能力的统一盘点，并对每项能力给出唯一裁决：保留、重命名、移位、合并、专家化、接口化或移除。

权威证据：

- [全系统功能目录与唯一产品裁决](product-function-catalog.md)
- [全系统产品信息架构裁决矩阵](product-ia-matrix.md)
- [14 个客户职责角色任务旅程与菜单快照](product-role-journeys.md)

最终客户主域为：工作台、机构治理、知识配置、临床协同、质量与运营。高级工具默认隐藏在所属主域专家模式内；仅服务外部系统的能力保留接口契约，不进入客户菜单。

## 2. 产品门禁结论

| 门禁 | 结论 | 证据 |
|---|---|---|
| 前后端菜单与路由同源 | 通过 | 后端 `DefaultPermissionPolicyTest` 锁定 14 角色菜单；前端 `routes/menu/productRoleJourneys` 测试锁定同一目录 |
| 14 角色默认工作台与主任务旅程 | 通过 | `frontend/e2e/product-role-journeys.spec.ts` 覆盖 1440、1366、768、390 四种视口 |
| 全页面可打开性 E2E | 通过 | `frontend/e2e/all-done-route-smoke.spec.ts` 遍历真实角色全部授权页面 |
| 全中文与技术对象清理 | 通过 | `customerLanguageGate.test.ts`、`npm run verify`、真实性门禁全仓扫描 |
| 六态与桌面/移动端 | 通过 | 页面测试、E2E 移动端用例、Browser 登录页冒烟 |
| 平台知识文献资料库根地址 | 通过 | 配置中心键 `medkernel.knowledge.literature.material-root-uri` 初始为空并显示“未配置”；V116 五方言迁移允许 `PLATFORM_SEED`；只能由系统配置页维护；兼容 COS/S3/OSS/OBS/MinIO/HTTPS 网关等受管 URI；禁止厂商默认值、`tmp`、`file://`、本机临时目录和非加密 HTTP |
| P0/P1 与阻断主任务 P2 | 清零 | 当前仅保留非阻断环境类延期项，见 `DEFER-023` |

## 3. 八视角复核

| 视角 | 复核结论 |
|---|---|
| 世界级医疗产品体验 | 五大主域按长期客户任务组织，角色工作台只暴露一个主动作和不超过三个高频入口；阶段名、技术名和重复入口已收敛。 |
| 临床安全 | 临床页面继续保持 AI 标识、医师确认、高危双签和禁止自动开嘱边界；无模型时 B0 主链路可运行。 |
| 机构管理 | 服务机构、人员与账号、身份来源、实施与验收归入机构治理，机构类型与稳定组织层级分离。 |
| 人员访问 | 自然人、任职、账号、身份来源、职责角色和组织范围在页面和权限上分离；身份来源页收紧为人员治理角色可管理。 |
| 安全合规 | 审计与证据、安全与配置、运行保障进入质量与运营；导出、数据权限、脱敏和系统配置仍保留审计证据。 |
| 数据治理 | 平台治理空间 `t-1` 继续作为平台医疗知识唯一主源；机构派生、换基线、恢复标准沿用不可变血缘和差异审阅。 |
| 架构运维 | 专家能力不污染客户菜单；系统接入、运行保障、国产化核验和诊断工具按专业角色和专家模式承载。 |
| 测试质量 | 后端全量、前端 verify/build、E2E、T-GATE、产品目录门禁均有新鲜证据；本机 Docker 已恢复并完成 Testcontainers PostgreSQL/Oracle/H2 迁移烟测。 |

## 4. 验证证据

| 类别 | 命令 | 结果 |
|---|---|---|
| 后端全量 | `mvn -q test` | Surefire 340 份报告，2214 tests，0 failures，0 errors，0 skipped；退出码 0，末尾有测试 JVM 关闭期调度线程连接噪声 |
| 五方言静态合同 | `MigrationBaselineContractTest` | 102 tests，0 failures，0 errors，覆盖 h2/postgres/oracle/dm/kingbase 迁移静态合同 |
| 容器迁移烟测 | `mvn -q -Dtest=FlywayMultiDialectSmokeTest test` | 通过；Docker Desktop 可用，Testcontainers 真实 PostgreSQL、Oracle、H2 均从空库迁移到 V116，并验证重复迁移幂等 |
| 前端完整验证 | `npm run verify` | lint、stylelint、规则测试、format、typecheck、Vitest 89 files / 649 tests 全通过 |
| 前端构建 | `npm run build` | TypeScript + Vite production build 通过，3408 modules transformed |
| 全量 E2E | `E2E_API_BASE_URL=http://localhost:18080/medkernel/api/v1 E2E_BASE_URL=http://127.0.0.1:5173 MEDKERNEL_API_PROXY_TARGET=http://localhost:18080 npx playwright test --project=chromium` | 31 passed，覆盖 14 角色、全授权路由、桌面/平板/移动旅程 |
| T-GATE 自测 | `node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/git-scan-files.test.mjs scripts/migration-convention-guard.test.mjs` | 40 tests passed |
| 真实性门禁 | `node scripts/authenticity-guard.mjs --mode=all` | 扫描 1582 个文件，0 阻断 |
| 配置边界门禁 | `node scripts/config-boundary-guard.mjs --mode=all` | 扫描 1492 个文件，0 阻断 |
| 迁移规约门禁 | `node scripts/migration-convention-guard.mjs --mode=all` | 扫描 580 个文件，0 阻断 |
| 中文注释门禁 | `scripts/check-comment-zh.sh` | 0 fail，0 warn |
| 产品目录门禁 | `node scripts/audit/export-product-capabilities.mjs --check` | 退出码 0，目录与裁决一致 |
| 知识文献资料配置后端 | `mvn -q -f medkernel-backend/pom.xml -Dtest=SystemConfigControllerTest#knowledgeLiteratureMaterialRootUriRequiresManagedStorageConfiguration test` | 通过；H2 空库迁移到 V116，配置中心平台种子初始为空，`tmp/file/http` 类非法地址被拒绝，S3 等受管 URI 可高风险维护 |
| 知识文献资料配置前端 | `npm test -- src/pages/compliance/SecurityBaseline.test.tsx` | 7 tests passed；系统配置页显示“平台知识文献资料”，并可编辑“平台知识文献资料库根地址” |
| 空白检查 | `git diff --check` | 退出码 0 |
| 本地浏览器冒烟 | Browser 打开 `http://127.0.0.1:5173/login` | 标题为“集团医疗智能中枢 · MedKernel”，登录页显示机构用户、平台治理、所在机构、统一身份入口和中文安全提示 |

## 5. 未冒领项

- 本机 Docker 已恢复并完成 Testcontainers PostgreSQL/Oracle/H2 迁移烟测；但 P3/P4 清库发布仍必须在目标环境提交真实备份、恢复和从零迁移证据，不能用本机容器结果替代 134 现场验收。
- 本报告在 P2 生成时只放行“进入 P3 演练前发布准备”。后续 P3/P4 已按串行门禁完成核验、备份恢复、清库和首次全新部署；当前边界以 [P4 报告](p4-first-fresh-deployment-acceptance.md) 为准，仍不放行平台知识生成。
- wave2 模型网关、AI 知识工厂、15 领域门面和 GA 总验收未开始，不得写成已完成。
- 平台知识资产生成未开始；所有生成知识必须在当前指定的 `193.112.107.134` 主平台知识管理服务器上完成，并且必须排在功能验收、清库双演练、结构冻结和第一阶段正式验收之后。正式文献资料库根地址不硬编码 134 或存储厂商，迁移主机或更换存储时通过系统配置页调整；正式文献资料不得使用 `tmp`。

## 6. 下一步

1. 当前长目标只在本会话内继续；上下文压缩后读取 `docs/_HANDOFF.md` 与最新阶段报告，不创建或切换新线程。
2. P4 从全新 `/bootstrap` 状态走真实前台首轮演练，发现不合理功能即按全新产品标准修正。
3. P5 从再次清库后的干净基线完整重演，完成第一阶段正式验收并冻结 134 平台知识结构。
4. P6 只在 134 上生成平台首发知识资产，并在生成前确认 `medkernel.knowledge.literature.material-root-uri` 已通过系统配置页指向正式受管文献资料库根地址。
