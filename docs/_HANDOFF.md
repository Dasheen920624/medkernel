# 会话接力

> 开工先读本文件。这里只保留当前可执行事实；产品范围见
> [PRODUCT_SCOPE](PRODUCT_SCOPE.md)，历史过程使用 Git 追溯，不在当前工作树保留第二套事实源。

## 当前主线

- 最新远端主线：`origin/main` 与本地 `main` 均为
  `1561ba6bef8777dcef76432696f43de4277fdd3f`
  （`完善全角色上线演练与134复演闭环 (#653)`）。
- 当前本地工作分支：`codex/final-handoff-product-optimization`，从 `1561ba6b` 创建；
  本阶段只做本地提交，不推送远程，不直接改写远端 `main`。
- 第一百五十批本地收尾：继续按全角色真实前台、真实服务链路和上线门禁推进；本批不是菜单、
  文案或片面页面优化，也不是完整 S0-S40、完整全角色全功能、完整上线验收或 134 清库部署收口，而是在前序
  S2/S4、S6 与 S13 机构生效版本证据切片基础上，补齐 S5 中 `VALUE_SET`、`FORMULA`、`ACTION_CARD`
  当前机构生效版本 runtime 消费的代表性证据切片。`launchCoverageEvidence` 现在只有在
  `cdss-runtime-declarative-assets-codes` 附件同时证明 `scenarios:S5`、`productLayers:CLINICAL_EXECUTION`、
  `serviceCombinations:CLINICAL_RUNTIME`、`versionedAssets:VALUE_SET/FORMULA/ACTION_CARD`，
  且真实附件证明三类资产由前台创建、本轮三类资产先激活成规则发布验证 runtime、发布验证快照绑定该 runtime、
  规则发布后从当前医院 runtime-candidates 解析 RULE `av-*` 统一资产版本、最终 runtime 同时包含 RULE 与三类资产、
  ACTIVE 临床快照绑定最终 runtime、临床用户从真实前台触发推荐评估、推荐详情解释返回同一 runtime 下三类资产物化证据时，
  才声明 S5 与三类声明式运行资产覆盖。
- 第一百五十批真实链路说明：新增目标真实 E2E
  `cdss-runtime-declarative-assets.spec.ts`。医疗引擎运营员先在真实前台 `/authoring/assets`
  创建本轮 `VALUE_SET.CDSS.RUNTIME.<suffix>`、`FORMULA.CDSS.RUNTIME.<suffix>` 与
  `ACTION_CARD.CDSS.RUNTIME.<suffix>` 草稿，值集包含 `J01GB03`，公式绑定受控 BMI 函数与
  `extensions.local.frontdeskContext.heightCm/weightKg`，提示卡为高风险强提醒且
  `requiresPhysicianConfirmation=true`，只生成建议、不自动开嘱。随后 E2E 激活本地上线演练医院 runtime，
  保留 13 类平台 baseline 资产并加入三类本轮资产；临床用户用该 runtime 创建规则发布验证 ACTIVE 快照；
  医疗引擎运营员创建引用三类资产的 DSL 规则，执行发布验证用例，并按 REVIEWED、SHADOW、CANARY、FULL 真实治理推进。
  发布后 E2E 通过
  `/engine/releases/hospitals/{hospitalId}/runtime-candidates?assetType=RULE&keyword=<ruleCode>`
  读取当前医院 RULE runtime 候选，显式要求 `versionId` 为 `av-*`，再激活最终 runtime，断言当前机构生效版本同时包含
  RULE、`VALUE_SET`、`FORMULA`、`ACTION_CARD` 的 `versionId/versionNo/contentHash`。
- 第一百五十批前台与 E2E 修复说明：目标 E2E 曾先后暴露四个真实前台 / 取证红点。第一，默认业务视图中
  `ContextSnapshotSelector` 的按钮可访问名只含建立时间，真实 E2E 无法按本轮 `snapshotId` 精确选择；
  已改为默认可访问名包含 `snapshotId + 建立时间`，但可见文本仍不暴露患者、就诊和快照原始 ID，避免把敏感技术身份直接放到普通业务列表。
  第二，S5 E2E 的 `textField()` 路径曾写成 `resources.encounters.0.encounterId`，但本地取值器只支持 bracket
  数组索引；已改为 `resources.encounters[0].encounterId`。第三，推荐评估响应中的 `cards` 顺序不稳定，不能用
  `cards[0]` 当作本轮推荐卡取证；E2E 现在精确等待本轮 evaluate 请求体，读取
  `/engine/recommendations/triggers/{triggerId}/diagnose` 的 `relatedEntities.cards`，再逐张读取推荐详情，唯一筛出
  本轮 S5 runtime 物化推荐卡，并在 `e2eAuthCredentialContract` 中禁止 `cards.0.cardId` 和直接
  `cards[0]` 最终取证。第四，提醒推荐默认业务表格可按 `cardId` 搜索过滤，但不会在默认列中显示 raw `cardId`；
  `CdssFatigue.test.tsx` 也加了“可按卡片身份过滤但默认业务表格不暴露身份”的护栏。
- 第一百五十批边界说明：本批 coverage 只是 S5 的“真实前台创建三类声明式运行资产 + 当前机构生效版本 RULE 与
  `VALUE_SET/FORMULA/ACTION_CARD` runtime 消费 + 推荐解释物化证据”代表性证据切片；不声明完整 S5、
  完整 CDSS 产品族、13 类 runtime 资产逐类业务消费者、完整 S0-S40、完整全医疗专业领域、完整全知识、
  全标准患者资源、全角色全功能真实操作、完整上线验收或 134 清库复演。后续不能把本批三类资产证据外推为
  13 类资产或全量上线完成。
- 第一百五十批验证证据：目标真实 E2E 在本地 18080 dev/H2 后端上通过：
  `E2E_API_BASE_URL=http://localhost:18080/medkernel/api/v1 MEDKERNEL_API_PROXY_TARGET=http://localhost:18080 E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-cdss-runtime-assets npm --prefix frontend run e2e -- --project=chromium cdss-runtime-declarative-assets.spec.ts`
  通过，仓库外 `results.json` 为 `PASSED`（1 expected，0 unexpected，0 flaky，duration=21851ms）。
  最新附件记录 `scenarioCodes=[S5]`、`versionedAssets=[VALUE_SET,FORMULA,ACTION_CARD]`，
  本轮资产为 `VALUE_SET.CDSS.RUNTIME.MRA4UOY0-0/versionId=av-01KWXCDYV3NXSWQCVSNJMQVZ9F/versionNo=V1/
  contentHash=7f0f21f0ee7e3e710bf902c90555f34e139fa4b6df03a4b77b631ead0c645125`、
  `FORMULA.CDSS.RUNTIME.MRA4UOY0-0/versionId=av-01KWXCE0FS04AMDDD09SN8MBT2/versionNo=V1/
  contentHash=5bed98e2e81b8730612915edd9023e6f4a9ab9a0c7d783ffb0b25b9e429050eb`、
  `ACTION_CARD.CDSS.RUNTIME.MRA4UOY0-0/versionId=av-01KWXCE3WVS1KP86G31ZZEPFYB/versionNo=V1/
  contentHash=ea6c2ecfc439c7b00bb2a1abb62c7febf3d2968e91b1de7a9df06dc70e16c6ff`；
  RULE runtime 候选为 `RULE.CDSS.RUNTIME.MRA4UOY0-0/versionId=av-01KWXCE9J9AQ2NYMF1KNJQKFTJ/versionNo=V1/
  contentHash=0ad489607b4ad666f93fa6257cd5268e37352dd85ae8fc095d703f3e7a2a0e93/sourceLayer=HOSPITAL`；
  三类声明式资产先激活的规则发布验证 runtime 为 `releaseId=runtime-01KWXCE48CR741FX68Q30HYZFK/revisionNo=12/
  manifestSha256=76d19a518afa208cb58507e02e4bc98272f5ecd2af81272e630cc3b83a92ccfd`；最终 runtime 为
  `releaseId=runtime-01KWXCE9N42HAPH945QC4DBGPE/revisionNo=13/
  manifestSha256=2dd299a634a3a65ad8f4d963a2534719edfe2b752b2cafb6e16c243c2cacf96c`；
  临床触发 `triggerId=rt-b8bef2e6-4610-4d6d-bd8c-e0dbe95d43a7/
  contextSnapshotId=ctx-de634340-3a0b-43e9-9d53-a69bdd4c770f/cardId=rc-91f889e7-8d83-420d-bfeb-d14ceff7647a/
  relatedCardIds=[rc-91f889e7-8d83-420d-bfeb-d14ceff7647a,rc-0090c863-3f3c-44b3-8e08-a54b3bc8d69d]`，
  推荐解释 `runtimeReleaseId` 与最终 runtime 一致，`runtimeAssetEvidence` 含 VALUE_SET `expandedCount=1`、
  FORMULA `runtimeFunction=BMI`、ACTION_CARD `resolvedActionCardVersion=V1/resolvedActionCardHash=<同上>/
  requiresPhysicianConfirmation=true`。
  其他新鲜门禁：`npm --prefix frontend run test -- ContextSnapshotSelector QcEvalSets` 通过（13 tests）；
  `npm --prefix frontend run test -- CdssFatigue e2eAuthCredentialContract -t "filters by card identifier|requires CDSS declarative runtime asset"`
  通过（2 selected，45 skipped）；`npm --prefix frontend run test -- e2eLaunchCoverageEvidence -t "VALUE_SET/FORMULA/ACTION_CARD"`
  通过；`npm --prefix frontend run test -- hooks -t "converts frontdesk current medications"` 通过；
  `npm --prefix frontend run test -- Mpi -t "creates a current clinical context"` 通过；
  `npm --prefix frontend run typecheck -- --pretty false` 通过；
  `mvn -f medkernel-backend/pom.xml -Dtest=RuleEngineServiceTest#peerReviewRunsTestCasesWithSnapshotRuntimeReleaseForDeclarativeAssets,RuleDslAssetMaterializerTest,RuleDslEvaluatorTest test`
  通过（83 tests）；`node --test scripts/release/launch-coverage-audit.test.mjs` 通过（6 tests）；
  `git diff --check` 通过。本地 18080 演练后端服务 PID `44154` 当前仍在运行，未停止。
- 第一百五十批后续主线：本批仍未发布到 134，未实际执行 134 清库重新部署，也不能把 S5 三类声明式资产代表性证据切片等同于完整上线验收。
  后续继续补真实覆盖缺口：完整 S0-S40、完整语义族 / 专业域 / 全知识、13 类 runtime 资产逐类业务消费者、
  全标准患者资源、多第三方系统族断连降级、真实备份 / 隔离恢复 / 重启恢复，以及 134 清库部署复演；继续坚持全角色真实前台和真实服务链路，
  发现红点先复现、定根因，再按上线级标准修复，不做片面优化。
- 第一百四十九批本地收尾：继续按全角色真实前台、真实服务链路和上线门禁推进；本批不是菜单、
  文案或片面页面优化，也不是完整 S0-S40、完整全角色全功能、完整上线验收或 134 清库部署收口，而是在前序
  S2/S4 与 S13 机构生效版本证据切片基础上，补齐 S6 专病路径中 `ORDER_SET` 当前机构生效版本 runtime
  消费的代表性证据切片。`launchCoverageEvidence` 现在只有在 `pathway-lifecycle-scenario-codes`
  附件同时证明 `scenarios:S6`、`productLayers:CLINICAL_EXECUTION`、`serviceCombinations:SPECIAL_DISEASE_PATHWAY`、
  `versionedAssets:ORDER_SET`、十阶段专病路径里程碑、完整前台 / API 生命周期证据，以及
  `orderSetRuntimeConsumer` 证明本轮 `ORDER_SET` 资产存在于同一医院当前 runtime、患者路径绑定同一
  `runtimeReleaseId`、`ASSESS -> FOLLOWUP` 推进响应在 `decisionEvidence` 中返回
  `pathway.currentNodeType=ORDER_SET`、`pathway.orderSetRef/version/hash/items` 和
  `pathway.orderSetRequiresPhysicianConfirmation=true` 时，才声明 S6 与 `ORDER_SET` 覆盖。
- 第一百四十九批真实链路说明：`pathway-lifecycle-frontdesk.spec.ts` 现在由医疗引擎运营员在真实前台
  `/authoring/assets` 创建本轮 `ORDER_SET.S6.COPD.<suffix>` 医嘱套餐草稿，套餐正文包含至少一条复查项目、
  `requiresPhysicianConfirmation=true`，仅生成建议、不自动开嘱；随后创建引用该 `ORDER_SET` 的 S6 慢阻肺专病路径，
  受控配置回填到节点画布并断言 `ASSESS` 节点的医嘱套餐引用就是本轮资产。激活本地上线演练医院 runtime 时，
  E2E 不再只带 `PATHWAY` 或空 `activeAssets`，而是保留 13 类平台 baseline 资产，并同时加入本轮
  `PATHWAY` 与 `ORDER_SET`；激活后回读 `/runtime-releases/current` 完整清单，断言本轮
  `ORDER_SET` 的 `versionId/versionNo/contentHash` 均在当前机构生效版本中。
- 第一百四十九批前台与 E2E 说明：激活 runtime 后，临床用户先回到真实患者 360 更新 ACTIVE 上下文，确保新的
  `entrySnapshot` 绑定最新 `runtimeReleaseId`；医疗引擎运营员再用这个激活后快照对已保存路径执行真实服务仿真，
  避免旧 snapshot 绑定旧 runtime 而误报缺少 `ORDER_SET`。随后临床用户按真实前台办理患者入径，先标准推进
  `SCREEN -> ASSESS`，在 `ASSESS` 节点通过真实 API 登记 `HOLD` 变异并暂停在医嘱套餐节点，再通过真实前台完成
  `ASSESS -> FOLLOWUP`，此时后端 `PathwayProgressor.recordOrderSetEvidence()` 从当前机构生效版本解析本轮
  `ORDER_SET` 正文并返回 `orderSetVersion/hash/items/requiresPhysicianConfirmation`；最后完成随访终点并回读时钟、
  变异和随访接续计划。`e2eAuthCredentialContract` 增加源码护栏，要求 S6 E2E 保留本轮 `ORDER_SET`
  创建、runtime 激活、消费证据与关键 `pathway.orderSet*` 字段。
- 第一百四十九批边界说明：本批 coverage 只是 S6 的“专病路径真实前台 + 当前机构生效版本 `ORDER_SET`
  runtime 消费 + 变异 / 随访接续”代表性证据切片；不声明完整 S6、完整临床路径产品族、13 类 runtime
  资产逐类业务消费者、完整 S0-S40、完整全医疗专业领域、完整全知识、全标准患者资源、全角色全功能真实操作、
  完整上线验收或 134 清库复演。后续不能把本批 `ORDER_SET` 证据外推为 13 类资产或全量上线完成。
- 第一百四十九批验证证据：目标真实 E2E 在本地 18080 dev/H2 后端上通过：
  `E2E_API_BASE_URL=http://localhost:18080/medkernel/api/v1 MEDKERNEL_API_PROXY_TARGET=http://localhost:18080 E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-pathway-orderset npm --prefix frontend run e2e -- --project=chromium pathway-lifecycle-frontdesk.spec.ts`
  通过，仓库外 `results.json` 为 `PASSED`（1 expected，0 unexpected，0 flaky，duration=22679ms）。
  最新附件记录 `scenarioCodes=[S6]`、`versionedAssets=[ORDER_SET]`、
  `orderSet.assetIdentity=ORDER_SET.S6.COPD.MR9XVPBO/versionId=av-01KWX18JRE6XXMH2VKYB6B1SG9/versionNo=V1/
  contentHash=191733edfb0104356e74257dc686d5fd91d98ad3a4c0ee8a2cade984252e3599`、
  `runtime.releaseId=runtime-01KWX18PEKTA00QC88F28PZ27X/revisionNo=13/
  manifestSha256=857d019b062e491a610bfed7d84cad01787a9cbd67ac4437b81e55fc91135393`，
  患者路径读回 `patientPathwayId=pp-99dfdc1d-954b-4738-bf49-0b10d4eed3b6/
  runtimeReleaseId=runtime-01KWX18PEKTA00QC88F28PZ27X`，推进证据为
  `previousNodeCode=ASSESS/nextNodeCode=FOLLOWUP/status=NODE_EXECUTING`，`decisionEvidence`
  含 `pathway.currentNodeType=ORDER_SET`、`pathway.orderSetRef=ORDER_SET.S6.COPD.MR9XVPBO`、
  `pathway.orderSetVersion=V1`、同一 `pathway.orderSetHash`、`pathway.orderSetItemCount=1`、
  `pathway.orderSetRequiresPhysicianConfirmation=true` 和一条 `LOCAL-E2E` 血气复查项目。其他新鲜门禁：
  `npm --prefix frontend run test -- e2eLaunchCoverageEvidence e2eAuthCredentialContract` 通过（80 tests）；
  `npm --prefix frontend run typecheck -- --pretty false` 通过；`node --test scripts/release/launch-coverage-audit.test.mjs`
  通过（6 tests）；`git diff --check` 通过。只读评审子代理因当前线程子代理池已满未能启动，已改由本地按同一清单自审；
  自审发现并修复真实 E2E 附件漏列新增 `ORDER_SET` 消费阶段的问题后重新通过上述命令。本地 18080 演练后端服务
  PID `89743` 当前仍在运行，未停止。
- 第一百四十九批后续主线：本批仍未发布到 134，未实际执行 134 清库重新部署，也不能把 S6 `ORDER_SET`
  代表性证据切片等同于完整上线验收。后续继续补真实覆盖缺口：完整 S0-S40、完整语义族 / 专业域 / 全知识、
  13 类 runtime 资产逐类业务消费者、全标准患者资源、多第三方系统族断连降级、真实备份 / 隔离恢复 / 重启恢复，
  以及 134 清库部署复演；继续坚持全角色真实前台和真实服务链路，发现红点先复现、定根因，再按上线级标准修复，
  不做片面优化。
- 第一百四十八批本地收尾：继续按全角色真实前台、真实服务链路和上线门禁推进；本批不是菜单、
  文案或片面页面优化，也不是完整 S0-S40、完整全角色全功能、完整上线验收或 134 清库部署收口，而是在前序
  S13 机构生效版本证据切片基础上，补齐 S2/S4 的“系统接入 + 字典映射 + 当前机构生效版本 runtime 消费 +
  真实入站归一”代表性证据切片。`launchCoverageEvidence` 现在只有在 `s2-s4-runtime-mapping-codes`
  附件同时证明 `scenarios:S2/S4`、数据互操作层与医疗资产层、术语版本化资产、管理前台与 API 事件、第三方接口与临床运行组合，
  并且真实附件包含前台适配器字段映射、前台回调通道签名预览、坏签名主数据同步拒绝、签名主数据同步登记院内术语、
  前台标准术语登记、候选生成与人工确认、不可变术语资产版本、`/config/releases` 真实机构生效版本激活、
  坏签名入站拒绝、真实 Webhook 入站成功、入站按当前 `runtimeReleaseId` 归一、第三方 runtime contract
  读回一致时，才声明 `scenarios:S2` 和 `scenarios:S4`。
- 第一百四十八批真实链路说明：后端 `EffectiveTermMappingResolver` 不再只按传入
  `runtimeReleaseId` 盲查术语快照；解析和覆盖率统计都会先校验该机构生效版本属于当前请求医院，并通过
  `OrgHierarchyRepository.findResolutionAncestorsAndSelf(...)` 使用当前组织树中的租户 / 区域 / 医院版本归属
  `orgPath` 解析有效术语映射，缺少可用版本归属范围时直接返回 `ORG_SCOPE_DENIED`，避免空组织范围进入 SQL。
  `TermMappingSnapshotRepository` / `TermMappingRepository` 改为按组织路径集合、区域路径和医院路径消费当前 runtime
  中的术语资产快照；入站 Webhook 字段映射使用当前机构生效版本完成术语归一，标准码、院内码、`mappingId`、
  `standardTermId` 和 `runtimeReleaseId` 均写入归一结果。
- 第一百四十八批前台与 E2E 说明：新增 `s2-s4-terminology-integration-rehearsal.spec.ts`。真实前台中，
  平台管理员进入 `/adapter/hub` 创建 LIS Webhook 适配器，配置 `/patientId` 到 `/patient/mpi` 和
  `/labCode` 到 `/observations/0` 的字段映射，第二条字段映射绑定 `LOINC` 和检验分类；随后创建回调通道并生成签名预览。
  外部签名主数据同步先用坏签名验证拒绝，再用真实 HMAC-SHA256 登记院内术语。医疗引擎运营员进入
  `/terminology/mapping` 登记标准术语、生成候选、人工确认映射并生成不可变术语资产版本；再进入
  `/config/releases` 选择本地上线演练医院，显式确认 13 类平台标准资产已作为 `versionId=null` 平台沿用选择保留，
  勾选本轮本院术语资产，完成发布影响评估后点击“生成新机构生效版本”。E2E 捕获真实激活请求，断言
  `activeAssets` 同时包含 13 类平台沿用资产和本轮术语资产；随后后端当前 runtime 与第三方 runtime contract
  均读回同一术语资产，真实入站 Webhook 先坏签名拒绝，再签名成功并产生 `normalizedCodeCount=1` 的标准临床事件。
- 第一百四十八批边界说明：本批 coverage 只是 S2/S4 的“真实系统接入 + 字段映射 + 术语映射 +
  当前机构生效版本消费 + 入站归一”证据切片；不声明完整第三方系统全景、所有院内系统族、完整字段目录业务消费、
  全标准患者资源、完整 S0-S40、完整全医疗专业领域、全角色全功能真实操作、完整上线验收或 134 清库复演。
  本地演练会持续在本地 H2 数据中追加 S2/S4 适配器、回调通道、术语和 runtime 修订，用于后续重复演练；下一棒不要把本批证据外推为全量上线完成。
- 第一百四十八批验证证据：目标真实 E2E 在本地 18080 dev/H2 后端上通过：
  `E2E_API_BASE_URL=http://localhost:18080/medkernel/api/v1 MEDKERNEL_API_PROXY_TARGET=http://localhost:18080 E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-s2-s4 npm --prefix frontend run e2e -- --project=chromium s2-s4-terminology-integration-rehearsal.spec.ts`
  重新通过，仓库外 `playwright-results.json` 为 1 expected、0 unexpected、0 flaky，duration=19070.822ms。
  最新附件记录 `scenarioCodes=[S2,S4]`、`adapterId=s2s4-lis-mr9wi033`、
  `terminology.assetIdentity=TERM.LAB.S2S4.MR9WI033/versionId=av-01KWWZ27F8AJCYGG9K5PJ6YXVC/versionNo=V1`、
  `runtime.releaseId=runtime-01KWWZ28YAEV8RKH1YR10MC8Z6/revisionNo=5/
  manifestSha256=56c4e42ec8e076d03ed4df0211ce5ed73a5f9330dd23dd7003dbcc3dbe2d6532`；
  入站归一读回 `standardCode=S2S4-MR9WI033/codeSystem=LOINC/
  localCode=LIS-HGB-MR9WI033/runtimeReleaseId=runtime-01KWWZ28YAEV8RKH1YR10MC8Z6/
  mappingId=5/standardTermId=5/mappedVersion=V1`，激活请求中的 13 类平台资产均为 `versionId=null`。
  其他新鲜门禁：`npm --prefix frontend run test -- e2eAuthCredentialContract -t "requires S2/S4"`
  通过（4 tests）；`npm --prefix frontend run test -- e2eLaunchCoverageEvidence -t "S2/S4"`
  通过（7 tests）；S2/S4 相关 TS 命令通过；
  `mvn -f medkernel-backend/pom.xml -Dtest=EffectiveTermMappingRepositoryIntegrationTest,IntegrationServiceTest#inboundWebhookCanonicalPayloadMatchesExternalJsonContract,IntegrationServiceTest#inboundWebhookVerifiesSignatureMapsFieldsAndNormalizesCodesByConfirmedTermMapping test`
  通过（8 tests）；`npm --prefix frontend run test -- AdapterHub ReleaseGovernance hooks e2eLaunchCoverageEvidence e2eAuthCredentialContract`
  通过（250 tests）；`mvn -f medkernel-backend/pom.xml -Dtest=IntegrationControllerSecurityTest,ThirdPartyKnowledgeRuntimeControllerSecurityTest test`
  通过（34 tests）；`npm --prefix frontend run typecheck` 通过；`node --test scripts/release/launch-coverage-audit.test.mjs`
  通过（6 tests）；`git diff --check` 通过。本地 18080 演练后端服务 PID `89743` 当前仍在运行，未停止。
- 第一百四十八批后续主线：本批仍未发布到 134，未实际执行 134 清库重新部署，也不能把 S2/S4 代表性证据切片等同于完整
  上线验收。后续继续补真实覆盖缺口：完整 S0-S40、完整语义族 / 专业域 / 全知识、13 类 runtime
  资产逐类业务消费者、全标准患者资源、多第三方系统族断连降级、真实备份 / 隔离恢复 / 重启恢复，以及 134
  清库部署复演；继续坚持全角色真实前台和真实服务链路，发现红点先复现、定根因，再按上线级标准修复，不做片面优化。
- 第一百四十七批本地收尾：继续按全角色真实前台、真实服务链路和上线门禁推进；本批不是菜单、
  文案或片面页面优化，也不是完整 S13、完整跨环境离线恢复、全角色全功能、完整上线验收或 134
  清库部署收口，而是在第一百四十三至一百四十六批 S13 部分选择、两机构差异化、离线交付预检和平台升级分析基础上，把
  `runtime-release-frontdesk.spec.ts` 继续补成可证明“已验签机构生效版本离线交付文件在当前 runtime 已变化后，经真实前台确认恢复为新的不可变机构生效版本，并被后端 current runtime 与第三方 runtime contract 共同读回”的真实证据切片。`launchCoverageEvidence`
  现在只有在既有 runtime-release coverage 继续满足发布、回滚、部分选择、两机构差异、平台升级分析、离线交付导出下载预检等门槛，并额外证明
  `offlineDeliveryRestoreExecuted/offlineDeliveryRestoreCreatedNewRevision/
  offlineDeliveryRestoreReadbackMatched/offlineDeliveryRestoreRuntimeConsumerMatched` 均为 true，且附件含恢复前 current
  runtime identity、restore 响应、恢复后后端 current runtime identity、恢复后第三方 runtime contract identity
  四方对账时，才额外声明 `scenarios:S13`。
- 第一百四十七批真实链路说明：后端新增受控恢复端点
  `POST /api/v1/engine/releases/hospitals/{hospitalId}/runtime-releases/offline-delivery:restore`，
  权限为 `tenant.override`。`RuntimeReleaseOfflineDeliveryService.restoreImport(...)` 先执行合规证据验签、SM3 文件摘要确认、
  存证元数据必须为 `evidenceType=RUNTIME_RELEASE_OFFLINE_DELIVERY/action=EXPORT/
  subjectType=clinical_runtime_release/subjectId=sourceReleaseId`、`deliveryKind=CLINICAL_RUNTIME_RELEASE`、
  `runtimeMutation=false`、认证租户、来源 release、目标医院和离线 items 重算 `manifestSha256` 校验；随后回查来源
  `ClinicalRuntimeRelease` 账本并确认医院、平台 baseline、修订号和清单摘要与离线快照一致，再调用
  `ClinicalRuntimeReleaseService.restoreOfflineSnapshot(...)` 生成新的 `runtime-*` 不可变机构生效版本，
  `revisionNo=current+1`，完整复制离线快照 items，并把来源 release 记录到 `rollbackFromReleaseId`。
  `ClinicalRuntimeReleaseService` 写账本前也会再次校验来源 release 账本，防止未来其他调用方绕过恢复入口。
  `validate-import` 仍保持只读预检语义，不写 runtime；离线文件仍不是临床运行指针。
- 第一百四十七批前台与 E2E 说明：`/config/releases` 在“当前机构生效版本”卡片新增“恢复为新机构生效版本”按钮；
  只有导出并完成导入预检后才启用。预检完成后即使当前机构生效版本因回滚等动作变化，前台仍保留已验签离线交付文件，
  恢复请求使用离线文件来源 `releaseId/fileDigest` 和当下 `currentRuntime.releaseId` 作为丢失更新保护，成功后展示新 H
  修订、恢复来源和“已追加新的不可变机构生效版本”。目标 E2E 真实前台先导出、下载、预检并断言 runtime 不变；
  再通过真实回滚按钮制造当前 runtime 变化；随后点击恢复按钮，捕获真实 restore API 响应；最后分别读取
  `/runtime-releases/current` 和
  `/engine/integration/knowledge-runtime/runtime-release/current`，断言两者均指向恢复生成的新
  `releaseId/revisionNo/manifestSha256` 且包含本轮 selected 本院候选。
- 第一百四十七批边界说明：本批 coverage 只是 S13 的“离线交付恢复执行 + 新不可变机构生效版本 + 后端与第三方读回一致”
  证据切片；不声明完整 S13、完整跨环境离线恢复、真实备份 / 隔离恢复 / 重启恢复、S2/S4 真实接入与字典映射、完整
  S0-S40、完整语义族 / 专业域 / 全知识、13 类 runtime 资产逐类业务消费者、全角色全功能真实操作或 134
  清库复演。恢复实现当前按同环境可信证据校验认证租户；跨环境导入恢复需要独立信任根、租户映射和现场运维流程，后续不能把本切片外推为已完成。
- 第一百四十七批验证证据：TDD 红灯
  `mvn -f medkernel-backend/pom.xml -Dtest=RuntimeReleaseOfflineDeliveryServiceTest,RuntimeReleaseControllerTest test`
  曾失败于 `RuntimeReleaseOfflineRestoreRequest/Response`、restore controller / service / runtime 写账本方法不存在；实现后相关后端测试转绿。
  只读 code review 子代理发现一个 Important：签名有效的同租户非离线交付存证可伪装为恢复文件；已按评审复核后新增红灯
  `RuntimeReleaseOfflineDeliveryServiceTest` / `ClinicalRuntimeReleaseServiceTest`，失败点覆盖非离线交付存证元数据、
  来源 runtime 账本不存在和账本不匹配；实现后同命令转绿。`launchCoverageEvidence` 也加固为缺少恢复阶段文字证据、
  或 `restoredReleaseId` 复用来源 release 时不得声明 `S13`，红灯后转绿。
  目标真实 E2E 在本地 18080 dev/H2 后端上通过：
  `E2E_API_BASE_URL=http://localhost:18080/medkernel/api/v1 MEDKERNEL_API_PROXY_TARGET=http://localhost:18080 E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-runtime-release-offline-restore npm --prefix frontend run e2e -- --project=chromium runtime-release-frontdesk.spec.ts`
  在安全门修复后重新通过，仓库外 `results.json` 为 `PASSED`（1 expected，0 unexpected，0 flaky，duration=20953ms）；附件记录
  `offlineDelivery.delivery.evidenceId=runtime-offline-1122301e1eec-01KWWBD9RQ2B0M10NT4N8B544R`、
  `fileDigest=sm3:10b11f614a4b6709f9ee242bbb487e9e85490440263871a2153b33f39ded047d`、
  预检前后 runtime identity 均为
  `releaseId=runtime-01KWWBD52XD1DW2Y3XZP86K2ZZ/revisionNo=2/
  manifestSha256=ceb176274735d83470210d89dbaaeca9c7610e10ae61852794ea4cc1bf8fe11b`；
  恢复前先回滚制造 current 变化为
  `releaseId=runtime-01KWWBDAYS3HJ07R9WE0RCPRWY/revisionNo=3/
  manifestSha256=4d7c28a5acbd0ce47cdededa84a1228f8cc55b17a69dee51013671cf2d126414`；
  restore 响应 `status=RESTORED/runtimeMutation=true/sourceReleaseId=runtime-01KWWBD52XD1DW2Y3XZP86K2ZZ/
  restoredReleaseId=runtime-01KWWBDBNSTMFR8QBWJFMVMGNS/restoredRevisionNo=4/
  rollbackFromReleaseId=runtime-01KWWBD52XD1DW2Y3XZP86K2ZZ`；恢复后后端 current runtime 与第三方 runtime
  contract 均读回 `releaseId=runtime-01KWWBDBNSTMFR8QBWJFMVMGNS/revisionNo=4/
  manifestSha256=ceb176274735d83470210d89dbaaeca9c7610e10ae61852794ea4cc1bf8fe11b` 且
  `selectedCandidatePresent=true`。其他新鲜门禁：
  `npm --prefix frontend run test -- ReleaseGovernance hooks e2eLaunchCoverageEvidence e2eAuthCredentialContract`
  通过（213 tests）；`npm --prefix frontend run typecheck` 通过；
  `node --test scripts/release/launch-coverage-audit.test.mjs` 通过（6 tests）；
  `mvn -f medkernel-backend/pom.xml -Dtest=RuntimeReleaseOfflineDeliveryServiceTest,RuntimeReleaseControllerTest,ClinicalRuntimeReleaseServiceTest test`
  通过（39 tests）；`git diff --check` 通过。本地 18080 演练后端服务已在收尾时停止。
- 第一百四十七批后续主线：本批仍未发布到 134，未实际执行 134 清库重新部署，也不能把离线交付恢复执行证据切片等同于完整
  上线验收。后续继续补真实覆盖缺口：S2/S4 真实接入与字典映射、完整 S0-S40、完整语义族 / 专业域 / 全知识、
  13 类 runtime 资产逐类业务消费者、真实备份 / 隔离恢复 / 重启恢复，以及 134 清库部署复演；继续坚持全角色真实前台和真实服务链路，
  发现红点先复现、定根因，再按上线级标准修复，不做片面优化。
- 第一百四十六批本地收尾：继续按全角色真实前台、真实服务链路和上线门禁推进；本批不是菜单、
  文案或片面页面优化，也不是完整 S13、完整离线导入恢复、全角色全功能、完整上线验收或 134
  清库部署收口，而是在第一百四十三至一百四十五批 S13 部分选择、两机构差异化和离线交付预检基础上，把
  `runtime-release-frontdesk.spec.ts` 继续补成可证明“平台权威 baseline 升级前先做差异与冲突分析，且分析不改写当前机构
  runtime”的真实证据切片。`launchCoverageEvidence` 现在只有在 runtime-release coverage 继续满足既有发布、
  回滚、部分选择、两机构差异、离线交付预检等门槛，并额外证明 `platformUpgradeAnalysis` 存在、`analysisDigest`
  为 64 位十六进制、`runtimeMutation=false`、目标平台 baseline 与当前机构 runtime 快照完整、`diffSummary`
  与 diff items 四类计数一致且有真实 changed diff、`conflictCount=0`、每个 diff item 无冲突、分析前后机构
  runtime `releaseId/revisionNo/manifestSha256` 完全不变时，才额外声明 `scenarios:S13`。
- 第一百四十六批真实链路说明：后端新增只读平台升级分析接口
  `GET /api/v1/engine/releases/hospitals/{hospitalId}/platform-upgrade-analysis?targetBaselineReleaseId=...`，
  通过 `RuntimeReleaseQueryService.analyzePlatformUpgrade(...)` 比较目标平台 baseline 与当前医院 runtime，
  返回 `ADDED/MODIFIED/DISABLED/UNCHANGED`、`diffSummary`、`analysisDigest` 和 `runtimeMutation=false`。
  新增 `PlatformUpgradeAnalysisResponse`、`PlatformUpgradeBaselineSnapshot`、
  `PlatformUpgradeRuntimeSnapshot`、`PlatformUpgradeDiffSummary`、`PlatformUpgradeDiffItem`
  作为只读契约。`analysisDigest` 纳入冲突明细字段
  `overrideId/orgPath/overrideMode/resultingSource`，避免只看冲突数量导致确认串用；`ClinicalRuntimeActivateRequest`
  新增 `confirmedPlatformUpgradeDigest`，当机构 runtime 的平台 baseline 变化时，激活必须携带匹配摘要，且只要分析仍有
  conflict，即使摘要匹配也拒绝激活并提示“平台升级分析仍存在机构覆盖冲突”。`ReleaseSimulationService`
  同步把 conflicts 作为不可发布条件。
- 第一百四十六批前台与 E2E 说明：`/config/releases` 新增“平台升级差异与冲突分析”卡片；baseline
  不一致时，未完成分析或分析存在冲突会阻止“生成新机构生效版本”，激活请求只在 baseline 变化时携带
  `confirmedPlatformUpgradeDigest`。`frontend/e2e/support/auth.ts` 修复本地演练医院 runtime
  自愈：当前 runtime ready 但 baseline 不一致时，会先调用平台升级分析并带 digest 激活，不再直接返回；同时导出
  `ensurePlatformRuntimeAssetApiSession(page)`，让 E2E 通过平台 `engine-operator` API 会话创建、发布平台运行资产。
  目标 E2E 现在创建唯一平台升级候选资产，发布新的平台 baseline，真实前台等待并触发平台升级影响分析，断言分析前后当前
  runtime identity 不变，再重新选择本院候选并完成影响评估与激活，捕获激活请求中的 digest 与分析响应一致。
- 第一百四十六批边界说明：本批 coverage 只是 S13 的“平台升级差异与冲突分析 + 冲突安全门 + 分析不改 runtime”
  证据切片；不声明完整 S13、完整跨环境离线导入恢复执行、S2/S4 真实接入与字典映射、完整 S0-S40、完整语义族 /
  专业域 / 全知识、13 类 runtime 资产逐类业务消费者、真实备份 / 隔离恢复 / 重启恢复、全角色全功能真实操作或
  134 清库复演。后续继续按全局全角色真实操作推进，发现红点先复现、定根因，再按上线级标准修复，不做片面优化。
- 第一百四十六批验证证据：目标真实 E2E 在本地 18080 dev/H2 后端上通过：
  `E2E_API_BASE_URL=http://localhost:18080/medkernel/api/v1 MEDKERNEL_API_PROXY_TARGET=http://localhost:18080 E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-runtime-release-platform-upgrade npm --prefix frontend run e2e -- --project=chromium runtime-release-frontdesk.spec.ts`
  通过，仓库外 `results.json` 为 `PASSED`（1 expected，0 unexpected，0 flaky）；附件记录
  `platformUpgradeAnalysis.analysisDigest=a23cd931dac766bafcd550fcc8fd3a53138bee798af084eeb09eac89808aa8de`、
  `runtimeMutation=false`、`diffSummary={ added:1, modified:0, disabled:0, unchanged:15, conflictCount:0 }`、
  `items.length=16`，
  changed item 为 `ACTION_CARD.RUNTIME.UPGRADE.1783359466227-0-platform-upgrade/ADDED`，分析前后 runtime identity 均为
  `releaseId=runtime-01KWW83NW35D5M03JWKV8FPCDV/revisionNo=3/manifestSha256=57f549...`。
  其他新鲜门禁：
  `npm --prefix frontend run test -- ReleaseGovernance hooks e2eLaunchCoverageEvidence e2eAuthCredentialContract`
  通过（207 tests）；`npm --prefix frontend run typecheck` 通过；
  `node --test scripts/release/launch-coverage-audit.test.mjs` 通过（6 tests）；
  `mvn -f medkernel-backend/pom.xml -Dtest=RuntimeReleaseControllerTest,RuntimeReleaseQueryServiceTest,ReleaseSimulationServiceTest test`
  通过（25 tests）；`git diff --check` 通过。本地 18080 演练后端服务已在收尾时停止。
- 第一百四十六批后续主线：本批仍未发布到 134，未实际执行 134 清库重新部署，也不能把平台升级分析证据切片等同于完整
  上线验收。后续继续补真实覆盖缺口：完整离线导入恢复执行、S2/S4 真实接入与字典映射、S0-S40 其余场景、完整语义族 /
  专业域 / 全知识、13 类 runtime 资产逐类业务消费者、真实备份 / 隔离恢复 / 重启恢复，以及 134 清库部署复演；继续坚持
  全角色真实前台和真实服务链路，下一棒不要回滚本批代码，应在此基础上继续全局推进。
- 第一百四十五批本地收尾：继续按全角色真实前台、真实服务链路和上线门禁推进；本批不是菜单、
  文案或片面页面优化，也不是完整 S13、完整离线导入恢复、平台升级冲突分析、全角色全功能或 134
  清库部署收口，而是在第一百四十三 / 一百四十四批部分选择和两机构差异化基础上，把
  `runtime-release-frontdesk.spec.ts` 继续补成可证明“机构生效版本离线交付文件导出 / SM3+SM2
  签名 / 真实文件下载 / 导入预检验签且不改写 runtime”的真实证据切片。`launchCoverageEvidence`
  现在只有在 runtime-release coverage 同时证明 partial selection、两机构差异读回、完整发布回滚、
  13 类 runtime 资产闭包，以及 `offlineDeliveryExported/offlineDeliveryFileDownloaded/
  offlineDeliveryImportPreviewValidated/offlineDeliveryRuntimeUnchanged` 均为 true 时，才额外声明
  `scenarios:S13`；离线交付附件还必须包含 `deliveryKind=CLINICAL_RUNTIME_RELEASE`、
  `signatureAlgorithm=SM3_WITH_SM2`、`runtimeMutation=false`、真实 `fileUri`、SM3 文件摘要、
  下载文件中的完整快照、导入预检 `VALIDATED/signatureValid/manifestMatched/runtimeMutation=false`，
  以及预检前后当前机构生效版本 `releaseId/revisionNo/manifestSha256` 完全不变。
- 第一百四十五批真实链路说明：真实前台在 `/config/releases` 完成本地上线演练医院发布和第二家医院差异化发布后，
  回到第一医院当前机构生效版本，点击“导出离线交付文件”；前端捕获真实
  `/engine/releases/hospitals/{hospitalId}/runtime-releases/offline-delivery` 响应，后端通过
  `RuntimeReleaseOfflineDeliveryService` 从当前机构生效版本账本物化完整快照，交由合规证据服务生成真实证据文件、
  `sm3:<hex>` 文件摘要和 `SM3_WITH_SM2` 签名。随后 E2E 用返回的 `fileUri` 下载真实文件，断言文件包含
  `deliveryKind`、`runtimeMutation=false` 和当前 `releaseId`；再点击“校验离线交付文件”，真实调用
  `/runtime-releases/offline-delivery:validate-import` 验签、对账医院和清单摘要，前台展示“导入预检通过”和
  “不会改写当前机构生效版本”，最后再次回读当前 runtime，断言预检前后 `releaseId/revisionNo/manifestSha256`
  不变。
- 第一百四十五批根因与底层修复：目标真实 E2E 最初红于离线交付导出 409，根因不是前台按钮，而是
  `evidence_snapshot.payload_snapshot` 仍是五方言 `VARCHAR(4000)/VARCHAR2(4000)`，完整机构生效版本快照
  JSON 超过旧上限；已按单一结构源把 `medkernel.schema.json` 中该字段改为 `text`，并由
  `node scripts/db/generate-migrations.mjs` 生成五方言 V1（H2 / Postgres / Kingbase 为 `TEXT`，
  Oracle / 达梦为 `CLOB`）。第二个红点暴露离线交付 `evidenceId` 用完整 `releaseId + ULID`
  拼接后超过 64 字段契约；已在 `RuntimeReleaseOfflineDeliveryService` 改为
  `runtime-offline-<releaseId SHA-256 短摘要>-<ULID>`，完整机构生效版本 ID 仍作为 `subjectId`
  和离线快照字段保存。新增 `MigrationBaselineContractTest` 静态护栏、`H2BaselineMigrationTest`
  超 4000 字符 JSON 真实写入护栏，以及 `RuntimeReleaseOfflineDeliveryServiceTest`
  证据 ID 长度护栏。
- 第一百四十五批边界说明：本批 coverage 只是 S13 的“部分选择 + 两机构差异化 + 离线交付导出签名下载 +
  导入预检不改 runtime + 回滚读回”证据切片；不声明完整 S13、完整离线导入恢复、跨环境离线导入执行、
  平台升级冲突分析、13 类资产逐类业务消费者、完整临床运行、全角色全功能真实操作、完整 S0-S40 或
  134 清库复演。离线交付文件用于完整性校验和导入预检，不作为临床运行指针，不自动改写当前机构生效版本。
- 第一百四十五批验证证据：TDD 红灯
  `mvn -f medkernel-backend/pom.xml -Dtest=MigrationBaselineContractTest#evidenceSnapshotPayloadSnapshotUsesLongTextForCompleteJsonPayloads,H2BaselineMigrationTest#h2AppliesSingleAuthoritativeBaseline test`
  曾失败于规范模型仍为 `string` 且 H2 插入 7135 字符离线交付 JSON 报
  `Value too long for column PAYLOAD_SNAPSHOT CHARACTER VARYING(4000)`；修正后转绿。
  TDD 红灯
  `mvn -f medkernel-backend/pom.xml -Dtest=RuntimeReleaseOfflineDeliveryServiceTest#exportEvidenceIdFitsEvidenceSnapshotColumnContractForUlidReleaseIds test`
  曾失败于生成 77 字符证据 ID，修正后 `RuntimeReleaseOfflineDeliveryServiceTest` 全部转绿。目标真实 E2E 在本地
  18080 dev/H2 后端上通过：
  `E2E_API_BASE_URL=http://localhost:18080/medkernel/api/v1 MEDKERNEL_API_PROXY_TARGET=http://localhost:18080 E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-runtime-release-offline-delivery npm --prefix frontend run e2e -- --project=chromium runtime-release-frontdesk.spec.ts`
  通过，仓库外 `results.json` 为 `PASSED`（1 expected，0 unexpected，0 flaky）；coverage 含
  `scenarios:S13`、`productLayers:RELEASE_GOVERNANCE`、13 类 `versionedAssets`、
  `deliveryShapes:MANAGEMENT_WORKSPACE/API_EVENT`、`serviceCombinations:CLINICAL_RUNTIME/THIRD_PARTY_INTERFACE`；
  附件记录 `offlineDelivery.delivery.evidenceId=runtime-offline-4920d23e6425-01KWW5EARMDXJJ3183EKEVZ7MQ`、
  `fileDigest=sm3:570a053009b450017909102ac26574eba747cb3bf514f563cdefe9a69642d40c`、
  `offlineDeliveryFileDownloaded=true`、`offlineDeliveryImportPreviewValidated=true`、
  `offlineDeliveryRuntimeUnchanged=true`，预检前后 runtime identity 均为
  `releaseId=runtime-01KWW5E6046MX2CY90M8D1NRYQ/revisionNo=2/manifestSha256=84ea2652acb033152f8a3b27debbd31d5c36741d85943d4f244ae85517691c15`。
  其他新鲜门禁：`npm --prefix frontend run test -- ReleaseGovernance hooks e2eLaunchCoverageEvidence e2eAuthCredentialContract`
  通过（4 files，202 tests）；`npm --prefix frontend run typecheck` 通过；
  `node --test scripts/release/launch-coverage-audit.test.mjs` 通过（6 tests）；
  `node scripts/db/generate-migrations.mjs --check` 通过；
  `mvn -f medkernel-backend/pom.xml -Dtest=RuntimeReleaseOfflineDeliveryServiceTest,RuntimeReleaseControllerTest,MigrationBaselineContractTest,H2BaselineMigrationTest test`
  通过（32 tests）；`git diff --check` 通过。
- 第一百四十五批后续主线：本批仍未发布到 134，未实际执行 134 清库重新部署，也不能把离线交付导出预检证据切片
  等同于完整上线验收。后续继续补真实覆盖缺口：S13 平台升级冲突分析、完整离线导入恢复执行、S2/S4
  真实接入与字典映射，S0-S40 其余场景、完整语义族 / 专业域 / 全知识、13 类 runtime 资产逐类业务消费者、
  真实备份 / 隔离恢复 / 重启恢复，以及 134 清库部署复演；继续坚持全角色真实前台和真实服务链路，发现红点先复现、
  定根因，再按上线级标准修复，不做片面优化。
- 第一百四十四批本地收尾：继续按全角色真实前台、真实服务链路和上线门禁推进；本批不是菜单、
  文案或片面页面优化，也不是完整 S13、完整离线交付、平台升级冲突分析、全角色全功能或 134
  清库部署收口，而是在第一百四十三批“部分选择上线”基础上，把既有
  `runtime-release-frontdesk.spec.ts` 继续补成可证明“两机构差异化发布 / 读回隔离”的真实证据切片。
  `launchCoverageEvidence` 现在只有在 `runtime-release-coverage-codes` 同时证明 partial selection、
  两家不同医院、两个不同本院候选、两院后端当前机构生效版本读回互不串用、两院第三方 runtime contract
  读回互不串用，且两院各自读回均包含本院 selected 候选并排除另一院候选时，才额外声明
  `scenarios:S13`；否则仍只按完整发布 / 回滚 / 13 类资产闭包事实声明基础 `RELEASE_GOVERNANCE` 等 coverage。
- 第一百四十四批真实链路说明：`runtime-release-frontdesk.spec.ts` 现在先由平台管理员真实确保第二家演练医院
  `本地上线演练二院`（code `e2e-rehearsal-hospital-b`）和第二医院职责账号
  `e2e-runtime-second-engine-operator` 存在，并把该账号绑定到第二医院 `engine-operator` facility scope；
  该账号首次登录需要改密时走真实 `/auth/change-password`，随后回读 `/security/me` 断言 JWT
  的 `dataScope.hospitalId` 必须是第二家医院。测试为第一医院和第二医院分别创建不同的
  `ACTION_CARD.RUNTIME.RELEASE.*` 本院候选；真实前台在 `/config/releases` 先为 `本地上线演练医院`
  选择第一医院候选并发布，再切到第二医院选择第二医院候选并发布；随后分别回读两院当前 runtime release
  和两院第三方运行契约 `/engine/integration/knowledge-runtime/runtime-release/current`。第二医院第三方运行契约
  必须用第二医院职责账号读取，避免后端按认证医院上下文返回第一医院数据；附件记录 primary / secondary 两组
  `hospitalId/hospitalName/selectedCandidate/activationReadback/runtimeConsumerReadback`，以及
  `distinctHospitals/distinctSelectedCandidates/backendReadbacksIsolated/runtimeConsumerReadbacksIsolated`。
- 第一百四十四批边界说明：本批 coverage 只是 S13 的“部分选择 + 两机构差异化后端读回 + 两机构第三方 runtime
  contract 读回隔离”窄口径切片；不声明完整 S13、离线交付文件导出 / 签名 / 导入校验、平台升级冲突分析、
  13 类资产逐类业务消费者、完整临床运行、全角色真实操作、完整 S0-S40 或 134 清库复演。第二医院和演练账号
  是本地 E2E 演练资产，未清理删除，用于后续重复演练和差异化隔离验证。
- 第一百四十四批验证证据：TDD 红灯
  `npm --prefix frontend run test -- e2eLaunchCoverageEvidence -t "two-hospital differentiation"`
  曾失败于缺两机构证据仍声明 `S13`；`npm --prefix frontend run test -- e2eAuthCredentialContract -t "runtime release frontdesk"`
  曾失败于目标 spec 缺 `multiHospitalDifferentiation` 等源码契约。实现后 E2E TS 检查
  `cd frontend && npx tsc --noEmit --pretty false --skipLibCheck false --allowImportingTsExtensions --moduleResolution bundler --module ESNext --target ES2022 --lib ES2023,DOM --strict --types node e2e/support/launchCoverageEvidence.ts e2e/runtime-release-frontdesk.spec.ts`
  通过。目标真实 E2E 在本地 18080 dev/H2 后端上通过：
  `E2E_API_BASE_URL=http://localhost:18080/medkernel/api/v1 MEDKERNEL_API_PROXY_TARGET=http://localhost:18080 E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-runtime-release-multi-hospital npm --prefix frontend run e2e -- --project=chromium runtime-release-frontdesk.spec.ts`
  通过，仓库外 `results.json` 为 `PASSED`（1 expected，0 unexpected，0 flaky）；coverage 含
  `scenarios:S13`、`productLayers:RELEASE_GOVERNANCE`、13 类 `versionedAssets`、`deliveryShapes:MANAGEMENT_WORKSPACE/API_EVENT`、
  `serviceCombinations:CLINICAL_RUNTIME/THIRD_PARTY_INTERFACE`；附件记录 `partialSelectionProved=true`、
  `multiHospitalDifferentiation.distinctHospitals=true`、`distinctSelectedCandidates=true`、
  `backendReadbacksIsolated=true`、`runtimeConsumerReadbacksIsolated=true`，primary / secondary 均
  `excludesOtherHospitalCandidate=true`。新增回归护栏
  `npm --prefix frontend run test -- e2eLaunchCoverageEvidence -t "two-hospital|reuses one hospital|leak"`
  通过（3 tests），证明缺两机构证据、同一医院冒充两机构、读回混入另一院候选时均不声明 `S13`，且基础
  `RELEASE_GOVERNANCE` 不被误删。其他新鲜门禁：
  `npm --prefix frontend run test -- e2eLaunchCoverageEvidence e2eAuthCredentialContract` 通过（59 tests）；
  `npm --prefix frontend run typecheck` 通过；`node --test scripts/release/launch-coverage-audit.test.mjs`
  通过（6 tests）；`git diff --check` 通过。
- 第一百四十四批后续主线：本批仍未发布到 134，未实际执行 134 清库重新部署，也不能把两机构差异证据切片
  等同于完整上线验收。后续继续补真实覆盖缺口：S13 离线交付文件导出 / 签名 / 导入校验闭环、平台升级冲突分析，
  S2/S4 真实接入与字典映射，S0-S40 其余场景、完整语义族 / 专业域 / 全知识、13 类 runtime 资产逐类消费者、
  真实备份 / 隔离恢复 / 重启恢复，以及 134 清库部署复演；继续坚持全角色真实前台和真实服务链路，发现红点先复现、
  定根因，再按上线级标准修复，不做片面优化。
- 第一百四十三批本地收尾：继续按全角色真实前台、真实服务链路和上线门禁推进；本批不是菜单、
  文案或片面页面优化，也不是完整 S13、两机构差异化发布、离线交付、导入校验、全角色全功能或 134
  清库部署收口，而是把既有 `runtime-release-frontdesk.spec.ts` 从“选择一个本院候选进入机构生效版本”
  补成可证明 S13 部分选择上线的真实证据切片。`launchCoverageEvidence` 现在将 runtime-release coverage
  拆成两层门槛：完整发布 / 回滚 / 13 类资产闭包证据继续只声明 `RELEASE_GOVERNANCE`、13 类 `versionedAssets`、
  `MANAGEMENT_WORKSPACE/API_EVENT` 和 `CLINICAL_RUNTIME/THIRD_PARTY_INTERFACE`；只有额外附件证明本轮同时创建
  selected 与 unselected 两个本院候选，且 unselected 在激活请求、后端当前版本读回和第三方运行契约读回均缺席时，
  才额外声明 `scenarios:S13`。
- 第一百四十三批真实链路说明：`runtime-release-frontdesk.spec.ts` 现在每轮通过真实后端创建两个本院
  `ACTION_CARD.RUNTIME.RELEASE.*` 候选；真实前台进入 `/config/releases` 选择本地上线演练医院，确认 13 类平台标准资产
  已勾选，在“集团与本院内容”中只勾选 selected 候选，并断言 unselected 候选可见但保持未选；完成发布影响评估后，
  前台生成机构生效版本。附件 `runtime-release-coverage-codes` 记录 selected / unselected 的
  `assetType/assetIdentity/versionId/versionNo`，并证明激活请求、后端当前机构生效版本读回、第三方运行契约读回
  只包含 selected、不包含 unselected；随后仍执行历史版本回滚并证明本轮 selected 在回滚后后端和第三方运行契约均缺席。
- 第一百四十三批边界说明：本批新增 coverage 只是在既有 runtime-release 证据上额外声明 `scenarios:S13` 的
  “部分选择上线”切片；不声明完整 S13、两家机构差异化扩展、平台升级冲突分析、离线交付文件导出 / 导入 / 签名校验、
  完整 13 类资产逐类消费者、全角色真实操作或 134 清库复演。
- 第一百四十三批验证证据：TDD 红灯
  `npm --prefix frontend run test -- e2eLaunchCoverageEvidence -t "release governance and runtime asset coverage"`
  曾失败于完整 partial-selection 附件仍不声明 `S13`（`expected undefined to deeply equal [ 'S13' ]`）；
  `npm --prefix frontend run test -- e2eAuthCredentialContract -t "runtime release frontdesk"` 曾失败于目标 spec
  缺 `assertRuntimeAssetsExcludeUnselectedCandidate`。修正后另一个回归红点显示，缺 partial proof 时不能连既有
  `RELEASE_GOVERNANCE` 一起拒绝，已拆成两层 reporter 门槛并转绿。E2E TS 检查
  `cd frontend && npx tsc --noEmit --pretty false --skipLibCheck false --allowImportingTsExtensions --moduleResolution bundler --module ESNext --target ES2022 --lib ES2023,DOM --strict --types node e2e/support/launchCoverageEvidence.ts e2e/runtime-release-frontdesk.spec.ts`
  通过。目标真实 E2E 在本地 18080 dev/H2 后端（PID 49326）上通过：
  `E2E_API_BASE_URL=http://localhost:18080/medkernel/api/v1 MEDKERNEL_API_PROXY_TARGET=http://localhost:18080 E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-runtime-release-partial-selection npm --prefix frontend run e2e -- --project=chromium runtime-release-frontdesk.spec.ts`
  通过，仓库外 `results.json` 为 `PASSED`（1 expected，0 unexpected，0 flaky），coverage 含
  `scenarios:S13`、`productLayers:RELEASE_GOVERNANCE`、13 类 `versionedAssets`、`deliveryShapes:MANAGEMENT_WORKSPACE/API_EVENT`、
  `serviceCombinations:CLINICAL_RUNTIME/THIRD_PARTY_INTERFACE`；附件记录 `partialSelectionProved=true`，
  selected 候选进入请求 / 后端读回 / 第三方读回，unselected 候选在三处均缺席。其他新鲜门禁：
  `npm --prefix frontend run test -- e2eLaunchCoverageEvidence e2eAuthCredentialContract` 通过（56 tests）；
  `npm --prefix frontend run typecheck` 通过；`node --test scripts/release/launch-coverage-audit.test.mjs`
  通过（6 tests）；`git diff --check` 通过。
- 第一百四十三批后续主线：本批仍未发布到 134，未实际执行 134 清库重新部署，也不能把 S13 部分选择证据切片
  等同于完整 S13 或总目标完成。后续优先继续真实覆盖缺口：S13 两机构差异化发布 / 平台升级冲突分析 /
  离线交付文件导出、签名校验、导入或校验闭环，S2/S4 真实接入与字典映射，S0-S40 其余场景、完整语义族 / 专业域 /
  全知识、真实备份 / 隔离恢复 / 重启恢复，以及 134 清库部署复演；继续坚持真实前台与真实服务链路，发现红点先复现、
  定根因，再按上线级标准修复，不做片面优化。
- 第一百四十二批本地收尾：继续按全角色真实前台、真实服务链路和上线门禁推进；本批不是菜单、
  文案或片面页面优化，也不是完整 S14、完整身份安全治理、全角色全功能或 134 清库部署收口，而是把既有
  `identity-binding-frontdesk.spec.ts` 从“真实前台绑定 / 解绑 + 隐私断言”补成可被浏览器覆盖 reporter
  消费的身份来源证据切片。`launchCoverageEvidence` 现在只有在该 spec 通过、非 flaky，且附件
  `identity-binding-scenario-codes` 完整包含 `scenarios:S14`、`productLayers:FOUNDATION_GOVERNANCE`、
  `serviceCombinations:COMPLIANCE_OPERATIONS`、七项 API evidence、两个真实演练人员账号、ACTIVE
  `EMPLOYEE_NO` 绑定响应、列表不返回身份摘要 / 原文、重复外部身份 409 拒绝、UNBOUND 解绑响应、清理停用结果和六个真实阶段时，才声明上述 coverage；没有附件或附件缺隐私、重复拒绝、解绑、清理事实时不再声明 coverage。
- 第一百四十二批真实链路说明：`identity-binding-frontdesk.spec.ts` 现在以平台管理员真实前台进入
  `/admin/users` 创建两个身份来源演练人员账号，再进入 `/security/identity-binding` 对其中一个账号执行
  单个绑定院内工号；随后通过真实后端列表回读确认只展示 `subjectHint`，不返回 `externalSubjectDigest` 或身份原文；
  用第二个真实账号提交同一外部身份，断言后端返回 409 且提示“该外部身份已绑定其他用户”；再由前台解除绑定，
  断言状态 `UNBOUND` 且版本递增；finally 中清理解除残留绑定并停用两个演练账号。附件记录绑定 `bindingId/userId/providerType/subjectHint/status/version`、
  重复拒绝响应、解绑结果、清理结果和阶段证据。
- 第一百四十二批边界说明：本批 coverage 只含 `scenarios:S14`、`productLayers:FOUNDATION_GOVERNANCE` 和
  `serviceCombinations:COMPLIANCE_OPERATIONS`；不声明完整 S14、完整组织 / 权限 / MFA / 身份安全治理、全角色真实操作、
  完整合规运维、完整上线验收或 134 清库复演。
- 第一百四十二批验证证据：TDD 红灯
  `npm --prefix frontend run test -- e2eLaunchCoverageEvidence -t "identity binding"` 曾失败于完整附件仍不声明
  `S14`（`expected undefined to deeply equal [ 'S14' ]`）；
  `npm --prefix frontend run test -- e2eAuthCredentialContract -t "identity binding"` 曾失败于目标 spec 缺
  `attachIdentityBindingScenarioEvidence`。实现后上述红灯转绿。E2E TS 检查
  `cd frontend && npx tsc --noEmit --pretty false --skipLibCheck false --allowImportingTsExtensions --moduleResolution bundler --module ESNext --target ES2022 --lib ES2023,DOM --strict --types node e2e/support/launchCoverageEvidence.ts e2e/identity-binding-frontdesk.spec.ts`
  通过。目标真实 E2E 在本地 18080 dev/H2 后端（PID 49326）上通过：
  `E2E_API_BASE_URL=http://localhost:18080/medkernel/api/v1 MEDKERNEL_API_PROXY_TARGET=http://localhost:18080 E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-identity-binding-coverage npm --prefix frontend run e2e -- --project=chromium identity-binding-frontdesk.spec.ts`
  通过，仓库外 `results.json` 为 `PASSED`（1 expected，0 unexpected，0 flaky），coverage 只含
  `scenarios:S14`、`productLayers:FOUNDATION_GOVERNANCE`、`serviceCombinations:COMPLIANCE_OPERATIONS`；附件记录两名演练人员、
  `EMPLOYEE_NO` ACTIVE 绑定、列表脱敏 / 不含摘要 / 不含原文、重复绑定 409、UNBOUND 解绑和清理停用均完成。其他新鲜门禁：
  `npm --prefix frontend run test -- e2eLaunchCoverageEvidence e2eAuthCredentialContract` 通过（55 tests）；
  `npm --prefix frontend run typecheck` 通过；`node --test scripts/release/launch-coverage-audit.test.mjs`
  通过（6 tests）；`git diff --check` 通过。
- 第一百四十二批后续主线：本批仍未发布到 134，未实际执行 134 清库重新部署，也不能把 identity-binding
  身份来源证据切片等同于完整 S14、完整基础治理、完整全角色或总目标完成。后续优先继续真实覆盖缺口：S13
  两机构差异化发布 / 部分选择 / 离线交付 / 导入或校验闭环，S2/S4 真实接入与字典映射，S0-S40 其余场景、
  完整语义族 / 专业域 / 全知识、真实备份 / 隔离恢复 / 重启恢复，以及 134 清库部署复演；继续坚持真实前台与真实服务链路，
  发现红点先复现、定根因，再按上线级标准修复，不做片面优化。
- 第一百四十一批本地收尾：继续按全角色真实前台、真实服务链路和上线门禁推进；本批不是菜单、
  文案或片面页面优化，也不是完整系统运维、真实备份 / 隔离恢复 / 重启恢复、完整 S15、完整全角色或 134
  清库部署收口，而是把既有 `system-providers-frontdesk.spec.ts` 从“真实前台只读核查 + 权限禁入”补成可被
  浏览器覆盖 reporter 消费的服务运行保障证据切片。`launchCoverageEvidence` 现在只有在该 spec 通过、非 flaky，
  且附件 `system-providers-operations-codes` 完整包含 `deliveryShapes:MANAGEMENT_WORKSPACE`、
  `serviceCombinations:COMPLIANCE_OPERATIONS`、五项 API evidence、运行快照字段、备份恢复只读证据、依赖诚实降级
  证据、临床账号 403 / 页面禁入证据和五个真实阶段时，才声明上述 coverage；没有附件或附件缺备份恢复、
  依赖降级、证据详情、临床禁入事实时不再声明 coverage。
- 第一百四十一批真实链路说明：`system-providers-frontdesk.spec.ts` 现在由平台管理员真实读取
  `/system/operations`，前台进入 `/system/providers` 展示核心服务、依赖服务、备份恢复就绪和国产化适配档案；
  默认视图不展示 `backup.sh` / `restore.sh` 等脚本路径，打开“证据详情”后才展示部署档案、数据库方言、迁移路径、
  备份脚本、恢复脚本和备份恢复诊断字段；依赖非 UP 时展示“核心业务继续走本地确定性主链路”的诚实降级提示。
  同一证明测试再切换临床账号，直接读 `/system/operations` 必须 403，前台 `/system/providers` 必须显示
  “当前权限不足”且不展示关系数据库或备份恢复就绪数据。当前本地 dev/H2 附件记录备份恢复 `drillEvidence.status`
  为 `NOT_AVAILABLE`，只证明“只读呈现和诚实未完成演练”，不证明真实备份恢复已通过。
- 第一百四十一批边界说明：本批 coverage 只含 `deliveryShapes:MANAGEMENT_WORKSPACE` 和
  `serviceCombinations:COMPLIANCE_OPERATIONS`；不新增 `scenarios:S15`，不声明 `deliveryShapes:ENGINE_CORE`、
  真实备份 / 隔离恢复 / 重启恢复完成、完整系统运维、完整国产化、完整部署验收、全角色真实操作或 134 清库复演。
- 第一百四十一批验证证据：TDD 红灯
  `npm --prefix frontend run test -- e2eLaunchCoverageEvidence -t "system operations"` 曾失败于
  `system-providers-frontdesk.spec.ts` 即使携带完整附件也不声明 `MANAGEMENT_WORKSPACE`（`expected undefined to deeply equal [ 'MANAGEMENT_WORKSPACE' ]`）；
  实现后转绿。E2E TS 检查
  `cd frontend && npx tsc --noEmit --pretty false --skipLibCheck false --allowImportingTsExtensions --moduleResolution bundler --module ESNext --target ES2022 --lib ES2023,DOM --strict --types node e2e/support/launchCoverageEvidence.ts e2e/system-providers-frontdesk.spec.ts`
  通过。目标真实 E2E 在本地 18080 dev/H2 后端（PID 49326）上通过：
  `E2E_API_BASE_URL=http://localhost:18080/medkernel/api/v1 MEDKERNEL_API_PROXY_TARGET=http://localhost:18080 E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-system-providers-coverage npm --prefix frontend run e2e -- --project=chromium system-providers-frontdesk.spec.ts`
  通过，仓库外 `results.json` 为 `PASSED`（2 expected，0 unexpected，0 flaky），coverage 只含
  `deliveryShapes:MANAGEMENT_WORKSPACE` 和 `serviceCombinations:COMPLIANCE_OPERATIONS`；平台管理员证明测试附件记录
  `/system/operations` 200、临床账号 403、备份 RPO/RTO/SHA-256/脚本路径、依赖降级明细和五个真实阶段。其他新鲜门禁：
  `npm --prefix frontend run test -- e2eLaunchCoverageEvidence e2eAuthCredentialContract` 通过（53 tests）；
  `npm --prefix frontend run typecheck` 通过；`node --test scripts/release/launch-coverage-audit.test.mjs`
  通过（6 tests）；`git diff --check` 通过。
- 第一百四十一批后续主线：本批仍未发布到 134，未实际执行 134 清库重新部署，也不能把服务运行保障只读证据
  切片等同于真实备份 / 隔离恢复 / 重启恢复或总目标完成。后续优先继续真实覆盖缺口：identity-binding 身份来源
  coverage 附件门槛，S13 两机构差异化发布 / 部分选择 / 离线交付 / 导入或校验闭环，S2/S4 真实接入与字典映射，
  S0-S40 其余场景、完整语义族 / 专业域 / 全知识、真实备份 / 隔离恢复 / 重启恢复，以及 134 清库部署复演；
  继续坚持真实前台与真实服务链路，发现红点先复现、定根因，再按上线级标准修复，不做片面优化。
- 第一百四十批本地收尾：继续按全角色真实前台、真实服务链路和上线门禁推进；本批不是菜单、
  文案或片面页面优化，也不是完整 S13、两机构差异化发布、离线交付、13 类 runtime 资产逐类业务消费者、
  全角色全功能或 134 清库部署收口，而是修复既有 `runtime-release-frontdesk.spec.ts` 被浏览器覆盖 reporter
  消费时“只按文件名/标题/passed 即声明版本治理与 13 类版本化资产”的门槛偏松风险。`launchCoverageEvidence`
  现在要求该 spec 通过、非 flaky，且附件 `runtime-release-coverage-codes` 完整包含
  `productLayers:RELEASE_GOVERNANCE`、13 类 `versionedAssets`、`deliveryShapes:MANAGEMENT_WORKSPACE/API_EVENT`、
  `serviceCombinations:CLINICAL_RUNTIME/THIRD_PARTY_INTERFACE`、八项 API evidence、同一本轮本院候选资产在
  激活请求 / 后端当前版本读回 / 第三方运行契约读回中的一致证据、回滚后后端和第三方运行契约均不再包含该候选的
  证据，以及七个真实阶段后，才声明上述 coverage；没有附件或附件缺发布影响评估、activeAssets 闭包、
  当前版本读回、第三方运行契约读回、回滚读回或本院候选贯穿证据时不再声明 coverage。
- 第一百四十批真实链路说明：`runtime-release-frontdesk.spec.ts` 现在先以本地上线演练医院职责的
  `engine-operator` 通过真实后端服务创建本院 `ACTION_CARD` 候选资产（受控 `INFO` 提示卡，不含诊疗结论，
  不自动开嘱）；随后真实前台进入 `/config/releases`，选择本地上线演练医院，打开证据详情，确认平台标准内容
  13 类资产均展示且已勾选；在“集团与本院内容”中选择本轮本院候选；强制点击“评估发布影响”并断言真实
  `/engine/versioning/releases/simulations` 返回 releasable；再由前台 POST 生成机构生效版本，断言请求
  `activeAssets` 携带 13 类平台基线资产且均沿用平台标准版本，并携带本轮本院 `ACTION_CARD.RUNTIME.RELEASE.*`
  候选的同一 `versionId`；后端回读当前医院 runtime release 并校验 13 类资产 ACTIVE，且本轮候选 ACTIVE；
  第三方运行契约 `/engine/integration/knowledge-runtime/runtime-release/current` 回读同一修订和同一本轮候选；
  最后前台从历史版本回滚，再次回读医院 runtime 和第三方运行契约同一修订，并断言两者均不再包含本轮本院候选。
  附件记录本轮本院候选 `ACTION_CARD.RUNTIME.RELEASE.*`、激活修订号、回滚修订号、激活请求候选、激活后后端读回
  候选、激活后第三方运行契约候选，以及回滚后后端 / 第三方运行契约候选缺席证据。
- 第一百四十批边界说明：本批只收紧并补实既有 runtime-release coverage 的证据门槛，coverage 只含
  `productLayers:RELEASE_GOVERNANCE`、13 类 `versionedAssets`、`deliveryShapes:MANAGEMENT_WORKSPACE/API_EVENT`、
  `serviceCombinations:CLINICAL_RUNTIME/THIRD_PARTY_INTERFACE`；不新增 `scenarios:S13`，不声明离线交付、两家机构
  差异化发布、完整 S0-S40、完整全资产消费者、完整临床运行、全角色真实操作或 134 清库复演。
- 第一百四十批验证证据：TDD 红灯
  `npm --prefix frontend run test -- e2eLaunchCoverageEvidence -t "release governance"` 曾失败于没有附件也声明
  `RELEASE_GOVERNANCE`（`expected [ Array(1) ] to be undefined`）；补强 reviewer P1 后，
  `npm --prefix frontend run test -- e2eLaunchCoverageEvidence -t "local candidate"` 曾失败于完整布尔和阶段文案仍可在缺
  本院候选读回事实时声明 coverage（`expected [ Array(1) ] to be undefined`）；
  `npm --prefix frontend run test -- e2eAuthCredentialContract -t "runtime release frontdesk"`
  曾失败于目标 spec 缺 `createHospitalRuntimeReleaseCandidate` 和 `runtime-release-coverage-codes`。实现后上述红灯转绿。
  E2E TS 检查
  `cd frontend && npx tsc --noEmit --pretty false --skipLibCheck false --allowImportingTsExtensions --moduleResolution bundler --module ESNext --target ES2022 --lib ES2023,DOM --strict --types node e2e/support/launchCoverageEvidence.ts e2e/runtime-release-frontdesk.spec.ts`
  通过。目标真实 E2E 在本地 18080 dev/H2 后端（PID 49326）上通过：
  `E2E_API_BASE_URL=http://localhost:18080/medkernel/api/v1 MEDKERNEL_API_PROXY_TARGET=http://localhost:18080 E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-runtime-release-coverage npm --prefix frontend run e2e -- --project=chromium runtime-release-frontdesk.spec.ts`
  通过，仓库外 `results.json` 为 `PASSED`（1 expected，0 unexpected，0 flaky），附件中本轮
  `ACTION_CARD.RUNTIME.RELEASE.*` 在激活请求、后端读回和第三方运行契约读回均为同一 `versionId`，回滚后后端和
  第三方运行契约均记录 `localCandidateAbsent: true`；coverage 只含
  `productLayers:RELEASE_GOVERNANCE`、13 类 `versionedAssets`、`deliveryShapes:MANAGEMENT_WORKSPACE/API_EVENT`、
  `serviceCombinations:CLINICAL_RUNTIME/THIRD_PARTY_INTERFACE`。其他新鲜门禁：
  `npm --prefix frontend run test -- e2eLaunchCoverageEvidence e2eAuthCredentialContract` 通过（51 tests）；
  `npm --prefix frontend run typecheck` 通过；`node --test scripts/release/launch-coverage-audit.test.mjs`
  通过（6 tests）；`git diff --check` 通过。
- 第一百四十批后续主线：本批仍未发布到 134，未实际执行 134 清库重新部署，也不能把 runtime-release
  证据门槛收紧等同于完整 S13、完整 13 类资产逐类业务消费者、完整五种交付形态或总目标完成。后续优先继续
  真实覆盖缺口：S13 两机构差异化发布 / 部分选择 / 离线交付 / 导入或校验闭环，S2/S4 真实接入与字典映射，
  system-providers 备份恢复与诚实降级 coverage 附件门槛，identity-binding 身份来源 coverage 附件门槛，
  S0-S40 其余场景、完整语义族 / 专业域 / 全知识、真实备份 / 隔离恢复 / 重启恢复，以及 134 清库部署复演；
  继续坚持真实前台与真实服务链路，发现红点先复现、定根因，再按上线级标准修复，不做片面优化。
- 第一百三十九批本地收尾：继续按全角色真实前台、真实服务链路和上线门禁推进；本批不是菜单、
  文案或片面页面优化，也不是完整 S7、全知识治理、全语义族、全角色、全功能或 134 清库部署收口，而是把
  既有 `d6-graph-explore.spec.ts` 从“图谱页面可见 + 存量投影兜底”补成可被浏览器覆盖 reporter 消费的
  S7 关系与来源追溯证据切片。该 spec 现在每轮真实登记受控来源、来源版本和来源锚点；通过
  `/engine/knowledge-production/generate` 从受控来源版本走真实 B0/人工候选生成编排，经过服务端安全门、
  分流和影子评测后进入候选审核链；真实绑定本轮 citation，生成服务端发布质量记录，审核激活知识候选；
  回读 `/engine/knowledge/identities/{id}/provenance` 与 `/citations`，断言 `partial=false`、
  `unresolvedCitationCount=0`，同一 `citationId` 同时存在于结构化引用和 `sourceEvidence`，并带出本轮
  `sourceCode/sourceVersionNo/anchorPath/textExcerpt/hash`；随后重建知识关系投影并断言本轮
  `KNOWLEDGE_IDENTITY -> KNOWLEDGE_VERSION -> SOURCE_FRAGMENT -> SOURCE_DOCUMENT` 投影事实存在；最后在真实前台
  `/advanced/graph` 切到知识关系、按本轮 `KNOWLEDGE_IDENTITY:{id}` 查询，打开证据详情并点选本轮知识身份节点，
  验证对象标识和追踪号可见且无“未返回”。`launchCoverageEvidence` 只有在目标 spec 通过、非 flaky，且附件
  `source-lineage-scenario-codes` 完整包含 S7、`SOURCE_VALIDITY`、必要 API evidence 和五个阶段时，才声明
  `scenarios:S7` 与 `semanticFamilies:SOURCE_VALIDITY`。
- 第一百三十九批边界说明：本批只声明 S7 和 `SOURCE_VALIDITY`。不声明完整 S7、完整语义族、全知识、全
  S0-S40、完整知识生产、全角色真实操作或 134 清库复演；本批登录角色为 `engine-operator`，前台证明点为
  知识关系图谱探索，来源登记、候选生成编排、citation 绑定、质量记录、审核、provenance/citations 回读和
  投影重建仍包含真实后端 API 辅助。目标 E2E 最初红于旧测试强制每轮创建 seed 后暴露
  `/model-candidates` 在当前无模型环境下没有候选路由证据；按“无模型可运行”红线改走受控来源版本
  `/engine/knowledge-production/generate` 的 B0/人工候选生成编排。随后红于手工直提候选没有安全门 /
  分流 / 影子评测记录，服务端拒绝发布质量记录；已改为生成编排入口产生真实门禁证据。最后红于生成编排已自动
  complete job，测试重复 complete 触发终态冲突，已删除重复 complete。
- 第一百三十九批验证证据：TDD 红灯
  `npm --prefix frontend run test -- e2eLaunchCoverageEvidence -t "S7 source lineage"` 曾失败于
  `expected undefined to deeply equal [ 'S7' ]`；实现后转绿。E2E TS 检查
  `cd frontend && npx tsc --noEmit --pretty false --skipLibCheck false --allowImportingTsExtensions --moduleResolution bundler --module ESNext --target ES2022 --lib ES2023,DOM --strict --types node e2e/support/launchCoverageEvidence.ts e2e/d6-graph-explore.spec.ts`
  通过。目标真实 E2E 在上一批本地 18080 dev/H2 后端（PID 49326）上通过：
  `E2E_API_BASE_URL=http://localhost:18080/medkernel/api/v1 MEDKERNEL_API_PROXY_TARGET=http://localhost:18080 E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-source-lineage-coverage npm --prefix frontend run e2e -- --project=chromium d6-graph-explore.spec.ts`
  通过（2 expected，0 unexpected，0 flaky），仓库外 `results.json` 为 `PASSED`，coverage 只含
  `scenarios:S7` 和 `semanticFamilies:SOURCE_VALIDITY`。其他新鲜门禁：
  `npm --prefix frontend run test -- e2eLaunchCoverageEvidence e2eAuthCredentialContract` 通过（49 tests）；
  `npm --prefix frontend run typecheck` 通过；`node --test scripts/release/launch-coverage-audit.test.mjs`
  通过（6 tests）；`git diff --check` 通过。
- 第一百三十九批后续主线：本批仍未发布到 134，未实际执行 134 清库重新部署，也不能把 S7 来源追溯证据切片
  等同于完整 S7、完整全知识、全角色全功能或总目标完成。后续仍需继续补 S0-S40 其余场景、全角色真实前台操作、
  13 类 runtime 资产逐类业务消费者、完整语义族 / 专业域 / 全知识、五种交付形态未覆盖项、两家机构差异化
  发布 / 回滚、真实备份 / 隔离恢复 / 重启恢复，以及 134 清库部署复演；继续坚持真实前台与真实服务链路，
  发现红点先复现、定根因，再按上线级标准修复，不做片面优化。
- 第一百三十八批本地收尾：继续按全角色真实前台、真实服务链路和上线门禁推进；本批不是菜单、
  文案或片面页面优化，也不是完整 S6、全角色、全功能、全专病十阶段运行覆盖或 134 清库部署收口，而是把
  S6 专病路径生命周期补成可被浏览器覆盖 reporter 消费的真实前台与真实服务链路证据切片。新增
  `pathway-lifecycle-frontdesk.spec.ts`：临床用户真实前台创建脱敏患者、建立 ACTIVE 当前就诊上下文；
  医疗引擎运营员真实前台新建专病路径草稿、读取本轮真实快照、回填受控配置、执行草稿试运行并保存；
  后端回读节点 / 边 / 时钟 / 十阶段 milestone 配置；真实服务链路仿真已保存路径；激活包含平台基线
  activeAssets 和本轮 PATHWAY 的本地医院 runtime；临床用户从患者 360 更新当前就诊上下文，基于当前机构
  生效版本读取入径候选、前台办理入径并完成一次标准推进；随后通过真实后端 API 辅助登记变异、完成随访
  接续终点节点，并回读关键时钟、变异事实和随访计划。`launchCoverageEvidence` 只有在 spec 通过、
  非 flaky，且附件 `pathway-lifecycle-scenario-codes` 完整包含 S6、`CLINICAL_EXECUTION`、
  `SPECIAL_DISEASE_PATHWAY`、必要 API evidence 和十阶段 milestone 配置时，才声明 `scenarios:S6`、
  `productLayers:CLINICAL_EXECUTION`、`serviceCombinations:SPECIAL_DISEASE_PATHWAY`。本批明确不输出
  `specialDiseaseStages` 到 coverage；附件里的 `specialDiseaseStages` 只作为“路径模板配置和后端详情回读
  含十阶段里程碑”的完整性门槛，不代表专病十阶段逐阶段运行覆盖。
- 第一百三十八批边界说明：本批不是纯前台闭环。真实前台覆盖患者 / 上下文 / 路径草稿 / 草稿试运行 /
  入径 / 一次标准推进；仿真、runtime 激活、变异登记、终点完成、时钟 / 变异 / 随访回读仍含 API 辅助。
  登录角色实际主要为 `clinical-user` 和 `engine-operator`，节点元数据中的护士、药师、质控等不等同于
  全角色真实操作。`PathwayEngineService` 为新建 PATHWAY 资产注册默认 `patient-view` trigger bindings：
  `PATHWAY_ENTRY_CANDIDATE` 需要 `patient.mpi`、`encounters[].encounterId`，`PATHWAY_PROGRESS` 需要
  `patientPathwayId`，用于让本轮 runtime 候选和患者路径推进能被机构生效版本消费。
- 第一百三十八批验证证据：TDD 红灯中，收紧口径后的
  `npm --prefix frontend run test -- e2eLaunchCoverageEvidence -t "S6 pathway lifecycle"` 曾失败于
  `expected undefined to deeply equal [ 'S6' ]`；`npm --prefix frontend run test -- e2eAuthCredentialContract -t "pathway lifecycle"`
  曾失败于旧 spec 缺 `真实服务链路对已保存路径执行仿真`，修正标题和阶段文案后两者转绿。重新打包后端
  `mvn -f medkernel-backend/pom.xml -DskipTests package` 通过，并在 2026-07-06 22:09 后启动当前分支
  JAR 的 18080 dev/H2 后端（PID 49326）。目标真实 E2E 通过：
  `E2E_API_BASE_URL=http://localhost:18080/medkernel/api/v1 MEDKERNEL_API_PROXY_TARGET=http://localhost:18080 E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-pathway-lifecycle-coverage npm --prefix frontend run e2e -- --project=chromium pathway-lifecycle-frontdesk.spec.ts`
  通过（1 expected，0 unexpected，0 flaky），仓库外 `results.json` 为 `PASSED`，coverage 只含
  `scenarios:S6`、`productLayers:CLINICAL_EXECUTION`、`serviceCombinations:SPECIAL_DISEASE_PATHWAY`。
  其他新鲜门禁：`npm --prefix frontend run test -- Mpi PatientPathways ContextSnapshotSelector QcEvalSets e2eLaunchCoverageEvidence e2eAuthCredentialContract`
  通过（87 tests）；`npm --prefix frontend run typecheck` 通过；E2E TS 检查
  `cd frontend && npx tsc --noEmit --pretty false --skipLibCheck false --allowImportingTsExtensions --moduleResolution bundler --module ESNext --target ES2022 --lib ES2023,DOM --strict --types node e2e/support/launchCoverageEvidence.ts e2e/pathway-lifecycle-frontdesk.spec.ts`
  通过；`node --test scripts/release/launch-coverage-audit.test.mjs` 通过（6 tests）；后端
  `mvn -f medkernel-backend/pom.xml -Dtest=PathwayEngineServiceTest test` 通过（63 tests）；
  `git diff --check` 通过。
- 第一百三十八批后续主线：本批仍未发布到 134，未实际执行 134 清库重新部署，也不能把 S6 证据切片等同于
  完整 S6、完整临床执行层、完整专病十阶段、全角色全功能或总目标完成。后续仍需继续补 S0-S40 其余场景、
  全角色真实前台操作、13 类 runtime 资产逐类业务消费者、完整语义族 / 专业域 / 全知识、两家机构差异化
  发布 / 回滚、真实备份 / 隔离恢复 / 重启恢复，以及 134 清库部署复演；发现红点继续先复现、定根因，再按
  上线级标准修复，不做片面优化。
- 第一百三十七批本地收尾：继续按全角色真实前台、真实服务链路和上线门禁推进；本批不是登录页文案或
  片面 MFA 优化，而是把既有 `mfa-login-frontdesk.spec.ts` 从“真实前台 E2E + 截图”补成可被浏览器覆盖
  reporter 消费的认证安全证据。该 spec 现在先由平台管理员真实创建 MFA 临时平台管理员账号，临时账号完成
  首次改密并绑定真实 TOTP；随后通过配置中心真实读取“上线默认 MFA 关闭”、临时开启 MFA，在登录页验证已绑定
  账号必须进入 MFA 步骤，提交真实动态验证码并进入工作台，再回读 `/security/me` 确认
  `mfaRequired/mfaBound/mfaVerified` 均为 true；finally 中恢复 MFA 默认关闭并停用临时管理员账号。
  `launchCoverageEvidence` 只有在 spec 通过、非 flaky，且附件 `mfa-login-scenario-codes` 完整包含
  九个阶段时才声明 `scenarios:S14`、`productLayers:FOUNDATION_GOVERNANCE` 和
  `serviceCombinations:COMPLIANCE_OPERATIONS`。本批明确不声明完整 S14、组织层级、全合规运维、全角色全功能
  或总目标完成。只读子代理 Mencius 核查确认 `pathway-graph-editor.spec.ts` 仅证明路径画布交互和真实页面读链路，
  未真实保存 / 发布 / 仿真 / 入径 / 变异 / 随访接续，不适合声明 S6、专病十阶段或
  `SPECIAL_DISEASE_PATHWAY`。
- 第一百三十七批验证证据：TDD 红灯
  `npm --prefix frontend run test -- e2eLaunchCoverageEvidence -t "MFA login"` 曾失败于
  `expected undefined to deeply equal [ 'S14' ]`；`npm --prefix frontend run test -- e2eAuthCredentialContract -t "requires MFA frontdesk rehearsal"`
  曾失败于 `mfa-login-frontdesk.spec.ts` 缺 `recordMfaLoginStage`。实现后两者转绿。新鲜门禁：
  `npm --prefix frontend run test -- e2eLaunchCoverageEvidence e2eAuthCredentialContract` 通过（44 tests）；
  `npm --prefix frontend run typecheck` 通过；E2E TS 检查
  `cd frontend && npx tsc --noEmit --pretty false --skipLibCheck false --allowImportingTsExtensions --moduleResolution bundler --module ESNext --target ES2022 --lib ES2023,DOM --strict --types node e2e/support/launchCoverageEvidence.ts e2e/mfa-login-frontdesk.spec.ts`
  通过；`node --test scripts/release/launch-coverage-audit.test.mjs` 通过（6 tests）；`git diff --check` 通过。
  本地重新打包后端 `mvn -f medkernel-backend/pom.xml -DskipTests package` 通过，并在 2026-07-06 20:10 后启动
  当前分支 JAR 的 18080 dev/H2 空库后端（PID 51036）。目标真实前台 E2E 通过：
  `E2E_API_BASE_URL=http://localhost:18080/medkernel/api/v1 MEDKERNEL_API_PROXY_TARGET=http://localhost:18080 E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-mfa-login-coverage npm --prefix frontend run e2e -- --project=chromium mfa-login-frontdesk.spec.ts`
  通过（1 expected，0 unexpected，0 flaky），仓库外 `results.json` 为 `PASSED`，
  `coverageKeys=["scenarios","productLayers","serviceCombinations"]`，只含 `scenarios:S14`、
  `productLayers:FOUNDATION_GOVERNANCE` 和 `serviceCombinations:COMPLIANCE_OPERATIONS`，附件九个阶段完整。
- 第一百三十七批后续主线：本批仍未发布到 134，未实际执行 134 清库重新部署，也不能把 MFA 认证安全切片
  等同于完整 S14、全基础治理或总目标完成。后续优先补真实前台 S6 临床路径端到端证据：前台完整创建路径草稿、
  真实 POST 保存、后端回读节点 / 边 / 时钟配置、发布 / 仿真 / 入径候选 / 推进 / 变异 / 随访接续，并以附件
  严格声明 S6、专病十阶段和 `SPECIAL_DISEASE_PATHWAY`；继续补 S0-S40 其余缺口、完整语义族 / 专业域、
  五种交付形态未覆盖项、两家机构差异化发布 / 回滚、真实备份 / 隔离恢复 / 重启恢复，以及 134 清库部署复演。
- 第一百三十六批本地收尾：继续按全角色真实前台、真实服务链路和上线门禁推进；本批不是菜单、
  文案或片面页面优化，而是把旧 `embed-business-host.spec.ts` 的 `page.route` mock 嵌入演示补成可被浏览器
  覆盖 reporter 消费的真实嵌入式交付和反馈闭环证据。该 spec 现在先以 `clinical-user` 真实登录前台
  `/mpi`，创建脱敏患者、进入患者 360、按页面业务校验补齐医技报告项目 / 报告结论 / 异常重点并建立当前就诊
  上下文，再通过真实后端创建候选推荐卡、登记宿主 Origin、签发一次性 iframe 启动凭证。独立宿主
  `127.0.0.1:4174` 从 query token 动态加载真实 `/embed/launch?token=...`，嵌入端真实兑换 token、读取推荐卡、
  医师在目标“检验危急值需人工确认”卡片内采纳建议，并把 postMessage 回传宿主；`launchCoverageEvidence`
  只有在 spec 通过、非 flaky，且附件 `embed-business-host-launch-codes` 完整包含六个阶段和
  `apiEvidence.launchTokenIssued/launchExchanged/recommendationsRead/feedbackSubmitted/hostMessageReceived`
  全为 true 时，才声明 `scenarios:S8`、`productLayers:DELIVERY_FEEDBACK` 和
  `deliveryShapes:EMBEDDED_COMPONENT`。本批还修复 `EmbedLaunch` 使用 AntD 静态 `message` 导致的浏览器
  console warning，改用 `AntdApp.useApp()`；补齐上一批第三方系统族类型红点：AdapterHub 测试样例携带
  `adapterId`，系统族 Select 接受普通数组选项。
- 第一百三十六批红点根因与定位修复：目标 E2E 最初红于前台建立上下文没有发出
  `/engine/context/snapshots` POST，根因是只填写“异常重点”触发了页面“医技报告项目和报告结论必须同时填写”
  业务校验，已按真实前台补齐医技报告事实。随后红于 iframe 内存在本轮危急值卡和平台基线规则卡两张建议卡，
  全局点击 `/采纳建议/` 在 strict mode 下不明确，已收敛为标题所在卡片内点击；再后红于推荐评估返回顺序不保证
  本轮 candidate card 为 `cards[0]`，已按标题和来源摘要匹配本轮真实推荐卡；最后红于 AntD 静态 message
  console warning，已迁入 App 上下文。全量前端 typecheck 额外暴露 AdapterHub 既有类型红点，已用最小类型
  补齐收口。
- 第一百三十六批验证证据：TDD 红灯
  `npm --prefix frontend run test -- e2eAuthCredentialContract -t "embedded business host"` 曾失败于
  `createClinicalContextFromFrontdesk` 缺 `医技报告项目/报告结论`、仍全局点击 iframe `采纳建议`、以及仍使用
  `payload.data?.cards?.[0]`；实现后同命令转绿。目标真实 E2E 在本地 18080 H2 后端上通过：
  `E2E_API_BASE_URL=http://localhost:18080/medkernel/api/v1 MEDKERNEL_API_PROXY_TARGET=http://localhost:18080 E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-embed-host-coverage npm --prefix frontend run e2e -- --project=chromium embed-business-host.spec.ts`
  通过（1 expected，0 unexpected，0 flaky），仓库外 `results.json` 为 `PASSED`，
  `coverageKeys=["scenarios","productLayers","deliveryShapes"]`，只含 `scenarios:S8`、
  `productLayers:DELIVERY_FEEDBACK` 和 `deliveryShapes:EMBEDDED_COMPONENT`，不含 `serviceCombinations` 或
  `CLINICAL_RUNTIME`。其他新鲜门禁：`npm --prefix frontend run test -- e2eLaunchCoverageEvidence
e2eAuthCredentialContract EmbedLaunch AdapterHub` 通过（74 tests）；`npm --prefix frontend run typecheck`
  通过；E2E TS 检查
  `cd frontend && npx tsc --noEmit --pretty false --skipLibCheck false --allowImportingTsExtensions --moduleResolution bundler --module ESNext --target ES2022 --lib ES2023,DOM --strict --types node e2e/support/launchCoverageEvidence.ts e2e/embed-business-host.spec.ts`
  通过；`node --test scripts/release/launch-coverage-audit.test.mjs` 通过（6 tests）；`git diff --check` 通过。
- 第一百三十六批后续主线：本批仍未发布到 134，未实际执行 134 清库重新部署，也不能把嵌入宿主 S8
  切片等同于完整 S8、完整临床运行组合、离线交付、全角色全功能或总目标完成。后续仍需按真实前台继续补齐
  S0-S40 其余未覆盖项、完整语义族 / 专业域 / 专病十阶段、13 类 runtime 资产逐类业务消费者、五种交付形态
  未覆盖项、两家机构差异化发布 / 回滚、真实备份 / 隔离恢复 / 重启恢复，以及 134 清库部署复演。
- 第一百三十五批本地收尾：继续按全角色真实前台、真实服务链路和上线门禁推进；本批不是菜单、
  文案或片面页面优化，而是把 `diagnosis-knowledge-maintenance.spec.ts` 从专项真实前台链路补成可被浏览器
  覆盖 reporter 消费的 S3 医疗资产生产证据。该 spec 现在只有在医疗引擎运营员真实登记标准发现项术语、
  创建证据完整诊断资产草稿、登记诊断标准和登记验证病例后，才附件 `diagnosis-knowledge-scenario-codes`。
  `launchCoverageEvidence` 只有在该 spec 通过、非 flaky，且附件完整包含 `scenarios:S3`、
  `productLayers:MEDICAL_ASSET`、`semanticFamilies:DISEASE_DIAGNOSIS` 和
  `specialtyDomains:CLINICAL_SPECIALTIES` 时才声明这些覆盖项；不声明 S16 诊断运行、全知识、完整语义族、
  全专业域、专病十阶段或 134 部署覆盖。
- 第一百三十五批验证证据：TDD 红灯
  `npm --prefix frontend run test -- e2eLaunchCoverageEvidence -t "diagnosis knowledge"` 曾失败于
  `expected undefined to deeply equal [ 'S3' ]`；`npm --prefix frontend run test -- e2eAuthCredentialContract -t "diagnosis knowledge"`
  曾失败于 `diagnosis-knowledge-maintenance.spec.ts` 缺 `recordDiagnosisKnowledgeStage`。实现后两者转绿。
  新鲜门禁：`npm --prefix frontend run test -- e2eLaunchCoverageEvidence e2eAuthCredentialContract`
  通过（39 tests）；E2E TS 检查
  `cd frontend && npx tsc --noEmit --pretty false --skipLibCheck false --allowImportingTsExtensions --moduleResolution bundler --module ESNext --target ES2022 --lib ES2023,DOM --strict --types node e2e/support/launchCoverageEvidence.ts e2e/diagnosis-knowledge-maintenance.spec.ts`
  通过；`node --test scripts/release/launch-coverage-audit.test.mjs` 通过（6 tests）；`git diff --check` 通过。
  目标真实前台 E2E 在重启后的本地 18080 H2 后端（当前监听 PID 60679）通过：
  `E2E_API_BASE_URL=http://localhost:18080/medkernel/api/v1 MEDKERNEL_API_PROXY_TARGET=http://localhost:18080 E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-diagnosis-knowledge-coverage npm --prefix frontend run e2e -- --project=chromium diagnosis-knowledge-maintenance.spec.ts`
  通过（1 expected，0 unexpected，0 flaky），仓库外 `results.json` 为 `PASSED`，
  `coverageKeys=["scenarios","productLayers","semanticFamilies","specialtyDomains"]`，只含
  `scenarios:S3`、`productLayers:MEDICAL_ASSET`、`semanticFamilies:DISEASE_DIAGNOSIS` 和
  `specialtyDomains:CLINICAL_SPECIALTIES`。
- 第一百三十五批后续主线：本批仍未发布到 134，未实际执行 134 清库重新部署，也不能把 S3 诊断知识资产生产
  切片等同于 S16 诊断支持运行、全知识或总目标完成。仍需继续补 S0-S40 其余场景、13 类标准患者资源逐类
  真实前台接入、完整语义族、完整专业域、专病十阶段、嵌入组件 / 离线交付、专业协同服务组合、两机构
  差异化发布 / 回滚、真实备份 / 隔离恢复 / 重启恢复，以及 134 清库部署复演。
- 第一百三十四批本地收尾：继续按全角色真实前台、真实服务链路和上线门禁推进；本批不是菜单、
  文案或片面页面优化，而是把已存在但只附截图的 `service-organization-frontdesk.spec.ts` 补成可被浏览器
  覆盖 reporter 消费的 S1/S14 真实前台证据。该 spec 现在只有在平台管理员真实开通服务机构、机构管理员
  首次登录并改密、前台创建医疗机构与科室、前台创建临床账号并绑定科室职责范围、临床账号首次登录后回读
  权限画像、回读新服务机构组织树，并在 finally 中真实停用演练账号后，才附件
  `service-organization-scenario-codes`。`launchCoverageEvidence` 只有在该 spec 通过、非 flaky，且附件完整
  包含 S1/S14、`organizationLevels:HOSPITAL/DEPARTMENT` 和
  `serviceCombinations:ONBOARDING_INTEGRATION/COMPLIANCE_OPERATIONS` 时才声明这些覆盖项；不声明 S0-S40
  全量、完整组织层级、全语义族、专业域、专病十阶段或 134 部署覆盖。
- 第一百三十四批验证证据：TDD 红灯
  `npm --prefix frontend run test -- e2eLaunchCoverageEvidence -t "service organization"` 曾失败于
  `expected undefined to deeply equal [ 'S1', 'S14' ]`；`npm --prefix frontend run test -- e2eAuthCredentialContract -t "service organization"`
  曾失败于 `service-organization-frontdesk.spec.ts` 缺 `recordServiceOrganizationStage`。实现后两者转绿。
  新鲜门禁：`npm --prefix frontend run test -- e2eLaunchCoverageEvidence e2eAuthCredentialContract`
  通过（36 tests）；E2E TS 检查
  `cd frontend && npx tsc --noEmit --pretty false --skipLibCheck false --allowImportingTsExtensions --moduleResolution bundler --module ESNext --target ES2022 --lib ES2023,DOM --strict --types node e2e/support/launchCoverageEvidence.ts e2e/service-organization-frontdesk.spec.ts`
  通过；`node --test scripts/release/launch-coverage-audit.test.mjs` 通过（6 tests）；`git diff --check` 通过。
  目标真实前台 E2E 在重启后的本地 18080 H2 后端（当前监听 PID 48944）通过：
  `E2E_API_BASE_URL=http://localhost:18080/medkernel/api/v1 MEDKERNEL_API_PROXY_TARGET=http://localhost:18080 E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-service-organization-coverage npm --prefix frontend run e2e -- --project=chromium service-organization-frontdesk.spec.ts`
  通过（1 expected，0 unexpected，0 flaky），仓库外 `results.json` 为 `PASSED`，
  `coverageKeys=["scenarios","organizationLevels","serviceCombinations"]`，只含
  `scenarios:S1/S14`、`organizationLevels:HOSPITAL/DEPARTMENT` 和
  `serviceCombinations:ONBOARDING_INTEGRATION/COMPLIANCE_OPERATIONS`。
- 第一百三十四批后续主线：本批仍未发布到 134，未实际执行 134 清库重新部署，也不能把 S1/S14
  服务机构与人员账号切片等同于总目标完成。只读子代理 Sartre/Erdos 均确认当前全量覆盖审计缺口仍包括
  S0-S40 未覆盖项、13 类标准患者资源逐类真实前台接入、完整语义族、专业域、专病十阶段、嵌入组件 /
  离线交付、专业协同服务组合、两机构差异化发布 / 回滚、真实备份 / 隔离恢复 / 重启恢复，以及 134 清库部署
  复演；`launch-coverage-audit` 因这些 UNKNOWN 项失败是正确门禁，不能绕过或用局部 E2E 静态包装。
- 第一百三十三批本地收尾：继续按全角色真实前台、真实服务链路和上线门禁推进；本批不是菜单、
  文案或片面页面优化，而是把 `real-frontdesk-rehearsal.spec.ts` 中 S10/S11/S12 的医保审核、
  CLAIM 评价指标激活、质量整改闭环和随访协同链路补成更强的真实前台证据。浏览器覆盖证据现在只有在
  `real-frontdesk-rehearsal.spec.ts` 真实通过、非 flaky，且附件 `real-frontdesk-scenario-codes`
  精确包含 S10/S11/S12 并逐项具备必要前台阶段时，才声明 `launchCoverage.scenarios:S10/S11/S12`；
  不声明 S0-S40 全量、第三方系统族、专病十阶段、完整语义族、专业域或 134 部署覆盖。
- 第一百三十三批修复范围：`frontend/e2e/support/auth.ts` 要求平台标准版本评价指标投影已经同步为
  ACTIVE，否则重发平台标准版本并阻断医院 runtime 复用旧缓存；`PlatformBaselineService` 对已发布资产也
  触发发布同步器，新增 `EvaluationPublicationStatusSynchronizer`，使统一 EVALUATION 资产发布后同步激活
  评价指标投影并下线旧 ACTIVE。`EvaluationEngineService.run` 在绑定机构生效版本时只允许该 runtime
  精确钉住的 ACTIVE 评价指标，可消费平台主租户指标；没有 runtime 时仍按当前租户 ACTIVE 指标校验。
  `real-frontdesk-rehearsal.spec.ts` 精确选择本轮 CDSS/医保上下文快照，机构版本激活前必须勾选 13 类平台
  runtime 基线资产和本院 CLAIM 指标，并断言生成请求 `activeAssets` 与 current runtime 均保留这些资产。
  随访计划响应新增 `templateCode/templateName`，前台优先显示计划响应自带的业务方案名，不再依赖当前模板分页
  列表猜测；随访计划表使用固定列宽、内部滚动和可换行证据标签，办理抽屉在移动视口按 `min(860px, 100vw)`
  和响应式 `Descriptions` 展示，任务、问卷、异常回院证据 ID 均可换行但不隐藏。
- 第一百三十三批红点根因与定位修复：最初真实 E2E 红于本地演练医院 runtime 激活请求曾使用
  `activeAssets: []`，导致字段目录和规则资产未启用；后续红于评价运行按医院租户查平台标准 EVALUATION
  指标抛 `ENG-EVAL-004`，根因是 runtime 选择器允许平台指标但 `run()` 二次按当前租户查询。修复后目标 E2E
  又暴露 CDSS 选择“第 1 个临床快照”可能拿错当前快照、随访方案证据详情只展示 `ftpl...` 不展示业务方案名、
  以及随访计划表长 `planId/patientId/encounterId/templateId` 撑破根布局；均按真实前台和源码合同修复。
  只读子代理 Avicenna 核查确认 S10/S11/S12 覆盖映射没有包装成全量 coverage，但指出医保 helper 仍有
  “选择第 1 个病案快照”回退，已删除并用合同锁住必须选择 `snapshot.snapshotId`。只读子代理 Euclid 指出
  随访业务方案名依赖分页、表格宽度预算不一致和抽屉证据移动端溢出风险，已补后端/前端合同并修复。
- 第一百三十三批验证证据：TDD 红灯包括
  `npm --prefix frontend run test -- Followup -t "随访计划证据标识在列表内换行"` 曾失败于缺
  `tablePanel/tableLayout/followupEvidenceText`；`mvn -f medkernel-backend/pom.xml -Dtest=FollowupEngineServiceTest#generatePlanUsesRuntimeReleasePinnedTemplateTasksAndQuestionnaireBinding test`
  曾编译失败于 `FollowupPlanDetailResponse` 缺 `templateCode()/templateName()`；相关修复后转绿。目标真实前台
  E2E 在 2026-07-06 18:57 后新启动的本地 18080 H2 空库后端（PID 10663）通过：
  `E2E_API_BASE_URL=http://localhost:18080/medkernel/api/v1 MEDKERNEL_API_PROXY_TARGET=http://localhost:18080 E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-real-frontdesk-scenarios npm --prefix frontend run e2e -- --project=chromium real-frontdesk-rehearsal.spec.ts`
  通过（1 expected，0 unexpected，0 flaky），仓库外 `results.json` 为 `PASSED`，`coverageKeys=["scenarios"]`，
  只含 S10/S11/S12。其他门禁：`npm --prefix frontend run test -- Followup` 通过（21 tests）；
  `npm --prefix frontend run test -- e2eAuthCredentialContract e2eLaunchCoverageEvidence` 通过（34 tests）；
  `node --test scripts/release/launch-coverage-audit.test.mjs` 通过（6 tests）；E2E TS 检查
  `cd frontend && npx tsc --noEmit --pretty false --skipLibCheck false --allowImportingTsExtensions --moduleResolution bundler --module ESNext --target ES2022 --lib ES2023,DOM --strict --types node e2e/support/auth.ts e2e/real-frontdesk-rehearsal.spec.ts`
  通过；后端相关窄测
  `mvn -f medkernel-backend/pom.xml -Dtest=PlatformBaselineServiceTest,EvaluationPublicationStatusSynchronizerTest,EvaluationEngineServiceTest,FollowupEngineServiceTest test`
  通过（55 tests）；`git diff --check` 通过。
- 第一百三十三批后续主线：本批仍未发布到 134，未实际执行 134 清库重新部署，也不能把 S10/S11/S12
  真实前台切片等同于总目标完成。仍需继续补 S0-S40 全量、13 类标准患者资源逐类接入、13 类 runtime 资产
  逐类业务消费者、完整全知识 / 语义族 / 专业域 / 专病十阶段、五种交付形态未覆盖项、真实备份 / 隔离恢复 /
  重启恢复、两家机构差异化发布 / 回滚和 134 清库部署复演。当前 18080 后端 PID 10663 在最后一次 Maven
  窄测后未重启；若下一步继续跑真实 E2E，应先重启后端以避免运行时类与 `target/classes` 不一致。
- 第一百三十二批本地收尾：继续按全角色真实前台、真实服务链路和上线门禁推进；本批不是菜单、
  文案或片面页面优化，而是把第三方系统族从旧 `sourceSystem`/名称推断收敛为一等
  `systemFamilyCode` 合同，并用真实 Playwright 前台操作生成浏览器上线覆盖证据。本批新增
  `third-party-system-families-rehearsal.spec.ts`：平台管理员真实登录 `/adapter/hub`，逐类创建 13 类
  第三方系统族适配器和接入申请，断言创建响应回传 `adapterId`、`systemFamilyCode`、`sourceSystem`、
  `NOT_CONNECTED`，再通过真实后端 API 回读 13 个 `systemFamilyCode` 附件，执行一次健康诊断和数据质量报告，
  继续要求断连/缺口诚实暴露。`launchCoverageEvidence` 只有在该 spec 通过、非 flaky、且附件完整回读 13 个
  code 时才声明 `productLayers:DATA_INTEROPERABILITY`、`deliveryShapes:API_EVENT`、
  `serviceCombinations:THIRD_PARTY_INTERFACE` 和 13 个 `thirdPartySystemFamilies`；失败、flaky、
  缺附件或 code 不全都不得声明覆盖。
- 第一百三十二批修复范围：后端 `IntegrationOnboarding`、五方言 V1 baseline、schema JSON、
  创建请求 / 响应 DTO、AdapterHub 必接清单均新增并持久化 `system_family_code`；`IntegrationService`
  用产品范围 13 类系统族作为权威清单，不再通过适配器 ID、名称或 `sourceSystem` substring 推断覆盖。
  `IntegrationOnboardingResponse` 新增 `adapterId`，ADAPTER 路线响应和列表回读必须暴露绑定适配器身份，
  FHIR 路线为 `null`。前端 `AdapterHub` 表单、列表、API 类型和测试同步 `systemFamilyCode` / `adapterId`，
  并修正 PACS/RIS 文案包含“超声”、护理族包含“手术室”。`e2eAuthCredentialContract` 锁住第三方系统族证据回读
  必须走真实 `apiBase` 后端，不得退回前端相对 `/api/v1`；E2E 按真实按钮文案“健康诊断”触发后端健康检查。
- 第一百三十二批红点根因与定位修复：目标真实 E2E 最初红于接入申请 POST 响应没有 `adapterId`，
  根因是 DTO 未暴露绑定适配器身份，已用后端红灯合同锁住 ADAPTER 返回 ID、FHIR 返回 `null`、列表可回读。
  随后红于 `page.request.get("/api/v1/engine/integration/onboardings")` 解析到前端 HTML `<!doctype...`，
  根因是 Playwright APIRequestContext 相对 URL 命中前端而非后端，已改为 `${apiBase}/engine/integration/onboardings`。
  再后红于等待健康检查响应超时，根因是前台按钮真实文案为“健康诊断”而测试找“健康检查”，已按真实前台操作修复。
  每次 Maven 重编译后均重启本地 18080 dev/H2 后端，避免运行时类与 `target/classes` 不一致。
- 第一百三十二批验证证据：TDD 红灯
  `mvn -f medkernel-backend/pom.xml -Dtest=IntegrationServiceTest#onboardingLifecycleComposesAdapterAndFhirRoutesWithoutFakingConnectivity test`
  曾失败于 `IntegrationOnboardingResponse` 缺 `adapterId()`，补 DTO/service 后通过。源码合同
  `npm --prefix frontend run test -- e2eAuthCredentialContract -t "third-party family evidence API readback"`
  曾失败于 E2E 未导入 `apiBase` 且使用前端相对 `/api/v1`，修复后通过。目标真实前台 E2E 在重启后的本地
  18080 H2 空库通过：
  `E2E_API_BASE_URL=http://localhost:18080/medkernel/api/v1 MEDKERNEL_API_PROXY_TARGET=http://localhost:18080 E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-third-party-families-coverage npm --prefix frontend run e2e -- --project=chromium third-party-system-families-rehearsal.spec.ts`
  通过（1 expected，0 unexpected，0 flaky），仓库外 `results.json` 为 `PASSED`，`coverageKeys` 为
  `productLayers/deliveryShapes/serviceCombinations/thirdPartySystemFamilies`，附件和覆盖均含完整 13 个 code。
  其他门禁：`npm --prefix frontend run test -- e2eLaunchCoverageEvidence` 通过（6 tests）；
  `npm --prefix frontend run test -- AdapterHub -t "loads adapter hub maintenance|canonical third-party system family|stable business identity labels for onboarding"`
  通过（3 tests）；`npm --prefix frontend run test -- hooks -t "integration adapter api helpers"` 通过（7 tests）；
  `node --test scripts/release/launch-coverage-audit.test.mjs` 通过（6 tests）；
  `node scripts/db/generate-migrations.mjs --check` 通过；
  `cd frontend && npx tsc --noEmit --pretty false --skipLibCheck false --allowImportingTsExtensions --moduleResolution bundler --module ESNext --target ES2022 --lib ES2023,DOM --strict --types node e2e/third-party-system-families-rehearsal.spec.ts`
  通过；后端目标窄测
  `mvn -f medkernel-backend/pom.xml -Dtest=IntegrationServiceTest#adapterHubStatusIncludesAllRequiredSystemFamiliesWithoutFakingMissingConnections,IntegrationServiceTest#onboardingRequiresCanonicalThirdPartySystemFamily,IntegrationServiceTest#requiredSystemFamilyChecklistDoesNotInferCoverageFromAdapterName,IntegrationServiceTest#onboardingLifecycleComposesAdapterAndFhirRoutesWithoutFakingConnectivity,MigrationBaselineContractTest#integrationOnboardingPersistsCanonicalThirdPartySystemFamily test`
  通过（5 tests）；`git diff --check` 通过。
- 第一百三十二批后续主线：本批仍未发布到 134，未实际执行 134 清库重新部署，也不能把第三方系统族前台接入、
  断连降级和覆盖附件切片等同于总目标完成。后续继续补 S0-S40、专病十阶段、完整语义族、专业域、全知识、
  13 类 runtime 资产逐类业务消费者、真实备份 / 隔离恢复 / 重启恢复、两家机构差异化发布 / 回滚，以及全角色
  真实前台体验复演；发现红点继续先复现、定根因，再修复，不做片面优化或假覆盖。
- 第一百三十一批本地收尾：继续按全角色真实前台、真实服务链路和上线门禁推进；本批不是菜单、
  文案或片面页面优化，而是把已经真实通过的浏览器前台发布回滚演练纳入上线覆盖证据聚合，并继续防止
  “单一浏览器切片包装成全量覆盖”。`frontend/e2e/support/launchCoverageEvidence.ts` 新增
  `runtime-release-frontdesk.spec.ts` proof：只有该 spec 通过且非 flaky 时，才声明
  `productLayers:RELEASE_GOVERNANCE`、13 类 `versionedAssets`、`deliveryShapes:MANAGEMENT_WORKSPACE/API_EVENT`
  和 `serviceCombinations:CLINICAL_RUNTIME/THIRD_PARTY_INTERFACE`。这些声明只对应
  `runtime-release-frontdesk.spec.ts` 已经真实断言的“13 类平台标准内容前台可见并勾选 → 生成/回滚请求携带
  `activeAssets` → 医院 current runtime 13 类 ACTIVE 且有 `versionId` → 第三方运行契约读取同一修订号”，
  不等同于 13 类资产逐类业务消费者证据。
- 第一百三十一批修复范围：`e2eLaunchCoverageEvidence.test.ts` 先红于 runtime 发布 spec 通过后没有覆盖声明，
  再补 reporter 映射转绿；`launch-coverage-audit.test.mjs` 新增“完整覆盖审计拒绝把单一浏览器角色切片包装成
  全量覆盖”回归，构造仅含 12 个 `stakeholderViews` 的 browser evidence，必须因缺
  `productLayers:DATA_INTEROPERABILITY` 等前置证据而失败。实现继续禁止静态声明 S0-S40、第三方系统族、
  专病十阶段、完整语义族或专业域。
- 第一百三十一批验证证据：TDD 红灯
  `npm --prefix frontend run test -- e2eLaunchCoverageEvidence` 曾失败于
  `expected undefined to deeply equal [ 'RELEASE_GOVERNANCE' ]`；补实现后同命令通过（4 tests）。
  `node --test scripts/release/launch-coverage-audit.test.mjs` 通过（6 tests）。真实 Playwright 在当前本地
  18080 H2 后端上通过：
  `E2E_API_BASE_URL=http://localhost:18080/medkernel/api/v1 MEDKERNEL_API_PROXY_TARGET=http://localhost:18080 E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-runtime-coverage npm --prefix frontend run e2e -- --project=chromium runtime-release-frontdesk.spec.ts`
  通过，仓库外 `results.json` 只包含
  `coverageKeys=["productLayers","versionedAssets","deliveryShapes","serviceCombinations"]`；组合复跑
  `runtime-release-frontdesk.spec.ts stakeholder-view-rehearsal.spec.ts` 通过（2 expected，0 unexpected，
  0 flaky），仓库外 `/tmp/medkernel-e2e-combined-coverage/report/results.json` 只包含
  `stakeholderViews/productLayers/versionedAssets/deliveryShapes/serviceCombinations`，并明确不包含
  `scenarios`、`thirdPartySystemFamilies`、`specialDiseaseStages`、`semanticFamilies`、`specialtyDomains`。
- 第一百三十一批后续主线：本批仍未发布到 134，未实际执行 134 清库重新部署，也未补齐
  S0-S40、第三方系统族、专病十阶段、完整语义族、专业域、五种交付形态中的未覆盖项，以及
  13 类资产逐类业务消费者证据。只读核查确认当前 13 类资产已有“清单/运行契约消费者”证据，但
  `SAFETY/CDSS_RISK` 红线命中和风险矩阵版本、`PATHWAY/ORDER_SET` 路径推进、`VALUE_SET/FORMULA/ACTION_CARD`
  规则展开、`FIELD_CATALOG/TERMINOLOGY` 集成运行、`FOLLOWUP` 版本证据化仍应继续补真实 E2E 或后端合同。
- 第一百三十批本地收尾：继续按全角色真实前台、真实服务链路和上线门禁推进；本批不是菜单、
  文案或片面页面优化，而是让浏览器 E2E 阶段在真实 Playwright 运行结束后输出可被
  `launch-coverage-audit` 消费的仓库外上线覆盖证据摘要。`frontend/playwright.config.ts` 保留 HTML 和
  原生 Playwright JSON 报告，但原生报告改写到 `playwright-results.json`；新增
  `frontend/e2e/support/launchCoverageReporter.ts` 写出审计消费的 `report/results.json`，新增
  `frontend/e2e/support/launchCoverageEvidence.ts` 只从真实通过且非 flaky 的 spec 派生
  `launchCoverage`。本批未推送远程、不合并 `main`。
- 第一百三十批修复范围：当前浏览器证据只声明 `stakeholder-view-rehearsal.spec.ts` 实际证明的
  12 类业务视角 `stakeholderViews`，不会静态声明 S0-S40、第三方系统族、专病十阶段或其他未由本轮
  spec 证明的覆盖项；spec 失败、运行结果 unexpected、或 Playwright `outcome()` 为 `flaky` 时不声明覆盖。
  `e2eLaunchCoverageEvidence.test.ts` 锁住缺证、失败、flaky 不得声明覆盖；`productRoleJourneys.test.ts`
  不再依赖默认代理 URL 的单引号表现；`e2eAuthCredentialContract.test.ts` 修复禁用资产测试数据的字面量
  类型过窄；`e2e/support/auth.ts` 删除未使用派生常量。`docs/audit/product-function-catalog.md` 由生成器同步
  当前安全红线草稿入口 `POST /api/v1/engine/safety/redlines`。
- 第一百三十批红点根因与定位修复：Playwright 原生 JSON 只有 `stats/suites`，不含上线覆盖矩阵的
  `launchCoverage`，导致 `browser-e2e` 作为前置阶段无法向收紧后的覆盖审计提供逐项证据。实现时避免把
  局部 E2E 通过包装成全量覆盖：真实验证只跑 `stakeholder-view-rehearsal.spec.ts` 后，仓库外
  `/tmp/medkernel-e2e-launch-evidence/report/results.json` 仅包含 `coverageKeys=["stakeholderViews"]` 和
  12 类业务视角，不包含 `scenarios` 或 `thirdPartySystemFamilies`。前端重门禁首次红于产品目录可复现检查和
  单引号 URL 断言；根因分别是安全红线草稿 POST 入口已新增但目录未同步、以及测试对格式化引号过窄，已按
  生成器事实和更稳健断言修复。
- 第一百三十批验证证据：`npm --prefix frontend run verify` 通过（116 files，1014 tests，仅保留既有
  AntD Timeline warning）；`npm --prefix frontend run test -- productCatalog productRoleJourneys
e2eLaunchCoverageEvidence e2eAuthCredentialContract` 通过（42 tests）；`npm --prefix frontend run typecheck`
  通过；`node --test scripts/release/full-system-rehearsal.test.mjs scripts/release/launch-coverage-audit.test.mjs`
  通过（11 tests）；`bash deploy/onprem/tests/validate-medkernel-post-rehearsal-verify.sh` 通过；目标真实前台
  E2E 在本地 18080 H2 后端和 Playwright 启动的 Vite 前端上通过：
  `E2E_API_BASE_URL=http://localhost:18080/medkernel/api/v1 MEDKERNEL_API_PROXY_TARGET=http://localhost:18080 E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-launch-evidence npm --prefix frontend run e2e -- --project=chromium stakeholder-view-rehearsal.spec.ts`
  通过，并生成仓库外 `results.json`/`playwright-results.json` 分离证据；`node scripts/audit/export-product-capabilities.mjs --check`
  通过；`git diff --check` 退出码 0。
- 第一百三十批后续主线：本批仍未发布到 134，未实际执行 134 清库重新部署，也未补齐 S0-S40、
  第三方系统族、专病十阶段、全交付形态、真实备份/隔离恢复/重启恢复、两家机构差异化发布 / 回滚等
  `browser-e2e` 逐项 `launchCoverage`。下一步应继续把真实浏览器 spec 与覆盖项建立一一对应证据，而不是
  扩大 reporter 静态声明；发现红点继续先复现、定根因，再修复。
- 第一百二十九批本地收尾：继续按全角色真实前台、真实服务链路和上线门禁推进；本批不是菜单、
  文案或片面页面优化，而是修复上线覆盖审计“静态矩阵自证全量通过”的门禁漏洞。覆盖审计现在只能从
  前置阶段实际输出的 `launchCoverage` 逐项聚合，覆盖行必须带 `evidenceStage`、`evidencePath`、
  `evidenceKey`、`observedCode`、`observedStatus`、`observedAt`，且 `evidenceStage` 不得为
  `launch-coverage`。本批未推送远程、不合并 `main`。
- 第一百二十九批修复范围：新增 `scripts/release/stage-launch-coverage-lib.mjs` 统一生成阶段覆盖声明；
  `full-system-rehearsal-lib.mjs` 的覆盖矩阵基线从静态 `PASSED` 改为 `UNKNOWN`，新增
  `buildLaunchCoverageFromStageEvidence`，只接受 account-bootstrap、model-provider、platform-baseline、
  sandbox、full-knowledge、runtime-resilience、browser-e2e 等前置阶段证据；`launch-coverage-audit.mjs`
  改为读取各阶段真实 evidence 后聚合并再跑完整矩阵断言。各阶段脚本只声明自身能真实证明的覆盖项：
  接管账号覆盖治理、平台、医院；Provider 覆盖来源发现、文档抽取、运行测试；平台基线覆盖字段目录和
  已发布全知识；全知识覆盖 11 个知识域；运行韧性覆盖引擎核心、质量改进、合规运行；沙盘覆盖
  13 类标准患者资源、临床运行和专病路径。`deploy/onprem/medkernel-post-rehearsal-verify.sh` 同步禁止
  post-rehearsal 验证接受覆盖审计自证；`docs/DEPLOYMENT_AND_REHEARSAL.md` 写明逐项证据字段要求。
- 第一百二十九批红点根因与定位修复：代码审查发现 `launch-coverage-audit` 原本先校验阶段 PASS，再调用
  `buildRequiredLaunchCoverage()` 把全矩阵静态标成 `PASSED`，会在缺失浏览器 E2E、S0-S40、第三方系统族、
  专病路径等真实证据时误放行上线覆盖审计。修复后，缺任一前置阶段逐项 coverage、覆盖行自称来自
  `launch-coverage`、或观测码 / 状态 / 引用字段不匹配都会失败；因此当前真实全量覆盖在尚未补齐这些
  evidence 前应继续失败，这是预期门禁，不是回归。
- 第一百二十九批验证证据：`node --test scripts/release/full-system-rehearsal.test.mjs scripts/release/launch-coverage-audit.test.mjs scripts/release/launch-account-bootstrap.test.mjs scripts/release/model-provider-launch.test.mjs scripts/release/platform-baseline-bootstrap.test.mjs scripts/release/runtime-resilience-rehearsal.test.mjs scripts/knowledge/full-knowledge-rehearsal.test.mjs scripts/sandbox/seed-scenarios.test.mjs scripts/sandbox/scenario-rules.test.mjs`
  通过（76 tests）；`bash deploy/onprem/tests/validate-medkernel-post-rehearsal-verify.sh` 通过；
  触达文件 Prettier check 通过；`git diff --check` 退出码 0（仅保留 Git 对既有 CRLF→LF 的提示）。
- 第一百二十九批后续主线：本批仍未发布到 134，未实际执行 134 清库重新部署，也未完成浏览器全量
  E2E、全角色真实前台、S0-S40、第三方系统族、专病路径、真实备份/隔离恢复/重启恢复、两家机构差异化
  发布 / 回滚等逐项 `launchCoverage` evidence。下一步继续补前置阶段真实证据和全角色真实操作复演；
  发现红点继续先复现、定根因，再修复，不做片面优化或假覆盖。
- 第一百二十八批本地收尾：继续按全角色真实前台、真实服务链路和上线门禁推进；本批不是菜单、
  文案或片面页面优化，而是补齐 13 类 runtime 闭包的真实运行消费者证据、SAFETY 红线运行时条件树执行、
  以及医技报告解读在普通临床权限下通过上下文快照消费 runtime 知识的前台证据。本批使用只读子代理
  Maxwell 核查报告解读后端链路与第三方 runtime current 权限边界；子代理未改文件、未提交。本批不推送
  远程、不合并 `main`。
- 第一百二十八批修复范围：`runtime-release-frontdesk.spec.ts` 在 `/config/releases` 机构生效版本页断言
  “平台标准内容”清单中 13 类 `requiredRuntimeAssetsForRehearsal` 均可见且启用复选框已勾选；前台生成
  新机构生效版本和回滚后，继续以授权 `engine-operator` 会话调用
  `/engine/integration/knowledge-runtime/runtime-release/current`，断言第三方运行契约 `contractVersion=v1`、
  `revisionNo` 与前台操作一致、13 类资产均为 `ACTIVE` 且带 `versionId`。`ClinicalRedlineMatcher` 修复
  SAFETY 红线 `conditionDsl`：若为完整 `when/then` 规则仍走规则 DSL，否则按条件树调用
  `evaluateConditionTree`，不再把安全红线条件误当完整规则动作包；`frontend/e2e/support/auth.ts` 的本地
  演练安全红线条件同步使用条件引擎所需 `fact: "medications[].dose"`。`stakeholder-view-rehearsal.spec.ts`
  不再让医技/`clinical-user` 直接读取第三方 runtime current 端点，而是断言前台创建的 context snapshot
  返回 `runtimeReleaseId`，报告解读响应 `runtimeReleaseId` 等于该快照版本，且
  `plat:diagnostic_item:lab-potassium` 解读项返回 `sourceVersionId`、`versionNo`，摘要包含同一
  `runtimeReleaseId`。信息科长数据质量报告断言同步覆盖空库真实缺口“未登记院内系统适配器”和已有适配器
  断连缺口“未接通适配器”，仍要求默认摘要不泄漏 `NOT_CONNECTED` 原始枚举。`e2eAuthCredentialContract`
  锁住上述源码合同：stakeholder E2E 不得调用 third-party current，runtime 发布 E2E 保留 current 消费证据，
  SAFETY 条件使用 `fact`。
- 第一百二十八批红点根因与定位修复：真实全角色 E2E 先红于医技报告解读前置断言
  `GET /engine/integration/knowledge-runtime/runtime-release/current` 返回 403，根因不是业务缺资产，而是测试把
  `asset.read` 的第三方运行契约端点误放到 `clinical-user` 医技角色链路中。后端源码核查确认
  `ReportInterpretationService` 通过 `snapshot.runtimeReleaseId` 调
  `RuntimeReleaseDiagnosticItemSelector` 读取当前机构生效版本中的 ACTIVE 诊断项目知识；第三方 current 端点
  由 `ThirdPartyKnowledgeRuntimeController` 保护 `@perm.has('asset.read')`，普通临床角色不应扩权。修复后
  E2E 越过医技链路，又红于信息科长数据质量报告摘要找不到“未接通适配器”；失败现场和
  `IntegrationService.generateDataQualityReport` 证明空库真实状态是“未登记院内系统适配器”，测试断言过窄，
  已改为接受真实业务缺口集合且继续禁止默认层枚举泄漏。随后 runtime 发布 E2E 曾红于发布影响评估 500，
  日志为 `NoClassDefFoundError: ReleaseSimulationService$1`；根因是同一 18080 Spring Boot 进程运行时并行
  执行 Maven 窄测重编译 `target/classes`，导致懒加载合成类短暂缺失。重启干净后端进程、不再并行编译后，
  同一 E2E 通过；该红点未改业务代码。
- 第一百二十八批验证证据：后端窄测
  `mvn -f medkernel-backend/pom.xml -Dtest=ClinicalRedlineMatcherTest test` 通过（4 tests）。源码合同
  `npm --prefix frontend run test -- e2eAuthCredentialContract` 通过（22 passed）。E2E 类型检查
  `cd frontend && npx tsc --noEmit --pretty false --skipLibCheck false --allowImportingTsExtensions --moduleResolution bundler --module ESNext --target ES2022 --lib ES2023,DOM --strict --types node e2e/stakeholder-view-rehearsal.spec.ts e2e/runtime-release-frontdesk.spec.ts`
  通过；触达文件 Prettier check 通过；`git diff --check` 退出码 0。真实前台 E2E 在 2026-07-06
  14:21 后新启动的 18080 后端 / H2 空库上通过：
  `E2E_API_BASE_URL=http://localhost:18080/medkernel/api/v1 MEDKERNEL_API_PROXY_TARGET=http://localhost:18080 npm --prefix frontend run e2e -- --project=chromium runtime-release-frontdesk.spec.ts`
  通过（1 passed，9.3 秒），随后同一干净后端进程复跑
  `E2E_API_BASE_URL=http://localhost:18080/medkernel/api/v1 MEDKERNEL_API_PROXY_TARGET=http://localhost:18080 npm --prefix frontend run e2e -- --project=chromium stakeholder-view-rehearsal.spec.ts`
  通过（1 passed，1.4 分钟），覆盖 12 类业务视角真实前台动作。
- 第一百二十八批后续主线：本批仍未发布到 134，未实际执行 134 清库重新部署，也未完成真实备份、
  隔离恢复、重启恢复、13 类患者资源逐类接入、11 类知识全流程、两家机构差异化发布 / 回滚和 S0-S40
  全量前台验收；不能把本地 H2 空库 runtime 运行消费者与 12 视角 E2E 切片等同于总目标完成。下一步继续从
  134 空库部署、全知识 / 全患者资源 / 全角色矩阵广度推进；跑真实 E2E 时避免与 Maven 编译并行使用同一
  `target/classes` 后端进程，发现红点仍先复现、定根因，再修复，不做片面优化。
- 第一百二十七批本地收尾：继续按全角色真实前台、真实服务链路和上线门禁推进；本批不是菜单、
  文案或片面页面优化，而是把本地上线演练平台标准版本和医院 runtime 从 3 类资产扩成 13 类资产闭包，
  通过真实服务创建并发布 `KNOWLEDGE`、`TERMINOLOGY`、`RULE`、`PATHWAY`、`EVALUATION`、
  `FOLLOWUP`、`FIELD_CATALOG`、`SAFETY`、`CDSS_RISK`、`VALUE_SET`、`FORMULA`、
  `ORDER_SET`、`ACTION_CARD`，再由前台 `/config/releases` 生成本地上线演练医院生效版本并回滚。
  本批复用上一轮只读子代理关于全资产 runtime 闭包、前台覆盖缺口和 134 上线验收缺口的结论；未新建
  线程，不推送远程、不合并 `main`。
- 第一百二十七批修复范围：`frontend/e2e/support/auth.ts` 导出
  `requiredRuntimeAssetsForRehearsal`，把本地演练 runtime 基线扩为 13 类精确
  `assetType + assetIdentity`，成功缓存命中后也会轻量回读医院 current runtime，缺资产时继续自愈，
  不再因旧缓存跳过资产闭包复核；并新增真实服务候选创建链路：声明式资产
  `/engine/authoring/declarative-assets` 覆盖 `VALUE_SET` / `FORMULA` / `ORDER_SET` /
  `ACTION_CARD`，路径资产使用合法字段目录与普通 `ASSESSMENT` 节点，评价指标会先保证平台组织科室，
  随访模板、CDSS 风险矩阵、术语映射、临床安全红线均走真实 API。术语资产草稿不再把
  `tenant:t-1` 伪组织路径传给统一版本底座，而是交由平台租户解析为唯一平台权威路径
  `/__platform__`。安全红线新增真实草稿入口 `POST /engine/safety/redlines`，必须先登记草稿、
  提交静默试运行证据，再 promote 生成并发布 `SAFETY` 统一资产；promote 时向统一发布服务传入完整
  `VersionPublishQualityGate`，质量门由 DSL 字段目录绑定、风险矩阵/证据引用/版本链、禁止下级关闭、
  安全事件为 0、静默试运行观察窗口和病例计数等真实证据派生，禁止硬编码自签全绿或绕过发布治理。
  `runtime-release-frontdesk.spec.ts` 复用同一份 13 类清单，断言 `/config/releases` 生成机构生效版本的
  POST 请求体 `activeAssets` 精确包含 13 类身份，且回读 current runtime 时 13 类均为 `ACTIVE` 并带有
  `versionId`。
- 第一百二十七批红点根因与定位修复：目标真实 E2E 首先红于
  `POST /engine/terminology/assets/drafts` 返回
  “平台资产必须使用唯一平台权威路径”。源码和窄测已证明修复后的
  `TerminologyAssetDraftService` 传 `organizationScope=null`，真实 18080 上的旧 Java 进程从
  7 月 5 日 23:12 启动，早于当前未提交修复；重启后术语草稿成功生成。随后 E2E 红于
  `POST /engine/safety/redlines:promote` 返回“平台发布质量校验未全部通过”，根因是 SAFETY
  promote 内部调用统一发布服务时质量门为 `null`；先用服务层红灯复现，再补
  `safetyPublishQualityGate` 与组织适用域校验转绿。审查指出的“SAFETY 不能硬编码五项质量门为
  true”Critical 已修复：不通过时抛 `VALIDATION_FAILED`，且不激活红线、不注册资产、不发布。最新后端
  重启后目标 E2E 又红于 `PUT /engine/cdss/risk-matrix` 返回“平台发布质量校验未全部通过”，根因是
  CDSS_RISK 发布命令缺真实派生的 `VersionPublishQualityGate`；已在 `CdssRiskMatrixService` 补齐
  CDSS 风险矩阵、安全基线、禁止自动执行和影响评估摘要后转绿。
- 第一百二十七批验证证据：目标真实前台 E2E
  `E2E_API_BASE_URL=http://localhost:18080/medkernel/api/v1 MEDKERNEL_API_PROXY_TARGET=http://localhost:18080 npm --prefix frontend run e2e -- --project=chromium runtime-release-frontdesk.spec.ts`
  在 2026-07-06 当前 18080 后端进程 / H2 空库重启后通过（1 passed，6.3 秒），同一 H2 进程重复运行再次通过
  （1 passed，6.4 秒），用于补证已有候选 / 已有 release 场景的自举幂等性。源码合同
  `npm --prefix frontend run test -- e2eAuthCredentialContract`
  通过（20 passed），其中锁住 13 类精确 runtime 身份、请求体断言、current runtime 断言和禁止
  `activeAssets: []` 回退。后端红绿：`ClinicalRedlineServiceTest` 先红于
  `publish.qualityGate()` 为 `null`，补质量门后转绿；`CdssRiskMatrixServiceTest` 锁住 CDSS_RISK 发布命令
  必须携带真实质量门。后端相关窄测
  `mvn -f medkernel-backend/pom.xml -Dtest=CdssRiskMatrixServiceTest,TerminologyAssetDraftServiceTest,ClinicalRedlineServiceTest,ClinicalRedlineControllerSecurityTest,ClinicalRuntimeReleaseServiceTest,ReleaseCandidateQueryServiceTest,ClinicalRuntimeDeclarativeAssetResolverTest test`
  通过（54 tests）。E2E support 类型检查
  `cd frontend && npx tsc --noEmit --pretty false --skipLibCheck false --allowImportingTsExtensions --moduleResolution bundler --module ESNext --target ES2022 --lib ES2023,DOM --strict --types node e2e/support/auth.ts e2e/runtime-release-frontdesk.spec.ts`
  通过；触达前端文件 Prettier check 通过；`git diff --check` 退出码 0，仅提示
  `docs/_HANDOFF.md` 下次 Git 触碰会从 CRLF 转 LF。
- 第一百二十七批后续主线：本批仍未发布到 134，未实际执行 134 清库重新部署，也未完成真实备份、
  隔离恢复、重启恢复、13 类患者资源逐类接入、11 类知识全流程、两家机构差异化发布 / 回滚和
  S0-S40 全角色前台演练；不能把本地 H2 空库 runtime 闭包切片等同于总目标完成。下一步继续从全局矩阵推进：
  优先把 13 类 runtime 资产的“候选可见 → 勾选 → 医院生效版本 ACTIVE → 运行消费者读取”补成更完整的
  自动化证据，再扩到 134 清库部署、全知识、全患者资源和全角色真实前台体验；发现红点仍先复现、
  定根因，再修复，不做片面优化。
- 第一百二十六批本地收尾：继续按全角色真实前台、真实服务链路和上线门禁推进；本批不是菜单、
  文案或片面页面优化，而是把部署侧 `backup-restore-drill.sh` 输出的
  `latest-restore-drill.properties` 恢复演练证据与后端 `/system/operations` 运行快照、
  前台 `/system/providers` 证据详情补成更强的上线合同。成功证据现在必须同时具备
  `status=SUCCESS`、合法 `completed_at`、`checksum_file=.sha256`、隔离演练库、正数
  `flyway_schema_history_rows`、非空 RPO/RTO，且演练库不得等于当前运行数据库名；应用只暴露
  “SHA-256 摘要已校验”“隔离库已验证”、RPO/RTO、迁移行数和 latest 证据文件名，不返回真实 dump
  路径或演练库名。本批使用只读子代理 Peirce / Harvey / James 分别核查部署恢复资产、运行保障证据链、
  全角色前台缺口；使用只读代码审查子代理 Ptolemy 审查安全字段和失败关闭，审查指出的 RPO/RTO
  可空、生产库名未对齐两项 Important 已在主线程修复；本批不推送远程、不合并 `main`。
- 第一百二十六批修复范围：`RuntimeBackupDrillEvidenceReader` 新增
  `read(configuredPath, productionDatabaseName)`，解析 properties 时强制校验 checksum、RPO/RTO、
  隔离演练库和迁移历史，失败一律 `INVALID`；`RuntimeOperationsService` 从 `spring.datasource.url`
  提取当前库名传入 reader，只传库名不传连接串。`RuntimeOperationsSnapshot.RuntimeBackupDrillEvidence`
  新增 `checksumEvidence`、`drillDatabaseIsIsolated`、`rpo`、`rto` 安全摘要字段。前端
  `SystemProviders` 在授权运维人员打开“证据详情”后展示校验证据、隔离库已验证和脚本证据 RPO/RTO；
  默认页面仍不展示部署脚本、配置来源或低频诊断。`system-providers-frontdesk.spec.ts` 和
  `e2eAuthCredentialContract.test.ts` 同步锁住真实 `/system/operations` 读取、证据详情展示和禁止
  写接口 / 本地命令执行。`RuntimeConfigurationContractTest` 加强脚本输出字段、latest 原子替换和
  恢复演练字段集合合同。
- 第一百二十六批红绿事实：后端测试先红于新增 `checksumEvidence` / `drillDatabaseIsIsolated` /
  `rpo` / `rto` 访问器不存在和 `RuntimeBackupDrillEvidence` 构造器缺参；实现安全摘要字段后转绿。
  前端 `SystemProviders` 测试先红于证据详情找不到“SHA-256 摘要已校验”，补页面诊断展示后转绿。
  代码审查后又新增红灯锁住“RPO/RTO 缺失仍 SUCCESS”和“自定义生产库名 `medkernel_prod` 被当作隔离库”
  两个缺口，先红于 overload 与 service 调用不存在，随后通过 reader overload、JDBC URL 库名解析和
  失败关闭条件转绿。
- 第一百二十六批验证证据：部署资产与上线脚本合同
  `bash deploy/docker/tests/validate-deployment-assets.sh && bash deploy/onprem/tests/validate-medkernel-fresh-deploy.sh && bash deploy/onprem/tests/validate-medkernel-failure-recovery.sh`
  通过；后端运行保障窄测
  `mvn -f medkernel-backend/pom.xml -Dtest=RuntimeConfigurationContractTest,RuntimeBackupDrillEvidenceReaderTest,RuntimeOperationsServiceTest test`
  通过（15 tests）；前端目标单测和源码合同
  `npm --prefix frontend run test -- SystemProviders e2eAuthCredentialContract -t
"system providers frontdesk|SystemProviders"` 通过（5 passed，16 skipped）；触达文件 Prettier check
  通过；`npm --prefix frontend exec tsc -- --noEmit --pretty false --skipLibCheck false --project frontend/tsconfig.json`
  通过；目标真实前台 E2E
  `E2E_API_BASE_URL=http://localhost:18080/medkernel/api/v1 MEDKERNEL_API_PROXY_TARGET=http://localhost:18080 npm --prefix frontend run e2e -- --project=chromium system-providers-frontdesk.spec.ts`
  通过（2 passed，约 7.5 秒）；本批后端修复前已跑前端重门禁
  `npm --prefix frontend run verify` 通过（115 files，1006 tests，仅既有 AntD Timeline warning）；
  `git diff --check` 退出码 0。
- 第一百二十六批后续主线：本批仍未发布到 134，未实际执行 134 清库重新部署，也未真实运行备份、
  隔离恢复、重启恢复和全功能 / 全知识复演；不能把恢复演练证据合同补强等同于总目标完成。下一步继续优先
  覆盖 134 空库首启与统一迁移、真实备份恢复 / 重启恢复证据、全资产 runtime 闭包、11 类知识全流程复演、
  两家机构差异化发布 / 回滚，以及 S16-S40 专业协同前台闭环；发现红点继续先复现、定根因，再按上线级标准修复。
- 第一百二十五批本地收尾：继续按全角色真实前台、真实服务链路和上线门禁推进；本批不是菜单、
  文案或片面 UI 优化，而是补齐 `/system/providers` 服务运行保障真实前台只读演练切片，验证平台管理员
  能从真实前台读取 `/api/v1/system/operations` 运行快照，核查核心服务、依赖服务、备份恢复 RPO/RTO、
  SHA-256 校验策略、恢复演练状态、国产化档案，以及在“证据详情”打开后才展示部署档案、数据库方言、
  迁移路径、备份脚本、恢复脚本和演练证据引用；同时验证临床账号无权直接读取运维快照，也不能在前台看到
  运维数据。本批不执行备份或恢复脚本，只验证核心 §12 要求的“应用只读呈现备份就绪状态和演练证据”；
  本批未使用子代理，不推送远程、不合并 `main`。
- 第一百二十五批修复范围：新增 `frontend/e2e/system-providers-frontdesk.spec.ts`。用例先用平台管理员真实
  会话读取 `/system/operations` API，断言响应包含 `database`、`backup-restore`、RPO、RTO、校验策略、
  `backup.sh` / `restore.sh` 只读证据和国产化目标操作系统；随后进入 `/system/providers` 前台，断言
  “服务运行保障”主页面没有权限错误，展示备份恢复就绪卡、依赖诚实降级说明或全部依赖正常状态，并在打开
  “证据详情”后展示部署和备份恢复诊断字段。第二个用例用 `clinical-user` 真实会话直接请求
  `/system/operations`，断言 403，再访问 `/system/providers` 断言只呈现“当前权限不足”，且未发起运维快照
  读取、未展示关系数据库或备份恢复就绪。`frontend/src/test/e2eAuthCredentialContract.test.ts` 新增源码合同，
  要求服务运行保障演练必须覆盖 `/system/providers`、`/system/operations`、备份恢复、证据详情、权限隔离，
  且不得引入 `postApi` 或本地命令执行。
- 第一百二十五批红点根因与定位修复：源码合同先红于
  `ENOENT: no such file or directory, open 'e2e/system-providers-frontdesk.spec.ts'`，新增 E2E 后转绿。
  目标真实 E2E 首轮红于 `依赖服务` 文本 strict locator 命中页面说明和统计标题两处，第二轮红于校验策略同时
  出现在依赖表和备份卡，第三轮红于短值 `h2` 同时出现在依赖详情、迁移路径和数据库方言统计；均为测试定位
  过宽，不是页面或后端业务失败。已分别改为精确文本和卡片作用域定位，保持产品页面与后端服务不变。
- 第一百二十五批验证证据：`npm --prefix frontend run test -- e2eAuthCredentialContract -t
"system providers frontdesk"` 通过（1 passed，16 skipped；曾先红后绿）；触达文件 Prettier check
  `npm --prefix frontend exec prettier -- --check frontend/e2e/system-providers-frontdesk.spec.ts
frontend/src/test/e2eAuthCredentialContract.test.ts` 通过；目标真实前台 E2E
  `E2E_API_BASE_URL=http://localhost:18080/medkernel/api/v1 MEDKERNEL_API_PROXY_TARGET=http://localhost:18080 npm --prefix frontend run e2e -- --project=chromium system-providers-frontdesk.spec.ts`
  通过（2 passed，约 5.8 秒）；`npm --prefix frontend run test -- SystemProviders operationalControlPages
e2eAuthCredentialContract` 通过（31 tests）；`npm --prefix frontend exec tsc -- --noEmit --pretty false
--skipLibCheck false --project frontend/tsconfig.json` 通过；后端运行保障窄测
  `mvn -f medkernel-backend/pom.xml -Dtest=RuntimeOperationsServiceTest,RuntimeBackupDrillEvidenceReaderTest,RuntimeConfigurationContractTest test`
  通过（11 tests）；前端重门禁 `npm --prefix frontend run verify` 通过（115 files，1006 tests，仅既有
  AntD Timeline warning）。
- 第一百二十五批后续主线：本批仍未发布到 134，尚未做 134 清库重新部署，也未完成真实备份、隔离恢复、
  重启恢复、全功能与全知识全流程复演；不能把服务运行保障只读前台证据等同于总目标完成。下一步继续按
  全角色真实操作扩面，优先覆盖 134 空库首启与统一迁移、实际备份恢复/重启恢复证据、全资产 runtime 闭包
  复核、全知识流程复演，以及跨角色真实前台体验中尚未跑透的端到端缺口；发现红点继续先复现、定根因，
  再按上线级标准修复，不做片面优化。
- 第一百二十四批本地收尾：继续按全角色真实前台、真实服务链路和上线门禁推进；本批不是菜单、
  文案或片面 UI 优化，而是补齐身份来源绑定真实前台上线演练切片：平台管理员先在
  `/admin/users` 前台创建两个真实临时人员账号，再进入 `/security/identity-binding` 真实前台对
  其中一个人员执行院内工号绑定，随后验证同一外部身份不能复用到第二个真实人员、前台列表不返回身份原文
  或摘要字段、绑定可通过“解除身份来源”前台弹层按乐观版本解绑，最后停用两名临时账号。本批使用只读子代理
  Goodall 审查身份来源演练覆盖、XSRF 写请求路径、清理污染和不落库证据；子代理未改文件，反馈中的清理和
  持久化证据风险已在主线程修复；本批不推送远程、不合并 `main`。
- 第一百二十四批修复范围：新增 `frontend/e2e/identity-binding-frontdesk.spec.ts`，真实操作
  `/admin/users` 新增人员、记录一次性凭证弹层、断言人员建档不预置身份来源，再真实操作
  `/security/identity-binding` 单个绑定、重复外部身份冲突和解绑。重复绑定检查使用
  `postApi(page, "/compliance/identity-bindings", ...)`，确保带同一会话的 `X-XSRF-TOKEN` 页面安全凭证；
  清理逻辑新增 `cleanupIdentityBindingRehearsal`，失败路径也会先读取当前绑定状态，必要时用 API
  后备解绑，再通过 `Promise.allSettled` 并行停用两个临时账号并汇总清理失败，避免一个清理失败挡住另一个。
  `frontend/src/test/e2eAuthCredentialContract.test.ts` 新增源码合同，锁住身份来源演练必须覆盖绑定、解绑、
  原文安全、`postApi` 写请求和失败路径清理。`IdentityBindingControllerTest` 加强后端契约：保存后断言
  `subjectHint` 仅为脱敏尾号，并通过 `INFORMATION_SCHEMA.COLUMNS` 确认
  `mk_compliance_identity_binding` 不存在 `EXTERNAL_SUBJECT` / `EXTERNAL_IDENTITY` /
  `SUBJECT_PLAINTEXT` 原文字段。
- 第一百二十四批红点根因与定位修复：强化“第二真实用户复用同一外部身份必须冲突”后，目标 E2E 初始红于
  `duplicateResponse.status()` 期望 409、实际 403。按系统调试先复现并把响应 body 打入断言，确认 403
  为 `缺少或不匹配的页面安全凭证`，不是业务重复身份判断；根因是重复绑定请求裸用
  `page.request.post(${apiBase}/compliance/identity-bindings)`，绕过了现有 E2E 写请求工具对后端
  `XSRF-TOKEN` 的读取和 `X-XSRF-TOKEN` 头注入。改为 `postApi` 后请求进入后端业务层，返回
  409 且包含“该外部身份已绑定其他用户”。子代理随后指出“原文不落库”不能只靠前台列表不展示证明、
  失败路径清理可能污染，已分别用后端列级契约和并行后备清理补齐。
- 第一百二十四批红绿事实：合同测试先红于
  `expected ... to contain 'cleanupIdentityBindingRehearsal'`，实现失败路径清理后转绿。目标真实前台
  E2E 先红于 403 页面安全凭证，改走 `postApi` 后转绿；加强清理后再次复跑仍通过。后端
  `IdentityBindingControllerTest#createsBindingWithoutPersistingExternalIdentityPlaintext` 在既有
  digest 断言基础上新增脱敏提示和原文字段不存在断言，配合实体和迁移约束证明持久化层不保存外部身份原文。
- 第一百二十四批验证证据：`npm --prefix frontend run test -- e2eAuthCredentialContract -t
"identity binding frontdesk"` 通过（1 passed，15 skipped；曾先红后绿）；触达文件 Prettier check
  `npm --prefix frontend exec prettier -- --check frontend/e2e/identity-binding-frontdesk.spec.ts
frontend/src/test/e2eAuthCredentialContract.test.ts` 通过；目标真实前台 E2E
  `E2E_API_BASE_URL=http://localhost:18080/medkernel/api/v1 MEDKERNEL_API_PROXY_TARGET=http://localhost:18080 npm --prefix frontend run e2e -- --project=chromium identity-binding-frontdesk.spec.ts`
  通过（1 passed，约 13.4 秒）；`npm --prefix frontend run test -- IdentityBinding operationalControlPages
e2eAuthCredentialContract` 通过（29 tests）；`npm --prefix frontend exec tsc -- --noEmit --pretty false
--skipLibCheck false --project frontend/tsconfig.json` 通过；后端窄测
  `mvn -f medkernel-backend/pom.xml -Dtest=IdentityBindingControllerTest,IdentityBindingExternalSyncTest test`
  通过（14 tests）；前端重门禁 `npm --prefix frontend run verify` 通过（115 files，1005 tests，仅既有
  AntD Timeline warning）。
- 第一百二十四批后续主线：本批仍未发布到 134，尚未做 134 清库重新部署，也未完成全功能与全知识全流程
  复演；不能把身份来源绑定切片等同于总目标完成。下一步继续按全角色真实操作扩面，优先覆盖 134 空库首启
  与统一迁移、全资产 runtime 闭包复核、备份恢复与重启恢复、运行保障、全知识流程复演，以及跨角色真实
  前台体验中尚未跑透的端到端缺口；发现红点继续先复现、定根因，再按上线级标准修复，不做片面优化。
- 第一百二十三批本地收尾：继续按全角色真实前台、真实服务链路和上线门禁推进；本批不是菜单、
  文案或片面 UI 优化，而是补齐 ACTIVE CLAIM 评价指标从前台创建、复核、发布、灰度、全量激活，
  再纳入本地上线演练医院机构生效版本，最后驱动 `/qc/insurance` 真实医保审核、绑定
  `evaluationRunId` / `quality_finding` / `mk_quality_insurance_issue` 并进入质量整改闭环的
  端到端切片。本批曾发起只读子代理 Hubble 审查医保质量链路，但截至本地提交前未返回可采纳结论，
  子代理未改文件；本批不推送远程、不合并 `main`。
- 第一百二十三批修复范围：`real-frontdesk-rehearsal.spec.ts` 新增
  `createActiveClaimEvaluationIndicatorFromUi`，由医疗引擎运营员在 `/qc/eval/sets` 前台创建
  CLAIM 主体费用型医保指标，提交安全复核、确认发布、开始 10% 灰度并全量激活；新增
  `activateHospitalRuntimeWithClaimIndicatorFromUi`，在 `/config/releases` 选择“本地上线演练医院”，
  勾选本院评价指标内容并生成新机构生效版本，随后用 current runtime API 精确断言本轮指标进入
  `ACTIVE` 清单。`/qc/insurance` 演练改为强制选择本轮 CLAIM 指标，不再接受
  `INSURANCE_RULE_MANUAL` 手工兜底；审核后断言 `evaluationRunId` 存在，且质量问题详情中的
  `runId` 与 `indicatorId` 分别绑定本次评估运行和本轮指标。
- 第一百二十三批后端防绕过：`InsuranceQualityService` 对非手工医保审核新增服务端基线校验：
  `indicatorId` 必须属于当前租户、状态为 `ACTIVE`、主体为 `CLAIM`，并且同一指标编码和规范版本号必须
  已进入快照锁定的 `clinical_runtime_release_item` 当前机构生效版本 `ACTIVE` 清单；评估运行完成后按
  `tenantId + evaluationRunId + finding_code=INSURANCE.<issueId>` 精确查找 `quality_finding`，
  回填医保 issue 的 `finding_id` / `evaluation_run_id` / `RECTIFICATION_CREATED` 状态。若运行没有生成
  匹配质量问题，服务端抛 `ENG_EVAL_005`，不再吞错或返回假成功；若使用非 CLAIM 指标，即使 ACTIVE 且
  已进 runtime 也会抛出“医保审核评价指标必须面向医保合规主体”。
- 第一百二十三批红点根因与定位修复：真实 E2E 红点的业务根因是本地演练医院 runtime 必须显式包含本轮
  ACTIVE CLAIM 评价指标，否则医保审核会退到手工规则路径或无法形成可追溯评估运行；本批把该要求同时放在
  前台演练和后端防线中。过程中的前台定位红点来自当前 AntD Select 虚拟列表、指标详情 Drawer、发布 Modal
  文案和客户机构版 `/config/releases` 当前结构差异，已改为按真实业务名称/弹层标题/确认按钮定位；Release
  页面没有旧搜索框，因此候选精确性用只读 `runtime-candidates?assetType=EVALUATION&keyword=...` 验证，
  生成机构生效版本仍走真实前台勾选与按钮动作。
- 第一百二十三批红绿事实：后端新增回归先用
  `InsuranceQualityServiceTest#insuranceAuditRejectsNonClaimIndicatorEvenWhenActiveInRuntimeRelease`
  复现了“ACTIVE 且进入 runtime 的 MEDICAL_RECORD 指标也可驱动医保审核”的绕过缺口，红于
  “Expecting code to raise a throwable”；随后在 `requireClaimIndicatorInSnapshotRuntime` 补 CLAIM 主体校验，
  单用例转绿。既有医保审核测试同步断言 issue / finding / run / indicator 直连、评估运行未落库对应 finding
  时诚实失败、指标未进入 snapshot runtime 时拒绝且不调用评估引擎。
- 第一百二十三批验证证据：目标真实前台 E2E
  `E2E_API_BASE_URL=http://localhost:18080/medkernel/api/v1 MEDKERNEL_API_PROXY_TARGET=http://localhost:18080 npm --prefix frontend run e2e -- --project=chromium real-frontdesk-rehearsal.spec.ts`
  通过（1 passed，约 1.1 分钟）；`npm --prefix frontend run test -- e2eAuthCredentialContract -t
"active CLAIM evaluation indicator"` 通过；`npm --prefix frontend run test -- InsuranceAudit QcEvalSets
e2eAuthCredentialContract` 通过（34 tests）；前端触达文件 Prettier check 通过；`npm --prefix frontend exec tsc
-- --noEmit --pretty false --skipLibCheck false --project frontend/tsconfig.json` 通过；`npm --prefix frontend run verify`
  通过（115 files，1004 tests，仅既有 AntD Timeline warning）；后端红灯单测先失败后通过，随后
  `mvn -f medkernel-backend/pom.xml -Dtest=InsuranceQualityServiceTest test` 通过（10 tests）；
  `git diff --check && git diff --cached --check` 退出码 0。
- 第一百二十三批后续主线：本批仍未发布到 134，尚未做 134 清库重新部署，也未完成全功能与全知识全流程
  复演；不能把 ACTIVE CLAIM 医保质量链路等同于总目标完成。下一步继续按全角色真实操作扩面，优先覆盖
  134 空库首启与统一迁移、全资产 runtime 闭包复核、备份恢复与重启恢复、身份来源绑定真实前台动作、运行保障
  和全知识流程复演；发现红点继续先复现、定根因，再按上线级标准修复，不做片面优化。
- 第一百二十二批本地收尾：继续按全角色真实前台、真实服务链路和上线门禁推进；本批不是菜单、
  文案或片面页面优化，而是补齐 S1/S14 服务机构开通、机构管理员首登、组织树建设、人员建档、
  科室职责范围、临床账号首登和 `/security/me` 登录画像的真实前台闭环。本批使用只读子代理
  Nietzsche 审查服务机构演练与后端权限画像缺口，子代理未改文件；本批不推送远程、不合并
  `main`。
- 第一百二十二批修复范围：新增 `service-organization-frontdesk.spec.ts`，平台管理员从
  `/tenant/onboarding` 前台开通新服务机构并捕获真实 `POST /api/v1/admin/tenants` 的
  `adminUserId` / 一次性密码；新机构管理员完成真实首登改密后，从前台创建 FACILITY 医院与
  DEPARTMENT 科室，随后在 `/admin/users` 建立临床人员档案、开通登录账号、追加
  `clinical-user` + `DEPARTMENT` 职责范围，再由临床账号完成首登改密并回读 `/security/me`
  验证 tenant / hospital / department dataScope、角色 scope 与菜单画像，最后回读
  `/engine/org/org-units` 证明组织树真实落库。清理逻辑按审查意见改为使用新机构管理员会话停用
  临床账号和机构管理员账号，所有清理响应走 `expectOk`，不再吞掉 `.catch(() => null)`。
- 第一百二十二批后端权限与画像修复：`ComplianceUserService` 的角色作用域校验从只拦跨租户
  TENANT 范围，收紧为 TENANT 必须当前租户，FACILITY / DEPARTMENT / WARD 等组织范围必须是
  当前租户 ACTIVE 组织且 `scopeLevel` 与组织实际层级一致，SPECIALTY 也必须能在当前租户
  ACTIVE 组织目录中找到挂载专科，避免自由编码职责范围写入。`EffectivePermissionService`
  修复同一 roleCode 的 JWT 空范围覆盖真实 assignment scope，以及同一角色多个适用组织范围互相
  覆盖的问题；权限计算改从角色视图 code 去重，保证 key 拆分后权限不丢失。
- 第一百二十二批红绿与调试事实：新增后端回归先复现了三个真实缺口：外部角色同步在收紧组织校验后
  红于 `组织 dept-1 不存在`；同一 `clinical-user` 多范围画像只剩 DEPARTMENT；SPECIALTY 自由编码
  仍可返回 200。随后分别补齐 mock ACTIVE 组织、角色 assignment key 与 SPECIALTY 目录校验，并用
  `ComplianceUserCredentialFlowTest`、`SecurityMeControllerTest`、
  `EffectivePermissionServiceTest`、`ComplianceUserExternalRoleSyncTest` 锁住缺口。前端新增
  `e2eAuthCredentialContract.test.ts` 合同，要求服务机构演练必须覆盖开通、首登、组织树、人员账号、
  清理函数、`adminUserId`、`/security/me` 和禁止吞失败清理。
- 第一百二十二批验证证据：目标真实前台 E2E
  `E2E_API_BASE_URL=http://localhost:18080/medkernel/api/v1 MEDKERNEL_API_PROXY_TARGET=http://localhost:18080 npm --prefix frontend run e2e -- --project=chromium service-organization-frontdesk.spec.ts`
  通过（1 passed）；`npm --prefix frontend run test -- e2eAuthCredentialContract -t
"service organization rehearsal"` 通过；触达文件 Prettier check 通过；后端窄测
  `mvn -f medkernel-backend/pom.xml -Dtest=ComplianceUserCredentialFlowTest,SecurityMeControllerTest,EffectivePermissionServiceTest,ComplianceUserExternalRoleSyncTest,TenantProvisioningControllerTest,OrgUnitControllerSecurityTest test`
  通过（49 tests）；前端重门禁 `npm --prefix frontend run verify` 通过（115 files，1003 tests，仅既有
  AntD Timeline warning）；后端全量 `mvn -f medkernel-backend/pom.xml test` 通过（3080 tests，
  0 failures，0 errors，7 skipped；本机 Docker 不可用导致 Testcontainers 多方言 / 空 Postgres
  smoke 按既有条件跳过）。
- 第一百二十二批后续主线：本批仍未发布到 134，尚未做 134 清库重新部署，也未完成全功能与全知识
  全流程复演；不能把服务机构与账号闭环等同于总目标完成。下一步继续按全角色真实操作扩面，优先覆盖
  评价指标发布驱动医保 / 质量链路、运行保障与备份恢复、身份来源绑定、平台标准版本 / 医院 runtime
  全资产闭包、134 空库首启迁移和全知识流程复演；发现红点先复现、定根因，再按上线级标准修复，
  不做片面优化。
- 第一百二十一批本地收尾：继续按全角色真实前台、真实服务链路和上线门禁推进；本批不是
  登录页或文案单点优化，而是补齐产品范围中“开启 MFA 后必须完成真实 TOTP 登录验证”的
  P0 安全演练证据。本批只做本地提交，不推送远程、不合并 `main`；已使用只读子代理 Darwin
  辅助排查非 MFA 缺口优先级，另使用只读子代理 Boyle 审查 MFA 改动，子代理均未改文件。
- 第一百二十一批根因与修复范围：目标真实 E2E 初始红于配置中心查不到
  `medkernel.auth.mfa.enabled`，根因是 `MfaRuntimePolicy` 运行时读取该 key，但
  `SystemConfigSeeder.seedAuthPolicy` 未把 MFA 开关种入配置中心。后端新增
  `SystemConfigService.AUTH_MFA_ENABLED_KEY`，`SystemConfigSeeder` 注入 `AuthMfaProperties`
  并以默认 `false`、`BOOLEAN`、`HIGH`、`protectedConfig=true` 正式种入配置中心；
  `SystemConfigControllerTest` 不再清理掉 MFA seed，而是在用例后恢复为默认关闭，并新增
  `mfaSwitchIsSeededAsProtectedConfigCenterPolicy` 覆盖受保护配置契约；按审查意见补
  `mfaSwitchIsBackedByConfigCenterWithoutRestart`，通过真实 `/auth/login` 验证配置中心 PATCH 后
  MFA 运行策略无需重启即生效，防止 seed key 与运行时 key 漂移。前端导出
  `frontend/e2e/support/auth.ts` 既有 `totp(secret)` 计算器，并用 RFC 风格向量测试证明
  真实前台 MFA 演练复用同一个 TOTP 生成逻辑；另新增 E2E 源码合同，要求 MFA 演练必须停用
  临时平台管理员账号。
- 第一百二十一批真实前台演练：新增 `frontend/e2e/mfa-login-frontdesk.spec.ts`。用例先创建
  可控平台管理员账号，API 首登改密并绑定真实 TOTP secret，再通过已验证账号临时开启
  `medkernel.auth.mfa.enabled`；随后从真实 `/login` 选择“平台治理”、输入账号密码进入
  `/bootstrap`，在“验证多因素认证”前台表单填写动态验证码，等待真实
  `/api/v1/auth/mfa/verify` 返回 `verified=true`，再进入 `/dashboard` 并读取
  `/security/me` 断言 `mfaRequired/mfaBound/mfaVerified` 均为 `true`。只读审查发现初版会留下
  已知密码的高权限临时平台管理员账号，已修复为 `finally` 中分别尽力恢复 MFA 配置、通过真实
  `/compliance/users/{userId}/status` 停用临时账号，并无条件关闭浏览器 context；目标 E2E
  复跑证明该清理不破坏真实登录链路。
- 第一百二十一批验证证据：`npm --prefix frontend run test -- e2eAuthCredentialContract -t
"requires MFA frontdesk rehearsal to disable"` 先红于缺少 `disableMfaAdminAccount`，实现后通过；
  `mvn -f medkernel-backend/pom.xml
-Dtest=SystemConfigControllerTest#mfaSwitchIsBackedByConfigCenterWithoutRestart test`
  先红于测试凭证占位 hash 无法真实登录，改为真实 BCrypt 测试密码后通过；`npm --prefix frontend run test --
e2eAuthCredentialContract -t "exports the shared TOTP calculator|requires MFA frontdesk rehearsal to disable"`
  通过（2 passed）；`mvn -f medkernel-backend/pom.xml
-Dtest=SystemConfigControllerTest#mfaSwitchIsSeededAsProtectedConfigCenterPolicy,mfaSwitchIsBackedByConfigCenterWithoutRestart test`
  通过；触达 MFA 文件 Prettier check 通过；目标真实前台 E2E
  `E2E_API_BASE_URL=http://localhost:18080/medkernel/api/v1 MEDKERNEL_API_PROXY_TARGET=http://localhost:18080 npm --prefix frontend run e2e -- --project=chromium mfa-login-frontdesk.spec.ts`
  通过。上一轮重门禁已跑：`npm --prefix frontend run test --
e2eAuthCredentialContract Login pages.smoke` 通过（3 files，61 tests）；
  `mvn -f medkernel-backend/pom.xml -Dtest=SystemConfigControllerTest,AuthControllerTest,SecurityMeControllerTest,MfaPolicyServiceTest,ComplianceUserCredentialFlowTest#setStatus_disablesAccount test`
  通过（52 tests）；`npm --prefix frontend run typecheck` 通过；`npm --prefix frontend run verify`
  通过（115 files，1002 tests，仅既有 AntD Timeline warning）；`mvn -f medkernel-backend/pom.xml test`
  通过（3074 tests，0 failures，0 errors，7 skipped；本机 Docker 不可用导致 Testcontainers
  多方言用例按既有条件跳过）；`git diff --check && git diff --cached --check` 退出码 0。
- 第一百二十一批后续主线：本批仍未发布到 134、尚未做 134 清库重新部署，也未完成全功能与
  全知识全流程复演，不能把 MFA 单点通过等同于总目标完成。下一步继续全局全角色扩面，优先
  覆盖服务机构前台开通与组织树、评价指标前台发布驱动医保 / 质量链路、运行保障与备份恢复、
  身份来源绑定真实前台动作，以及平台标准版本 / 医院 runtime 全资产闭包和 134 空库首启迁移
  证据；发现红点先复现和定根因，再做上线级修复，不做片面优化。
- 第一百二十批本地收尾：继续按全角色真实前台、真实服务链路和上线门禁推进；本批不是菜单、
  文案或局部 UI 优化，而是把质量问题前台从“确认提醒 / 派发整改”推进到责任科室提交整改证据、
  质控复核关闭、非 P0 豁免、P0 豁免阻断和整改闭环报告的真实后端任务链路。本批使用 1 个只读
  子代理发起代码审查，子代理未改文件；本批不推送远程、不合并 `main`。
- 第一百二十批修复范围：`frontend/src/shared/api/hooks.ts` 将质量问题详情 DTO 对齐后端真实字段
  `rectificationTask`，`useSubmitRectification` / `useReviewRectification` 改为任务 ID 版
  `/engine/rectifications/{taskId}/submit|review`，新增 `useWaiveRectification` 与
  `useRectificationReport`。`QcAlerts` 接入整改报告、任务状态识别、提交整改证据、复核通过关闭、
  退回继续整改、非 P0 豁免和 P0 豁免阻断；`quality_finding` 先读详情中的
  `rectificationTask.taskId/status`，`rectification_task` 直接使用 `sourceId` 作为任务 ID。
  `QcEvalResults` 同步使用 `rectificationTask` 字段。新增
  `frontend/src/shared/lib/idempotencyKey.ts`，生成稳定且不超过后端 128 字符门禁的幂等键，
  替换整改派发 / 提交 / 复核 / 豁免中包含长说明导致失败的旧拼接方式。质量提醒列表行新增
  `data-source-id` / `data-alert-id` 稳定锚点，仅用于真实演练绑定本次事实，不新增可见技术文案。
- 第一百二十批红绿与调试事实：新增幂等键和质量整改单测后，已覆盖任务级提交、复核关闭、复核退回、
  退回后重提、非 P0 豁免、P0 豁免阻断、报告读取和 `rectificationTask` 字段契约。目标真实前台
  E2E 首次复跑红于质量整改提交后，提交接口返回本次任务 `SUBMITTED`，但随后按 finding 回查到另一个
  `ASSIGNED` 任务；根因不是后端提交失败，而是 `/qc/alerts` 列表存在历史同标题医保审核质量问题，
  E2E 仍用标题 + `.first()` 打开抽屉，可能点到历史行。按 TDD 先新增“同标题历史问题也存在时，行必须
  暴露稳定 sourceId 锚点”的组件测试并观察红灯，再给列表行补稳定锚点，E2E 改为按本次 `findingId`
  定位行，复跑通过。
- 第一百二十批验证证据：`npm --prefix frontend run test -- QcAlerts -t "stable source identifiers"`
  先红后绿；触达文件 Prettier check
  `npm --prefix frontend exec prettier -- --check frontend/e2e/real-frontdesk-rehearsal.spec.ts frontend/src/pages/quality/QcAlerts.tsx frontend/src/pages/quality/QcAlerts.test.tsx frontend/src/pages/quality/QcEvalResults.tsx frontend/src/pages/quality/QcEvalResults.test.tsx frontend/src/shared/api/hooks.ts frontend/src/shared/api/hooks.test.ts frontend/src/shared/lib/idempotencyKey.ts frontend/src/shared/lib/idempotencyKey.test.ts`
  通过；`npm --prefix frontend run test -- QcAlerts QcEvalResults hooks idempotencyKey productRoleJourneys routes`
  通过（6 files，216 tests）；`npm --prefix frontend run typecheck` 通过；目标真实前台 E2E
  `E2E_API_BASE_URL=http://localhost:18080/medkernel/api/v1 MEDKERNEL_API_PROXY_TARGET=http://localhost:18080 npm --prefix frontend run e2e -- --project=chromium real-frontdesk-rehearsal.spec.ts`
  通过（1 passed，约 47.8 秒）；`npm --prefix frontend run build` 通过；`npm --prefix frontend run verify`
  通过（115 test files，1000 tests，仅既有 antd Timeline deprecation warning）。本批未改后端代码，
  后端控制器和契约测试已核对存在 `rectificationTask` 与任务级整改接口，未跑后端全量。
- 第一百二十批后续主线：本批仍未发布到 134、尚未做 134 清库重新部署，也未完成全功能与全知识全流程复演；
  不能把 `/qc/alerts` 闭环等同于总目标完成。下一步继续按全角色真实操作扩面，优先覆盖服务机构开通与组织树、
  评价指标发布驱动医保 / 质量链路、MFA 真实开启后登录、运行保障 / 备份恢复证据、身份来源绑定，以及平台标准版本
  / 医院 runtime 全资产闭包和 134 空库首启迁移证据；发现红点先复现和定根因，再做上线级修复，不做片面优化。
- 第一百一十九批本地收尾：继续按全角色真实前台、真实服务链路和上线门禁推进；本批不是菜单、
  文案或局部 UI 优化，而是把协同任务从“报告解读待办出现”推进到医技真实前台完成、服务端持久化、
  已完成筛选回看和后端来源枚举完整中文呈现。本批使用 2 个只读子代理辅助核查 `/workflow/todos` 与
  `/qc/alerts`，子代理未改文件；本批不推送远程、不合并 `main`。
- 第一百一十九批修复范围：`WorkflowTodos` 待办状态筛选补齐后端真实状态 `TRANSFERRED` / `CANCELLED`
  的“已转交 / 已取消”回看入口；协同来源类型补齐后端真实 `RULE_EVENT` / `PATHWAY_EVENT`，页面标签、
  来源跳转按钮、业务摘要、来源筛选和前端 API 类型均使用“规则事件 / 路径事件”，避免真实投影扩面后暴露裸枚举。
  `WorkflowTodos` 从 Ant Design 静态 `message` 切到 `App.useApp()`，修复真实浏览器完成待办时的
  `Static function can not consume context` 警告。`stakeholder-view-rehearsal.spec.ts` 的医技报告解读链路
  不再只断言协同待办出现，而是在 `/workflow/todos` 点击“完成”、填写完成说明，断言
  `POST /engine/workflow/todos/{todoId}/complete` 返回 `COMPLETED`、`completedBy` 和完成说明，再切到
  “已完成”并用本次 `todoId` 验证真实列表接口与表格行可回看，避免历史演练数据污染。
- 第一百一十九批红绿与调试事实：新增协同来源单测后，旧实现跑
  `npm --prefix frontend run test -- WorkflowTodos -t "source labels"` 预期失败，找不到“规则事件”；
  补齐类型和映射后同命令通过。目标 E2E 首次复跑失败于 `WorkflowTodos` 静态 `message` 触发 AntD
  App 上下文 warning，已改 `App.useApp()` 并补完成待办无 warning 回归；第二次复跑完成动作已成功，
  但本地演练库存在上次失败留下的同类已完成报告解读行，严格 locator 命中 2 行，已改为绑定本次完成响应
  `todoId` 并同时验证接口 items 与表格 `data-row-key`。
- 第一百一十九批验证证据：触达文件 Prettier check
  `npm --prefix frontend exec prettier -- --check frontend/src/pages/clinical/WorkflowTodos.tsx frontend/src/pages/clinical/WorkflowTodos.test.tsx frontend/src/shared/api/hooks.ts frontend/e2e/stakeholder-view-rehearsal.spec.ts`
  通过；`npm --prefix frontend run test -- WorkflowTodos` 通过（22 tests）；
  `npm --prefix frontend run test -- WorkflowTodos hooks productRoleJourneys routes` 通过（4 files，214 tests）；
  目标真实前台 E2E
  `E2E_API_BASE_URL=http://localhost:18080/medkernel/api/v1 MEDKERNEL_API_PROXY_TARGET=http://localhost:18080 npm --prefix frontend run e2e -- --project=chromium stakeholder-view-rehearsal.spec.ts`
  通过（1 passed，约 1.4 分钟）；`npm --prefix frontend run build` 通过；`npm --prefix frontend run verify`
  通过（114 test files，988 tests，仅既有 antd Timeline deprecation warning）；
  `git diff --check && git diff --cached --check` 退出码 0。本批未改后端代码，未跑后端全量。
- 第一百一十九批后续主线：本批仍未发布到 134、尚未做 134 清库重新部署，也未完成全功能与全知识全流程复演。
  `/workflow/todos` 子代理确认“升级”仅在功能目录出现，前后端真实链路无升级按钮/API，不能作为已落地点伪演；
  后续如要保留该功能名，需先补真实契约，否则应收敛目录口径。`/qc/alerts` 子代理确认质量问题前台当前只到
  确认提醒和派发整改，后端已有提交整改、复核、豁免/关闭接口，但前台未接入；下一批可优先补质量整改提交、
  复核、关闭真实前台闭环并用 E2E 验证。继续按全角色真实操作扩面到服务机构开通与组织树、评价指标发布驱动
  医保/质量链路、MFA 真实开启后登录、运行保障/备份恢复、身份来源绑定、平台标准版本/医院 runtime 全资产闭包、
  134 空库首启与迁移证据；发现红点先复现和定根因，再做上线级修复，不做片面优化。
- 第一百一十六批本地收尾：接续 `2058e000` 未完成上线演练交接，仍按全角色真实前台、
  真实服务链路和上线门禁推进，不把本轮工作降级成菜单或文案单点优化。本批未推送远程、
  未合并 `main`；用户已允许使用子代理，但本批现有长门禁和修复均在当前线程完成，未实际使用子代理。
- 第一百一十六批修复范围：`frontend/e2e/support/auth.ts` 已补齐本地上线演练平台基线资产解析，
  从当前平台标准版本收集 active `FIELD_CATALOG`、`RULE` 等资产并传入医院运行时激活；若当前医院
  运行时缺少 active 字段目录或规则资产，会基于现有 release 重新激活带完整基线资产的新医院生效版本，
  避免旧 `activeAssets: []` 生成的空资产医院 release 卡住真实 E2E。同步补充
  `e2eAuthCredentialContract.test.ts`，覆盖本地演练租户、演练医院、四职责账号、平台标准版本资产和
  医院 runtime 自愈契约；后端新增知识与规则发布状态同步器及测试，保证平台权威资产发布状态进入版本链。
- 第一百一十六批前台体验与真实链路修复：`ProviderSetupPanel` 从静态 `message` 切到 Ant Design
  `App.useApp()`，仅对登记/编辑模型服务弹窗启用 `forceRender`，修复真实浏览器打开表单时的
  `useForm not connected` 警告，同时避免高风险密钥/启停/移除表单被隐藏预挂载；对应测试已包
  `ConfigProvider` 与 `App` 并新增无警告回归。信息科长真实演练从旧“插件边界”同步为当前
  “扩展能力”契约，先在运行诊断查看扩展能力边界，再进入系统接入生成数据质量报告，继续覆盖真实服务链路。
- 第一百一十六批验证证据：目标真实前台 E2E
  `E2E_API_BASE_URL=http://localhost:18080/medkernel/api/v1 MEDKERNEL_API_PROXY_TARGET=http://localhost:18080 npm --prefix frontend run e2e -- --project=chromium real-frontdesk-rehearsal.spec.ts stakeholder-view-rehearsal.spec.ts`
  通过（2 passed，约 2.0 分钟）；`npm --prefix frontend run test -- ProviderSetupPanel`
  通过（14 tests）；`npm --prefix frontend run test -- ProviderSetupPanel e2eAuthCredentialContract QcDashboard InsuranceAudit`
  通过（4 files，47 tests）；`npm --prefix frontend run test -- Login pages.smoke e2eAuthCredentialContract viteProxyGuard productRoleJourneys ProviderSetupPanel`
  通过（6 files，86 tests）；`mvn -f medkernel-backend/pom.xml -Dtest=KnowledgePublicationStatusSynchronizerTest,RulePublicationStatusSynchronizerTest test`
  通过（5 tests）；`npm --prefix frontend run build` 通过；`npm --prefix frontend run verify`
  通过（114 test files，983 tests，只有既有 antd Timeline 弃用警告）；`mvn -f medkernel-backend/pom.xml test`
  通过（3072 tests，0 failures，0 errors，7 skipped；本机 Docker 不可用导致 Testcontainers 多方言用例按既有条件跳过）；
  `git diff --check && git diff --cached --check` 退出码 0。
- 第一百一十六批后续主线：继续按宪章、产品范围、体验契约、功能目录和四职责旅程推进全局上线演练；
  不要片面做 UI 或文案优化。下一步应在当前已通过的本地全角色演练基础上，继续扩展到剩余页面、
  权限、迁移、134 清库部署和全知识全流程复演；若发现真实链路红点，先复现和定根因，再最小修复并跑相应门禁。
- 第一百一十七批本地收尾：继续按全角色真实前台、真实服务链路和上线门禁推进；本批不是菜单或文案单点优化，
  而是把全局上线演练中暴露的真实权限边界、当前知识关系契约和根级布局红点纳入闭环。本批使用 2 个只读子代理
  辅助核查 `/config/releases` 与 `/advanced/graph` 当前产品契约，子代理未改文件；本批不推送远程、不合并 `main`。
- 第一百一十七批修复范围：`ReleaseGovernance` 按当前登录租户区分平台治理租户与客户机构租户，客户机构不再请求
  需要 `platform.publish` 和平台上下文的 `/engine/releases/platform-baselines/candidates`，也不展示平台发布、
  本次发布变更和当前清单停用操作；客户机构仍可查看当前平台标准版本，并在机构生效版本中选择医院、启用平台与本地内容。
  `usePlatformReleaseCandidates` 增加 `enabled` 开关与回归测试，避免客户租户真实访问 `/config/releases` 时出现 403 噪声。
  `d6-graph-explore.spec.ts` 从旧“图谱查询 / 投影关系图 / 投影目标 / 知识关系投影”同步为当前“知识关系 /
  知识关系图 / 关系范围 / 知识关系”契约；不把页面改回旧口径。`SandboxHost.module.css` 将沙盘工作区折叠阈值从
  `78rem` 调整为 `90rem`，修复 1280 桌面含应用侧栏时 `/sandbox` 根级横向溢出，并补 CSS 合同测试。
- 第一百一十七批验证证据：TDD 红灯已捕获客户租户仍展示平台发布操作、沙盘仍使用 `78rem` 折叠阈值；修正后
  `npm --prefix frontend run test -- ReleaseGovernance -t "keeps platform publishing operations"` 通过，
  `npm --prefix frontend run test -- hooks -t "does not request platform publishing candidates"` 通过，
  `npm --prefix frontend run test -- ReleaseGovernance GraphExplore hooks` 通过（3 files，141 tests），
  `npm --prefix frontend run test -- SandboxHost` 通过（12 tests），触达文件 Prettier check 通过。真实 E2E 子集
  `E2E_API_BASE_URL=http://localhost:18080/medkernel/api/v1 MEDKERNEL_API_PROXY_TARGET=http://localhost:18080 npm --prefix frontend run e2e -- --project=chromium all-done-route-smoke.spec.ts core-ui-runtime.spec.ts d6-graph-explore.spec.ts`
  通过（4 passed）；全量 Chromium E2E
  `E2E_API_BASE_URL=http://localhost:18080/medkernel/api/v1 MEDKERNEL_API_PROXY_TARGET=http://localhost:18080 npm --prefix frontend run e2e -- --project=chromium`
  通过（27 passed）；`npm --prefix frontend run build` 通过；`npm --prefix frontend run verify` 通过（114 test files，
  986 tests，仅既有 antd Timeline deprecation warning）；`mvn -f medkernel-backend/pom.xml test` 通过（3072 tests，
  0 failures，0 errors，7 skipped；本机 Docker 不可用导致 Testcontainers 多方言/空 Postgres smoke 按既有条件跳过）；
  `git diff --check && git diff --cached --check` 退出码 0。
- 第一百一十七批后续主线：本批尚未发布到 134、尚未做 134 清库重新部署，也未完成全功能与全知识全流程复演。
  下一步继续从真实前台全角色操作入手，按平台管理员、医院管理员、医生、质控/运营等角色覆盖剩余页面、
  权限、真实后端服务、统一迁移、部署与回滚证据；发现不符合项先复现并定位根因，再做上线级修复，不做片面优化。
- 第一百一十八批本地收尾：继续按全角色真实前台、真实服务链路和上线门禁推进；本批不是局部 UI、
  菜单或文案优化，而是把真实 CDSS 推荐卡、运行版本资产证据和机构生效版本发布/回滚链路纳入上线演练证据。
  本批使用 2 个只读子代理核查前端 E2E 缺口与后端推荐调用链；提交前曾尝试补开 1 个只读 reviewer，
  但多轮等待未返回并已关闭，不作为完成证据。子代理均未改文件。本批不推送远程、不合并 `main`。
- 第一百一十八批修复范围：`real-frontdesk-rehearsal.spec.ts` 不再接受 `visibleCardCount >= 0` 的空推荐结果，
  医生从真实前台创建上下文快照并触发 CDSS 后，必须断言评估状态为 `EVALUATED`、`triggerId`/`traceId` 非空、
  `visibleCardCount > 0`、`suppressedCardCount = 0`，且推荐卡响应包含 `cardId`、运行版本、`asset_version`、
  来源层和 `content_hash`；随后通过真实推荐卡详情接口读取落库卡，验证触发记录关联机构生效版本，并在前台列表
  用本次 `cardId` 打开“推荐详情与反馈闭环”。`stakeholder-view-rehearsal.spec.ts`
  将医生/药师触发推荐后 0 卡失败前移到创建推荐卡 helper 内，避免上层反馈动作才暴露“无卡”。
  新增 `runtime-release-frontdesk.spec.ts`，覆盖医疗引擎运营员在 `/config/releases` 选择“本地上线演练医院”、
  必要时评估发布影响、生成新机构生效版本、从历史版本回滚；发布与回滚后均通过真实 API 读取 current runtime，
  断言当前修订号正确且 active `FIELD_CATALOG`、`RULE` 均启用并带 `versionId`，避免“修订号递增但不可运行”。
- 第一百一十八批验证证据：触达文件 Prettier check
  `npm --prefix frontend exec prettier -- --check frontend/e2e/real-frontdesk-rehearsal.spec.ts frontend/e2e/stakeholder-view-rehearsal.spec.ts frontend/e2e/runtime-release-frontdesk.spec.ts`
  通过；`npm --prefix frontend run test -- e2eAuthCredentialContract CdssFatigue ReleaseGovernance hooks`
  通过（4 files，160 tests）；后端窄测
  `mvn -f medkernel-backend/pom.xml -Dtest=RecommendationDeterministicMatcherTest,RuntimeReleaseRuleSelectorTest,ClinicalRuntimeReleaseServiceTest test`
  通过（19 tests）。目标真实前台 E2E
  `E2E_API_BASE_URL=http://localhost:18080/medkernel/api/v1 MEDKERNEL_API_PROXY_TARGET=http://localhost:18080 npm --prefix frontend run e2e -- --project=chromium real-frontdesk-rehearsal.spec.ts stakeholder-view-rehearsal.spec.ts runtime-release-frontdesk.spec.ts`
  通过（3 passed，约 2.1 分钟）；单条回归 `runtime-release-frontdesk.spec.ts` 通过（1 passed），
  `real-frontdesk-rehearsal.spec.ts` 通过（1 passed）。重门禁：`npm --prefix frontend run build` 通过；
  `npm --prefix frontend run verify` 通过（114 test files，986 tests，仅既有 antd Timeline deprecation warning）；
  `mvn -f medkernel-backend/pom.xml test` 通过（3072 tests，0 failures，0 errors，7 skipped；本机 Docker 不可用导致
  Testcontainers 多方言/空 Postgres smoke 按既有条件跳过）；`git diff --check && git diff --cached --check`
  退出码 0。
- 第一百一十八批后续主线：本批仍未发布到 134、尚未做 134 清库重新部署，也未完成全功能与全知识全流程复演；
  Docker 跳过的多方言迁移测试不能替代 134 空库首启证据。下一步继续从真实前台全角色操作扩面，优先覆盖
  服务机构开通与组织树、协同待办完成/转交、质量问题整改闭环、评价指标发布驱动医保/质量链路、MFA 真实开启后登录、
  运行保障/备份恢复证据、身份来源绑定，以及平台标准版本/医院 runtime 的全资产闭包；发现红点先复现和定根因，
  再按上线级标准修复并跑对应门禁，不做片面优化。
- 第一百一十五批本地交接（未完成，因额度中止）：本批只做可续接交接，不使用子代理、不推送远程、
  不合并 `main`，也不把当前未完成修复伪装为阶段完成。开工已重新读取 `AGENTS.md` 与本文件；
  `git status --short --branch` 显示当前分支仍为 `codex/final-handoff-product-optimization`，
  工作树有 26 个未提交修改文件；交接开工核查时 `git log --oneline -8` 显示 HEAD 为
  `76080ba257d8351d776cfbd79bd466bc59cc3abb`（`docs: 记录实施验收入口口径复演`），
  最新应用提交仍为 `6d12324d9710bc9920b21f897abfb73cdf2a4571`
  （`fix: 收敛实施验收处理入口口径`）；`git rev-parse HEAD origin/main main`
  分别为 `76080ba257d8351d776cfbd79bd466bc59cc3abb`、
  `1561ba6bef8777dcef76432696f43de4277fdd3f`、
  `1561ba6bef8777dcef76432696f43de4277fdd3f`；`git worktree list` 仅有当前工作区。
- 第一百一十五批用户最新口径：当前目标不是只改菜单名称和页面文案，而是为了上线做全方面、
  全角色、全功能、真实前台和真实服务链路的完整验证；菜单名称、顺序和登录页滚动条/措辞只是
  产品体验线索，后续必须扩展到权限、后端服务、迁移、契约、真实 E2E、构建、T-GATE、134 发布证据映射。
  后续按最新约束不使用子代理、不咨询，按最优上线级决策执行；只做本地阶段提交，不推送远程，
  不直接改写或合并 `main`。
- 第一百一十五批未提交修改范围（不要回滚，下一棒直接续接）：`frontend/playwright.config.ts`
  将本地代理默认收敛到 `http://localhost:18080`；`RuntimeReleaseController` /
  `RuntimeReleaseQueryService` 及测试让医院当前生效版本空态返回可识别 `null/optional` 而不是前台 404；
  `frontend/src/shared/api/hooks.ts` 与测试同步空态；`Login.tsx`、`Login.module.css`、
  `Login.test.tsx` 和 `pages.smoke.test.tsx` 优化登录页低高度滚动条和“平台治理 / 机构工作台”措辞；
  `AiWorkflows.tsx` 收敛模型能力说明布局；`TerminologyMapping.tsx` 与测试修正未挂载表单
  `setFieldsValue` 警告；`DiagnosisKnowledgePanel.test.tsx` 增加同类回归断言；
  多个 Playwright 用例修正最新医疗产品口径、菜单顺序和选择器稳健性，包括
  `core-ui-runtime.spec.ts`、`d6-ai-workflows.spec.ts`、`d6-graph-explore.spec.ts`、
  `diagnosis-knowledge-maintenance.spec.ts`、`pathway-graph-editor.spec.ts`、
  `product-role-journeys.spec.ts`、`stakeholder-view-rehearsal.spec.ts`；
  `frontend/e2e/support/auth.ts` 是最大改动，新增本地上线演练租户
  `t-e2e-rehearsal-local`、演练医院 `e2e-rehearsal-hospital`、四职责账号开通、首次改密、
  平台标准版本发布和医院生效版本激活准备；`e2eAuthCredentialContract.test.ts`
  增加断言，确保本地全角色前台演练不再把 `t-1` 平台标准源当客户机构使用。
- 第一百一十五批已跑过的非最终验证（均针对当前未提交工作树，不能作为最终完成声明）：后端运行时空态窄测
  14 个通过；`npm --prefix frontend run test -- hooks productRoleJourneys viteProxyGuard`
  141 个通过；产品角色/all-done Chromium 子集 6 个通过；`npm --prefix frontend run test -- Login pages.smoke e2eAuthCredentialContract`
  51 个通过；`npm --prefix frontend run test -- AiWorkflows` 11 个通过；
  `d6-ai-workflows.spec.ts` Chromium 2 个通过；组合单测
  `AiWorkflows Login pages.smoke e2eAuthCredentialContract TerminologyMapping DiagnosisKnowledgePanel`
  99 个通过；诊断知识与路径编辑器 targeted E2E 通过；广度子集
  `core-ui-runtime d0-bootstrap-closure d0-login-domain d6-ai-workflows theme-mobile-browser-compatibility diagnosis-knowledge-maintenance pathway-graph-editor`
  Chromium 16 个通过；`TerminologyMapping` 14 个通过；本地演练租户隔离 TDD 红绿已完成：
  旧实现跑 `npm --prefix frontend run test -- e2eAuthCredentialContract -t "local full-role"`
  失败于 `auth.resolvedTenantIdFor is not a function`，实现后
  `npm --prefix frontend run test -- e2eAuthCredentialContract` 4 个通过；
  `npm --prefix frontend exec prettier -- --check frontend/e2e/support/auth.ts frontend/src/test/e2eAuthCredentialContract.test.ts`
  通过；`npm --prefix frontend run test -- Login pages.smoke e2eAuthCredentialContract viteProxyGuard productRoleJourneys`
  65 个通过。
- 第一百一十五批当前真实 E2E 红点与根因：命令
  `E2E_API_BASE_URL=http://localhost:18080/medkernel/api/v1 MEDKERNEL_API_PROXY_TARGET=http://localhost:18080 npm --prefix frontend run e2e -- --project=chromium real-frontdesk-rehearsal.spec.ts stakeholder-view-rehearsal.spec.ts`
  初次失败于本地租户开通密码策略（`PWD_POLICY_VIOLATION`，密码至少 12 位），已用
  `localInitialPassword = "Mk@2026localinit"` 修正。第二次已越过租户/医院/账号准备，但仍红：
  `real-frontdesk-rehearsal.spec.ts` 在“前台创建系统接入适配器”记录浏览器错误，
  运行记录含 `400 GET .../engine/integration/data-contract`，页面展示“数据接入契约暂时不可用”；
  `stakeholder-view-rehearsal.spec.ts` 医生视角触发推荐后返回
  “推荐评估已完成：本次未新增可见提醒；当前列表已刷新存量提醒。”，`visibleCardCount=0`。
  根因已追到本地演练医院生效版本激活逻辑：`frontend/e2e/support/auth.ts`
  当前 `ensureHospitalRuntime` 只要存在当前医院 release 就直接返回，且首次激活请求传
  `activeAssets: []`；后端 `ClinicalRuntimeReleaseService.activate` 会从平台标准版本复制为
  disabled，再只启用请求中的资产。因此本地演练医院的运行时版本所有平台资产都处于禁用状态，
  `IntegrationDataContractService.generate()` 解析不到 active `FIELD_CATALOG`
  `ContextFieldCatalogAssets.CLINICAL_CONTEXT_IDENTITY`，推荐链路的
  `RuntimeReleaseRuleSelector.select(...)` 也选不到 active `RULE`。
- 第一百一十五批下一棒首要修复路径：先继续 TDD，不要直接改成“看起来能跑”。在
  `frontend/e2e/support/auth.ts` 增加平台基线资产解析与医院生效版本自愈：
  `ensurePlatformBaseline` 不应只返回 `baselineReleaseId`，应返回
  `{ baselineReleaseId, activeAssets }`，从 `/engine/releases/platform-baselines/current`
  的 `items` 中收集 `entryState === "ACTIVE"` 且有 `assetType/assetIdentity` 的资产，
  传给医院激活请求时使用 `{ assetType, assetIdentity, versionId: null }`；如果当前平台标准版本
  缺少 active `FIELD_CATALOG` 或 `RULE`，应读取 `/engine/releases/platform-baselines/candidates`
  并发布候选后重读 current，仍缺则抛清晰错误并继续追种子/资产发布根因，不得伪造通过。
  `ensureHospitalRuntime` 应读取当前医院 release 的 `items`，只有已存在 active `FIELD_CATALOG`
  且 active `RULE` 时才返回；否则用现有 `releaseId` 作为 `expectedCurrentReleaseId`
  再激活一版带 `baseline.activeAssets` 的医院运行时。若因为第二次 E2E 已创建过空资产 release，
  这个自愈逻辑必须能生成新医院 release，而不是被旧空 release 卡住。
- 第一百一十五批建议下一棒命令顺序：补/改 `e2eAuthCredentialContract.test.ts` 或相邻单测先红后绿，
  再跑 `npm --prefix frontend exec prettier -- --check frontend/e2e/support/auth.ts frontend/src/test/e2eAuthCredentialContract.test.ts`，
  `npm --prefix frontend run test -- e2eAuthCredentialContract`，
  然后复跑上述两个真实前台 E2E。通过后再进入更广的全角色/全功能门禁：
  `npm --prefix frontend run test -- Login pages.smoke e2eAuthCredentialContract viteProxyGuard productRoleJourneys`、
  相关 Playwright Chromium 子集、`npm --prefix frontend run build`、
  `npm --prefix frontend run verify`、`mvn -f medkernel-backend/pom.xml test`、
  `git diff --check` 与 `git diff --cached --check`。最终仍需更新本文件并只做本地提交。
- 第一百一十四批最新应用提交为 `6d12324d9710bc9920b21f897abfb73cdf2a4571`
  （`fix: 收敛实施验收处理入口口径`）。本批未使用子代理、未推送远程、未合并 `main`；
  继续以宪章、产品范围、体验契约、功能目录和四职责旅程为准，复核平台管理员在 `实施与验收`
  主链路中的下一步理解。结论：菜单命名、顺序和页面措辞本身是医疗产品全角色体验的重要入口，
  但不能等同于完整上线验收；本批只将 `实施与验收` 页中会把院方实施人员引向抽象后台对象的
  `下一配置页`、`机构实施配置`、`对应配置页` 等口径，收敛为 `下一处理入口`、
  `服务机构开通`、`对应处理入口`，并同步后端 `ImplementationStep` 公共 Javadoc。
  路由、菜单顺序、接口字段、步骤 ID、幂等和持久化契约均不改。
- 第一百一十四批验证：先改 `ImplementationGuide` 用例并在旧实现上跑出预期失败
  （`npm --prefix frontend run test -- ImplementationGuide` 无法找到 `下一处理入口`，旧 DOM 仍展示
  `下一配置页/机构实施配置/对应配置页`）；修正后同命令通过（1 个文件、4 个测试）。
  扩展前端 `npm --prefix frontend run test -- ImplementationGuide WorkbenchPanel productRoleJourneys routes`
  通过（4 个文件、83 个测试）；租户实施/权限相关后端窄测
  `mvn -f medkernel-backend/pom.xml -Dtest=TenantPilotServiceTest,TenantEngineControllerContractTest test`
  通过（9 个测试），
  `mvn -f medkernel-backend/pom.xml -Dtest=DefaultPermissionPolicyTest,MenuPermissionCatalogTest test`
  通过（16 个测试）。重门禁：`npm --prefix frontend run build` 通过，
  `npm --prefix frontend run verify` 通过（114 个测试文件、967 个测试；仅既有 antd Timeline deprecation warning），
  `mvn -f medkernel-backend/pom.xml test` 通过（3064 个测试、0 failures、0 errors、7 skipped；
  Docker/Testcontainers 多方言容器用例因本机无 Docker 按既有条件跳过），`git diff --check`
  与 `git diff --cached --check` 均退出码 0。旧词扫描
  `rg -n "配置页|下一配置页|机构实施配置|对应配置页" ...` 仅剩体验契约中的通用页面类型、
  `WorkbenchPanel` 的合法 `安全配置页面`、审计配置 Javadoc、`RuleDefinitions` 测试名和本批测试反向断言；
  `ImplementationGuide` 生产代码与 `ImplementationStep` Javadoc 已收敛为处理入口口径。
- 第一百一十四批开工/收尾核查：`git fetch origin main` 后远端 `refs/heads/main`、本地 `origin/main`
  与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`
  （`完善全角色上线演练与134复演闭环 (#653)`）；应用提交前本地分支领先 `origin/main` 307 个提交，
  应用提交后领先 308 个提交。134 当前公网首页 `https://193.112.107.134/medkernel/` HTTP 200，
  `Date=Sun, 05 Jul 2026 05:08:06 GMT`，`Content-Length=832`，仍指向
  `/assets/index-DYTh-Ceu.js`、`/assets/vendor-react-bdrMx_IT.js`、`/assets/vendor-data-D9EFEnEk.js`、
  `/assets/vendor-react-C5ap-Sga.css`、`/assets/index-XMjG4gr3.css`；正确 readiness 端点
  `https://193.112.107.134/medkernel/actuator/health/readiness` HTTP 200 / `{"status":"UP"}`，
  `X-Trace-Id=555079e9-e622-4dfa-b5ec-2d6c9ff4a183`；登录租户入口
  `https://193.112.107.134/medkernel/api/v1/auth/login-tenants` HTTP 200，
  `success=true`、`hasCustomerTenants=true`、`primaryTenants[0].tenantId=t-rehearsal`、
  `primaryTenants[0].name=完整上线演练机构`、`X-Trace-Id=4b446197-3394-461f-b3eb-1be757f2bf7d`。
  本批本地应用提交尚未发布到 134、尚未推送远程、尚未合并 `main`。
- 下一步继续主线：不使用子代理；不要把命名优化与内容修正降级为次要项，但后续每批都要把菜单、
  页面、权限、后端服务、迁移、契约测试、E2E/真实前台演练和 134 发布映射一起核查，按原始权威范围与
  全领域上线标准判断是否存在未完整实现的功能闭环，发现问题即按上线级方案修正并阶段提交。
- 第一百一十三批最新应用提交为 `01e10939c20e4128e5b5bfc8ada3e54971a794f4`
  （`fix: 收敛质量评价前台口径`）。本批未使用子代理、未推送远程、未合并 `main`；
  继续以宪章、产品范围、体验契约、功能目录和四职责旅程为准，从全角色医疗产品体验复核质量域、
  临床路径和租户实施入口的剩余用户可见名称。结论：一级菜单顺序和全局分布保持现状，继续保留
  `诊断知识库`、`临床路径库`、`质量风险概览`、`质量问题与整改` 和 `评价指标`；不采用
  `临床路径模板` 作为前台菜单名，避免被理解为引用模板。现将前台对象和服务口径中残留的
  `质控指标`、`质控缺陷`、`质控追溯`、`质控服务状态`、`科室质控`、`质控时钟`、
  `生效质控指标`、`自动质控` 等收敛为 `评价指标`、`质量缺陷`、`质量追溯`、
  `质量管理服务状态`、`科室质量评价`、`质量评价时钟`、`生效评价指标`、`自动质量评价`；
  保留 `质控人员`、`病历内涵质控`、`医保质控` 等作为院内职责或业务活动名称，不改接口字段、
  枚举、表结构、ID、幂等和持久化契约。
- 第一百一十三批验证：先补 `QcEvalSets`、`QcEvalResults`、`InsuranceAudit`、
  `QcDashboard`、`PatientPathways`、`TenantLifecyclePanel`、`PermissionDimensionModelTest`、
  `InsuranceQualityServiceTest` 与 `QualityDashboardServiceTest` 等用例，在旧实现上跑出预期失败，
  捕获 `定义质控评价指标`、`科室质控`、`质控追溯`、`质控缺陷`、`质控服务状态`、
  `关联质控指标`、`质控指标达成与物理验收`、`发布质控指标` 和 `未绑定生效质控指标`
  等旧口径。修正后窄测 `npm --prefix frontend run test -- PatientPathways TenantLifecyclePanel`
  通过（2 个文件、14 个测试），
  `mvn -f medkernel-backend/pom.xml -Dtest=PermissionDimensionModelTest#permissionCatalogDoesNotExposeLegacyPackageContainerBoundaries test`
  通过（1 个测试），`npm --prefix frontend run test -- QcDashboard` 通过（13 个测试）；
  扩展套件
  `npm --prefix frontend run test -- QcEvalSets QcEvalResults InsuranceAudit QcDashboard PatientPathways TenantLifecyclePanel PathwayTemplates productRoleJourneys routes customerLanguageGate`
  通过（10 个文件、130 个测试），
  `mvn -f medkernel-backend/pom.xml -Dtest=InsuranceQualityServiceTest,QualityDashboardServiceTest,PermissionDimensionModelTest,PathwayEngineServiceTest,MenuPermissionCatalogTest,DefaultPermissionPolicyTest,EvaluationEngineServiceTest test`
  通过（124 个测试）。重门禁：`npm --prefix frontend run build` 通过，
  `mvn -f medkernel-backend/pom.xml test` 通过（3064 个测试、0 failures、0 errors、7 skipped；
  Docker/Testcontainers 多方言容器用例因本机无 Docker 按既有条件跳过），
  `npm --prefix frontend run verify` 通过（114 个测试文件、967 个测试），`git diff --check`
  与 `git diff --cached --check` 均退出码 0。旧词扫描
  `rg -n "质控评价|质控缺陷|质控追溯|科室质控|质控时钟|生效质控指标|自动质控|质控指标|质控服务状态|当前筛选下暂无质控数据|质控汇总服务|质量管理概览|诊断知识维护|临床路径模板" ...`
  仅剩测试反向断言、`QualityDashboardService` 的历史标题兼容归一化字面量、历史种子数据和
  `RuntimeDiagnosticsControllerTest` 的旧诊断菜单名负断言。
- 第一百一十三批开工/收尾核查：`git fetch origin main` 后远端 `refs/heads/main` 仍为
  `1561ba6bef8777dcef76432696f43de4277fdd3f`
  （`完善全角色上线演练与134复演闭环 (#653)`）；应用提交前本地分支领先 `origin/main` 305 个提交，
  应用提交后领先 306 个提交。134 当前公网首页 `https://193.112.107.134/medkernel/` HTTP 200，
  `Date=Sun, 05 Jul 2026 04:04:06 GMT`，`Content-Length=832`，仍指向
  `/assets/index-DYTh-Ceu.js`、`/assets/vendor-react-bdrMx_IT.js`、`/assets/vendor-data-D9EFEnEk.js`、
  `/assets/vendor-react-C5ap-Sga.css`、`/assets/index-XMjG4gr3.css`；正确 readiness 端点
  `https://193.112.107.134/medkernel/actuator/health/readiness` HTTP 200 / `{"status":"UP"}`，
  `X-Trace-Id=34b6f3c4-d9d6-4286-8899-c580ed4dca1a`。本批本地应用提交尚未发布到 134、尚未推送远程、
  尚未合并 `main`。
- 下一步继续主线：不使用子代理；继续按全角色真实前台体验广度复核剩余上线级问题，阶段完成后继续更新本文件并本地提交。
- 第一百一十二批最新应用提交为 `c4ca70701195925be2453803291c5ff10880dd08`
  （`fix: 收敛质量问题整改口径`）。本批未使用子代理、未推送远程、未合并 `main`；
  继续以宪章、产品范围、体验契约、功能目录和四职责旅程为准，从医疗产品全局视角复核质量域剩余前台可见口径。
  发现前端菜单与页面已收敛为 `质量问题` 后，后端价值指标、监控指标说明、质量评估审计摘要、
  权限目录和全真体验沙盘场景仍残留 `质控问题/质控证据/质控整改` 等内部治理表达，会让院方质量人员、
  临床科室负责人和运营人员把当前对象理解为后台质控配置，而不是需要被发现、派发、提交和复核闭环的医疗质量问题。
  现将用户可见价值指标公式、指标来源、自动评估证据、派发/提交/复核审计摘要、P0 豁免错误消息、
  权限说明、沙盘场景名和公共 Javadoc 收敛为 `质量问题`、`质量证据`、`质量问题整改`；
  保留 `quality_finding`、接口字段、枚举、实体、表结构、ID、幂等和持久化契约不变。
- 第一百一十二批验证：先补 `ValueMetricsServiceTest`、`BusinessMetricsTest`、
  `EvaluationEngineServiceTest`、`PermissionDimensionModelTest` 与 `SandboxScenarioCatalogTest`
  用例并在旧实现上跑出预期失败，分别捕获旧公式 `已豁免质控问题 / 已复核质控问题`、监控说明
  `质量管理：当前未闭环质控问题数`、P0 普通豁免错误 `P0 质控问题不得通过普通复核豁免`、
  自动评估证据 `系统自动评估扫描质控证据支撑。`、审计摘要 `派发质控整改 qf-new` /
  `提交质控整改 task-1`、权限说明 `复核质控整改并关闭问题` 和沙盘场景 `质控整改复核闭环`。
  修正后窄测 `mvn -f medkernel-backend/pom.xml -Dtest=ValueMetricsServiceTest,BusinessMetricsTest,EvaluationEngineServiceTest#p0FindingCannotBeWaivedByOrdinaryReview test`
  通过（8 个测试），
  `mvn -f medkernel-backend/pom.xml -Dtest=EvaluationEngineServiceTest#rectificationSubmissionAndApprovalCloseFinding+dispatchRectificationCreatesTaskForNewFindingAndRejectsChangedReplay+evaluateSnapshotPersistsRuleExplanationIntoResultAndFindingEvidence test`
  通过（3 个测试），
  `mvn -f medkernel-backend/pom.xml -Dtest=PermissionDimensionModelTest#permissionCatalogDoesNotExposeLegacyPackageContainerBoundaries,SandboxScenarioCatalogTest#registersTheCompleteScenarioMatrixAndAllTenRuleScenariosReachRuntimeResolution test`
  通过（2 个测试）。随后质量域后端扩展套件
  `mvn -f medkernel-backend/pom.xml -Dtest=ValueMetricsServiceTest,BusinessMetricsTest,EvaluationEngineServiceTest,QualityDashboardServiceTest,QualityDashboardControllerSecurityTest,PermissionDimensionModelTest,MenuPermissionCatalogTest,DefaultPermissionPolicyTest,SandboxScenarioCatalogTest,SandboxOrchestrationServiceTest,ServiceContractGovernanceTest,RuntimeDiagnosticsControllerTest,EmrLevelServiceTest,EmrLevelControllerSecurityTest test`
  通过（97 个测试），`npm --prefix frontend run test -- QcDashboard QcAlerts QcEvalResults productRoleJourneys routes customerLanguageGate`
  通过（6 个文件、92 个测试），`npm --prefix frontend run build` 通过，
  `npm --prefix frontend run verify` 通过（114 个测试文件、966 个测试；仅出现既有 antd Timeline deprecation warning），
  `mvn -f medkernel-backend/pom.xml test` 通过（3064 个测试、0 failures、0 errors、7 skipped；
  Docker/Testcontainers 多方言容器用例因本机无 Docker 按既有条件跳过），`git diff --check` 与
  `git diff --cached --check` 均退出码 0。旧词扫描
  `rg -n "质控问题|质控事实|质控证据|质控整改|手工质控整改|派发质控|提交质控|复核质控" ...`
  仅剩前端测试里的反向断言和 `QualityDashboardService#qualityProblemTitle` 的历史标题兼容归一化字面量；
  菜单旧名扫描 `rg -n "诊断知识维护|临床路径模板|质量管理概览|质控预警" ...`
  仅剩测试里的反向断言和职责旅程旧词黑名单。
- 第一百一十二批开工/收尾核查：`origin/main` 仍为
  `1561ba6bef8777dcef76432696f43de4277fdd3f`
  （`完善全角色上线演练与134复演闭环 (#653)`）；本批应用提交前本地分支领先 `origin/main` 303 个提交，
  应用提交后领先 304 个提交。134 当前公网首页 `https://193.112.107.134/medkernel/` HTTP 200，
  `Date=Sat, 04 Jul 2026 20:57:25 GMT`，`Content-Length=832`，仍指向
  `/assets/index-DYTh-Ceu.js`、`/assets/vendor-react-bdrMx_IT.js`、`/assets/vendor-data-D9EFEnEk.js`、
  `/assets/vendor-react-C5ap-Sga.css`、`/assets/index-XMjG4gr3.css`；正确 readiness 端点
  `https://193.112.107.134/medkernel/actuator/health/readiness` HTTP 200 / `{"status":"UP"}`，
  `X-Trace-Id=ef631444-93bd-4ef5-a227-1b565b517d32`。本批本地应用提交尚未发布到 134、尚未推送远程、
  尚未合并 `main`。
- 下一步继续主线：不使用子代理；继续按全角色真实前台体验广度复核剩余上线级问题，阶段完成后继续更新本文件并本地提交。
- 第一百一十一批最新应用提交为 `c8006c9e936723be6b52c37d71efc1332ec172c5`
  （`fix: 统一质量问题前台口径`）。本批未使用子代理、未推送远程、未合并 `main`；
  继续以宪章、产品范围、体验契约、功能目录和四职责旅程为准复核质量域前台体验。发现
  `/qc/dashboard`、`/qc/eval/results` 和质量看板后端提醒标题仍残留“质控问题/质控证据”等内部治理口径，
  容易让院方质量人员把页面理解为后台质控配置或抽象质控事实，而不是需要闭环处置的医疗质量问题。
  现将前台统计、入口卡片、空态、下钻证据、错误消息、权限说明和后端提醒标题收敛为 `质量问题`、
  `质量问题与整改入口`、`质量问题来源`、`质量证据` 和 `高风险质量问题待闭环`；保留
  `quality_finding` 等接口字段、枚举、表结构、ID 与持久化契约不变，并在后端对历史标题中的
  `质控问题/质控事实` 做用户可见标题归一化。
- 第一百一十一批验证：先补 `QcDashboard`、`QcEvalResults`、`QcAlerts` 与
  `QualityDashboardServiceTest#alertRefreshIsIdempotentAndAlertsEndpointFiltersOpenStatus` 用例，
  旧实现上 `npm --prefix frontend run test -- QcDashboard QcEvalResults QcAlerts` 预期失败，
  其中 `/qc/eval/results` 仍展示 `质控问题与整改入口` 和 `当前没有符合筛选条件的评价结果或问题。`；
  后端窄测旧实现也失败，实际提醒标题仍为 `高风险质控问题待闭环：质控问题 qf-critical`。
  修正后 `npm --prefix frontend run test -- QcDashboard QcEvalResults QcAlerts` 通过
  （3 个文件、25 个测试），
  `mvn -f medkernel-backend/pom.xml -Dtest=QualityDashboardServiceTest#alertRefreshIsIdempotentAndAlertsEndpointFiltersOpenStatus test`
  通过；随后 `npm --prefix frontend run test -- QcDashboard QcEvalResults QcAlerts productRoleJourneys routes customerLanguageGate`
  通过（6 个文件、92 个测试），
  `mvn -f medkernel-backend/pom.xml -Dtest=QualityDashboardServiceTest,QualityDashboardControllerSecurityTest,MenuPermissionCatalogTest,DefaultPermissionPolicyTest,EvaluationEngineServiceTest test`
  通过（53 个测试）。提交前重门禁：`git diff --check` 与 `git diff --cached --check` 均退出码 0，
  `npm --prefix frontend run build` 通过，`npm --prefix frontend run verify` 通过
  （114 个测试文件、966 个测试；仅出现既有 antd Timeline deprecation warning），
  `mvn -f medkernel-backend/pom.xml test` 通过（3064 个测试、0 failures、0 errors、7 skipped；
  Docker/Testcontainers 多方言容器用例因本机无 Docker 按既有条件跳过）。旧词扫描
  `rg -n "质控问题|质控事实|质控证据|质量预警|预警服务|预警状态|预警时间|预警级别|预警处置|确认预警|预警标题|确认预警失败" ...`
  仅剩测试里的反向断言、`QualityDashboardServiceTest` 的历史种子数据和
  `QualityDashboardService#qualityProblemTitle` 的兼容归一化字面量。
- 第一百一十一批开工/收尾核查：`origin/main` 仍为
  `1561ba6bef8777dcef76432696f43de4277fdd3f`
  （`完善全角色上线演练与134复演闭环 (#653)`）；本批应用提交前本地分支领先 `origin/main` 301 个提交，
  应用提交后领先 302 个提交。134 当前公网首页 `https://193.112.107.134/medkernel/` HTTP 200，
  `Date=Sat, 04 Jul 2026 20:37:41 GMT`，`Content-Length=832`，仍指向
  `/assets/index-DYTh-Ceu.js`、`/assets/vendor-react-bdrMx_IT.js`、`/assets/vendor-data-D9EFEnEk.js`、
  `/assets/vendor-react-C5ap-Sga.css`、`/assets/index-XMjG4gr3.css`；正确 readiness 端点
  `https://193.112.107.134/medkernel/actuator/health/readiness` HTTP 200 / `{"status":"UP"}`，
  `X-Trace-Id=032f195d-ab8e-4de0-8713-befb247fb7d0`。本批本地应用提交尚未发布到 134、尚未推送远程、
  尚未合并 `main`。
- 下一步继续主线：不使用子代理；继续按全角色真实前台体验广度复核剩余上线级问题，阶段完成后继续更新本文件并本地提交。
- 第一百一十批最新应用提交为 `75c7fb3bee5241f12fd30e7a027dd010e9345d67`
  （`fix: 收敛质量整改前台口径`）。本批未使用子代理、未推送远程、未合并 `main`；
  继续以宪章、产品范围、体验契约、功能目录和四职责旅程为准复核质量域前台体验。发现
  `/qc/alerts` 已作为 `质量问题与整改`，但页面筛选、抽屉、确认动作、来源标签和错误态服务名仍以
  “预警/质控问题/质控事实/质量预警服务”表达，容易把院方质量人员的整改闭环任务误理解为后台预警配置。
  现将前台可见口径收敛为 `处置状态`、`发现时间`、`风险级别`、`质量风险处置证据`、`确认风险提醒`、
  `质量问题来源`、`高风险质量问题仍未闭环` 和 `质量问题与整改服务`；`QualityDashboardAlert`
  接口字段、枚举、ID、持久化与后端契约不改。
- 第一百一十批验证：先补 `QcAlerts` 错误态用例并在旧实现上跑出预期失败
  （`npm --prefix frontend run test -- QcAlerts -t "错误态使用质量问题与整改服务口径"` 无法找到
  `质量问题与整改服务`，DOM 中仍为 `质量预警服务`）；随后扩展同页契约，`npm --prefix frontend run test -- QcAlerts`
  在旧实现上 5 项失败，覆盖 `风险级别`、`质量风险处置证据`、`确认风险提醒` 和错误态服务名。修正后
  `npm --prefix frontend run test -- QcAlerts` 通过（7 个测试），
  `npm --prefix frontend run test -- QcAlerts QcDashboard productRoleJourneys routes` 通过
  （4 个文件、82 个测试），
  `npm --prefix frontend exec prettier -- --check frontend/src/pages/quality/QcAlerts.tsx frontend/src/pages/quality/QcAlerts.test.tsx`
  通过，`git diff --check` 与 `git diff --cached --check` 均退出码 0。重门禁：
  `npm --prefix frontend run build` 通过，`npm --prefix frontend run verify` 通过
  （114 个测试文件、966 个测试；仅出现既有 antd Timeline deprecation warning）。本批仅改前端页面与测试，
  未触及后端实现或迁移。
- 第一百一十批开工/收尾核查：`origin/main` 仍为
  `1561ba6bef8777dcef76432696f43de4277fdd3f`
  （`完善全角色上线演练与134复演闭环 (#653)`）；本批应用提交后本地分支领先 `origin/main` 300 个提交。
  134 当前公网首页 `https://193.112.107.134/medkernel/` HTTP 200，
  `Date=Sat, 04 Jul 2026 20:23:07 GMT`，`Content-Length=832`，仍指向
  `/assets/index-DYTh-Ceu.js`、`/assets/vendor-react-bdrMx_IT.js`、`/assets/vendor-data-D9EFEnEk.js`、
  `/assets/vendor-react-C5ap-Sga.css`、`/assets/index-XMjG4gr3.css`；正确 readiness 端点
  `https://193.112.107.134/medkernel/actuator/health/readiness` HTTP 200 / `{"status":"UP"}`，
  `X-Trace-Id=dc563d5b-d71a-43ce-9162-cc75a1746c7e`。本批本地应用提交尚未发布到 134、尚未推送远程、
  尚未合并 `main`。
- 下一步继续主线：不使用子代理；继续按全角色真实前台体验广度复核剩余上线级问题，阶段完成后继续更新本文件并本地提交。
- 第一百零九批最新应用提交为 `104f43a99ac65fb75c74477a2f27d9d768183bea`
  （`fix: 收敛质量风险提醒口径`）。本批未使用子代理、未推送远程、未合并 `main`；
  继续以宪章、产品范围、体验契约、功能目录和四职责旅程为准复核质量域前台体验。发现
  `/qc/dashboard` 已作为 `质量风险概览`，`/qc/alerts` 已作为 `质量问题与整改`，但质量看板默认风险来源、
  下钻证据摘要、后端错误消息和 API 说明仍使用旧入口式表述 `质控预警`，会让院方质控人员和医疗引擎运营员误以为
  当前对象是后台预警配置，而不是需要跟踪的医疗质量风险提醒。现将前端默认来源口径收敛为 `质量风险提醒`，
  对含 `alert-*` 等追溯令牌的预览标题默认收起为业务来源；证据详情打开后仍展示原始追溯字段。后端
  `QualityDashboardService` 的未找到 / 已闭环错误消息、`QualityDashboardController` 和过滤 DTO 中文说明同步收敛为
  `质量风险提醒`；表名、枚举、接口字段与持久化契约不改。
- 第一百零九批验证：前端先补 `QcDashboard` 用例并在旧实现上跑出预期失败
  （`npm --prefix frontend run test -- QcDashboard -t "质量风险来源"` 无法找到 `质量风险提醒`）；
  后端先改 `QualityDashboardServiceTest` 期望，`mvn -f medkernel-backend/pom.xml -Dtest=QualityDashboardServiceTest#acknowledgeAlertIsTenantScopedAndRejectsMissingAlert test`
  在旧实现上失败，实际消息仍为 `质控预警 不存在`。修正后同两个窄测均通过；随后
  `npm --prefix frontend run test -- QcDashboard QcAlerts productRoleJourneys routes` 通过
  （4 个文件、81 个测试），
  `mvn -f medkernel-backend/pom.xml -Dtest=QualityDashboardServiceTest test` 通过（8 个测试），
  `npm --prefix frontend exec prettier -- --check frontend/src/pages/quality/QcDashboard.tsx frontend/src/pages/quality/QcDashboard.test.tsx`
  通过，`git diff --check` 和 `git diff --cached --check` 均退出码 0。旧词扫描
  `rg -n "质控预警" frontend/src medkernel-backend/src/main/java medkernel-backend/src/test/java docs/CONSTITUTION.md docs/PRODUCT_SCOPE.md docs/EXPERIENCE_CONTRACT.md docs/audit/product-function-catalog.md docs/audit/product-role-journeys.md`
  仅剩测试里的“不得出现旧词”断言。重门禁：`npm --prefix frontend run build` 通过，
  `npm --prefix frontend run verify` 通过（114 个测试文件、965 个测试；仅出现既有 antd Timeline deprecation warning），
  `mvn -f medkernel-backend/pom.xml test` 通过（3064 个测试、0 failures、0 errors、7 skipped；
  Docker/Testcontainers 多方言容器用例因本机无 Docker 按既有条件跳过）。
- 第一百零九批开工核查：`origin/main` 仍为
  `1561ba6bef8777dcef76432696f43de4277fdd3f`
  （`完善全角色上线演练与134复演闭环 (#653)`）；本批应用提交前本地分支领先 `origin/main` 297 个提交，
  应用提交后领先 298 个提交。134 当前公网首页 `https://193.112.107.134/medkernel/` HTTP 200，
  `Date=Sat, 04 Jul 2026 16:51:47 GMT`，`Content-Length=832`；正确 readiness 端点
  `https://193.112.107.134/medkernel/actuator/health/readiness` HTTP 200 / `{"status":"UP"}`，
  `X-Trace-Id=885a6f94-3d85-40de-93bb-f69ab446b696`。误打
  `https://193.112.107.134/medkernel/api/actuator/health/readiness` 返回 HTTP 401，不作为 readiness 发布证据。
  本批本地应用提交尚未发布到 134、尚未推送远程、尚未合并 `main`。
- 下一步继续主线：不使用子代理；继续按全角色真实前台体验广度复核剩余上线级问题，阶段完成后继续更新本文件并本地提交。
- 第一百零八批最新应用提交为 `1ce52eea11d04facbeb3285ac08131bd1043ff23`
  （`fix: 校准临床工作台协同入口`）。本批未使用子代理、未推送远程、未合并 `main`；
  继续按宪章、体验契约、功能目录和四职责旅程复核全角色前台体验。发现临床使用者工作台的“临床协同入口”
  把 `消息通知` 放在临床卡片第三动作，并在空待办说明中与患者路径、提醒推荐、随访协同并列，和职责旅程中的临床高频任务
  `患者路径 / 提醒与推荐 / 随访协同` 不一致，也会把应位于全局页眉的通知入口误包装成临床协同主链路。现将卡片第三动作改为
  `随访协同` 并进入 `/clinical/followup`，空待办说明同步收敛为患者路径、提醒与推荐或随访协同；`消息通知`
  继续保留为全局页眉通知与通知设置能力。
- 第一百零八批验证：先改 `WorkbenchPanel` 测试并在旧实现上跑出预期失败
  （`npm --prefix frontend run test -- WorkbenchPanel -t "prioritizes my todo"` 因旧空待办说明仍包含消息通知而失败）；
  修正后同命令通过（1 个测试通过、16 个跳过）。随后
  `npm --prefix frontend run test -- WorkbenchPanel productRoleJourneys Notifications AppLayout routes` 通过
  （6 个文件、123 个测试），
  `npm --prefix frontend exec prettier -- --check frontend/src/widgets/WorkbenchPanel.tsx frontend/src/widgets/WorkbenchPanel.test.tsx`
  通过，`npm --prefix frontend run build` 通过，`npm --prefix frontend run verify` 通过
  （114 个测试文件、964 个测试；仅出现既有 antd Timeline deprecation warning），`git diff --check`
  和 `git diff --cached --check` 均退出码 0。
- 第一百零八批开工核查：`origin/main` 仍为
  `1561ba6bef8777dcef76432696f43de4277fdd3f`
  （`完善全角色上线演练与134复演闭环 (#653)`）；本批应用提交前本地分支领先 `origin/main` 295 个提交。
  134 当前公网首页 `https://193.112.107.134/medkernel/` HTTP 200，
  `Date=Sat, 04 Jul 2026 16:43:08 GMT`，`Content-Length=832`；
  readiness `https://193.112.107.134/medkernel/actuator/health/readiness` HTTP 200 /
  `{"status":"UP"}`，`X-Trace-Id=b5422552-0561-40e5-8314-611b9321b466`。本批本地应用提交尚未发布到
  134、尚未推送远程、尚未合并 `main`。
- 第一百零七批最新应用提交为 `542ae273e8a0616f967fcfb253d967d9bc140af6`
  （`fix: 优化工作台职责筛选口径`）。本批未使用子代理、未推送远程、未合并 `main`；
  以宪章、体验契约和四职责旅程为准，继续全角色前台体验复核，发现工作台三项默认筛选对所有职责统一展示
  “病种”，会把平台管理员、审计员和全医疗资产运营误收窄成临床病种视角。现改为按职责展示第三筛选维度：
  平台管理员为“上线状态”，医疗引擎运营员为“资产类型”，临床使用者为“临床场景”，审计员为“证据类型”；
  仍保持默认筛选 3 项、主按钮 1 个、职责高频任务不超过 3 个。
- 第一百零七批验证：先改 `WorkbenchPanel` 测试，`npm --prefix frontend run test -- WorkbenchPanel -t "default filter"`
  在旧实现上预期失败（2 个测试失败、15 个跳过）；实现职责筛选维度后同命令通过（2 个测试通过、15 个跳过）。
  随后 `npm --prefix frontend run test -- WorkbenchPanel productRoleJourneys pages.smoke` 通过
  （3 个文件、52 个测试），
  `npm --prefix frontend exec prettier -- --check frontend/src/widgets/WorkbenchPanel.tsx frontend/src/widgets/WorkbenchPanel.test.tsx`
  通过，`npm --prefix frontend run build` 通过，`npm --prefix frontend run verify` 通过
  （114 个测试文件、964 个测试），`git diff --check` 退出码 0。
- 第一百零六批最新应用提交为 `52c5344a151231924363849005b1e51bbfb959ea`
  （`fix: 收敛平台管理员工作台建议动作`）。本批未使用子代理、未推送远程、未合并 `main`；
  以十二角色真实前台体验、菜单权限和运行职责边界为准，修正平台管理员工作台“本周建议动作”。
  原建议把平台管理员引向 `/config/releases` 的“机构生效版本”，容易越过平台管理员默认菜单与资产发布职责边界；
  现改为“核对服务运行保障”并进入 `/system/providers`，同时将实施进度说明收敛为
  “查看实施阶段、系统接入状态与上线准备项。”，避免把运行侧生效版本误包装成平台管理员的默认复核动作。
- 第一百零六批验证：先在 `WorkbenchPanel` 测试中补入“核对服务运行保障”与新实施说明断言并跑出预期失败；
  修正后 `npm --prefix frontend run test -- WorkbenchPanel -t "shows lifecycle governance slices"` 通过，
  `npm --prefix frontend run test -- WorkbenchPanel productRoleJourneys routes` 通过（3 个文件、78 个测试），
  `npm --prefix frontend exec prettier -- --check frontend/src/widgets/WorkbenchPanel.tsx frontend/src/widgets/WorkbenchPanel.test.tsx`
  通过，`npm --prefix frontend run build` 通过，`npm --prefix frontend run verify` 通过
  （114 个测试文件、963 个测试），`git diff --check` 退出码 0。
- 第一百零四批最新应用提交为 `ec4a973f03a0a629d5204cd6461ee7d281df8e57`
  （`fix: 收敛全局菜单医疗场景命名`）。本批未使用子代理、未推送远程、未合并 `main`；
  以宪章、产品范围、体验契约、功能目录和十二角色职责为准，从医疗产品全局视角复核菜单命名与分布。
  结论：保留 `诊断知识库`、`临床路径库`、`全真体验沙盘` 等已契合业务对象的入口；
  不采用 `临床路径模板` 作为前台菜单名，避免被理解为引用模板；一级分组和菜单顺序保持现状，
  继续按“知识治理 / 知识生产 / 临床协同 / 质量管理 / 系统运维”的职责链路组织。
  本批将 `质量管理概览` 收敛为 `质量风险概览`，将 `运行保障` 收敛为 `服务运行保障`，
  将 `模型能力` 菜单与页面标题收敛为 `模型能力与安全`；领域字段中的“模型能力”作为能力对象名保留。
- 第一百零四批验证：先改 `routes`、`AiWorkflows`、`QcDashboard`、`WorkbenchPanel` 相关测试并跑出旧菜单名预期失败；
  后端 `MenuPermissionCatalogTest` 也先跑出旧权限菜单名预期失败。修正后
  `npm --prefix frontend run test -- routes AiWorkflows QcDashboard WorkbenchPanel SystemProviders pages.smoke AppLayout`
  通过（7 个文件、146 个测试），
  `mvn -f medkernel-backend/pom.xml -Dtest=MenuPermissionCatalogTest,RuntimeDiagnosticsControllerTest,RuntimeOperationsServiceTest test`
  通过（13 个测试），`npm --prefix frontend run build` 通过，
  `mvn -f medkernel-backend/pom.xml test` 通过
  （3064 个测试、0 failures、0 errors、7 skipped；Docker/Testcontainers 多方言容器用例因本机无 Docker 按既有条件跳过），
  `npm --prefix frontend run verify` 通过（114 个测试文件、963 个测试），`git diff --check` 退出码 0
  （仅提示 `scripts/audit/export-product-capabilities.mjs` 行尾将转 LF），旧词扫描未发现
  `质量管理概览` 或裸 `运行保障` 残留，仅保留两处有意的领域字段 `模型能力`。
- 第一百零五批 134 / squash main 发布证据映射核查只更新文档，不产生应用提交、不使用子代理、
  不推送远程、不合并 `main`。新鲜核查结果：本地 `main`、本地 `origin/main` 与远端
  `refs/heads/main` 均为 `1561ba6bef8777dcef76432696f43de4277fdd3f`
  （`完善全角色上线演练与134复演闭环 (#653)`）；当批核查基点为交接提交 `dabb740b`，
  包含 `origin/main` 且本地领先 290 个提交，未进入远程 `main`。
- 第一百零五批 134 新鲜公网证据：`https://193.112.107.134/medkernel/` HTTP 200，
  `Date=Sat, 04 Jul 2026 16:27:43 GMT`，`index.html` 832 字节，仍指向
  `/assets/index-DYTh-Ceu.js`、`/assets/vendor-react-bdrMx_IT.js`、`/assets/vendor-data-D9EFEnEk.js`；
  `/assets/index-DYTh-Ceu.js` HTTP 200 / 876520 字节，
  `/assets/KnowledgeProduction-ClNuDXyb.js` HTTP 200 / 20735 字节。readiness 使用
  `https://193.112.107.134/medkernel/actuator/health/readiness` 返回 HTTP 200 /
  `{"status":"UP"}`，`X-Trace-Id=0c7a3a0c-1edc-4233-bd21-5d92deb21250`；登录租户入口
  `https://193.112.107.134/medkernel/api/v1/auth/login-tenants` 返回 HTTP 200，
  `success=true`、`hasCustomerTenants=true`、`primaryTenants[0].tenantId=t-rehearsal`、
  `primaryTenants[0].name=完整上线演练机构`、`X-Trace-Id=c449f3d2-96e8-4fa9-a841-bcac72622637`。
- 第一百零五批映射结论：134 已部署后端/JAR 仍是完整发布
  `3ddd979b3151e3eb1d40712e76b513e4cdce260c`，134 已部署前端 dist 仍是前端-only 发布
  `95bb816292f59833005df4761866dd9d89886cb4`；本地第一百零四批及后续应用提交
  均尚未发布到 134、尚未推送远程、尚未合并 `main`，后续不得把本地提交误写为 134 已上线。
- 第一百零三批最新应用提交为 `690e86e71968622688ee0dc15a3b8c7f695aad07`
  （`fix: 优化登录页单屏体验`）。本批未使用子代理、未推送远程、未合并 `main`；
  将登录页默认措辞从“平台治理登录名 / 集团医疗智能中枢”等偏治理后台话术，收敛为
  “平台管理员账号 / 医疗知识与决策支持平台”等更贴近医疗机构登录场景的表达；同时压缩登录卡片、
  主题入口、提示条和页脚间距，默认登录态使用 `100dvh` 与 `overflow: clip` 避免外层页面滚动条，
  展开登录帮助 / 统一身份或低高度视口时恢复纵向滚动，确保内容仍可达。
- 第一百零三批验证：先改 `Login` 与 `pages.smoke` 测试并跑出旧文案 / 旧滚动约束预期失败；
  修正后 `npm --prefix frontend run test -- Login pages.smoke` 通过（2 个文件、48 个测试），
  `npm --prefix frontend run build` 通过，Playwright 预览验证 `http://127.0.0.1:4173/login`
  在 1280×720 与 390×844 视口均为 `scrollHeight == clientHeight`、默认外层 `overflow: clip`，
  `npm --prefix frontend run verify` 通过（114 个测试文件、963 个测试）。
- 第一百零三批后续项中的全局菜单名称与顺序分布复核已由第一百零四批处理。
- 第一百零二批最新应用提交为 `673e6aa2ca4dc88ee65a63f96199803b1ea62ce7`
  （`fix: 收敛随访方案前台口径`）。本批未使用子代理、未推送远程、未合并 `main`；
  以权威产品/体验/职责文件和当前代码为准，将临床随访资产在前台、权限展示、审计摘要、资产编目、
  职责演练、后端用户可见消息、5 方言迁移注释和架构文档中的业务口径从“随访模板”收敛为
  “随访方案”。`FollowupTemplate*` 类名、`templateId` 等接口字段、表名/列名与既有持久化契约保持不变；
  证据详情继续展示后端原始名称/分类，用于证明兼容历史数据且不污染业务默认视图。
- 第一百零二批验证：先改 `Followup`、`AdminAudit`、`AuthoringAssets`、`routes` 相关测试并跑出旧词预期失败；
  修正后 `npm --prefix frontend run test -- Followup AdminAudit AuthoringAssets routes` 通过
  （4 个文件、90 个测试），`npm --prefix frontend run build` 通过，`npm --prefix frontend run verify` 通过
  （114 个测试文件、963 个测试），
  `mvn -f medkernel-backend/pom.xml -Dtest=FollowupTemplateServiceTest,RuntimeReleaseFollowupTemplateSelectorTest test`
  通过（10 个测试），`mvn -f medkernel-backend/pom.xml test` 通过
  （3064 个测试、0 failures、0 errors、7 skipped；Docker/Testcontainers 多方言容器用例因本机无 Docker 按既有条件跳过）。
- 第一百零二批后续项中的登录界面滚动条与登录文案措辞已由第一百零三批处理。
- 第一百零一批最新应用提交为 `0abba88f4e63db2bd165af1419add30d4341f1b3`
  （`fix: 收敛页面动作口径`）。其前置本地提交包括第一百批阶段交接提交
  `a62896490dd1e489835805b20761f9d5531f5a67`
  （`docs: 记录登录入口与工作台主动作复演`）、第一百批应用提交
  `ef3afbfc877e340aea85a2484eb80a4ca91e9e52`
  （`fix: 收敛登录入口与工作台主动作口径`）、第九十九批阶段交接提交
  `c8cff43e2c0376a21a9da8ee7a9573fd122564c1`
  （`docs: 记录初始化提示菜单名复演`）、第九十九批应用提交
  `a800aa8b986330d0a620e325cfbe59edefe516da`
  （`fix: 校准初始化提示菜单名`）、第九十八批阶段交接提交
  `f9ac02060fc3c94f5f996194f6dc5900413ea311`
  （`docs: 记录服务机构状态口径复演`）、第九十八批应用提交
  `96699172a2824d2be51c12cdda3e1cd54c668eab`
  （`fix: 收敛服务机构状态口径`）、第九十七批阶段交接提交
  `5294afb81a3ccf1c870cf8bd48a14f98069ae127`
  （`docs: 记录临床路径起始结构口径复演`）、第九十七批应用提交
  `868205296a043ab32ca8959806da64b25ad06c38`
  （`fix: 收敛临床路径起始结构口径`）、第九十六批阶段交接提交
  `ae85fd11339fc3f02a83e3a85a9a4f156da0c03b`
  （`docs: 记录机构差异版本操作口径复演`）、第九十六批应用提交
  `29b219cac27de363023457c49149d49eeaac609a`
  （`fix: 收敛机构差异版本操作口径`）、第九十五批阶段交接提交
  `d0b7425381a0b8d3e6224d41fbfe7ac96927384d`
  （`docs: 记录登录平台治理口径复演`）、第九十五批应用提交
  `5da7ae7738ce2e98543f5602d4fc2ae3365c24a4`
  （`fix: 收敛登录平台治理口径`）、第九十四批阶段交接提交
  `905037be5df9f9057e37db7e35cd9e186d110308`
  （`docs: 记录机构知识库任务口径复演`）、第九十四批应用提交
  `bbeed1a0b2af476ab0c1e8bc8bece2136a4719d5`
  （`fix: 收敛机构知识库任务口径`）、第九十三批阶段交接提交
  `ea57f11cc84ff93f961d38c250f02c928a8bc6da`
  （`docs: 记录系统接入任务口径复演`）、第九十三批应用提交
  `ac7a3dc6ea72af1ddce98534c193ee2195e78b96`
  （`fix: 收敛系统接入任务口径`）、第九十二批阶段交接提交
  `8d3934892eb6e0ad23c951e393dedad93d440d14`
  （`docs: 记录术语字典任务口径复演`）、第九十二批应用提交
  `ade2cbe050a9ea75204f83b127132ca107a2cae5`
  （`fix: 收敛术语字典任务口径`）、第九十一批阶段交接提交
  `c5bfbb481bb698bf084ccb0c2ec0edec29dbbfd9`
  （`docs: 记录机构生效版本任务口径复演`）、第九十一批应用提交
  `7cfd8494aa4d53f43ed103a82669ebf8a925fe08`
  （`fix: 收敛机构生效版本任务口径`）、第九十批阶段交接提交
  `b68223a6e7f4f94636546c639e1da427726a109f`
  （`docs: 记录临床规则任务口径复演`）、第九十批应用提交
  `30114152426d375b0c792b8e92ffc3f2a92268d7`
  （`fix: 收敛临床规则任务口径`）、第八十九批阶段交接提交
  `78200ea95625ee20ea9ee46e801035e935cee313`
  （`docs: 记录评价指标任务口径复演`）、第八十九批应用提交
  `58c01f7546bad98d3c1022b6f939d78cb4b1cbb4`
  （`fix: 收敛评价指标任务口径`）、第八十八批阶段交接提交
  `25d797f2b499cf5872decb4a7f96739e6f249765`
  （`docs: 记录临床路径库任务口径复演`）、第八十八批应用提交
  `e901e739ffab92bf66f6438d306cf68b3cfe63c4`
  （`fix: 收敛临床路径库任务口径`）、第八十七批阶段交接提交
  `448d93e957a0fee9757c60e7455cf359e8921be8`
  （`docs: 记录诊断知识库任务口径复演`）、第八十七批应用提交
  `e4bcc19f4e8b8380368a1c71684a037a35002223`
  （`fix: 收敛诊断知识库任务口径`）、第八十六批阶段交接提交
  `a49defb4a3f17c7dab073927d3c26cdb461f6b9d`
  （`docs: 记录资产编目入口口径复演`）、第八十六批应用提交
  `e5490c79c8cb1907e602db4208e54e1f7b25e8ec`
  （`fix: 收敛资产编目入口口径`）、第八十五批阶段交接提交
  `d117c80251b88e3312ee8b574b312f33b60402b3`
  （`docs: 记录七步流来源口径复演`）、第八十五批应用提交
  `b748e260301b849f5f615954751828f56c925b8f`
  （`fix: 收敛七步流来源选择口径`）、第八十四批阶段交接提交
  `a962b613ae72bc1e76dec1028acfc750a0dbb5dd`
  （`docs: 记录批量规则示例口径复演`）、第八十四批应用提交
  `df1135ca32b6980f2bcfb86e523e182b85030dd0`
  （`fix: 收敛批量规则默认示例口径`）、第八十三批阶段交接提交
  `ac9a65f2f3d8ae6ae77db7a33afb1d86be4b3207`
  （`docs: 记录临床路径建模示例复演`）、第八十三批应用提交
  `8ad454f29f42f610a88dc3a11691224d8ac9caa5`
  （`fix: 收敛临床路径建模示例口径`）、第八十二批阶段交接提交
  `83b92fba8f3ff68d0b70e8b034a7c059341ea0d6`
  （`docs: 记录诊断知识表单示例复演`）、第八十二批应用提交
  `6a7682444f228eba6677c9b3c209b786020f7f53`
  （`fix: 收敛诊断知识表单示例口径`）、第八十一批阶段交接提交
  `af769750b97c6f1a43c562d77457e0df7d7c9c06`
  （`docs: 记录诊断知识库职责边界复演`）、第八十一批应用提交
  `66a429a4ef00663abc3247f47112f16d0a3ac9d0`
  （`fix: 收敛诊断知识库职责边界口径`）、第八十批阶段交接提交
  `f158744e394ffa11bd8853d3ae519f58645bba4c`
  （`docs: 记录工作台运行底座默认层复演`）、第八十批应用提交
  `0322041c2ddc0b3c97db0ead209eaed191219957`
  （`fix: 收敛工作台运行底座默认层`）、第七十九批阶段交接提交
  `c244e72ec8605c5ddbe5a5ef17991c33ff632f15`
  （`docs: 记录随访模板默认业务口径复演`）、第七十九批应用提交
  `90dce703a9848147e7f4d02ff43b0f35fc22e7e0`
  （`fix: 收敛随访模板默认业务口径`）、第七十八批阶段交接提交
  `9a3dfac17bb114342d616130ec3fc48e02a080cc`
  （`docs: 记录临床路径层级前台口径复演`）、第七十八批应用提交
  `4384ab19b37250ac1d0923f08d4b1f7b5c05e1e8`
  （`fix: 收敛临床路径层级前台口径`）、第七十七批阶段交接提交
  `68bb943a924e483e7b69fdb38b8188fbb6239e61`
  （`docs: 记录评价指标默认术语复演`）、第七十七批应用提交
  `0c94be2b4d7dfd0d0500771cfd762d6fab8e5063`
  （`fix: 统一评价指标默认术语`）、第七十六批阶段交接提交
  `3d60a2ab5d8ffe8358268d5f27a23b3f3a35f64c`
  （`docs: 记录质量评价默认真实口径复演`）、第七十六批应用提交
  `1267b484af12c35640cbaa010ebc58a99854b0c0`
  （`fix: 收敛质量评价默认真实口径`）、第七十五批阶段交接提交
  `f9a4c679764f4eedba1691bf1876435908c06d71`
  （`docs: 记录质量概览默认真实口径复演`）、第七十五批应用提交
  `e9670a65270ab1e3e365cfcf038e94beb9eaa335`
  （`fix: 收敛质量概览默认真实口径`）、第七十四批阶段交接提交
  `f0532f4e26bcaf954eeaa9fb77a6cb9cac9d5e8a6`
  （`docs: 记录工作台知识关系同步口径复演`）、第七十四批应用提交
  `bb5c552600ce6d51a93bbd9a731c1c64a0ac4dc4`
  （`fix: 收敛工作台知识关系同步口径`）、第七十三批阶段交接提交
  `2676d85e4e082fb1aa28ba7b3a08d215feac1127`
  （`docs: 记录安全与配置前台入口口径复演`）、第七十三批应用提交
  `9ce90c04ffcbfa99be2054d5c16ed09b798202a9`
  （`fix: 收敛安全与配置前台入口口径`）、第七十二批阶段交接提交
  `05c3b49b9003088f37c0a01599320d552ef439d5`
  （`docs: 记录质量管理前台入口口径复演`）、第七十二批应用提交
  `b10c3d9e00e3cc48c5385bd9f992c54f05e50e83`
  （`fix: 收敛质量管理前台入口口径`）、第七十一批阶段交接提交
  `112765d1c3a54b740237d485ac3c15a0379365f8`
  （`docs: 记录评价指标前台入口口径复演`）、第七十一批应用提交
  `609d49f37f9c364d9b55beb01a0e754da8e5fa70`
  （`fix: 收敛评价指标前台入口口径`）、第七十批阶段交接提交
  `e16a37bea5230807c1d6a9bcad10da5380483175`
  （`docs: 记录知识关系前台入口口径复演`）、第七十批应用提交
  `050278b0c4a77576293462224ca1eb39e808d8d2`
  （`fix: 收敛知识关系前台入口口径`）、第六十九批阶段交接提交
  `fe774a3b7d4380af732c6c96e5dc0468d61546be`
  （`docs: 记录职责旅程随访协同菜单复演`）、第六十九批应用/目录提交
  `14a62a587c58517482b891cd71f07249d045d677`
  （`fix: 校准职责旅程随访协同菜单快照`）、第六十八批阶段交接提交
  `6b9f2e2c677f4c32bcaa46766932d26b8086b545`
  （`docs: 记录产品目录知识生产业务域复演`）、第六十八批应用提交
  `9b4c4daab336c5a890fe5b7f0928a4035ff2fac2`
  （`fix: 校准产品目录知识生产业务域`）、第六十七批阶段交接提交
  `8629e5ff8cfff27301d7aae689a3fef625ca74ae`
  （`docs: 记录知识生产候选分流口径复演`）、第六十七批应用提交
  `01b6c6270e8b1b2b9138e80c2bd60ce321a99898`
  （`fix: 收敛知识生产候选分流前台口径`）、第六十六批阶段交接提交
  `e1f16076bb7058617a4f7954d5292ceec625652f`
  （`docs: 记录知识生产分流状态口径复演`）、第六十六批应用提交
  `08e3cc4d7cc6801ca850f543c8ce3c475a5f4c7c`
  （`fix: 收敛知识生产分流状态前台口径`）、第六十五批阶段交接提交
  `fb2df0c6a5d80f63fd17ee8040d7b0092562b1ef`
  （`docs: 记录区域来源状态口径复演`）、第六十五批应用提交
  `8b7a01c3dee997b65011f731e98bb168e9db5c5d`
  （`fix: 收敛区域来源状态前台口径`）、第六十四批阶段交接提交
  `f7cb2cc64b66d8b8f17b17beeb66fba4cd6c3818`
  （`docs: 记录沙盘生效版本口径复演`）、第六十四批应用提交
  `d8c376de2f700078f3b73877049942dbec3ecaba`
  （`fix: 收敛沙盘机构生效版本前台口径`）、第六十三批阶段交接提交
  `e678e7de6717e88f8e098f1d76f3c56a7b7b6ec8`
  （`docs: 记录知识生产校验状态口径复演`）、第六十三批应用提交
  `d3b64d1b3b42cc7e93586856e1dbf12b1b7245ec`
  （`fix: 收敛知识生产校验状态前台口径`）、第六十二批阶段交接提交
  `43418a945944eb83fac64d9ee77c0a4434ce8041`
  （`docs: 记录质量域组织范围口径复演`）、第六十二批应用提交
  `1c87961e779ecaf125bc49c23670e6c1589ffffa`
  （`fix: 收敛质量域组织范围前台口径`）、第六十一批阶段交接提交
  `6eafbfea4c2f19458f296cb52a046e7294ad5a9d`
  （`docs: 记录质控指标影响范围口径复演`）、第六十一批应用提交
  `8652321c49d23f7a160699e9445293811080c9bc`
  （`fix: 收敛质控指标影响范围前台口径`）、第六十批阶段交接提交
  `eb60ceec79f67cbf8027059aca0ba843453f5785`
  （`docs: 记录导出与批量任务编号复演`）、第六十批应用提交
  `4de1116aa2b4cf13d5a22b3ce8b6c83701232fdb`
  （`fix: 收敛导出与批量任务编号层级`）、第五十九批阶段交接提交
  `ab7fe656bb14805e4f1f52d9169c4bb9d74d7b9e`
  （`docs: 记录验收与生产目录口径复演`）、第五十九批应用提交
  `ae2557ba4b2772934b055bae120a9dc8be12004e`
  （`fix: 收敛验收与生产目录前台口径`）、第五十八批阶段交接提交
  `6f162420b7e051a75be934a0dddaa1874597c310`
  （`docs: 记录临床路径时窗校验口径复演`）、第五十八批应用提交
  `5e406100cfc808a327d42fc4dd668eb7529915b8`
  （`fix: 收敛临床路径时窗校验口径`）、第五十七批阶段交接提交
  `b7b8604c04c3b9b265c5ccd39c938ed79e822fb3`
  （`docs: 记录临床路径责任分工口径复演`）、第五十七批应用提交
  `1b47acb632319ac0b1ce980d4a580c2018a65ed5`
  （`fix: 收敛临床路径责任分工口径`）、第五十六批阶段交接提交
  `ec3b7170c7112421f147a55506b355fc984fdf08`
  （`docs: 记录临床事件协同链路口径复演`）、第五十六批应用提交
  `67a15e48bc0c3d56f84b79e63f7f75add6e08261`
  （`fix: 收敛临床事件协同链路口径`）、第五十五批阶段交接提交
  `c732c9f72c86dabb25ba3d220705f09c669db33c`
  （`docs: 记录安全配置运行环境口径复演`）、第五十五批应用提交
  `01bf139f2fb5e8ef90a24e17defb4e40a4e1a13f`
  （`fix: 收敛安全配置运行环境口径`）、第五十四批阶段交接提交
  `8637710c05e6cf68e3a0ac6e08e8f25dd30afd52`
  （`docs: 记录患者路径证据口径复演`）、第五十四批应用提交
  `691a5397b2ca50f60b4a3636ef3216f0d5c657f9`
  （`fix: 收敛患者路径证据口径`）、第五十三批阶段交接提交
  `b96ba67856ee6b0a124ec36ae84615f6fce8f306`
  （`docs: 记录临床规则推荐口径复演`）、第五十三批应用提交
  `ea76cbb69f6d39115ca32608161ad8c470ff7430`
  （`fix: 收敛临床规则与推荐前台口径`）、第五十二批阶段交接提交
  `b5a72c7e055b48f0f481e607887dcd43938a884c`
  （`docs: 记录批量规则基准资产口径复演`）、第五十二批应用提交
  `2a97de1738226b46e528e4b0445af4348227bf42`
  （`fix: 收敛批量规则基准资产口径`）、第五十一批阶段交接提交
  `184c831c6e1d48cd4ce3189fbc6b04a637448e31`
  （`docs: 记录沙盘智能协同口径复演`）、第五十一批应用提交
  `e4720e932c8f53f99ccfa3f3b8d4e16ad3777560`
  （`fix: 收敛沙盘智能协同口径`）、第五十批阶段交接提交
  `b091eecf1f7fccdfec2c1b523f7877b5e065f87`
  （`docs: 记录临床路径公开口径复演`）、第五十批应用提交
  `34bb45bc50591d57671f3dbbc426f63368ac342d`
  （`fix: 收敛临床路径公开口径`）、第四十九批阶段交接提交
  `a687b1a4a4913d1026db607d818bf8b7e5234e71`
  （`docs: 记录菜单服务口径复演`）、第四十九批应用提交
  `d109291c85dfa973b5362e8ff97e769537da79b0`
  （`fix: 收敛菜单服务与临床路径前台口径`）、第四十八批应用提交
  `d6e6db93f361bb8ffe6f750a75e9143445ebdf1b`
  （`fix: 收敛沙盘当前机构口径`）、第四十七批阶段交接提交
  `172658530152fef3439f456b4b2dbf996fedfc8d`
  （`docs: 记录未找到页面文案复演`）、第四十七批应用提交
  `eed002f1eeb880217c30525b1843c26bd4436e9c`
  （`fix: 收敛未找到页面工程态文案`）、第四十六批阶段交接提交
  `484ad513869d52280002f2e8c75036ef76d73944`
  （`docs: 记录知识生产候选治理文案复演`）、第四十六批应用提交
  `24687d0fcb9cc48173a2005c490456008e8a7cad`
  （`fix: 收敛知识生产与规则路径前台文案`）、第四十五批应用提交
  `54a5161befb248970bc24ab645ebf97b73bbcfbf`
  （`fix: 收敛国产化适配自检权威文案`）、第四十四批应用提交
  `b80735791243e989c1253ae94a19aa25fa289553`
  （`fix: 优化全局菜单命名与顺序`）、
  `2068d35de48326bf442e79455fd2a9bc21dd5f5b`
  （`docs: 澄清前端发布证据映射`）、
  `0552b0c1f86c606efedd61538f50ddc671661210`
  （`fix: 收敛运行保障国产化档案文案`）、
  `882f33f8e1e4520fdfa64a741e36c6b312f954b4`
  （`docs: 记录知识生产工作区层级复演`）、第四十三批应用提交
  `95bb816292f59833005df4761866dd9d89886cb4`
  （`fix: 收敛知识生产工作区层级`），以及第四十二批应用提交
  `2dbd668fde81540a4fc19ce9acd38b931cf7c2d2`
  （`fix: 收敛系统接入健康检查默认文案`）、第四十一批应用提交
  `3ddd979b3151e3eb1d40712e76b513e4cdce260c`
  （`fix: 强化模型外调核心标识遮蔽`）、第四十批应用提交
  `8889efc754b6c192708ddb118a5b9fa7d03cb28e`
  （`fix: 隐藏来源血缘演练版次标识`）和前置应用提交
  `70a0925cda2b642665ac930396c2d1e3eec06db1`
  （`fix: 收敛审计摘要临床内部标识`）已一并包含。`f461a1c5` / `f2590325` / `f98997cd`
  仍分别对应第三十九批标准术语登记、标准上下文严格契约和诊断专项 E2E 按钮稳定性修正。
- 当前本地最新应用代码已包含第十二批医保结算到质控整改数据路线、
  第十三批质量/医保默认信息层级、第十四批知识生产治理语义、第十五批知识生产统一入口与证据层级，
  第十六批知识生产上线准备默认证据收敛、第十七批医保审核快照默认标识收敛、第十八批随访模板默认展示收敛，
  第十九批系统接入默认技术信息收敛、第二十批质量下钻默认追溯信息收敛、第二十一批协同任务列表可读性收敛，
  第二十二批诊断知识统一治理边界收敛、第二十三批随访办理底部操作区收敛，
  第二十四批系统接入字段映射缺口分页摘要收敛、第二十五批医保问题服务端翻页收敛，
  第二十六批系统接入质量报告单一呈现收敛、第二十七批接入阻塞项默认文案收敛、
  第二十八批质量问题服务端翻页收敛、第二十九批质量概览待办密度收敛、第三十批模型能力默认表格层级收敛，
  第三十一批随访模板演练批次默认展示收敛、第三十二批临床提醒采纳率口径收敛、第三十三批人员详情抽屉证据稳定性，
  第三十四批诊断知识技术版次默认展示收敛、第三十五批知识资产随访模板默认名收敛、第三十六批来源血缘版本沿革默认展示收敛，
  第三十七批审计摘要默认标识收敛、第三十八批审计导出记录默认标识收敛、第四十批默认可见低频标识清零，
  第四十一批模型外调核心标识遮蔽、第四十二批系统接入健康检查默认文案收敛、第四十三批知识生产工作区层级收敛，
  第四十四批全局菜单命名与顺序收敛、第四十五批国产化适配自检权威文案收敛、第四十六批知识生产候选治理与规则路径前台文案收敛，
  第四十七批未找到页面工程态文案收敛、第四十八批全真体验沙盘当前机构口径收敛，第四十九批菜单服务与临床路径前台口径收敛，
  第五十批临床路径公开口径收敛、第五十一批全真体验沙盘智能协同口径收敛、第五十二批批量规则基准资产口径收敛，
  第五十三批临床规则与提醒推荐前台口径收敛、第五十四批患者路径证据口径收敛、第五十五批安全配置运行环境口径收敛，
  第五十六批临床事件协同链路口径收敛、第五十七批临床路径责任分工口径收敛、第五十八批临床路径时窗校验口径收敛，
  第五十九批验收与生产目录前台口径收敛、第六十批导出与批量任务编号层级收敛，
  第六十一批质控指标影响范围前台口径收敛、第六十二批质量域组织范围前台口径收敛，
  第六十三批知识生产校验状态前台口径收敛、第六十四批沙盘机构生效版本前台口径收敛，
  第六十五批系统接入区域来源状态前台口径收敛、第六十六批知识生产分流状态前台口径收敛、
  第六十七批知识生产候选分流前台口径收敛、第六十八批产品目录知识生产业务域校准，
  第六十九批职责旅程随访协同菜单快照校准、第七十批知识关系前台入口口径收敛，
  第七十一批评价指标前台入口口径收敛、第七十二批质量管理前台入口口径收敛，
  第七十三批安全与配置前台入口口径收敛、第七十四批工作台知识关系同步口径收敛，
  第七十五批质量管理概览默认真实口径收敛、第七十六批质量评价默认真实口径收敛，
  第七十七批评价指标默认术语统一、第七十八批临床路径层级前台口径收敛，
  第七十九批随访模板默认业务口径收敛、第八十批工作台运行底座默认层收敛、第八十一批诊断知识库职责边界口径收敛，
  第八十二批诊断知识表单示例口径收敛、第八十三批临床路径建模示例口径收敛、第八十四批批量规则默认示例口径收敛，
  第八十五批七步流来源选择口径收敛、第八十六批资产编目入口口径收敛、第八十七批诊断知识库任务口径收敛、
  第八十八批临床路径库任务口径收敛、第八十九批评价指标任务口径收敛、第九十批临床规则任务口径收敛，
  第九十一批机构生效版本任务口径收敛、第九十二批术语字典任务口径收敛、第九十三批系统接入任务口径收敛，
  第九十四批机构知识库任务口径收敛、第九十五批登录平台治理口径收敛，第九十六批机构差异版本操作口径收敛，
  第九十七批临床路径起始结构口径收敛、第九十八批服务机构状态口径收敛，
  第九十九批初始化提示菜单名校准，以及第一百批登录入口首屏与工作台主动作口径收敛。
- 134 当前后端/JAR 仍来自全量部署 `3ddd979b3151e3eb1d40712e76b513e4cdce260c`；发布命令为
  `deploy/onprem/mk-publish.sh --source 3ddd979b3151e3eb1d40712e76b513e4cdce260c`。远端备份
  `/zoesoft/medkernel/backups/deploy-20260703-123810`；manifest 记录
  `source=3ddd979b3151e3eb1d40712e76b513e4cdce260c`、
  `commit=3ddd979b3151e3eb1d40712e76b513e4cdce260c`、
  `deployedAt=2026-07-03T12:38:13+08:00`、
  `jarSha256=37da0f4b8d42e040408ab530714f68228e8060a21f6690146d8cb58126ca96ec`；readiness HTTP 200 /
  `{"status":"UP"}`，服务 `active/enabled`、最近一次前端-only 发布后 `MainPID=3600701`、`NRestarts=0`。
- 134 当前前端 dist 来自 `95bb816292f59833005df4761866dd9d89886cb4` 的前端-only 发布；命令为
  `deploy/onprem/mk-publish.sh --frontend --source 95bb816292f59833005df4761866dd9d89886cb4`，远端备份
  `/zoesoft/medkernel/backups/deploy-20260703-144653`。这是前端-only 发布，后端 manifest/JAR 继续记录
  `3ddd979b3151e3eb1d40712e76b513e4cdce260c` 是正确状态，不要误判为前端未更新。外部 `index.html` 指向
  `/assets/index-DYTh-Ceu.js`，`/assets/KnowledgeProduction-ClNuDXyb.js` HTTP 200 / `20735` 字节。
- 134 对外 E2E 入口使用 `https://193.112.107.134/medkernel` 与
  `https://193.112.107.134/medkernel/api/v1`，当前证书按现场自签/非可信处理，Playwright 需带
  `E2E_IGNORE_HTTPS_ERRORS=1`。后端 `18080` 只监听 `127.0.0.1`，不要从外网使用
  `http://193.112.107.134:18080` 作为演练入口。
- 后续如只改前端可按新提交版本执行前端-only 重发；如改后端/JAR 或迁移才需要完整发布。当前 134 状态是
  “后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`，前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`”，
  不要继续沿用旧的 `ef662ced` / `8889efc7` 拆分描述。
- 当前本地最新应用代码提交为 `ec4a973f03a0a629d5204cd6461ee7d281df8e57`；当前本地分支仍只本地提交，
  不推送远程 `main`，不得把本地最新应用提交误记为 134 已部署版本。
- 当前上线 E2E 职责账号契约：`E2E_ROLE_CREDENTIALS_FILE` 必须指向 READY 状态
  `schemaVersion=1.0.0` 文件；平台治理与平台知识生产显式读取 canonical `platform.accounts`，
  真实前台、客户职责旅程与机构业务链路默认读取 canonical `rehearsal.accounts`。
  `rehearsal` 是当前 1.0 契约中的完整上线演练机构块，不是旧兼容入口；
  `roleAccounts`、`platformRoleAccounts`、`customerTenant` 等旧 root 账号字段不得作为登录权威。
- 当前用户约束：全程按最优决策执行，不中途咨询；后续不使用子代理；每阶段更新接力并提交到本地分支；
  最终统一确认前不推送远程 `main`。
- `.codex/config.toml` 为未跟踪本地配置，不提交。

## 最新阶段交接（2026-07-04 全视角真实前台体验优化第一百零一批·页面动作口径收敛）

- 本批基于全局菜单、产品目录、职责旅程和医疗场景体验继续复核页面级动作口径。结论：当前菜单 IA 与顺序不改；
  `诊断知识维护` 已按权威入口收敛为 `诊断知识库`，`临床路径模板` 已收敛为 `临床路径库`，避免被理解为引用模板。
  本批只修正页面内用户可见标题、标签、空态和表格列名，不改路由、菜单顺序、后端 API、数据库或 134 发布状态。
- 已本地提交 `0abba88f4e63db2bd165af1419add30d4341f1b3`
  （`fix: 收敛页面动作口径`）：
  - `Provenance` 页面标题从“知识来源追溯”统一为权威入口名“来源与血缘”。
  - `RuntimeDiagnostics` 将前台“插件管理 / 注册插件 / 插件列表”等技术色彩入口收敛为“扩展能力 / 登记扩展能力 /
    扩展能力读取失败”等医疗运行语境；后台变量和 API 契约仍保持 `plugin*`，未改数据模型。
  - `RuleDefinitions`、`PathwayTemplates` 将“管理字段目录”收敛为“查看字段目录”；`PathwayTemplates` 表格列
    “管理动作”改为“路径操作”；`CdssFatigue` 表格列“管理”改为“反馈操作”。
  - `SecurityBaselinePanels` 默认文案去掉 `tmp` 技术 token；`DeclarativeAssetWorkbench` 将泛化“各自工作台管理”
    收敛为“对应工作台维护”。
  - `AdapterHub` 组织范围空态从抽象“维护组织架构”改为指向权威菜单“服务机构”；同时修复 `OrgUnitSelect`
    原本覆盖页面级 `notFoundContent` 的问题，保留组织目录读取失败时的诚实错误态。
- 本地验证：
  - 红灯：先改测试后执行
    `npm --prefix frontend test -- Provenance.test.tsx operationalControlPages.test.tsx RuleDefinitions.test.tsx PathwayTemplates.test.tsx CdssFatigue.test.tsx SecurityBaseline.test.tsx DeclarativeAssetWorkbench.test.tsx`
    在旧实现下失败，DOM 仍展示“知识来源追溯 / 插件管理 / 管理字段目录 / 管理动作”等旧口径；新增
    `AdapterHub.test.tsx -t "points empty organization"` 在旧 `OrgUnitSelect` 下失败，证明页面级空态被覆盖。
  - 定向绿灯：
    `npm --prefix frontend test -- Provenance.test.tsx operationalControlPages.test.tsx RuleDefinitions.test.tsx PathwayTemplates.test.tsx CdssFatigue.test.tsx SecurityBaseline.test.tsx DeclarativeAssetWorkbench.test.tsx AdapterHub.test.tsx`
    通过，`8` 个测试文件 / `103` 项。
  - 菜单、路由、产品目录和烟测：
    `npm --prefix frontend test -- pages.smoke.test.tsx productCatalog.test.ts menu.test.ts routes.test.ts productRoleJourneys.test.ts`
    通过，`5` 个测试文件 / `101` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `963` 项；包含 lint、
    stylelint、真实性/视觉自定义规则、Prettier、TypeScript 与全量 Vitest；仅保留既有 AntD `Timeline.Item`
    deprecation warning。
  - 生产构建：`npm --prefix frontend run build` 通过，Vite 构建完成；`Provenance-CjVyFA6v.js`、
    `RuntimeDiagnostics-BrwwTTbw.js`、`AdapterHub-CQOh_SVr.js` 等新产物生成。
  - 旧口径生产源码反扫：
    `rg -n "知识来源追溯|插件管理|注册插件|插件列表读取失败|暂无插件|稳定插件能力身份|插件授权|管理字段目录|管理动作|tmp 临时目录|字段目录与完整路径分别由各自工作台管理|暂无可选组织，请先维护组织架构" frontend/src --glob '!**/*.test.ts' --glob '!**/*.test.tsx'`
    返回 `NO_MATCH`；旧语义只保留在测试反向断言与本接力记录中。
  - `git diff --check`、应用提交前 `git diff --cached --check` 均通过。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `https://193.112.107.134/medkernel/` HTTP 200，`Date=Sat, 04 Jul 2026 12:32:05 GMT`，
  `Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，`Content-Length=832`，`ETag="6a475ada-340"`；
  `https://193.112.107.134/medkernel/api/v1/auth/login-tenants` 返回
  `primaryTenants[0].tenantId=t-rehearsal`、`name=完整上线演练机构`、`platformTenant.tenantId=t-1`、
  `name=平台治理入口（唯一内置）`、`hasCustomerTenants=true`，`traceId=f013bab8-b07f-45d5-b467-90ff2bdd6980`。
  当前 134 映射仍是后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、前端
  dist=`95bb816292f59833005df4761866dd9d89886cb4`；不要把本地 `0abba88f` 记为已部署。
- 下一步：后续不使用子代理；继续按权威文档、产品目录、职责旅程和真实前台体验广度优先复核。优先看登录后全角色
  高频页面中的泛化“管理 / 维护 / 模板 / 配置”是否仍误导职责或对象边界；发现真实产品体验、流程、前后端、
  文档、测试、构建、部署问题，直接按上线级最优方案修复并本地提交。

## 最新阶段交接（2026-07-04 全视角真实前台体验优化第一百批·登录入口首屏与工作台主动作口径收敛）

- 本批基于用户补充反馈“登录界面有滑动滚动条、整体界面和文字措辞需优化”，结合全局菜单、全角色职责旅程、
  医疗产品体验与 134 真实登录租户状态复核。结论：登录入口不应再使用泛化“账号”口径；机构用户应表达为
  院内登录名 / 工号，平台入口应表达为平台治理登录名；平台管理员工作台唯一主动作应对齐权威菜单
  `人员与账号`，但按钮语义要从泛化“管理账号”收敛为“维护人员与账号”。登录页首屏滚动根因是页面
  `min-height` 与上下 padding 未纳入 border-box，再叠加卡片内容密度导致初始视口溢出。
- 已本地提交 `ef3afbfc877e340aea85a2484eb80a4ca91e9e52`
  （`fix: 收敛登录入口与工作台主动作口径`）：
  - `frontend/src/pages/Login.tsx` 将标题收敛为“进入平台治理 / 进入机构工作台”，登录字段收敛为
    “工号 / 登录名”，占位、帮助、统一身份与安全策略文案改为医疗机构登录与平台治理口径；不改变登录 API、
    租户选择、权限或路由。
  - `frontend/src/pages/Login.module.css` 使用 `100dvh`、`border-box` 和更紧凑的卡片/表单间距，保留备案、
    统一身份和登录帮助信息，同时消除桌面与窄屏首屏默认纵向滚动。
  - `frontend/src/shared/config/productRoleJourneys.ts` 与
    `docs/audit/product-role-journeys.md` 将平台管理员旅程主动作从“管理账号”改为“维护人员与账号”，摘要同步
    “人员账号、系统接入、运行配置和上线验收”；`WorkbenchPanel` 与登录冒烟测试同步正向锁定新口径，并反向阻断
    “管理账号 / 登录平台治理 / 工号 / 账号”等旧口径回流。
- 本地验证：
  - 定向回归：
    `npm --prefix frontend test -- Login.test.tsx WorkbenchPanel.test.tsx productRoleJourneys.test.ts pages.smoke.test.tsx`
    通过，`4` 个测试文件 / `73` 项。
  - 生产构建：`npm --prefix frontend run build` 通过，生成 `Login-BczseoeM.js` / `Login-bfo8LQ4r.css` 等产物。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `962` 项；仅保留既有 AntD
    `Timeline.Item` deprecation warning。
  - 产品目录一致性：`node scripts/audit/export-product-capabilities.mjs --check` 通过；本批未改变自动生成产品目录。
  - 中文注释与真实性门禁：`bash scripts/check-comment-zh.sh --mode=full` 通过，engine/shared 类级 Javadoc 与
    oracle/postgres/kingbase 建表 COMMENT 覆盖均为 `100%`；`node --test scripts/authenticity-guard.test.mjs`
    通过，`51` 项。
  - 首屏滚动复测：本地 Vite
    `VITE_API_PROXY_TARGET=https://193.112.107.134 MEDKERNEL_API_PROXY_ALLOW_SELF_SIGNED=true npm --prefix frontend run dev -- --host 127.0.0.1 --port 5182`
    下，Playwright 测得桌面 `1365x768` 为
    `documentScrollHeight=768 / bodyScrollHeight=768 / innerHeight=768 / hasInitialVerticalOverflow=false`，
    手机 `390x844` 为
    `documentScrollHeight=844 / bodyScrollHeight=844 / innerHeight=844 / hasInitialVerticalOverflow=false`；截图保存在
    `/tmp/medkernel-login-check/desktop-1365x768.png` 与 `/tmp/medkernel-login-check/mobile-390x844.png` 并已目检，
    未见内容互相遮挡或挤出首屏。
  - 旧口径扫描：
    `rg -n "管理账号|账号与权限|账号管理|权限管理|登录平台治理|登录机构工作台|工号 / 账号|请输入平台治理账号|请输入工号或机构账号|使用平台治理账号继续|使用管理员开通的账号" frontend/src docs/audit/product-role-journeys.md docs/audit/product-function-catalog.md --glob '!**/*.test.ts' --glob '!**/*.test.tsx'`
    返回 `NO_MATCH`；旧语义只保留在测试反向断言中。
  - `git diff --check` 与应用提交前 `git diff --cached --check` 均通过。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `https://193.112.107.134/medkernel/` HTTP 200，`Date=Sat, 04 Jul 2026 10:56:58 GMT`，
  `Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，`Content-Length=832`，`ETag="6a475ada-340"`；
  `https://193.112.107.134/medkernel/api/v1/auth/login-tenants` 返回 `primaryTenants[0].tenantId=t-rehearsal`、
  `name=完整上线演练机构`、`platformTenant.tenantId=t-1`、`name=平台治理入口（唯一内置）`、
  `hasCustomerTenants=true`，`traceId=786c9621-bbc9-46d9-91bf-dc2fa8938224`。当前 134 映射仍是
  后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`；
  不得把本地 `ef3afbfc` 记为已部署。
- 并行核查结论与下一步：菜单 IA / 职责旅程核查未发现需要改菜单顺序或结构的问题；“临床路径模板”当前已收敛为
  `临床路径库`，避免被理解成引用模板。下一阶段继续做页面级业务措辞广度优先收敛，优先核查并可落地候选包括：
  `frontend/src/pages/advanced/Provenance.tsx` 标题“知识来源追溯”是否改为“来源与血缘”；
  `RuntimeDiagnostics.tsx` 的“插件管理”是否收敛为“扩展能力”；`RuleDefinitions.tsx` /
  `PathwayTemplates.tsx` 的“管理字段目录”是否改为“查看字段目录”；`PathwayTemplates.tsx` 的“管理动作”是否改为
  “路径操作”；`AdapterHub.tsx` 组织空态、`SecurityBaselinePanels.tsx` 临时文件口径、
  `DeclarativeAssetWorkbench.tsx` 泛化“管理”文案与 `CdssFatigue.tsx` 表格“管理”列名。继续按最优决策执行，
  不咨询；后续不使用子代理，所有结论以本地代码、权威文档和实测为准。

## 最新阶段交接（2026-07-04 全视角真实前台体验优化第九十九批·初始化提示菜单名校准）

- 本批继续沿机构与人员域菜单、权威产品目录和真实初始化状态复核，发现第九十八批系统已初始化提示误写为
  非权威账号入口名。权威菜单实际是 `人员与账号`（`/admin/users`），承载自然人、任职、账号、职责和组织范围；
  因此本批不改菜单名、不改顺序，只校准初始化提示指向真实菜单，避免交付现场被引向不存在入口。菜单键、
  路由、权限、后端 API、数据库、产品目录、职责旅程与 134 发布配置均未改变。
- 已本地提交 `a800aa8b986330d0a620e325cfbe59edefe516da`
  （`fix: 校准初始化提示菜单名`）：
  - `frontend/src/pages/Bootstrap.tsx` 将系统已初始化提示从“账号进入账号与权限处理，集团、医院和其他服务机构进入服务机构页处理”
    校准为“账号进入人员与账号处理，集团、医院和其他服务机构进入服务机构页处理”。
  - `frontend/src/pages/Bootstrap.test.tsx` 正向锁定新提示，并反向阻断“账号与权限 / 服务机构管理 / 工作台内维护”回流。
- 本地验证：
  - 红灯：先改测试后执行
    `npm --prefix frontend test -- Bootstrap.test.tsx -t "系统已初始化时直接访问接管页"` 在旧实现下失败，DOM
    仍展示错误账号入口名。
  - 绿灯：实现后同一命令通过，`1` 个测试文件 / `1` 项。
  - 关联回归：
    `npm --prefix frontend test -- Bootstrap.test.tsx routes.test.ts menu.test.ts productCatalog.test.ts productRoleJourneys.test.ts pages.smoke.test.tsx`
    通过，`6` 个测试文件 / `113` 项。
  - 生成一致性：`node scripts/audit/export-product-capabilities.mjs --check` 通过；本批未改变自动生成产品目录。
  - 旧口径扫描：
    `rg -n "账号与权限|账号管理|权限管理|服务机构管理|工作台内维护" frontend/src docs/audit/product-function-catalog.md docs/audit/product-role-journeys.md --glob '!**/*.test.ts' --glob '!**/*.test.tsx'`
    无输出；旧语义只保留在测试反向断言中。
  - `bash scripts/check-comment-zh.sh --mode=full` 通过；`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `962` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `Bootstrap-rLrOwJnK.js`、`TenantOnboarding-DiOFZdGb.js`、
    `PathwayTemplates-DsXesiQ6.js`、`index-Cvvlqtej.js` 等前端产物。
  - `git diff --check`、应用提交前 `git diff --cached --check` 均通过。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 权威公网入口
  `https://193.112.107.134/medkernel/` HTTP 200，`Date=Sat, 04 Jul 2026 07:34:37 GMT`，
  `Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，`Content-Length=832`，`ETag="6a475ada-340"`；
  readiness 使用 `https://193.112.107.134/medkernel/actuator/health/readiness` 返回 HTTP 200 /
  `{"status":"UP"}`，`X-Trace-Id=3d665584-38f4-4701-a4a5-6a5dff787afd`。当前 134 映射仍是
  后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`；
  不得把本地 `a800aa8b` 记为已部署。
- 下一步继续沿全局菜单、全角色职责旅程、真实前台默认层做广度优先复核；重点继续检查人员与账号、
  服务机构、机构知识、知识生产和质量治理之间的入口边界，发现可真实落地的问题直接按权威标准修复；
  后续不使用子代理，不咨询，不推送远程 `main`。

## 最新阶段交接（2026-07-04 全视角真实前台体验优化第九十八批·服务机构状态口径收敛）

- 本批继续按全局菜单、医疗产品体验和全角色职责旅程复核服务机构入口。结论：`服务机构` 菜单名与
  机构与人员域排序仍符合权威产品目录，不应改回“机构管理”或“服务机构管理”；真实缺口是初始化接管和
  服务机构页加载/异常状态仍残留“管理 / 工作台内维护”口径，用户不知道该去哪个具体菜单。按医疗现场
  平台治理视角，本批将可见状态收敛为具体入口：账号进入“人员与账号”，集团、医院和其他服务机构进入
  “服务机构”页；菜单键、菜单顺序、权限、路由、后端 API、数据库、产品目录、职责旅程与 134 发布配置均未改变。
- 已本地提交 `96699172a2824d2be51c12cdda3e1cd54c668eab`
  （`fix: 收敛服务机构状态口径`）：
  - `frontend/src/pages/tenant/TenantOnboarding.tsx` 将服务机构范围加载态与异常态标题从
    “服务机构管理”改为“服务机构”。
  - `frontend/src/pages/Bootstrap.tsx` 将系统已初始化提示从“账号与服务机构统一在工作台内维护”改为
    “账号进入人员与账号处理，集团、医院和其他服务机构进入服务机构页处理”；并将首次接管完成分支的
    “服务机构管理中维护”收敛为“服务机构页中处理”。
  - `TenantOnboarding.test.tsx`、`Bootstrap.test.tsx` 正向锁定新状态口径，并反向阻断“服务机构管理 /
    工作台内维护”回流。
- 本地验证：
  - 红灯：先改测试后执行
    `npm --prefix frontend test -- TenantOnboarding.test.tsx -t "服务机构范围"` 在旧实现下失败，
    加载态/异常态仍展示“服务机构管理”；`npm --prefix frontend test -- Bootstrap.test.tsx -t "通过 token 后创建初始管理员"`
    在旧实现下也先失败，随后根因复查确认该用例属于首次改密提示分支，具体菜单指引应落在系统已初始化状态。
  - 绿灯：实现并校正测试覆盖点后，
    `npm --prefix frontend test -- Bootstrap.test.tsx -t "系统已初始化时直接访问接管页|通过 token 后创建初始管理员"`
    通过，`1` 个测试文件 / `2` 项；`npm --prefix frontend test -- TenantOnboarding.test.tsx -t "服务机构范围"`
    通过，`1` 个测试文件 / `2` 项。
  - 关联回归：
    `npm --prefix frontend test -- TenantOnboarding.test.tsx Bootstrap.test.tsx routes.test.ts menu.test.ts productCatalog.test.ts productRoleJourneys.test.ts pages.smoke.test.tsx`
    通过，`7` 个测试文件 / `123` 项。
  - 生成一致性：`node scripts/audit/export-product-capabilities.mjs --check` 通过；本批未改变自动生成产品目录。
  - 旧口径扫描：
    `rg -n "服务机构管理|机构管理|工作台内维护" frontend/src docs/audit/product-function-catalog.md docs/audit/product-role-journeys.md --glob '!**/*.test.ts' --glob '!**/*.test.tsx'`
    无输出；旧语义只保留在测试反向断言中。
  - `bash scripts/check-comment-zh.sh --mode=full` 通过；`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `962` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `Bootstrap-C2C-LjGj.js`、`TenantOnboarding-Hf2gEjQf.js`、
    `PathwayTemplates-Cy-pAoyh.js`、`index-OYP-Qei7.js` 等前端产物。
  - `git diff --check`、应用提交前 `git diff --cached --check` 均通过。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 权威公网入口
  `https://193.112.107.134/medkernel/` HTTP 200，`Date=Sat, 04 Jul 2026 07:29:30 GMT`，
  `Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，`Content-Length=832`，`ETag="6a475ada-340"`；
  readiness 使用 `https://193.112.107.134/medkernel/actuator/health/readiness` 返回 HTTP 200 /
  `{"status":"UP"}`，`X-Trace-Id=560b5f7e-a532-4dd9-86fa-159e340f7e9d`。当前 134 映射仍是
  后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`；
  不得把本地 `96699172` 记为已部署。
- 下一步继续沿全局菜单、全角色职责旅程、真实前台默认层做广度优先复核；重点继续检查人员与账号、
  服务机构、机构知识、知识生产和质量治理之间的入口边界，发现可真实落地的问题直接按权威标准修复；
  后续不使用子代理，不咨询，不推送远程 `main`。

## 最新阶段交接（2026-07-04 全视角真实前台体验优化第九十七批·临床路径起始结构口径收敛）

- 本批继续按全局菜单、医疗产品体验和全角色职责旅程复核，并重点回应“临床路径模板是否像引用模板”的疑义。
  结论：`临床路径库` 菜单名、知识治理内顺序和 `/pathway/templates` 内部路由/API 仍成立；权威目录将该入口定义为
  “编排、审核、发布和回滚临床路径版本”，菜单位于临床规则之后符合规则到路径的医疗治理依赖，不应改成
  “临床路径模板”。真实缺口是建模弹窗 L1 基础信息里的“路径原型”容易被理解成引用外部模板或可复用模板资产。
  本批只将客户可见字段收敛为“起始结构”，表达创建新路径时初始化空白路径或基础节点闭环；接口枚举、
  `pathwayPrototypeOptions`、后端 API、数据库、产品目录、菜单 IA 与 134 发布配置均未改变。
- 已本地提交 `868205296a043ab32ca8959806da64b25ad06c38`
  （`fix: 收敛临床路径起始结构口径`）：
  - `frontend/src/pages/tenant/PathwayTemplates.tsx` 将新建临床路径弹窗里的 `Form.Item label`
    从“路径原型”改为“起始结构”。
  - `PathwayTemplates.test.tsx` 正向锁定“起始结构”，并反向阻断“路径原型 / 临床路径模型 / 基础模板”回流。
- 本地验证：
  - 红灯：先改测试后执行
    `npm --prefix frontend test -- PathwayTemplates.test.tsx -t "起始结构|路径编辑器"`
    在旧实现下失败，页面找不到“起始结构”，并且仍展示“路径原型”。
  - 绿灯：实现后同一命令通过，`1` 个测试文件 / `2` 项；关联回归
    `npm --prefix frontend test -- PathwayTemplates.test.tsx routes.test.ts menu.test.ts productCatalog.test.ts productRoleJourneys.test.ts pages.smoke.test.tsx`
    通过，`6` 个测试文件 / `113` 项。
  - 生成一致性：`node scripts/audit/export-product-capabilities.mjs --check` 通过；本批未改变自动生成产品目录。
  - 旧口径扫描：
    `rg -n "临床路径模板|路径模板|路径原型|基础模板|临床路径模型" frontend/src docs/audit/product-function-catalog.md docs/audit/product-role-journeys.md --glob '!**/*.test.ts' --glob '!**/*.test.tsx'`
    无输出；旧语义只保留在测试反向断言中。
  - `bash scripts/check-comment-zh.sh --mode=full` 通过；`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `960` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `PathwayTemplates-j-0Zvqzc.js`、`KnowledgeGovernance-B5coImzn.js`、
    `index-QgyRR14p.js` 等前端产物。
  - `git diff --check`、应用提交前 `git diff --cached --check` 均通过。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 权威公网入口
  `https://193.112.107.134/medkernel/` HTTP 200，`Date=Sat, 04 Jul 2026 07:20:35 GMT`，
  `Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，`Content-Length=832`，`ETag="6a475ada-340"`；
  readiness 使用 `https://193.112.107.134/medkernel/actuator/health/readiness` 返回 HTTP 200 /
  `{"status":"UP"}`，`X-Trace-Id=7d4810c7-d739-4631-a6b3-12214b78073b`。当前 134 映射仍是
  后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`；
  不得把本地 `86820529` 记为已部署。
- 下一步继续沿全局菜单、全角色职责旅程、真实前台默认层做广度优先复核；优先处理可真实落地的产品体验、
  契约、测试、构建和文档问题；不使用子代理、不咨询、不推送远程 `main`。

## 最新阶段交接（2026-07-04 全视角真实前台体验优化第九十六批·机构差异版本操作口径收敛）

- 本批继续按全局菜单、医疗产品体验和全角色职责旅程复核。结论：`机构知识库` 菜单名与顺序仍符合
  “从平台标准派生机构版本、查看机构覆盖血缘并恢复平台标准”的客户任务，不应改成实现层或流程型入口。
  真实缺口是页内动作、表头、弹窗和共享标签仍使用“机构定制 / 定制原因 / 定制草稿”，容易让医疗机构用户误解为
  随意复制或私改平台标准。按 `CONSTITUTION` 客户面禁止暴露实现缩写、唯一权威知识与机构差异不回写平台标准的边界，
  本批将前台可见口径统一为“机构差异版本 / 差异原因 / 机构差异草稿”；接口枚举、mutation、后端 API、
  数据库、产品目录、菜单 IA 与 134 发布配置均未改变。
- 已本地提交 `29b219cac27de363023457c49149d49eeaac609a`
  （`fix: 收敛机构差异版本操作口径`）：
  - `frontend/src/pages/quality/KnowledgeGovernance.tsx` 将派生、发布、恢复链路中的按钮、表头、弹窗、
    成功/失败提示和权限提示统一为机构差异版本语言。
  - `frontend/src/shared/config/customerLabels.ts` 将 `LOCAL_CUSTOMIZATION`、`DRAFT` 的客户标签改为
    “机构差异版本”“机构差异草稿”，保留接口枚举不变。
  - `KnowledgeGovernance.test.tsx` 正向锁定“创建机构差异版本 / 差异原因 / 机构差异草稿 / 发布机构差异版本”，
    反向阻断旧“机构定制 / 定制原因 / 定制草稿”回流。
- 本地验证：
  - 红灯：先改测试后执行
    `npm --prefix frontend test -- KnowledgeGovernance.test.tsx -t "derive|standalone maintenance entry"`
    在旧实现下失败，页面仍只有“定制为本机构版本”和旧表头；补充发布用例后同链路继续锁定新口径。
  - 绿灯：实现后执行
    `npm --prefix frontend test -- KnowledgeGovernance.test.tsx -t "derive|publish|standalone maintenance entry"`
    通过，`3` 项；关联回归
    `npm --prefix frontend test -- KnowledgeGovernance.test.tsx customerLanguageGate.test.ts routes.test.ts menu.test.ts pages.smoke.test.tsx`
    通过，`5` 个测试文件 / `127` 项。
  - 生成一致性：`node scripts/audit/export-product-capabilities.mjs --check` 通过；本批未改变自动生成产品目录。
  - 旧口径扫描：
    `rg -n "机构定制|定制原因|定制草稿|定制为本机构版本|创建定制草稿|创建机构知识定制|发布机构定制|历史定制|发布机构版本|无机构定制权限|定制机构知识" frontend/src docs/audit/product-function-catalog.md docs/audit/product-role-journeys.md --glob '!**/*.test.ts' --glob '!**/*.test.tsx'`
    无输出；旧语义只保留在测试反向断言中。
  - `bash scripts/check-comment-zh.sh --mode=full` 通过；`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `960` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `KnowledgeGovernance-lG-Kj6Hh.js`、`InstitutionKnowledge-B32uhtkM.js`、
    `index-DPZRomS3.js` 等前端产物。
  - `git diff --check`、应用提交前 `git diff --cached --check` 均通过。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 权威公网入口为
  `https://193.112.107.134/medkernel/`，HTTP 200，`Date=Sat, 04 Jul 2026 07:12:14 GMT`，
  `Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，`Content-Length=832`，`ETag="6a475ada-340"`；
  readiness 使用 `https://193.112.107.134/medkernel/actuator/health/readiness` 返回 HTTP 200 /
  `{"status":"UP"}`，`X-Trace-Id=2946c9cd-a80b-4258-97c9-5151124bc4ff`。当前 134 映射仍是
  后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`；
  不得把本地 `29b219ca` 记为已部署。排查时确认 `192.168.1.134:8080` 与公网
  `193.112.107.134:18080` 均不是权威演练入口，其中 `18080` 按手册仅监听服务器回环地址。
- 下一步继续沿全局菜单、全角色职责旅程、真实前台默认层做广度优先复核；优先处理可真实落地的产品体验、
  契约、测试、构建和文档问题；不使用子代理、不咨询、不推送远程 `main`。

## 最新阶段交接（2026-07-04 全视角真实前台体验优化第九十五批·登录平台治理口径收敛）

- 本批继续按全局菜单、医疗产品体验和全角色职责旅程复核。结论：登录页的 `平台治理` / `机构用户`
  二层入口结构仍成立，登录页不是业务菜单，不需要改变菜单顺序或认证入口结构。真实缺口是客户租户存在时，
  平台治理入口说明仍写“知识标准维护”和“机构定制不会回写平台标准”，与第九十四批已收敛的知识治理口径不一致，
  容易把平台标准治理理解成后台维护，把机构差异版本理解成复制或回写平台标准。按 `CONSTITUTION` 唯一权威知识、
  机构生效版本只组合不复制和职责权限边界，本批将登录入口说明收敛为
  “仅供平台治理、知识标准治理和系统运维人员使用；机构差异不会改写平台标准。”；登录流程、租户目录、
  统一身份入口、菜单、后端 API、数据库、产品目录、134 发布配置均未改变。
- 已本地提交 `5da7ae7738ce2e98543f5602d4fc2ae3365c24a4`
  （`fix: 收敛登录平台治理口径`）：
  - `frontend/src/pages/Login.tsx` 调整平台治理入口说明，统一为“知识标准治理 / 机构差异不会改写平台标准”。
  - `Login.test.tsx` 正向锁定新说明，并反向阻断旧“知识标准维护 / 机构定制不会回写平台标准”回流。
- 本地验证：
  - 红灯：先改测试后执行
    `npm --prefix frontend test -- Login.test.tsx -t "客户租户存在时可展开第二层切换平台主租户登录"`
    在旧实现下失败，页面找不到新说明，仍展示旧平台治理说明。
  - 绿灯：实现后同一命令通过，`1` 项；关联回归
    `npm --prefix frontend test -- Login.test.tsx pages.smoke.test.tsx customerLanguageGate.test.ts routes.test.ts menu.test.ts`
    通过，`5` 个测试文件 / `112` 项。
  - 生成一致性：`node scripts/audit/export-product-capabilities.mjs --check` 通过；本批未改变自动生成产品目录。
  - 旧口径扫描：
    `rg -n "知识标准维护|机构定制不会回写平台标准" frontend/src docs/audit/product-function-catalog.md docs/audit/product-role-journeys.md --glob '!**/*.test.ts' --glob '!**/*.test.tsx'`
    无输出；旧语义只保留在测试反向断言中。
  - `bash scripts/check-comment-zh.sh --mode=full` 通过；`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `960` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `Login-Cld7DwA8.js`、`index-B4Lxbt0N.js` 等前端产物。
  - `git diff --check`、应用提交前 `git diff --cached --check` 均通过。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 公网首页
  `https://193.112.107.134/medkernel/` HTTP 200，`Date=Sat, 04 Jul 2026 07:03:00 GMT`，
  `Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，`Content-Length=832`，`ETag="6a475ada-340"`；
  readiness 使用 `https://193.112.107.134/medkernel/actuator/health/readiness` 返回 HTTP 200 /
  `{"status":"UP"}`，`X-Trace-Id=dd444340-9471-445e-ba46-f91e45cfeeca`。当前 134 映射仍是
  后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`；
  不得把本地 `5da7ae77` 记为已部署。
- 下一步继续沿全局菜单、全角色职责旅程、真实前台默认层做广度优先复核；优先处理可真实落地的产品体验、
  契约、测试、构建和文档问题；不使用子代理、不咨询、不推送远程 `main`。

## 最新阶段交接（2026-07-04 全视角真实前台体验优化第九十四批·机构知识库任务口径收敛）

- 本批继续按全局菜单、医疗产品体验和全角色职责旅程复核。结论：左侧菜单 `机构知识库` 与知识治理域顺序仍成立，
  位于 `知识审核发布中心` 之后、`知识生产工作台` 之前，表达的是平台标准与机构差异版本的治理入口；
  不需要重命名为“机构知识维护”或移出知识治理。真实缺口是页面目标、职责旅程和平台入口提示仍使用
  “维护院内覆盖、机构定制”等口径，容易把机构差异版本理解成后台维护台或复制一套知识源。按
  `CONSTITUTION` “机构生效版本只组合不复制”、唯一权威知识与职责边界要求，`PRODUCT_SCOPE` 的机构覆盖边界，
  以及 `EXPERIENCE_CONTRACT` 的医院任务语言，本批将默认可见口径收敛为“治理院内覆盖、机构差异、换基线和恢复平台标准”，
  平台入口提示改为“平台负责管理权威标准；机构差异、发布和恢复操作在对应医疗机构内完成。”；
  机构页提示改为“机构差异版本会复制当前平台版本及完整证据链”。菜单键、菜单顺序、权限、后端 API、数据库、
  产品目录生成器、134 发布配置均未改变。
- 已本地提交 `bbeed1a0b2af476ab0c1e8bc8bece2136a4719d5`
  （`fix: 收敛机构知识库任务口径`）：
  - `frontend/src/shared/config/routes.ts` 将 `/knowledge/institution` 体验目标和医疗引擎运营员职责从
    “维护院内覆盖、机构定制、换基线和恢复平台标准 / 维护机构定制、换基线和恢复平台标准”收敛为
    “治理院内覆盖、机构差异、换基线和恢复平台标准 / 治理机构差异、换基线和恢复平台标准”。
  - `frontend/src/pages/quality/KnowledgeGovernance.tsx` 同步机构知识库页面目标、平台治理入口提示和机构差异版本提示。
  - `KnowledgeGovernance.test.tsx` 与 `routes.test.ts` 正向锁定新口径，并反向阻断旧“维护权威标准”“机构定制会复制”
    和 `/knowledge/institution` 目标中的“维护”回流。
- 本地验证：
  - 红灯：先改测试后执行
    `npm --prefix frontend test -- KnowledgeGovernance.test.tsx routes.test.ts -t "institution|service institution language|全视角职责边界"`
    在旧实现下失败，页面仍展示 `维护院内覆盖、机构定制、换基线和恢复平台标准`，平台提示仍展示
    `平台负责维护权威标准；机构定制、发布和恢复操作在对应医疗机构内完成。`。
  - 绿灯：实现后同一命令通过，`2` 个测试文件 / `10` 项；关联回归
    `npm --prefix frontend test -- KnowledgeGovernance.test.tsx routes.test.ts menu.test.ts productCatalog.test.ts productRoleJourneys.test.ts pages.smoke.test.tsx`
    通过，`6` 个测试文件 / `138` 项。
  - 生成一致性：`node scripts/audit/export-product-capabilities.mjs --check` 通过；本批未改变自动生成产品目录。
  - 旧口径扫描：
    `rg -n "维护院内覆盖|维护机构定制|平台负责维护权威标准|维护院内覆盖、机构定制、换基线和恢复平台标准|机构定制会复制当前平台版本" frontend/src docs/audit/product-function-catalog.md scripts/audit/export-product-capabilities.mjs --glob '!**/*.test.ts' --glob '!**/*.test.tsx'`
    无输出；旧语义只保留在测试反向断言中。
  - `bash scripts/check-comment-zh.sh --mode=full` 通过；`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `960` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `KnowledgeGovernance-BrrPMT_c.js`、`InstitutionKnowledge-DQv0gizl.js`
    和 `index-C22msxV5.js` 等前端产物。
  - `git diff --check`、应用提交前 `git diff --cached --check` 均通过。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 公网首页
  `https://193.112.107.134/medkernel/` HTTP 200，`Date=Sat, 04 Jul 2026 06:58:00 GMT`，
  `Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，`Content-Length=832`，`ETag="6a475ada-340"`；
  readiness 使用 `https://193.112.107.134/medkernel/actuator/health/readiness` 返回 HTTP 200 /
  `{"status":"UP"}`，`X-Trace-Id=31bd5942-be7a-45cc-ae84-b77e9e24d4af`。当前 134 映射仍是
  后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`；
  不得把本地 `bbeed1a0` 记为已部署。
- 下一步继续沿全局菜单、全角色职责旅程、真实前台默认层做广度优先复核；优先处理可真实落地的产品体验、
  契约、测试、构建和文档问题；不使用子代理、不咨询、不推送远程 `main`。

## 最新阶段交接（2026-07-04 全视角真实前台体验优化第九十三批·系统接入任务口径收敛）

- 本批继续按全局菜单、医疗产品体验和全角色职责旅程复核。结论：左侧菜单 `系统接入` 与系统运维域顺序仍成立，
  位于 `实施与验收` 后承接上线联调后的外部系统接入治理，比“接入维护”“适配器维护”更适合信息科、实施工程师和平台管理员心智；
  不需要重命名或重排。真实缺口是自动生成功能目录仍将 `/adapter/hub` 唯一客户任务写成
  “由集成和实施角色维护外部系统接入及失败补偿”，容易把页面理解成后台维护台，而该页实际承担接入治理、字段映射、
  健康检查、死信补偿、数据质量报告和接入验收。按 `CONSTITUTION` 诚实降级、真实连接器和断连诚实暴露要求，
  `PRODUCT_SCOPE` 的系统运维边界，以及 `EXPERIENCE_CONTRACT` 的医院任务语言，本批仅将目录任务收敛为
  “治理外部系统接入、字段映射、健康检查和失败补偿”；菜单键、菜单顺序、权限、页面交互、后端 API、数据库、构建配置和
  134 发布配置均未改变。
- 已本地提交 `ac7a3dc6ea72af1ddce98534c193ee2195e78b96`
  （`fix: 收敛系统接入任务口径`）：
  - `scripts/audit/export-product-capabilities.mjs` 将 `/adapter/hub` 产品目录任务从
    `由集成和实施角色维护外部系统接入及失败补偿` 改为
    `治理外部系统接入、字段映射、健康检查和失败补偿`。
  - 重新生成 `docs/audit/product-function-catalog.md`，保持功能目录由生成器确定性产出。
  - `productCatalog.test.ts` 正向锁定新的系统接入任务行，并反向阻断旧“维护外部系统接入及失败补偿”语义回流。
- 本地验证：
  - 红灯：先改测试后执行
    `npm --prefix frontend test -- productCatalog.test.ts -t "hospital-facing language"`
    在旧目录下失败，明确命中 `/adapter/hub` 仍展示
    `由集成和实施角色维护外部系统接入及失败补偿`，缺少新的系统接入任务行。
  - 绿灯：实现并重新生成目录后同一命令通过，`1` 项；关联回归
    `npm --prefix frontend test -- AdapterHub.test.tsx routes.test.ts menu.test.ts productCatalog.test.ts productRoleJourneys.test.ts pages.smoke.test.tsx`
    通过，`6` 个测试文件 / `123` 项。
  - 生成一致性：`node scripts/audit/export-product-capabilities.mjs --check` 通过。
  - 旧口径扫描：
    `rg -n "维护外部系统接入|由集成和实施角色维护外部系统接入及失败补偿" frontend/src docs/audit/product-function-catalog.md scripts/audit/export-product-capabilities.mjs --glob '!**/*.test.ts' --glob '!**/*.test.tsx'`
    无输出；旧语义只保留在测试反向断言中。
  - `bash scripts/check-comment-zh.sh --mode=full` 通过；`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `960` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `AdapterHub-CwCA1ZD8.js`、`index-CYiLRTam.js` 等前端产物。
  - `git diff --check`、应用提交前 `git diff --cached --check` 均通过；`scripts/audit/export-product-capabilities.mjs`
    仅出现既有 CRLF/LF 提示，实际 staged diff 只有目标行变更。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 公网首页
  `https://193.112.107.134/medkernel/` HTTP 200，`Date=Sat, 04 Jul 2026 06:50:02 GMT`，
  `Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，`Content-Length=832`，`ETag="6a475ada-340"`；
  readiness 使用 `https://193.112.107.134/medkernel/actuator/health/readiness` 返回 HTTP 200 /
  `{"status":"UP"}`，`X-Trace-Id=521ad25e-4c1c-4d2b-9de4-541e085c01db`。当前 134 映射仍是
  后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`；
  不得把本地 `ac7a3dc6` 记为已部署。
- 下一步继续沿全局菜单、全角色职责旅程、真实前台默认层做广度优先复核；优先处理可真实落地的产品体验、
  契约、测试、构建和文档问题；不使用子代理、不咨询、不推送远程 `main`。

## 最新阶段交接（2026-07-04 全视角真实前台体验优化第九十二批·术语字典任务口径收敛）

- 本批继续按全局菜单、医疗产品体验和全角色职责旅程复核。结论：左侧菜单 `术语字典` 与当前知识治理顺序仍成立，
  比“术语维护”“术语映射维护”更贴合医院对院内码、标准码和来源系统映射关系的心智，不需要重命名或重排；真实缺口是
  `/terminology/mapping` 页面说明、路由职责和产品功能目录仍使用“维护院内术语映射 / 术语维护与上线修订分离 /
  当前维护者”口径，容易让临床专家、实施顾问和医疗引擎运营员误解为后台表维护或个人维护动作。按
  `CONSTITUTION` 唯一权威知识与高危逐条责任确认、`PRODUCT_SCOPE` 的术语映射治理边界和
  `EXPERIENCE_CONTRACT` 的医院任务语言，本批仅把客户可见任务收敛为“校准院内术语映射、裁定冲突并逐条确认高危近似”；
  菜单键、菜单顺序、权限、后端 API、数据库、构建配置和 134 发布配置均未改变。
- 已本地提交 `ade2cbe050a9ea75204f83b127132ca107a2cae5`
  （`fix: 收敛术语字典任务口径`）：
  - `frontend/src/pages/tenant/TerminologyMapping.tsx` 将提示从 `术语维护与上线修订分离` 改为
    `术语校准与上线生效分离`，说明从 `本页维护院内字典、标准字典和映射版本` 改为
    `本页校准院内字典、标准字典和映射版本`，并把高危近似候选确认人从 `当前维护者` 收敛为 `当前责任人`。
  - `frontend/src/shared/config/routes.ts` 将术语字典职责从
    `确认院内码、标准码和来源系统映射` 改为 `校准院内码、标准码和来源系统映射`。
  - `scripts/audit/export-product-capabilities.mjs` 将 `/terminology/mapping` 产品目录任务改为
    `校准院内术语映射、裁定冲突并逐条确认高危近似`，并重新生成
    `docs/audit/product-function-catalog.md`，保持目录文档由同一生成器产出。
  - `TerminologyMapping.test.tsx`、`routes.test.ts` 与 `productCatalog.test.ts` 正向锁定新口径，并反向阻断旧“维护”任务语义回流。
- 本地验证：
  - 红灯：先改测试后执行
    `npm --prefix frontend test -- TerminologyMapping.test.tsx productCatalog.test.ts routes.test.ts -t "renders the complete mapping workspace|hospital-facing language|为上线配置与知识建模入口登记全视角职责边界"`
    在旧实现下失败，明确命中页面仍展示 `术语维护与上线修订分离` / `本页维护院内字典`，产品功能目录缺少新的
    `/terminology/mapping` 任务行，路由职责仍是 `确认院内码、标准码和来源系统映射`。
  - 绿灯：实现后同一命令通过，`3` 个测试文件 / `3` 项目标用例；关联回归
    `npm --prefix frontend test -- TerminologyMapping.test.tsx routes.test.ts menu.test.ts productCatalog.test.ts productRoleJourneys.test.ts pages.smoke.test.tsx customerLanguageGate.test.ts`
    通过，`7` 个测试文件 / `119` 项。
  - 生成一致性：`node scripts/audit/export-product-capabilities.mjs --check` 通过。
  - 旧口径扫描：
    `rg -n "维护院内术语映射|术语维护与上线修订分离|本页维护院内字典|当前维护者|确认院内码、标准码和来源系统映射" frontend/src docs/audit/product-function-catalog.md scripts/audit/export-product-capabilities.mjs --glob '!**/*.test.ts' --glob '!**/*.test.tsx'`
    无输出；旧语义只保留在测试反向断言中。
  - `bash scripts/check-comment-zh.sh --mode=full` 通过；`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `960` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `TerminologyMapping-DF8CcNBn.js`、`index-CYiLRTam.js` 等前端产物。
  - `git diff --check`、应用提交前 `git diff --cached --check` 均通过；`scripts/audit/export-product-capabilities.mjs`
    仅出现既有 CRLF/LF 提示，实际 staged diff 只有目标行变更。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 公网首页
  `https://193.112.107.134/medkernel/` HTTP 200，`Date=Sat, 04 Jul 2026 06:23:52 GMT`，
  `Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，`Content-Length=832`，`ETag="6a475ada-340"`；
  readiness 使用 `https://193.112.107.134/medkernel/actuator/health/readiness` 返回 HTTP 200 /
  `{"status":"UP"}`，`X-Trace-Id=5d3b7a45-49bf-4b2d-8680-37d6a9928a90`。当前 134 映射仍是
  后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`；
  不得把本地 `ade2cbe0` 记为已部署。
- 下一步继续沿全局菜单、全角色职责旅程、真实前台默认层做广度优先复核；优先处理可真实落地的产品体验、
  契约、测试、构建和文档问题；不使用子代理、不咨询、不推送远程 `main`。

## 最新阶段交接（2026-07-04 全视角真实前台体验优化第九十一批·机构生效版本任务口径收敛）

- 本批继续按全局菜单、医疗产品体验和全角色职责旅程复核。结论：左侧菜单 `机构生效版本` 与当前知识治理发布顺序仍成立，
  比“版本维护”“发布配置”更贴合医院对当前运行版本、回滚证据和机构确认的心智，不需要重命名或重排；真实缺口是
  `/config/releases` 页面说明和产品功能目录任务仍使用“维护平台标准版本 / 机构生效版本”口径，容易让运营角色误解为
  直接维护已发布版本。按 `CONSTITUTION` 统一最小发布流、`PRODUCT_SCOPE` 的平台标准版本与机构生效版本边界、
  `EXPERIENCE_CONTRACT` 的医院任务语言，本批仅将客户可见任务收敛为“发布平台标准版本、生成机构生效版本并保留影响和回滚证据”；
  菜单键、菜单顺序、权限、后端 API、数据库、构建配置和 134 发布配置均未改变。
- 已本地提交 `7cfd8494aa4d53f43ed103a82669ebf8a925fe08`
  （`fix: 收敛机构生效版本任务口径`）：
  - `frontend/src/pages/tenant/ReleaseGovernance.tsx` 将页面说明从
    `维护平台标准版本，并为机构确认当前生效版本。` 改为
    `发布平台标准版本，为机构生成可回滚的当前生效版本。`。
  - `scripts/audit/export-product-capabilities.mjs` 将 `/config/releases` 产品目录任务改为
    `发布平台标准版本、生成机构生效版本并保留影响和回滚证据`，并重新生成
    `docs/audit/product-function-catalog.md`，保持目录文档由同一生成器产出。
  - `ReleaseGovernance.test.tsx` 与 `productCatalog.test.ts` 正向锁定新口径，并反向阻断旧“维护平台标准版本”语义回流。
- 本地验证：
  - 红灯：先改测试后执行
    `npm --prefix frontend test -- ReleaseGovernance.test.tsx productCatalog.test.ts -t "publishes the selected platform draft|hospital-facing language"`
    在旧实现下失败，明确命中发布页仍展示 `维护平台标准版本，并为机构确认当前生效版本。`，且产品功能目录缺少新的
    `/config/releases` 任务行。
  - 绿灯：实现后同一命令通过，`2` 项；关联回归
    `npm --prefix frontend test -- ReleaseGovernance.test.tsx routes.test.ts menu.test.ts productCatalog.test.ts productRoleJourneys.test.ts pages.smoke.test.tsx`
    通过，`6` 个测试文件 / `108` 项。
  - 生成一致性：`node scripts/audit/export-product-capabilities.mjs --check` 通过。
  - 旧口径扫描：
    `rg -n "维护平台标准版本|维护平台标准版本、机构生效版本|为机构确认当前生效版本" frontend/src docs/audit/product-function-catalog.md scripts/audit/export-product-capabilities.mjs --glob '!**/*.test.ts' --glob '!**/*.test.tsx'`
    无输出；旧语义只保留在测试反向断言中。
  - `bash scripts/check-comment-zh.sh --mode=full` 通过；`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项。
  - 完整前端门禁：首次 `npm --prefix frontend run verify` 在 `format:check` 因
    `ReleaseGovernance.test.tsx` 新增长中文断言未按 Prettier 折行而失败；执行
    `npm exec prettier -- --write src/pages/tenant/ReleaseGovernance.test.tsx` 后重跑
    `npm --prefix frontend run verify` 通过，`114` 个测试文件 / `960` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `ReleaseGovernance-B_3ZG0Jb.js`、`index-D9URhpzW.js` 等前端产物。
  - `git diff --check`、应用提交前 `git diff --cached --check` 均通过；`scripts/audit/export-product-capabilities.mjs`
    仅出现既有 CRLF/LF 提示，实际 staged diff 只有目标行变更。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 公网首页
  `https://193.112.107.134/medkernel/` HTTP 200，`Date=Sat, 04 Jul 2026 06:17:17 GMT`，
  `Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，`Content-Length=832`；readiness 使用
  `https://193.112.107.134/medkernel/actuator/health/readiness` 返回 HTTP 200 / `{"status":"UP"}`，
  `X-Trace-Id=383ff086-18af-4b46-8a4b-cdd49149575c`。当前 134 映射仍是
  后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`；
  不得把本地 `7cfd8494` 记为已部署。
- 下一步继续沿全局菜单、全角色职责旅程、真实前台默认层做广度优先复核；优先处理可真实落地的产品体验、
  契约、测试、构建和文档问题；不使用子代理、不咨询、不推送远程 `main`。

## 最新阶段交接（2026-07-04 全视角真实前台体验优化第九十批·临床规则任务口径收敛）

- 本批继续按全局菜单、医疗产品体验和全角色职责旅程复核。结论：左侧菜单 `临床规则` 与当前知识治理顺序仍成立，
  比“规则库”“规则维护”更贴合医院对规则资产、试运行和发布治理的心智，不需要重命名或重排；真实缺口是
  `/rule/definitions` 页面说明、创建弹窗提示和路由职责仍使用“维护临床规则资产 / 规则版本独立维护 /
  维护触发条件”口径。按 `CONSTITUTION` 统一最小发布流、`PRODUCT_SCOPE` 对规则资产的上线验收要求和
  `EXPERIENCE_CONTRACT` 的医院语言要求，本批仅把客户可见任务收敛为“配置临床规则草稿，完成试运行、影响分析、
  安全复核并纳入机构生效版本”；菜单键、菜单顺序、权限、后端 API、数据库、构建配置和 134 发布配置均未改变。
- 已本地提交 `30114152426d375b0c792b8e92ffc3f2a92268d7`
  （`fix: 收敛临床规则任务口径`）：
  - `frontend/src/pages/tenant/RuleDefinitions.tsx` 将页面说明从
    `维护临床规则资产，完成验证、解释和临床治理。` 改为
    `配置临床规则草稿，完成试运行、影响分析、安全复核并纳入机构生效版本。`。
  - 同页创建弹窗提示从 `规则版本独立维护` 改为 `规则草稿统一发布`，说明创建或编辑只形成规则草稿版本，
    完成试运行、安全复核并纳入平台标准版本或机构生效版本后才会参与临床运行。
  - `frontend/src/shared/config/routes.ts` 将临床规则 route experience goal 从
    `核查规则资产准备状态` 改为 `核查临床规则发布准备`；医疗引擎运营员职责从
    `维护触发条件、建议动作、验证病例和分阶段上线范围` 改为
    `配置触发条件、建议动作、验证病例和上线影响范围`。
  - `RuleDefinitions.test.tsx` 与 `routes.test.ts` 正向锁定新口径，并反向阻断旧“维护/独立维护”语义回流。
- 本地验证：
  - 红灯：先改测试后执行
    `npm --prefix frontend test -- RuleDefinitions.test.tsx routes.test.ts -t "创建规则不绑定旧上线容器|为知识治理入口登记全视角职责边界"`
    在旧实现下失败，明确命中规则页仍展示 `维护临床规则资产，完成验证、解释和临床治理。`；该命令中
    `routes.test.ts` 因过滤名不匹配被跳过，随后使用实际用例名补跑。
  - 绿灯：实现后
    `npm --prefix frontend test -- RuleDefinitions.test.tsx -t "创建规则不绑定旧上线容器"` 通过，`1` 项；
    `npm --prefix frontend test -- routes.test.ts -t "为上线配置与知识建模入口登记全视角职责边界"` 通过，`1` 项。
  - 临床规则与菜单目录关联回归：
    `npm --prefix frontend test -- RuleDefinitions.test.tsx RulePathwayCleanliness.test.ts routes.test.ts menu.test.ts productCatalog.test.ts productRoleJourneys.test.ts pages.smoke.test.tsx`
    通过，`7` 个测试文件 / `142` 项。
  - 生成一致性：`node scripts/audit/export-product-capabilities.mjs --check` 通过。
  - 旧口径扫描：
    `rg -n "维护临床规则资产|规则版本独立维护|维护触发条件|核查规则资产准备状态" frontend/src docs/audit/product-function-catalog.md scripts/audit/export-product-capabilities.mjs --glob '!**/*.test.ts' --glob '!**/*.test.tsx'`
    无输出；全量扫描只剩 `RuleDefinitions.test.tsx` 内反向断言。
  - `bash scripts/check-comment-zh.sh --mode=full` 通过；`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项。
  - 完整前端门禁：首次 `npm --prefix frontend run verify` 在 `format:check` 因
    `RuleDefinitions.test.tsx` 新增长中文断言未按 Prettier 折行而失败；执行
    `npm exec prettier -- --write src/pages/tenant/RuleDefinitions.test.tsx` 后重跑
    `npm --prefix frontend run verify` 通过，`114` 个测试文件 / `960` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `RuleDefinitions-CItxKS6f.js`、`index-D6w1kFJk.js` 等前端产物。
  - `git diff --check`、应用提交前 `git diff --cached --check` 均通过。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 公网首页
  `https://193.112.107.134/medkernel/` HTTP 200，`Date=Sat, 04 Jul 2026 06:08:01 GMT`，
  `Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，`Content-Length=832`；readiness 使用
  `https://193.112.107.134/medkernel/actuator/health/readiness` 返回 HTTP 200 / `{"status":"UP"}`，
  `X-Trace-Id=924fac92-47c1-4ec5-b413-376dcf8f1ccf`。当前 134 映射仍是
  后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`；
  不得把本地 `30114152` 记为已部署。
- 下一步继续沿全局菜单、全角色职责旅程、真实前台默认层做广度优先复核；优先处理可真实落地的产品体验、
  契约、测试、构建和文档问题；不使用子代理、不咨询、不推送远程 `main`。

## 最新阶段交接（2026-07-04 全视角真实前台体验优化第八十九批·评价指标任务口径收敛）

- 本批继续按全局菜单、医疗产品体验和全角色职责旅程复核。结论：左侧菜单 `评价指标` 属于质量管理域，
  仍比“评估指标库”或“质控指标维护”更适合医院质控负责人心智，不需要重命名或重排；本批发现的真实缺口是
  `/qc/eval/sets` 页面说明、路由职责和自动生成功能目录仍使用“维护评价指标 / 待维护指标”口径。
  按 `PRODUCT_SCOPE` 对 EVALUATION 资产的定义、`CONSTITUTION` 统一最小发布流和 `EXPERIENCE_CONTRACT`
  的医院语言要求，本批仅把客户可见任务收敛为“定义评价指标、试算影响分析并发布生效”；菜单键、菜单顺序、权限、后端 API、
  数据库、构建配置和 134 发布配置均未改变。
- 已本地提交 `58c01f7546bad98d3c1022b6f939d78cb4b1cbb4`
  （`fix: 收敛评价指标任务口径`）：
  - `frontend/src/pages/quality/QcEvalSets.tsx` 将页面说明从
    `维护质控评价指标、影响分析和发布状态` 改为
    `定义质控评价指标，试算影响范围并通过机构生效版本统一发布。`，并把创建弹窗提示从
    `指标版本独立维护` 改为 `指标草稿统一发布`，强调通过安全复核并纳入机构生效版本后才真正上线。
  - `frontend/src/shared/config/routes.ts` 将评价指标 route experience goal 从 `核查评价指标配置状态` 改为
    `核查评价指标发布状态`，默认视图从 `待维护指标` 改为 `待处理指标`；质控负责人职责从
    `维护评价指标、适用范围和发布节奏` 改为 `定义评价指标口径、适用范围和发布节奏`。
  - `scripts/audit/export-product-capabilities.mjs` 与 `docs/audit/product-function-catalog.md` 将
    `/qc/eval/sets` 唯一客户任务同步为 `定义评价指标、试算影响分析并发布生效`。
  - `QcEvalSets.test.tsx`、`routes.test.ts` 和 `productCatalog.test.ts` 正向锁定新口径，并反向阻断
    “维护质控评价指标”“维护评价指标”“待维护指标”“指标版本独立维护”等旧语义回流。
- 本地验证：
  - 红灯：先改测试后执行
    `npm --prefix frontend test -- QcEvalSets.test.tsx routes.test.ts productCatalog.test.ts -t "loads real indicators|creates a draft indicator|为剩余真实前台|hospital-facing language"`
    在旧实现下失败，明确命中 4 个预期缺口：页面仍展示“维护质控评价指标”，弹窗仍展示“指标版本独立维护”，
    路由 goal 仍是“核查评价指标配置状态”，功能目录仍未出现新的评价指标客户任务。
  - 绿灯：实现后同一命令通过，`3` 个测试文件 / `4` 项目标用例。
  - 评价指标与菜单目录关联回归：
    `npm --prefix frontend test -- QcEvalSets.test.tsx QcEvalResults.test.tsx routes.test.ts menu.test.ts productCatalog.test.ts productRoleJourneys.test.ts pages.smoke.test.tsx`
    通过，`7` 个测试文件 / `116` 项。
  - 生成一致性：`node scripts/audit/export-product-capabilities.mjs --check` 通过。
  - 旧口径扫描：
    `rg -n "维护质控评价指标|维护评价指标|待维护指标|指标版本独立维护|核查评价指标配置状态" frontend/src docs/audit/product-function-catalog.md scripts/audit/export-product-capabilities.mjs --glob '!**/*.test.tsx' --glob '!**/*.test.ts'`
    无输出；全量扫描也无输出。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `960` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `QcEvalSets-BsjMOlAc.js`、`index-dU9RfWM-.js` 等前端产物。
  - `bash scripts/check-comment-zh.sh --mode=full` 通过；`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项；`git diff --check`、应用提交前 `git diff --cached --check` 均通过。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 公网首页
  `https://193.112.107.134/medkernel/` HTTP 200，`Date=Sat, 04 Jul 2026 05:58:33 GMT`，
  `Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，`Content-Length=832`；readiness 使用
  `https://193.112.107.134/medkernel/actuator/health/readiness` 返回 HTTP 200 / `{"status":"UP"}`，
  `X-Trace-Id=e07f5545-d3d2-4146-803c-024def3c0fff`。当前 134 映射仍是
  后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`；
  不得把本地 `58c01f75` 记为已部署。
- 下一步继续沿全局菜单、全角色职责旅程、真实前台默认层做广度优先复核；优先处理可真实落地的产品体验、
  契约、测试、构建和文档问题；不使用子代理、不咨询、不推送远程 `main`。

## 最新阶段交接（2026-07-04 全视角真实前台体验优化第八十八批·临床路径库任务口径收敛）

- 本批继续按全局菜单、医疗产品体验和全角色职责旅程复核。结论：第四十四批菜单名与顺序仍成立，
  `/pathway/templates` 的左侧菜单应保持 `临床路径库`，不回退为“临床路径模板”；“模板”在医院语境中更像可引用样板，
  容易削弱当前页面作为临床路径版本编排、审核、发布、回滚入口的治理语义。本批发现的真实缺口是临床路径库页面说明、
  路由职责和自动生成产品目录仍使用“维护专病临床路径 / 维护临床路径版本”口径，容易让医疗引擎运营员误解为后台修表或直接改写运行版本。
  按 `CONSTITUTION` 的统一知识治理与机构生效版本边界、`EXPERIENCE_CONTRACT` 的医院语言要求，本批仅把客户可见任务收敛为
  “编排临床路径版本 / 编排、审核、发布和回滚临床路径版本”；菜单键、菜单顺序、权限、后端 API、数据库、构建配置和 134 发布配置均未改变。
- 已本地提交 `e901e739ffab92bf66f6438d306cf68b3cfe63c4`
  （`fix: 收敛临床路径库任务口径`）：
  - `frontend/src/pages/tenant/PathwayTemplates.tsx` 将 `临床路径库` 页面说明从
    `维护专病临床路径，使用统一条件树、规则引用和真实快照试运行；上线生效由机构生效版本统一管理。` 改为
    `编排专病临床路径，使用统一条件树、规则引用和真实快照试运行；上线生效由机构生效版本统一管理。`
  - `frontend/src/shared/config/routes.ts` 将医疗引擎运营员在临床路径库的职责从
    `维护临床路径版本、机构覆盖和验证用例` 改为 `编排临床路径版本、机构覆盖和验证用例`，继续保留
    `临床路径不能自动改写患者当前医嘱` 的医疗安全边界。
  - `scripts/audit/export-product-capabilities.mjs` 与 `docs/audit/product-function-catalog.md` 将
    `/pathway/templates` 唯一客户任务同步为 `编排、审核、发布和回滚临床路径版本`。
  - `PathwayTemplates.test.tsx`、`routes.test.ts` 和 `productCatalog.test.ts` 正向锁定新口径，并反向阻断
    “临床路径模板”“维护专病临床路径”“维护临床路径版本”等旧入口语义回流。
- 本地验证：
  - 红灯：先改测试后执行
    `npm --prefix frontend test -- PathwayTemplates.test.tsx routes.test.ts productCatalog.test.ts -t "路径库不再展示|为上线配置与知识建模入口登记全视角职责边界|hospital-facing language"`
    在旧实现下失败，明确命中 3 个预期缺口：页面仍展示“维护专病临床路径”，路由职责仍是“维护临床路径版本”，功能目录仍未出现新的临床路径库客户任务。
  - 绿灯：实现后同一命令通过，`3` 个测试文件 / `3` 项目标用例。
  - 临床路径库与菜单目录关联回归：
    `npm --prefix frontend test -- PathwayTemplates.test.tsx routes.test.ts menu.test.ts productCatalog.test.ts productRoleJourneys.test.ts pages.smoke.test.tsx`
    通过，`6` 个测试文件 / `113` 项。
  - 生成一致性：`node scripts/audit/export-product-capabilities.mjs --check` 通过。
  - 生产旧口径扫描：
    `rg -n "临床路径模板|路径模板|维护专病临床路径|维护临床路径版本|维护、审核、发布和回滚临床路径版本|上线路径维护" frontend/src docs/audit/product-function-catalog.md scripts/audit/export-product-capabilities.mjs --glob '!**/*.test.tsx' --glob '!**/*.test.ts'`
    无输出；全量扫描也无输出。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `960` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `PathwayTemplates-lYIBu4hC.js`、`index-CL8hKRQa.js` 等前端产物。
  - `bash scripts/check-comment-zh.sh --mode=full` 通过；`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项；`git diff --check`、应用提交前 `git diff --cached --check` 均通过。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 公网首页
  `https://193.112.107.134/medkernel/` HTTP 200，`Date=Sat, 04 Jul 2026 05:50:33 GMT`，
  `Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，`Content-Length=832`；readiness 使用
  `https://193.112.107.134/medkernel/actuator/health/readiness` 返回 HTTP 200 / `{"status":"UP"}`，
  `X-Trace-Id=b2bc03a0-54f6-4cab-b73b-720335fb7955`。当前 134 映射仍是
  后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`；
  不得把本地 `e901e739` 记为已部署。
- 下一步继续沿全局菜单、全角色职责旅程、真实前台默认层做广度优先复核；优先处理可真实落地的产品体验、
  契约、测试、构建和文档问题；不使用子代理、不咨询、不推送远程 `main`。

## 最新阶段交接（2026-07-04 全视角真实前台体验优化第八十七批·诊断知识库任务口径收敛）

- 本批继续按全局菜单、医疗产品体验和全角色职责旅程复核。结论：第四十四批菜单名与顺序仍成立，
  `诊断知识库` 作为左侧客户任务入口比“诊断知识维护”更符合医院知识库心智，`临床路径库` 也不应退回“模板”或“配置”口径；
  本批发现的真实缺口是诊断知识库页面、路由体验和功能目录仍把唯一客户任务写成
  “维护诊断身份 / 维护诊断标准”，容易让临床专家和医疗引擎运营员误解为后台修表或直接改写当前运行版本。
  按 `CONSTITUTION` 的统一知识治理与最小生命周期、`EXPERIENCE_CONTRACT` 的医院语言要求，本批仅收敛
  `/knowledge/diagnosis` 的页面说明、路由职责、自动生成目录和测试快照为
  “管理诊断身份 / 编审诊断标准”；菜单键、菜单顺序、权限、后端 API、数据库、构建配置和 134 发布配置均未改变。
- 已本地提交 `e4bcc19f4e8b8380368a1c71684a037a35002223`
  （`fix: 收敛诊断知识库任务口径`）：
  - `frontend/src/pages/quality/DiagnosisKnowledgeMaintenance.tsx` 将页面说明从
    `在统一知识治理下维护诊断身份、诊断标准、鉴别诊断、验证病例与来源证据` 改为
    `在统一知识治理下管理诊断身份、诊断标准、鉴别诊断、验证病例与来源证据`，继续强调发布后再进入平台标准版本或机构生效版本。
  - `frontend/src/shared/config/routes.ts` 将诊断知识库 route experience goal 同步为
    `在统一知识治理下管理诊断身份、诊断标准、鉴别关系、验证病例和来源证据`，临床专家职责从
    `维护诊断标准、鉴别诊断和验证病例` 改为 `编审诊断标准、鉴别诊断和验证病例`；医疗引擎运营员职责仍保留
    诊断语义资产、版本和统一发布校验边界。
  - `scripts/audit/export-product-capabilities.mjs` 与 `docs/audit/product-function-catalog.md` 将
    `/knowledge/diagnosis` 唯一客户任务同步为
    `管理诊断身份、诊断标准、鉴别诊断、验证病例与来源证据`。
  - `DiagnosisKnowledgeMaintenance.test.tsx`、`routes.test.ts`、`productCatalog.test.ts`、
    `productRoleJourneys.test.ts` 和 `KnowledgeGovernance.test.tsx` 正向锁定新口径，并继续反向阻断
    `诊断知识维护`、`维护诊断身份` 等旧入口语义回流。
- 本地验证：
  - 红灯：先改测试后执行
    `npm --prefix frontend test -- DiagnosisKnowledgeMaintenance.test.tsx routes.test.ts productCatalog.test.ts productRoleJourneys.test.ts KnowledgeGovernance.test.tsx -t "diagnosis knowledge library|separates knowledge publishing|hospital-facing language|removed domain|review workspace|stakeholder views"`
    在旧实现下失败，明确命中 3 个预期缺口：页面找不到新的“管理诊断身份”说明，路由 goal 仍是“维护诊断身份”，功能目录仍未出现新的诊断知识库客户任务。
  - 绿灯：实现后同一命令通过，`5` 个测试文件 / `6` 项目标用例。
  - 诊断知识库与菜单目录关联回归：
    `npm --prefix frontend test -- DiagnosisKnowledgeMaintenance.test.tsx DiagnosisKnowledgePanel.test.tsx KnowledgeGovernance.test.tsx routes.test.ts menu.test.ts productCatalog.test.ts productRoleJourneys.test.ts`
    通过，`7` 个测试文件 / `134` 项。
  - 生成一致性：`node scripts/audit/export-product-capabilities.mjs --check` 通过。
  - 生产旧口径扫描：
    `rg -n "诊断知识维护|维护诊断身份|维护诊断标准|manual diagnosis maintenance|diagnosis maintenance" frontend/src docs/audit/product-function-catalog.md scripts/audit/export-product-capabilities.mjs --glob '!**/*.test.tsx' --glob '!**/*.test.ts'`
    无输出；全量扫描只剩 `DiagnosisKnowledgeMaintenance.test.tsx` 中的反向断言。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `960` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `DiagnosisKnowledgeMaintenance-cCtAMBx-.js`、
    `DiagnosisKnowledgeMaintenance-B6Vrc1aI.css` 等前端产物。
  - `bash scripts/check-comment-zh.sh --mode=full` 通过；`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项；`git diff --check`、应用提交前 `git diff --cached --check` 均通过。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 公网首页
  `https://193.112.107.134/medkernel/` HTTP 200，`Date=Sat, 04 Jul 2026 05:36:23 GMT`，
  `Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，`Content-Length=832`，外部 `index.html` 仍指向
  `/assets/index-DYTh-Ceu.js`、`/assets/vendor-react-bdrMx_IT.js`、`/assets/vendor-data-D9EFEnEk.js`、
  `/assets/vendor-react-C5ap-Sga.css`、`/assets/index-XMjG4gr3.css`；134 health 使用
  `https://193.112.107.134/medkernel/actuator/health` 返回 HTTP 200 /
  `{"status":"UP","groups":["liveness","readiness"]}`，响应时间头
  `Date=Sat, 04 Jul 2026 05:42:15 GMT`，`X-Trace-Id=e3cff176-6677-4c30-aef0-0400a6a60c31`；
  readiness 使用 `https://193.112.107.134/medkernel/actuator/health/readiness` 返回 HTTP 200 /
  `{"status":"UP"}`，`X-Trace-Id=a781b16f-d516-46ca-83ab-eb443b2f321d`。当前 134 映射仍是
  后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`；
  不得把本地 `e4bcc19f` 记为已部署。
- 下一步继续沿全局菜单、全角色职责旅程、真实前台默认层做广度优先复核；优先处理可真实落地的产品体验、
  契约、测试、构建和文档问题；不使用子代理、不咨询、不推送远程 `main`。

## 最新阶段交接（2026-07-04 全视角真实前台体验优化第八十六批·资产编目入口口径收敛）

- 本批继续按全局菜单、医疗产品体验和全角色职责旅程复核。结论：第四十四批全局菜单名与顺序仍成立，
  `诊断知识库`、`临床路径库`、`知识审核发布中心`、`知识生产工作台` 等左侧菜单不需要继续改名或重排；新发现的真实体验缺口在
  `/authoring/assets` 的隐藏页内资产入口与字段目录抽屉。该页虽然不占左侧主菜单，但客户可见的 `配置资产维护`、`维护字段目录`、
  `上下文字段目录维护`、`医疗配置资产独立维护` 等词会继续强化“维护”像低层后台修表的感受，也容易让院内运营员误解为直接修改当前运行版本。
  按 `CONSTITUTION` 的统一最小发布流与 `EXPERIENCE_CONTRACT` 的医院语言要求，本批仅收敛可见命名和说明为
  “编目 / 字段与配置资产 / 字段目录 / 字段目录草稿 / 调整平台字段展示”；菜单键、路由、权限、后端 API、数据契约、数据库、构建配置和
  134 发布配置均未改变。
- 已本地提交 `e5490c79c8cb1907e602db4208e54e1f7b25e8ec`
  （`fix: 收敛资产编目入口口径`）：
  - `frontend/src/pages/tenant/AuthoringAssets.tsx` 将统一资产库说明改为
    `检索、收藏、编目和复用医疗知识与配置资产`，页内标签从 `配置资产维护` 改为 `字段与配置资产`，字段入口从
    `维护字段目录` 改为 `字段目录`，加载和错误状态同步从“创作资产”收敛为医疗资产语言。
  - `frontend/src/shared/ui/condition/FieldCatalogManager.tsx` 将抽屉标题从 `上下文字段目录维护` 改为
    `字段目录草稿`，提示改为 `这里编排下一版本的字段目录`，说明改为本次调整需固化为草稿后才可进入平台标准版本或机构生效版本，
    按钮从 `维护平台字段覆盖` 改为 `调整平台字段展示`。
  - `frontend/src/pages/tenant/DeclarativeAssetWorkbench.tsx` 将说明从 `医疗配置资产独立维护` 改为
    `配置资产按类型编目`，并把字段目录与完整路径的关系表述为各自工作台管理。
  - `frontend/src/pages/tenant/AuthoringAssets.test.tsx`、`frontend/src/shared/ui/condition/FieldCatalogManager.test.tsx`、
    `frontend/src/pages/tenant/DeclarativeAssetWorkbench.test.tsx` 正向锁定新口径并反向阻断旧“维护”入口回流。
- 本地验证：
  - 红绿核验：先改测试后执行
    `npm --prefix frontend test -- AuthoringAssets.test.tsx FieldCatalogManager.test.tsx DeclarativeAssetWorkbench.test.tsx -t "searches unified assets|keeps asset codes|surfaces independent|展示平台|无 context.write|shows four"`
    在旧实现下失败，明确找不到 `配置资产按类型编目`、`字段目录草稿`、`字段与配置资产` 等新口径；实现后同命令通过，
    `3` 个测试文件 / `6` 项目标用例。
  - 关联资产与菜单回归：
    `npm --prefix frontend test -- AuthoringAssets.test.tsx FieldCatalogManager.test.tsx DeclarativeAssetWorkbench.test.tsx PathwayTemplates.test.tsx routes.test.ts menu.test.ts productCatalog.test.ts productRoleJourneys.test.ts`
    通过，`8` 个测试文件 / `107` 项。
  - 生成一致性：`node scripts/audit/export-product-capabilities.mjs --check` 通过。
  - 生产旧口径扫描：
    `rg -n "配置资产维护|维护字段目录|维护平台字段覆盖|上下文字段目录维护|医疗配置资产独立维护|字段目录与完整路径分别由各自工作台维护|检索、收藏、维护和复用|这里维护下一版本|维护结果需固化" frontend/src --glob '!**/*.test.tsx' --glob '!**/*.test.ts'`
    无输出；测试文件仅保留反向断言，`PathwayTemplates.test.tsx` 仍有一个不相关权限夹具 `维护字段目录`。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `960` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `AuthoringAssets-C6FNc56h.js`、`FieldCatalogManager-OBa3-Tnn.js`、
    `index-DDzH_WR4.js` 等前端产物。
  - `bash scripts/check-comment-zh.sh --mode=full` 通过；`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项；`git diff --check`、应用提交前 `git diff --cached --check` 均通过。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 公网首页
  `https://193.112.107.134/medkernel/` HTTP 200，`Date=Sat, 04 Jul 2026 05:25:57 GMT`，
  `Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，`Content-Length=832`，外部 `index.html` 仍指向
  `/assets/index-DYTh-Ceu.js`、`/assets/vendor-react-bdrMx_IT.js`、`/assets/vendor-data-D9EFEnEk.js`、
  `/assets/vendor-react-C5ap-Sga.css`、`/assets/index-XMjG4gr3.css`；134 health 使用
  `https://193.112.107.134/medkernel/actuator/health` 返回 HTTP 200 /
  `{"status":"UP","groups":["liveness","readiness"]}`，响应时间头
  `Date=Sat, 04 Jul 2026 05:26:45 GMT`，`X-Trace-Id=e2e15388-a8d5-4d8-b889e-b51abc2d4ee7`；
  readiness 使用 `https://193.112.107.134/medkernel/actuator/health/readiness` 返回 HTTP 200 /
  `{"status":"UP"}`，`X-Trace-Id=d3dc14d2-3d16-417b-9ef9-5c3f50ecfa89`。当前 134 映射仍是
  后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`；
  不得把本地 `e5490c79` 记为已部署。
- 下一步继续沿全局菜单、全角色职责旅程、真实前台默认层做广度优先复核；优先处理可真实落地的产品体验、
  契约、测试、构建和文档问题；不使用子代理、不咨询、不推送远程 `main`。

## 最新阶段交接（2026-07-04 全视角真实前台体验优化第八十五批·七步流来源选择口径收敛）

- 本批继续按全局菜单、医疗产品体验和全角色职责旅程复核。结论：第四十四批全局菜单名与顺序仍成立，
  `临床路径库` 不应回到“模板”口径；新发现的体验缺口在共享 7 步配置流和系统接入说明。权威宪章 §4 定义的最小发布流为
  “创建/导入/生成草稿 → 自动校验与测试 → 依赖闭包和影响分析 → 当前授权责任人确认发布 → 纳入机构生效版本并留证/可回滚”，
  并未要求把第一步表达为模板选择。默认“选模板/导入 / 从专病模板或文件开始”会让系统接入、评价指标等非模板型配置页误读为
  必须先引用模板。本批仅收敛客户可见七步流标题、说明和系统接入页说明；内部 `select_template` 状态键、发布流状态机、菜单、
  路由、权限、后端 API、数据契约、数据库、构建配置和 134 发布配置均未改变。
- 已本地提交 `b748e260301b849f5f615954751828f56c925b8f`
  （`fix: 收敛七步流来源选择口径`）：
  - `frontend/src/shared/ui/StepFlow.contract.ts` 将共享七步流第一步标题从 `选模板/导入` 改为
    `选来源/导入`，说明从 `从专病模板或文件开始` 改为 `从院内来源、既有资产或文件开始`。
  - `frontend/src/pages/tenant/AdapterHub.tsx` 将系统接入页“适配器接入 7 步流”说明同步为
    `选来源/导入 → 自动校验 → 看影响 → 提交审核 → 灰度发布 → 全量 → 留证据/可回滚`。
  - `frontend/src/shared/ui/StepFlow.test.tsx`、`frontend/src/pages/tenant/AdapterHub.test.tsx`、
    `frontend/src/pages/quality/QcEvalSets.test.tsx` 锁定新口径并反向阻断旧 `选模板/导入` 回流。
- 本地验证：
  - 红绿核验：先改测试后执行
    `npm --prefix frontend test -- StepFlow.test.tsx AdapterHub.test.tsx QcEvalSets.test.tsx -t "locks the exact 7-step|renders the unified adapter workspace|loads real indicators"`
    在旧实现下失败，`StepFlow` 仍返回 `选模板/导入`，系统接入和评价指标页均找不到 `选来源/导入`；实现后同命令通过，
    `3` 个测试文件 / `3` 项目标用例。
  - 关联配置回归：
    `npm --prefix frontend test -- StepFlow.test.tsx AdapterHub.test.tsx QcEvalSets.test.tsx pages.smoke.test.tsx routes.test.ts menu.test.ts productCatalog.test.ts productRoleJourneys.test.ts`
    通过，`8` 个测试文件 / `136` 项。
  - 生成一致性：`node scripts/audit/export-product-capabilities.mjs --check` 通过。
  - 旧口径扫描：
    `rg -n "选模板/导入|从专病模板或文件开始|适配器属于配置类资产，必须按“选模板" frontend/src docs/_HANDOFF.md`
    仅命中测试反向断言；生产文件无旧可见口径残留。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `960` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `StepFlow-CqnEf_X5.js`、`AdapterHub-CeSaQq8U.js`、
    `QcEvalSets-CWQGf6pR.js`、`index-0cD17mx9.js` 等前端产物。
  - `bash scripts/check-comment-zh.sh --mode=full` 通过；`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项；`git diff --check`、应用提交前 `git diff --cached --check` 均通过。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 公网首页
  `https://193.112.107.134/medkernel/` HTTP 200，`Date=Sat, 04 Jul 2026 05:22:58 GMT`，
  `Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，`Content-Length=832`，外部 `index.html` 仍指向
  `/assets/index-DYTh-Ceu.js`、`/assets/vendor-react-bdrMx_IT.js`、`/assets/vendor-data-D9EFEnEk.js`、
  `/assets/vendor-react-C5ap-Sga.css`、`/assets/index-XMjG4gr3.css`；134 readiness 使用
  `https://193.112.107.134/medkernel/actuator/health` 返回 HTTP 200 /
  `{"status":"UP","groups":["liveness","readiness"]}`，响应时间头
  `Date=Sat, 04 Jul 2026 05:22:58 GMT`，`X-Trace-Id=613b739e-c07b-430e-ac92-f624e4c4f5b7`。
  当前 134 映射仍是后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、前端 dist=
  `95bb816292f59833005df4761866dd9d89886cb4`；不得把本地 `b748e260` 记为已部署。
- 下一步继续沿全局菜单、全角色职责旅程、真实前台默认层做广度优先复核；优先处理可真实落地的产品体验、
  契约、测试、构建和文档问题；不使用子代理、不咨询、不推送远程 `main`。

## 最新阶段交接（2026-07-04 全视角真实前台体验优化第八十四批·批量规则默认示例口径收敛）

- 本批继续按全局菜单、医疗产品体验和全角色职责旅程复核。结论：第四十四批全局菜单名与顺序仍成立，
  `诊断知识库`、`临床路径库`、`知识生产工作台`、`规则发布` 等入口不需要继续改名；新发现的体验缺口在
  `AuthoringBatchDrawer` 的批量规则默认示例。批量生成与批量发布仍必须使用稳定规则身份，以便知识审核、机构生效版本和审计追溯；
  但默认占位仍展示 `CKD-阈值-45`、`CKD 阈值 1`、`RULE.CKD.1` 等疾病/技术混合样例，容易让院内运营员把规则发布误解为
  英文疾病模板或内部编码填写页。本批仅将默认示例改为院内可读的拼音业务身份和中文规则名称；菜单、路由、权限、后端 API、
  数据契约、数据库、构建配置和 134 发布配置均未改变。
- 已本地提交 `df1135ca32b6980f2bcfb86e523e182b85030dd0`
  （`fix: 收敛批量规则默认示例口径`）：
  - `frontend/src/pages/tenant/AuthoringBatchDrawer.tsx` 将基准规则资产占位从
    `输入已审核基准规则的稳定身份` 改为 `如 weijizhi-huidan-jichu-guize`。
  - 同抽屉将批量生成表格示例从 `CKD-阈值-45,CKD 阈值 1,45,true` 改为
    `lujing-jiedian-yuqi-tixing,路径节点逾期提醒,3,true`。
  - 同抽屉将批量发布规则身份示例从 `RULE.CKD.1 / RULE.CKD.2` 改为
    `lujing-jiedian-yuqi-tixing / weijizhi-huidan-shixian`。
  - `frontend/src/pages/tenant/AuthoringBatchDrawer.test.tsx` 增加默认占位回归，正向锁定院内业务身份，反向阻断旧 CKD/RULE.CKD 示例。
- 本地验证：
  - 红绿核验：先改测试后执行
    `npm --prefix frontend test -- AuthoringBatchDrawer.test.tsx -t "uses hospital-facing rule identities"`
    在旧实现下失败，明确找不到 `如 weijizhi-huidan-jichu-guize`；实现后同命令通过，`1` 项。期间发现多行
    placeholder 精确匹配受 Testing Library 规范化影响，已将测试改为按关键业务身份匹配，不改变产品实现。
  - 关联配置回归：
    `npm --prefix frontend test -- AuthoringBatchDrawer.test.tsx AuthoringAssets.test.tsx pages.smoke.test.tsx productCatalog.test.ts routes.test.ts menu.test.ts productRoleJourneys.test.ts`
    通过，`7` 个测试文件 / `113` 项。
  - 生成一致性：`node scripts/audit/export-product-capabilities.mjs --check` 通过。
  - 旧占位扫描：
    `rg -n "CKD-阈值-45|CKD 阈值 1|RULE\\.CKD\\.1|RULE\\.CKD\\.2|输入已审核基准规则的稳定身份" frontend/src/pages/tenant/AuthoringBatchDrawer.tsx`
    无输出，生产文件无旧默认占位残留；测试文件仅保留反向断言和既有契约夹具。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `960` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `AuthoringAssets-D8twlqww.js`、`RuleDefinitions-DzVLWcRx.js`、
    `index-DyjCtNjm.js` 等前端产物。
  - `bash scripts/check-comment-zh.sh --mode=full` 通过；`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项；`git diff --check`、应用提交前 `git diff --cached --check` 均通过。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 公网首页
  `https://193.112.107.134/medkernel/` HTTP 200，`Date=Sat, 04 Jul 2026 05:16:55 GMT`，
  `Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，`Content-Length=832`，外部 `index.html` 仍指向
  `/assets/index-DYTh-Ceu.js`、`/assets/vendor-react-bdrMx_IT.js`、`/assets/vendor-data-D9EFEnEk.js`、
  `/assets/vendor-react-C5ap-Sga.css`、`/assets/index-XMjG4gr3.css`；134 readiness 使用
  `https://193.112.107.134/medkernel/actuator/health` 返回 HTTP 200 /
  `{"status":"UP","groups":["liveness","readiness"]}`，响应时间头
  `Date=Sat, 04 Jul 2026 05:16:55 GMT`，`X-Trace-Id=468962c5-a4ca-45c4-8547-e9c032335d5b`。
  当前 134 映射仍是后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、前端 dist=
  `95bb816292f59833005df4761866dd9d89886cb4`；不得把本地 `df1135ca` 记为已部署。
- 下一步继续沿全局菜单、全角色职责旅程、真实前台默认层做广度优先复核；优先处理可真实落地的产品体验、
  契约、测试、构建和文档问题；不使用子代理、不咨询、不推送远程 `main`。

## 最新阶段交接（2026-07-04 全视角真实前台体验优化第八十三批·临床路径建模示例口径收敛）

- 本批继续围绕用户对“临床路径模板”是否像引用模板含义的担心做全局复核。结论：第四十四批菜单命名与第七十八批
  `临床路径库` 口径仍成立，菜单和路由不再暴露 `临床路径模板`；新发现的真实体验缺口在新建临床路径建模表单。
  表单中的稳定路径、病种、阶段、里程碑、节点、时钟、医嘱套餐和流转身份都是版本治理、机构生效和审计追溯所需，
  不能移除；但默认占位仍以 `PATH.CARDIO.REVIEW`、`PREOP`、`M-PREOP-ASSESS`、`PATH.TIME.ASSESS`、
  `sepsis-order-set`、`EDGE.ASSESS.FOLLOWUP` 等工程式例子呈现，容易把“路径库”误读成技术模板或内部编码填写页。
  本批仅将默认示例改为院内路径可理解的拼音业务身份，并保留 `ICD10-I63` 作为可选标准病种身份示例；菜单、路由、
  权限、后端 API、数据契约、数据库、构建配置和 134 发布配置均未改变。
- 已本地提交 `8ad454f29f42f610a88dc3a11691224d8ac9caa5`
  （`fix: 收敛临床路径建模示例口径`）：
  - `frontend/src/pages/tenant/PathwayTemplates.tsx` 将新建临床路径基础信息占位收敛为
    `如 xinxueguan-lujing-fuhe`、`如 xinxueguanbing 或 ICD10-I63`。
  - 同页将节点画布中的阶段、里程碑、节点、时钟指标、医嘱套餐和流转身份占位收敛为
    `如 shuqian`、`如 shuqian-rujing-pinggu`、`如 N1，可改为 rujing-pinggu`、
    `如 rujing-pinggu-shichuang`、`如 ganranxing-xiuke-yizhu-taocan`、`如 E1，可改为 rujing-daosuifang`。
  - `frontend/src/pages/tenant/PathwayTemplates.test.tsx` 扩展路径建模表单回归，锁定新占位并反向断言旧工程式示例不再回流。
- 本地验证：
  - 红绿核验：先改测试后执行
    `npm --prefix frontend test -- PathwayTemplates.test.tsx -t "路径建模表单以稳定业务身份表达结构化路径对象"`
    在旧实现下失败，明确找不到 `如 xinxueguan-lujing-fuhe`；实现后同命令通过，`1` 项。期间发现多节点同占位导致测试唯一性断言不合理，
    已按多节点表单事实改为 `getAllByPlaceholderText`，不改变产品实现。
  - 关联配置回归：
    `npm --prefix frontend test -- PathwayTemplates.test.tsx pages.smoke.test.tsx productCatalog.test.ts routes.test.ts menu.test.ts productRoleJourneys.test.ts`
    通过，`6` 个测试文件 / `113` 项。
  - 生成一致性：`node scripts/audit/export-product-capabilities.mjs --check` 通过。
  - 旧占位扫描：
    `rg -n "PATH\\.CARDIO\\.REVIEW|CARDIO 或 ICD10-I63|M-PREOP-ASSESS|如 PREOP|如 N1，可改为 ASSESS|PATH\\.TIME\\.ASSESS|sepsis-order-set|EDGE\\.ASSESS\\.FOLLOWUP" frontend/src/pages/tenant/PathwayTemplates.tsx frontend/src/pages/tenant/PathwayTemplates.test.tsx`
    仅命中测试夹具、payload 契约断言和反向断言；生产文件无旧默认占位残留。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `959` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `PathwayTemplates-D0UZDBWq.js`、`Dashboard-CJ22CjjL.js`、
    `index-wleQcgEG.js` 等前端产物。
  - `bash scripts/check-comment-zh.sh --mode=full` 通过；`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项；`git diff --check`、应用提交前 `git diff --cached --check` 均通过。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 公网首页
  `https://193.112.107.134/medkernel/` HTTP 200，`Date=Sat, 04 Jul 2026 05:08:47 GMT`，
  `Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，`Content-Length=832`，外部 `index.html` 仍指向
  `/assets/index-DYTh-Ceu.js`、`/assets/vendor-react-bdrMx_IT.js`、`/assets/vendor-data-D9EFEnEk.js`、
  `/assets/vendor-react-C5ap-Sga.css`、`/assets/index-XMjG4gr3.css`；134 readiness 使用
  `https://193.112.107.134/medkernel/actuator/health` 返回 HTTP 200 /
  `{"status":"UP","groups":["liveness","readiness"]}`，响应时间头
  `Date=Sat, 04 Jul 2026 05:08:47 GMT`，`X-Trace-Id=53774559-8a39-46ce-8815-e3f51230fd80`。
  当前 134 映射仍是后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、前端 dist=
  `95bb816292f59833005df4761866dd9d89886cb4`；不得把本地 `8ad454f2` 记为已部署。
- 下一步继续沿全局菜单、全角色职责旅程、真实前台默认层做广度优先复核；优先处理可真实落地的产品体验、
  契约、测试、构建和文档问题；不使用子代理、不咨询、不推送远程 `main`。

## 最新阶段交接（2026-07-04 全视角真实前台体验优化第八十二批·诊断知识表单示例口径收敛）

- 本批继续按用户对诊断知识维护入口、全局菜单医疗语境和全角色前台体验的线索做广度复核。结论：
  第四十四批全局菜单命名与顺序仍成立，`诊断知识库` 作为菜单名继续比“诊断知识维护”更符合统一知识治理和发布职责；
  新发现的体验缺口在 `/quality/DiagnosisKnowledgePanel.tsx` 的诊断资产与验证病例表单。稳定业务身份字段本身是契约必需，
  但默认占位仍使用英文疾病 slug、`repository://...` 和 `CKD-CASE-001`，会让医疗运营人员误以为需要填写技术仓库地址或英文测试编号。
  本批仅把占位示例收敛为院内可理解的慢病诊断知识示例和受控资料链接提示；菜单、路由、权限、后端 API、数据契约、数据库、
  构建配置和 134 发布配置均未改变。
- 已本地提交 `6a7682444f228eba6677c9b3c209b786020f7f53`
  （`fix: 收敛诊断知识表单示例口径`）：
  - `frontend/src/pages/quality/DiagnosisKnowledgePanel.tsx` 将稳定诊断身份占位从
    `例如 chronic-kidney-disease` 改为 `例如 manxing-shenbing`，保留字段校验与提交值不变。
  - 同页将受控文件地址占位从 `repository://...` 改为 `粘贴受控资料库地址或院内文档链接`，明确这是受控来源线索而非技术协议要求。
  - 同页将稳定验证病例身份占位从 `如 CKD-CASE-001，用于复算与验收追溯` 改为
    `如 manxing-shenbing-case-001，用于复算与验收追溯`。
  - `frontend/src/pages/quality/DiagnosisKnowledgePanel.test.tsx` 增加新占位正向断言和旧技术/英文示例反向断言，阻断回流。
- 本地验证：
  - 红绿核验：先改测试后执行
    `npm --prefix frontend test -- DiagnosisKnowledgePanel.test.tsx -t "creates a diagnosis asset exactly once|uses stable business identity wording"`
    在旧实现下失败，明确找不到 `例如 manxing-shenbing` 与
    `如 manxing-shenbing-case-001，用于复算与验收追溯`；实现后同命令通过，`2` 项。
  - 关联配置回归：
    `npm --prefix frontend test -- DiagnosisKnowledgePanel.test.tsx DiagnosisKnowledgeMaintenance.test.tsx routes.test.ts productCatalog.test.ts menu.test.ts productRoleJourneys.test.ts`
    通过，`6` 个测试文件 / `97` 项。
  - 生成一致性：`node scripts/audit/export-product-capabilities.mjs --check` 通过。
  - 旧占位扫描：
    `rg -n "chronic-kidney-disease|repository://\\.\\.\\.|CKD-CASE-001" frontend/src/pages/quality/DiagnosisKnowledgePanel.tsx frontend/src/pages/quality/DiagnosisKnowledgePanel.test.tsx`
    仅命中测试反向断言；生产文件无旧占位残留。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `959` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `DiagnosisKnowledgeMaintenance-VeqXshNJ.js`、
    `Dashboard-Bffzjclb.js`、`index-BoB7APkS.js` 等前端产物。
  - `bash scripts/check-comment-zh.sh --mode=full` 通过；`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项；`git diff --check`、应用提交前 `git diff --cached --check` 均通过。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 公网首页
  `https://193.112.107.134/medkernel/` HTTP 200，`Date=Sat, 04 Jul 2026 05:01:21 GMT`，
  `Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，`Content-Length=832`，外部 `index.html` 仍指向
  `/assets/index-DYTh-Ceu.js`、`/assets/vendor-react-bdrMx_IT.js`、`/assets/vendor-data-D9EFEnEk.js`、
  `/assets/vendor-react-C5ap-Sga.css`、`/assets/index-XMjG4gr3.css`；134 readiness 使用
  `https://193.112.107.134/medkernel/actuator/health` 返回 HTTP 200 /
  `{"status":"UP","groups":["liveness","readiness"]}`，响应时间头
  `Date=Sat, 04 Jul 2026 05:01:21 GMT`，`X-Trace-Id=554bf63b-db04-4a09-904b-564c9ad5b19d`。
  当前 134 映射仍是后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、前端 dist=
  `95bb816292f59833005df4761866dd9d89886cb4`；不得把本地 `6a768244` 记为已部署。
- 下一步继续沿全局菜单、全角色职责旅程、真实前台默认层做广度优先复核；优先处理可真实落地的产品体验、
  契约、测试、构建和文档问题；不使用子代理、不咨询、不推送远程 `main`。

## 最新阶段交接（2026-07-04 全视角真实前台体验优化第八十一批·诊断知识库职责边界口径收敛）

- 本批继续按用户对诊断知识入口命名、临床路径模板语义和全局菜单医疗场景契合度的线索做广度复核。结论：
  第四十四批全局菜单名和顺序仍成立，`/knowledge/diagnosis` 在路由、菜单、后端菜单和功能目录中均为 `诊断知识库`，
  生产代码没有旧客户入口名残留。新发现的真实体验缺口在路由体验元数据：诊断知识库的医疗引擎运营员职责边界仍写作
  `诊断维护不绕过知识审核、平台标准版本或机构生效版本`。该短语不是菜单名，但会进入页面体验说明、职责视角测试和目录语境，
  容易把“诊断知识库”拉回后台维护动作。本批收敛为 `诊断知识发布不绕过知识审核、平台标准版本或机构生效版本`；
  菜单、路由路径、权限、后端 API、数据库、构建配置和 134 发布配置均未改变。
- 已本地提交 `66a429a4ef00663abc3247f47112f16d0a3ac9d0`
  （`fix: 收敛诊断知识库职责边界口径`）：
  - `frontend/src/shared/config/routes.ts` 将 `diagnosisKnowledgeExperience` 的运营员边界从 `诊断维护...`
    改为 `诊断知识发布...`，保持统一知识治理、平台标准版本和机构生效版本的边界不变。
  - `frontend/src/shared/config/routes.test.ts` 同步锁定诊断知识库职责视角和路由元数据，避免旧后台动作词回流。
- 本地验证：
  - 红绿核验：先改测试后执行
    `npm --prefix frontend test -- routes.test.ts -t "separates knowledge publishing from the diagnosis knowledge library|keeps stakeholder-specific views"`
    在旧实现下失败，明确仍返回 `诊断维护不绕过知识审核、平台标准版本或机构生效版本`；实现后同命令通过，`1` 项。
  - 关联配置回归：
    `npm --prefix frontend test -- routes.test.ts menu.test.ts productRoleJourneys.test.ts productCatalog.test.ts DiagnosisKnowledgeMaintenance.test.tsx`
    通过，`5` 个测试文件 / `76` 项。
  - 生成一致性：`node scripts/audit/export-product-capabilities.mjs --check` 通过。
  - 旧词扫描：
    `rg -n "诊断维护|诊断知识维护" frontend/src --glob '!**/*.test.*' --glob '!**/*.spec.*'`
    无输出；含测试扫描仅命中 `productRoleJourneys.test.ts` 的旧词反向防回流清单。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `959` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `DiagnosisKnowledgeMaintenance-By6C-nVv.js`、
    `Dashboard-BTSeoag4.js`、`index-CpT8rvel.js` 等前端产物。
  - `bash scripts/check-comment-zh.sh --mode=full` 通过；`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项；`git diff --check`、应用提交前 `git diff --cached --check` 均通过。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 公网首页
  `https://193.112.107.134/medkernel/` HTTP 200，`Date=Sat, 04 Jul 2026 04:55:14 GMT`，
  `Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，`Content-Length=832`，外部 `index.html` 仍指向
  `/assets/index-DYTh-Ceu.js`、`/assets/vendor-react-bdrMx_IT.js`、`/assets/vendor-data-D9EFEnEk.js`、
  `/assets/vendor-react-C5ap-Sga.css`、`/assets/index-XMjG4gr3.css`；134 readiness 使用
  `https://193.112.107.134/medkernel/actuator/health` 返回 HTTP 200 /
  `{"status":"UP","groups":["liveness","readiness"]}`，响应时间头
  `Date=Sat, 04 Jul 2026 04:55:14 GMT`，`X-Trace-Id=7836d1ab-86c4-4d8b-9c42-89f0f22987d3`。
  当前 134 映射仍是后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、前端 dist=
  `95bb816292f59833005df4761866dd9d89886cb4`；不得把本地 `66a429a4` 记为已部署。
- 下一步继续沿全局菜单、全角色职责旅程、真实前台默认层做广度优先复核；优先处理可真实落地的产品体验、
  契约、测试、构建和文档问题；不使用子代理、不咨询、不推送远程 `main`。

## 最新阶段交接（2026-07-04 全视角真实前台体验优化第八十批·工作台运行底座默认层收敛）

- 本批继续按全局菜单、医疗产品体验和全角色真实前台复核。结论：第四十四批全局菜单命名与顺序仍成立；
  `docs/EXPERIENCE_CONTRACT.md` 要求默认层说用户任务、低频技术对象收进高级信息。平台管理员工作台的
  `系统健康` 和 `外部依赖连通` 是角色首屏运营视图，不应直接展示 `数据库：postgres`、`关系数据库 · 正常`。
  数据库方言、迁移位置、依赖明细仍属于 `/system/providers`、运行诊断和安全基线语境；工作台只提示可进入运行保障核查，
  并将依赖标签映射为 `运行数据服务`，避免把技术底座当作医疗产品主任务。菜单、路由、权限、运行保障 API、后端、
  数据库、构建配置和 134 发布配置均未改变。
- 已本地提交 `0322041c2ddc0b3c97db0ead209eaed191219957`
  （`fix: 收敛工作台运行底座默认层`）：
  - `frontend/src/widgets/WorkbenchPanel.tsx` 将平台工作台 `系统健康` 卡片中的
    `数据库：...` 改为 `运行保障可查看数据库和依赖明细`。
  - 同文件新增工作台依赖展示映射：运行快照中的数据库依赖在工作台默认层显示为 `运行数据服务`；
    `知识关系同步` 映射保持第七十四批业务口径；原始 `database`、`displayName`、`detail` 等契约值继续保留给运行保障详情。
  - `frontend/src/widgets/WorkbenchPanel.test.tsx` 增加默认层和部分来源失败场景反向断言，阻断
    `数据库：` 与 `关系数据库` 回流平台工作台首屏。
- 本地验证：
  - 红绿核验：先改测试后执行
    `npm --prefix frontend test -- WorkbenchPanel.test.tsx -t "renders the platform operations view without customer-visible technical English"`
    在旧实现下失败，明确找不到 `运行保障可查看数据库和依赖明细`；实现第一步后继续暴露 `关系数据库 · 正常`，
    最终加入工作台依赖映射后同命令通过，`1` 项。
  - `npm --prefix frontend test -- WorkbenchPanel.test.tsx` 通过，`16` 项。
  - 关联配置回归：
    `npm --prefix frontend test -- WorkbenchPanel.test.tsx pages.smoke.test.tsx productCatalog.test.ts routes.test.ts menu.test.ts productRoleJourneys.test.ts`
    通过，`6` 个测试文件 / `117` 项。
  - 生产工作台扫描：
    `rg -n "数据库：|关系数据库" frontend/src/widgets/WorkbenchPanel.tsx frontend/src/widgets/WorkbenchPanel.test.tsx`
    仅命中测试夹具和反向断言；`rg -n "数据库：|关系数据库" frontend/src --glob '!**/*.test.*' --glob '!**/*.spec.*'`
    仅命中安全基线、运行诊断和国产化自检等运行保障/合规明细页面。
  - 生成一致性：`node scripts/audit/export-product-capabilities.mjs --check` 通过。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `959` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `Dashboard-CxLK2_Pw.js`、`index-B1bGHYLa.js`、
    `SystemProviders-BVkJXjDM.js` 等前端产物。
  - `bash scripts/check-comment-zh.sh --mode=full` 通过；`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项；`git diff --check`、应用提交前 `git diff --cached --check` 均通过。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 公网首页
  `https://193.112.107.134/medkernel/` HTTP 200，`Date=Sat, 04 Jul 2026 04:49:59 GMT`，
  `Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，`Content-Length=832`，外部 `index.html` 仍指向
  `/assets/index-DYTh-Ceu.js`、`/assets/vendor-react-bdrMx_IT.js`、`/assets/vendor-data-D9EFEnEk.js`、
  `/assets/vendor-react-C5ap-Sga.css`、`/assets/index-XMjG4gr3.css`；134 readiness 使用
  `https://193.112.107.134/medkernel/actuator/health` 返回 HTTP 200 /
  `{"status":"UP","groups":["liveness","readiness"]}`，响应时间头
  `Date=Sat, 04 Jul 2026 04:49:59 GMT`，`X-Trace-Id=ad4dd6b2-d5ae-46ee-b0fd-05fa4d72526c`。
  当前 134 映射仍是后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、前端 dist=
  `95bb816292f59833005df4761866dd9d89886cb4`；不得把本地 `0322041c` 记为已部署。
- 下一步继续沿全局菜单、全角色职责旅程、真实前台默认层做广度优先复核；优先处理可真实落地的产品体验、
  契约、测试、构建和文档问题；不使用子代理、不咨询、不推送远程 `main`。

## 最新阶段交接（2026-07-04 全视角真实前台体验优化第七十九批·随访模板默认业务口径收敛）

- 本批继续按全局菜单、医疗产品体验和全角色真实前台复核。结论：菜单 IA 与第六十九批“随访协同”职责旅程仍成立；
  问题不在菜单顺序，而在 `/clinical/followup` 的“新建随访模板”默认下拉选项。`docs/EXPERIENCE_CONTRACT.md`
  明确客户面默认说业务任务，不放阶段名或技术对象；旧字典把问卷选项显示为 `真实前台慢病随访问卷`，
  把院内依据显示为 `真实前台演练随访制度`，这会把上线复演阶段词带入真实临床随访配置。新口径改为
  `慢病随访问卷` 与 `慢病随访管理制度`；底层 value、API 字段、菜单、路由、权限、服务端分页、后端、
  数据库、构建配置和 134 发布配置均未改变。
- 已本地提交 `90dce703a9848147e7f4d02ff43b0f35fc22e7e0`
  （`fix: 收敛随访模板默认业务口径`）：
  - `frontend/src/shared/config/followupTemplateCatalog.ts` 仅替换随访模板创建表单的客户可见 label：
    `FOLLOWUP_QUESTIONNAIRE_REAL_FRONTDESK` 继续作为契约值，前台显示 `慢病随访问卷`；
    `REAL_FRONTDESK_FOLLOWUP_TEMPLATE` 继续作为契约值，前台显示 `慢病随访管理制度`。
  - `frontend/src/pages/clinical/Followup.test.tsx` 将随访模板创建用例改为点击新业务名称，并反向断言
    `真实前台慢病随访问卷` 与 `真实前台演练随访制度` 不回流默认前台；提交 payload 仍断言原有契约 value。
- 本地验证：
  - 红绿核验：`npm --prefix frontend test -- Followup.test.tsx -t "创建随访模板时使用业务选项生成可审计的标准契约"`
    在旧实现下先失败，明确找不到 `慢病随访问卷`；实现后通过，`1` 项。
  - 关联配置回归：
    `npm --prefix frontend test -- Followup.test.tsx pages.smoke.test.tsx productCatalog.test.ts routes.test.ts menu.test.ts productRoleJourneys.test.ts`
    通过，`6` 个测试文件 / `120` 项。
  - 生产旧词扫描：
    `rg -n "真实前台慢病随访问卷|真实前台演练随访制度" frontend/src --glob '!**/*.test.*' --glob '!**/*.spec.*'`
    无输出。
  - 生成一致性：`node scripts/audit/export-product-capabilities.mjs --check` 通过。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `959` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `Followup-DmvzQMPS.js`、`index-CIstSJTI.js`、
    `Clinical-D4ESWhZ0.css` 等前端产物。
  - `bash scripts/check-comment-zh.sh --mode=full` 通过；`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项；`git diff --check`、应用提交前 `git diff --cached --check` 均通过。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 公网首页
  `https://193.112.107.134/medkernel/` HTTP 200，`Date=Sat, 04 Jul 2026 04:42:18 GMT`，
  `Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，`Content-Length=832`，外部 `index.html` 仍指向
  `/assets/index-DYTh-Ceu.js`、`/assets/vendor-react-C5ap-Sga.css`、`/assets/index-XMjG4gr3.css`；
  134 readiness 使用 `https://193.112.107.134/medkernel/actuator/health` 返回 HTTP 200 /
  `{"status":"UP","groups":["liveness","readiness"]}`，响应时间头
  `Date=Sat, 04 Jul 2026 04:42:18 GMT`，`X-Trace-Id=69c227cc-2dc1-4977-9663-5adfce2c49c2`。
  当前 134 映射仍是后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、前端 dist=
  `95bb816292f59833005df4761866dd9d89886cb4`；不得把本地 `90dce703` 记为已部署。
- 下一步继续沿全局菜单、全角色职责旅程、真实前台默认层做广度优先复核；优先处理可真实落地的产品体验、
  契约、测试、构建和文档问题；不使用子代理、不咨询、不推送远程 `main`。

## 最新阶段交接（2026-07-04 全视角真实前台体验优化第七十八批·临床路径层级前台口径收敛）

- 本批继续按全局菜单、医疗产品体验和全角色真实前台复核。结论：第四十四批全局菜单命名与顺序仍成立；
  `docs/audit/product-function-catalog.md` 将 `/pathway/templates` 定义为知识治理域的 `临床路径库`，
  任务是维护、审核、发布和回滚临床路径版本；`docs/audit/product-role-journeys.md` 中的临床路径职责旅程
  要求业务角色看到的是可理解的路径层级和发布语义。问题不在菜单名或顺序，而在 `临床路径库` 列表与详情默认层：
  旧实现把 `templateLevel` 直接作为表格值展示，`STANDARD` 会出现在前台；详情页复用全局枚举兜底后还会把路径层级误显示为
  `状态待确认`。本批改为使用临床路径专属业务标签，`STANDARD` 默认显示 `平台标准路径`；未改菜单、路由、权限、
  服务端分页、后端 API、数据库、构建配置和 134 发布配置。
- 已本地提交 `4384ab19b37250ac1d0923f08d4b1f7b5c05e1e8`
  （`fix: 收敛临床路径层级前台口径`）：
  - `frontend/src/pages/tenant/PathwayTemplates.tsx` 新增 `pathwayTemplateLevelText`，统一复用
    `templateLevelOptions` 的临床路径层级业务名；列表 `层级` 列和详情 `层级` 字段均通过该函数展示，避免裸露
    `STANDARD` 或落入通用 `状态待确认`。
  - `frontend/src/pages/tenant/PathwayTemplates.test.tsx` 增加“路径层级在列表与详情使用临床路径业务名称”回归：
    列表和详情都断言 `平台标准路径`，并反向断言 `STANDARD` 与 `状态待确认` 不回流默认前台。
- 本地验证：
  - 红绿核验：`npm --prefix frontend test -- PathwayTemplates.test.tsx -t "路径层级在列表与详情使用临床路径业务名称"`
    在旧实现下先失败，明确找不到 `平台标准路径`；实现后通过，`1` 项。
  - 关联配置回归：
    `npm --prefix frontend test -- PathwayTemplates.test.tsx pages.smoke.test.tsx productCatalog.test.ts routes.test.ts menu.test.ts productRoleJourneys.test.ts`
    通过，`6` 个测试文件 / `113` 项。
  - 生成一致性：`node scripts/audit/export-product-capabilities.mjs --check` 通过。
  - 生产必要残留核验：
    `rg -n "状态待确认|STANDARD" frontend/src/pages/tenant/PathwayTemplates.tsx` 仅命中 `templateLevelOptions`
    与新建表单默认值中的真实枚举常量，未命中默认展示层。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `959` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `PathwayTemplates-BsORp5l3.js`、`index-C-ZU8rh9.js`、
    `QcEvalSets-EVTA5QPb.js` 等前端产物。
  - `bash scripts/check-comment-zh.sh --mode=full` 通过；`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项；`git diff --check`、应用提交前 `git diff --cached --check` 均通过。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 公网首页
  `https://193.112.107.134/medkernel/` HTTP 200，`Date=Sat, 04 Jul 2026 04:36:31 GMT`，
  `Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，`Content-Length=832`，外部 `index.html` 仍指向
  `/assets/index-DYTh-Ceu.js`、`/assets/vendor-react-C5ap-Sga.css`、`/assets/index-XMjG4gr3.css`；
  134 readiness 使用 `https://193.112.107.134/medkernel/actuator/health` 返回 HTTP 200 /
  `{"status":"UP","groups":["liveness","readiness"]}`，响应时间头
  `Date=Sat, 04 Jul 2026 04:36:31 GMT`，`X-Trace-Id=1538a367-07fc-4055-b21a-e20a8ac0a350`。
  当前 134 映射仍是后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、前端 dist=
  `95bb816292f59833005df4761866dd9d89886cb4`；不得把本地 `4384ab19` 记为已部署。
- 下一步继续沿全局菜单、全角色职责旅程、真实前台默认层做广度优先复核；优先处理可真实落地的产品体验、
  契约、测试、构建和文档问题；不使用子代理、不咨询、不推送远程 `main`。

## 最新阶段交接（2026-07-04 全视角真实前台体验优化第七十七批·评价指标默认术语统一）

- 本批继续按全局菜单、医疗产品体验和全角色真实前台复核。结论：第四十四批全局菜单命名与顺序仍成立；
  `docs/audit/product-function-catalog.md` 将 `/qc/eval/sets` 定义为质量管理域的 `评价指标`，
  任务是维护评价指标、影响分析和发布状态；`/pathway/templates` 定义为知识治理域的 `临床路径库`，
  任务是维护、审核、发布和回滚临床路径版本。临床路径结局指标绑定引用的是已生效评价指标，不应另起
  `评估指标` 资产口径；`QcEvalSets` 加载态中的 `EVAL-01` 属于技术编号，也不应出现在默认前台层。
  本批仅统一评价指标默认可见术语；`评估主体`、`仿真评估` 等表示动作或主体的业务词继续保留，后端 API、
  `indicatorCode` 字段、菜单顺序、权限、服务端分页、数据库、构建配置和 134 发布配置均未改变。
- 已本地提交 `0c94be2b4d7dfd0d0500771cfd762d6fab8e5063`
  （`fix: 统一评价指标默认术语`）：
  - `frontend/src/pages/quality/QcEvalSets.tsx` 将加载说明从
    `正在读取 EVAL-01 指标版本台账。` 收敛为 `正在读取评价指标版本台账。`。
  - `frontend/src/pages/tenant/PathwayTemplates.tsx` 将结局指标绑定表单的标签、占位和空列表提示从
    `评估指标` / `选择已生效评估指标` / `暂无已生效评估指标` 统一为
    `评价指标` / `选择已生效评价指标` / `暂无已生效评价指标`。
  - `frontend/src/shared/ui/StepFlow.tsx` 将共享 7 步流注释中的同一资产名同步为 `评价指标`。
  - `frontend/src/pages/quality/QcEvalSets.test.tsx`、`frontend/src/pages/tenant/PathwayTemplates.test.tsx`
    增加加载态与临床路径结局指标绑定反向断言，阻断 `EVAL-01` 与旧 `评估指标` 口径回流。
- 本地验证：
  - 红绿核验：`npm --prefix frontend test -- QcEvalSets.test.tsx -t "uses business wording while loading evaluation indicators"`
    在旧实现下先失败，明确显示页面仍为 `正在读取 EVAL-01 指标版本台账。`；实现后通过，`1` 项。
  - 红绿核验：路径表单用例先按真实用户动作修正为“空白路径 → 节点画布 → 添加结局指标”，并规避 ReactFlow
    SVG marker 对 `getByLabelText` 的 jsdom 选择器干扰；临时还原旧 `评估指标` 实现后执行
    `npm --prefix frontend test -- PathwayTemplates.test.tsx -t "结局指标绑定引用已生效评价指标"` 失败，
    明确找不到 `评价指标`；恢复新实现后同命令通过，`1` 项。
  - 生产旧词扫描：
    `rg -n "评估指标|EVAL-01" frontend/src/pages/quality/QcEvalSets.tsx frontend/src/pages/tenant/PathwayTemplates.tsx frontend/src/shared/ui/StepFlow.tsx`
    无输出；包含测试的旧词扫描仅命中反向断言。
  - 关联配置回归：
    `npm --prefix frontend test -- QcEvalSets.test.tsx PathwayTemplates.test.tsx pages.smoke.test.tsx productCatalog.test.ts routes.test.ts menu.test.ts`
    通过，`6` 个测试文件 / `113` 项。
  - 生成一致性：`node scripts/audit/export-product-capabilities.mjs --check` 通过。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `958` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `QcEvalSets-DNi0J8Ac.js`、`PathwayTemplates-BHwxDTHg.js`、
    `StepFlow-Db3oPus5.js`、`index-DETlYp-g.js` 等前端产物。
  - `bash scripts/check-comment-zh.sh --mode=full` 通过；`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项；`git diff --check`、应用提交前 `git diff --cached --check` 均通过。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 公网首页
  `https://193.112.107.134/medkernel/` HTTP 200，`Date=Sat, 04 Jul 2026 03:23:50 GMT`，
  `Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，`Content-Length=832`，外部 `index.html` 仍指向
  `/assets/index-DYTh-Ceu.js`；134 readiness 使用 `https://193.112.107.134/medkernel/actuator/health`
  返回 HTTP 200 / `{"status":"UP","groups":["liveness","readiness"]}`，响应时间头
  `Date=Sat, 04 Jul 2026 03:23:50 GMT`，`X-Trace-Id=f3e8b89b-f9dd-4089-a5ce-7050ad19b39e`。
  134 映射仍按当前已核定事实保持：后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、
  前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`，不要把本地 `0c94be2b` 误写为已上线。
- 后续继续长目标：不再开子代理，不中途咨询；继续广度优先核查真实前台、职责旅程、菜单分布、构建门禁、
  134 证据映射和最终远程收口条件。

## 最新阶段交接（2026-07-04 全视角真实前台体验优化第七十六批·质量评价默认真实口径收敛）

- 本批继续按全局菜单、医疗产品体验和全角色真实前台复核。结论：第四十四批全局菜单命名与顺序仍成立；
  `docs/audit/product-function-catalog.md` 将 `/qc/eval/sets` 定义为 `评价指标`，用于维护评价指标、影响分析和发布状态；
  `/qc/eval/results` 是隐藏来源视图，并入 `质量问题与整改`，用于把评估结果作为问题发现和整改页来源；
  `docs/PRODUCT_SCOPE.md` S11 明确质量域产物是评价结果、质量问题和整改闭环。页面默认层仍出现
  `按真实评价结果追溯问题证据`、`真实评价结果总数`、`当前筛选下暂无真实评价结果`、
  `当前查询返回 ... 个真实指标版本`。这些词适合测试夹具和真实性门禁，不适合质控办、科主任和运营人员的质量评价默认视图。
  本批仅收敛质量评价结果和评价指标版本的默认业务语言；规则试运行中的 `真实上下文快照` 仍是业务对象，未改动。
- 已本地提交 `1267b484af12c35640cbaa010ebc58a99854b0c0`
  （`fix: 收敛质量评价默认真实口径`）：
  - `frontend/src/pages/quality/QcEvalResults.tsx` 将描述改为 `按评价结果追溯问题证据`，
    空态改为 `当前筛选下暂无评价结果` / `暂无评价结果`，指标卡改为 `评价结果总数`。
  - `frontend/src/pages/quality/QcEvalSets.tsx` 将 7 步配置流选模板阶段从
    `当前查询返回 ... 个真实指标版本` 收敛为 `当前查询返回 ... 个评价指标版本`。
  - `frontend/src/pages/quality/QcEvalResults.test.tsx`、`frontend/src/pages/quality/QcEvalSets.test.tsx`
    和 `frontend/src/pages/pages.smoke.test.tsx` 增加默认层与空态反向断言，阻断旧 `真实评价...` /
    `真实指标版本` 口径回流。
  - 未改变质量域菜单、路由、权限、后端 API、数据契约、服务端分页、数据库、构建配置或 134 发布配置。
- 本地验证：
  - 红绿核验：先改测试后执行
    `npm --prefix frontend test -- QcEvalResults.test.tsx QcEvalSets.test.tsx -t "loads real results|uses quality source wording|loads real indicators"`
    在旧实现下失败，明确显示找不到 `按评价结果追溯问题证据`、`当前筛选下暂无评价结果`、
    `当前查询返回 ... 个评价指标版本`；实现并校正 7 步流测试场景后，同类命令
    `npm --prefix frontend test -- QcEvalResults.test.tsx QcEvalSets.test.tsx -t "loads real results|uses quality source wording|loads real indicators|uses evaluation indicator wording"`
    通过，`4` 项。
  - 烟测根因与修复：首次完整 `npm --prefix frontend run verify` 在
    `pages.smoke.test.tsx > renders the quality qc-eval-results console` 失败，根因为烟测仍断言旧
    `当前筛选下暂无真实评价结果`；同步烟测为 `当前筛选下暂无评价结果` 后，定向
    `npm --prefix frontend test -- pages.smoke.test.tsx -t "renders the quality qc-eval-results console"`
    通过，随后完整前端门禁通过。
  - 生产旧词扫描：
    `rg -n "按真实评价结果追溯问题证据|当前筛选下暂无真实评价结果|真实评价结果总数|暂无真实评价结果|真实指标版本" frontend/src/pages/quality/QcEvalResults.tsx frontend/src/pages/quality/QcEvalSets.tsx`
    无输出；包含测试的同词扫描仅命中反向断言。
  - 关联配置回归：
    `npm --prefix frontend test -- QcEvalResults.test.tsx QcEvalSets.test.tsx QcAlerts.test.tsx QcDashboard.test.tsx productCatalog.test.ts routes.test.ts menu.test.ts`
    通过，`7` 个测试文件 / `98` 项。
  - 生成一致性：`node scripts/audit/export-product-capabilities.mjs --check` 通过。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `956` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `QcEvalResults-DklLnfPl.js`、`QcEvalSets-tXCAzIY1.js`、
    `index-DND9jmju.js` 等前端产物。
  - `bash scripts/check-comment-zh.sh --mode=full` 通过；`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项；`git diff --check`、应用提交前 `git diff --cached --check` 均通过。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 公网首页
  `https://193.112.107.134/medkernel/` HTTP 200，`Date=Sat, 04 Jul 2026 03:06:51 GMT`，
  `Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，`Content-Length=832`，外部 `index.html` 仍指向
  `/assets/index-DYTh-Ceu.js`；134 readiness 使用 `https://193.112.107.134/medkernel/actuator/health`
  返回 HTTP 200 / `{"status":"UP","groups":["liveness","readiness"]}`，响应时间头
  `Date=Sat, 04 Jul 2026 03:06:51 GMT`，`X-Trace-Id=5ca19f6a-9918-45d3-af7f-8bc2707d6c12`。
  134 映射仍按当前已核定事实保持：后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、
  前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`，不要把本地 `1267b484` 误写为已上线。
- 后续继续长目标：不再开子代理，不中途咨询；继续广度优先核查真实前台、职责旅程、菜单分布、构建门禁、
  134 证据映射和最终远程收口条件。

## 最新阶段交接（2026-07-04 全视角真实前台体验优化第七十五批·质量管理概览默认真实口径收敛）

- 本批继续按全局菜单、医疗产品体验和全角色真实前台复核。结论：第四十四批全局菜单命名与顺序仍成立；
  `docs/audit/product-function-catalog.md`、职责旅程和 `frontend/src/shared/config/routes.ts` 均将
  `/qc/dashboard` 定义为 `质量管理概览`，用于查看质量风险、运营趋势并下钻到责任问题；
  `docs/PRODUCT_SCOPE.md` 的质量域强调评价结果、质量问题与整改闭环。页面主标题已正确，但默认层仍出现
  `真实指标、风险热力与闭环价值`、`真实质控问题总数`、`当前筛选下暂无真实质控数据`、`真实下钻证据`
  等实现真实性强调。该表达适合门禁和证据说明，不适合医疗质量管理首屏，会让质控办、科主任和运营角色把
  “真实”误读为指标品类或证据状态。本批仅收敛质量概览页面默认业务口径；证据详情、接口、路由、权限、
  服务端分页、数据库、菜单顺序和 134 发布配置均未改变。
- 已本地提交 `e9670a65270ab1e3e365cfcf038e94beb9eaa335`
  （`fix: 收敛质量概览默认真实口径`）：
  - `frontend/src/pages/quality/QcDashboard.tsx` 将页面描述统一为
    `质量指标、风险热力与整改闭环`，并在加载、错误、空态和正常态共用同一页面常量。
  - 同文件把默认指标名从 `真实质控问题总数` 收敛为 `质控问题总数`；空态从
    `当前筛选下暂无真实质控数据` / `暂无真实科室风险热力` / `暂无真实质量成效` 收敛为
    `当前筛选下暂无质控数据` / `暂无科室风险热力` / `暂无质量成效`。
  - 质量问题下钻抽屉从 `真实下钻证据` / `暂无真实下钻证据` 收敛为
    `问题下钻证据` / `暂无问题下钻证据`，保留原有证据列表、分页和下钻类型。
  - `frontend/src/pages/quality/QcDashboard.test.tsx` 增加质量概览默认层、空态和下钻抽屉反向断言，
    阻断旧 `真实...` 口径回流。
  - 未改变质量域菜单、路由、接口、权限、后端 API、数据契约、构建配置或 134 发布配置。
- 本地验证：
  - 红绿核验：先改测试后执行
    `npm --prefix frontend test -- QcDashboard.test.tsx -t "renders real dashboard aggregation|uses quality management wording|默认用业务语言打开下钻证据"`
    在旧页面下失败，明确显示找不到 `质量指标、风险热力与整改闭环`、`当前筛选下暂无质控数据`、
    `问题下钻证据`；实现后同命令通过，`3` 项。
  - 旧词扫描：
    `rg -n "真实指标、风险热力与闭环价值|当前筛选下暂无真实质控数据|真实质控问题总数|暂无真实科室风险热力|暂无真实质量成效|真实下钻证据|暂无真实下钻证据" frontend/src/pages/quality/QcDashboard.tsx`
    无输出；包含测试的同词扫描仅命中反向断言。
  - 关联配置回归：
    `npm --prefix frontend test -- QcDashboard.test.tsx pages.smoke.test.tsx QcAlerts.test.tsx InsuranceAudit.test.tsx QcEvalSets.test.tsx productCatalog.test.ts routes.test.ts menu.test.ts`
    通过，`8` 个测试文件 / `127` 项。
  - 生成一致性：`node scripts/audit/export-product-capabilities.mjs --check` 通过。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `954` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `QcDashboard-DIXMoyTU.js`、`Quality-CyanocAS.css`、
    `index-tzhCk-9a.js` 等前端产物。
  - `bash scripts/check-comment-zh.sh --mode=full` 通过；`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项；`git diff --check`、应用提交前 `git diff --cached --check` 均通过。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 公网首页
  `https://193.112.107.134/medkernel/` HTTP 200，`Date=Sat, 04 Jul 2026 02:54:10 GMT`，
  `Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，`Content-Length=832`，外部 `index.html` 仍指向
  `/assets/index-DYTh-Ceu.js`；134 readiness 使用 `https://193.112.107.134/medkernel/actuator/health`
  返回 HTTP 200 / `{"status":"UP","groups":["liveness","readiness"]}`，响应时间头
  `Date=Sat, 04 Jul 2026 02:54:10 GMT`，`X-Trace-Id=57d4225b-6d88-4024-990a-d1f9dc4cf518`。
  134 映射仍按当前已核定事实保持：后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、
  前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`，不要把本地 `e9670a65` 误写为已上线。
- 后续继续长目标：不再开子代理，不中途咨询；继续广度优先核查真实前台、职责旅程、菜单分布、构建门禁、
  134 证据映射和最终远程收口条件。

## 最新阶段交接（2026-07-04 全视角真实前台体验优化第七十四批·工作台知识关系同步口径收敛）

- 本批继续按全局菜单、医疗产品体验和全角色真实前台复核。结论：第四十四批全局菜单命名与顺序仍成立；
  `docs/audit/product-function-catalog.md`、职责旅程和 `frontend/src/shared/config/routes.ts` 均将
  `/advanced/graph` 定义为 `知识关系`，用于查看知识之间的来源、适应证、禁忌和相互作用关系。
  但平台管理员工作台默认层仍把运行依赖显示为 `知识图谱投影`，下钻按钮为 `查看知识图谱`，缺少依赖时提示
  `核查图谱投影配置`。这会让平台管理员和医疗引擎运营员从首屏误以为进入的是图数据库运维对象，而不是
  可复核的知识关系同步状态。本批仅收敛工作台默认层；运行保障和证据语境中的图投影/图谱技术边界继续保留在
  对应运维页面与后端契约中。
- 已本地提交 `bb5c552600ce6d51a93bbd9a731c1c64a0ac4dc4`
  （`fix: 收敛工作台知识关系同步口径`）：
  - `frontend/src/widgets/WorkbenchPanel.tsx` 将知识同步卡片标题统一为 `知识关系同步`，下钻按钮改为
    `查看知识关系`，仍指向既有 `/advanced/graph`。
  - 同文件新增工作台依赖展示映射，将运行快照中的 `graph-projection` 默认显示为 `知识关系同步`；当图关系依赖未连接时，
    默认说明改为“知识关系同步未连接；核心业务继续使用关系库权威数据”，不再把图谱投影能力开关暴露在首屏。
  - 缺少 graph 依赖时，空态从 `知识同步来源待配置` / `图谱投影配置` 收敛为
    `知识关系同步来源待配置` / `知识关系同步配置`。
  - `frontend/src/widgets/WorkbenchPanel.test.tsx` 锁定平台管理员工作台新口径，并反向拦截
    `知识图谱投影`、`图谱投影能力开关`、`图谱投影配置` 回流。
  - 未改变菜单顺序、路由路径、权限、后端 API、运行保障数据契约、数据库、目录生成脚本或 134 发布配置。
- 本地验证：
  - 红绿核验：`npm --prefix frontend test -- WorkbenchPanel.test.tsx -t "renders the platform operations view|shows actionable knowledge sync status"`
    在旧页面下先失败，明确显示找不到 `知识关系同步` 和 `知识关系同步来源待配置`；实现后通过，`2` 项。
  - 生产旧词扫描：`rg -n "查看知识图谱|图谱投影配置|知识同步来源待配置|未返回知识同步来源|图谱投影能力开关|知识图谱投影" frontend/src/widgets/WorkbenchPanel.tsx frontend/src -g '*.ts' -g '*.tsx' --glob '!**/*.test.ts' --glob '!**/*.test.tsx'`
    无输出；包含测试的同词扫描仅命中测试夹具和反向断言。
  - 关联配置回归：`npm --prefix frontend test -- WorkbenchPanel.test.tsx productRoleJourneys.test.ts routes.test.ts menu.test.ts productCatalog.test.ts`
    通过，`5` 个测试文件 / `91` 项。
  - 生成一致性：`node scripts/audit/export-product-capabilities.mjs --check` 通过。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `953` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `Dashboard-C14d2DOP.js`、`GraphExplore-D7jiwTwq.js`、
    `index-DzYiSxfY.js` 等前端产物。
  - `bash scripts/check-comment-zh.sh --mode=full` 通过；`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项；`git diff --check`、应用提交前 `git diff --cached --check` 均通过。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 公网首页
  `https://193.112.107.134/medkernel/` HTTP 200，`Date=Sat, 04 Jul 2026 02:45:38 GMT`，
  `Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，`Content-Length=832`，外部 `index.html` 仍指向
  `/assets/index-DYTh-Ceu.js`；134 readiness 使用 `https://193.112.107.134/medkernel/actuator/health`
  返回 HTTP 200 / `{"status":"UP","groups":["liveness","readiness"]}`，响应时间头
  `Date=Sat, 04 Jul 2026 02:45:38 GMT`，`X-Trace-Id=ec3a6d15-f9c3-4e7b-acef-0e202702faeb`。
  134 映射仍按当前已核定事实保持：后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、
  前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`，不要把本地 `bb5c5526` 误写为已上线。
- 后续继续长目标：不再开子代理，不中途咨询；继续广度优先核查真实前台、职责旅程、菜单分布、构建门禁、
  134 证据映射和最终远程收口条件。

## 最新阶段交接（2026-07-04 全视角真实前台体验优化第七十三批·安全与配置前台入口口径收敛）

- 本批继续按全局菜单、医疗产品体验和全角色真实前台复核。结论：第四十四批全局菜单命名与顺序仍成立；
  `docs/audit/product-function-catalog.md`、职责旅程和 `frontend/src/shared/config/routes.ts` 均将
  `/security/baseline` 定义为 `安全与配置`，任务是维护安全基线、系统配置、数据权限和脱敏策略。
  但页面首屏仍显示 `安全基线与系统配置`，空态还出现 `安全与配置合同暂无数据`、`暂无安全基线状态`。
  这会让平台管理员、信息科和安全合规人员误以为进入的是技术基线或合同对象，而不是合规安全域下的安全配置、
  运行配置、数据访问和脱敏策略统一维护入口。本批按权威菜单与功能目录收敛页面入口；不扩大菜单顺序、路由、
  权限、后端 API、数据库和发布配置。
- 已本地提交 `9ce90c04ffcbfa99be2054d5c16ed09b798202a9`
  （`fix: 收敛安全与配置前台入口口径`）：
  - `frontend/src/pages/compliance/SecurityBaseline.tsx` 新增页面标题常量并将加载、错误、空态和正常态统一为
    `安全与配置`；错误说明改为 `安全与配置状态读取失败`，空态改为 `安全与配置暂无数据` /
    `暂无安全与配置状态`，去掉前台默认层的“合同”和旧“安全基线”入口口径。
  - `frontend/src/pages/compliance/SecurityBaseline.test.tsx` 锁定新首屏标题和空态文案，并反向拦截
    `安全基线与系统配置`、`安全与配置合同暂无数据`、`暂无安全基线状态` 回流。
  - `frontend/src/pages/operationalControlPages.test.tsx` 同步安全配置聚合页断言，确保运营控制 smoke 不再接受旧标题。
  - 未改变菜单顺序、路由路径、权限、后端 API、数据库、目录生成脚本或 134 发布配置。
- 本地验证：
  - 红绿核验：`npm --prefix frontend test -- SecurityBaseline.test.tsx -t "unifies runtime baseline"`
    在旧页面下先失败，明确显示可访问 heading 仍是 `安全基线与系统配置`；页面修正后与空态测试一起通过。
  - 红绿核验：`npm --prefix frontend test -- operationalControlPages.test.tsx -t "renders security baseline"`
    在旧页面下先失败，明确显示可访问 heading 仍是 `安全基线与系统配置`；聚合页断言修正后通过。
  - 红绿核验：`npm --prefix frontend test -- SecurityBaseline.test.tsx -t "uses customer-facing wording"`
    在旧页面下先失败于 `安全与配置合同暂无数据` / `暂无安全基线状态`；空态文案收敛后通过。
  - 定点回归：`npm --prefix frontend test -- SecurityBaseline.test.tsx -t "unifies runtime baseline|uses customer-facing wording"`
    通过，`2` 项；`npm --prefix frontend test -- operationalControlPages.test.tsx -t "renders security baseline"`
    通过，`1` 项。
  - 旧词扫描：`rg -n "安全基线与系统配置|安全与配置合同暂无数据|暂无安全基线状态|暂时无法读取安全基线" frontend/src docs/audit -g '*.ts' -g '*.tsx' -g '*.md'`
    仅命中测试中的反向断言。
  - 关联配置回归：`npm --prefix frontend test -- SecurityBaseline.test.tsx operationalControlPages.test.tsx productCatalog.test.ts routes.test.ts menu.test.ts productRoleJourneys.test.ts`
    通过，`6` 个测试文件 / `96` 项。
  - 生成一致性：`node scripts/audit/export-product-capabilities.mjs --check` 通过。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `953` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `SecurityBaseline-BXWw3UO5.js`、`Quality-CyanocAS.css`、
    `index-D1USUB8-.js` 等前端产物。
  - `bash scripts/check-comment-zh.sh --mode=full` 通过；`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项；`git diff --check`、应用提交前 `git diff --cached --check` 均通过。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 公网首页
  `https://193.112.107.134/medkernel/` HTTP 200，`Date=Sat, 04 Jul 2026 02:39:36 GMT`，
  `Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，`Content-Length=832`，外部 `index.html` 仍指向
  `/assets/index-DYTh-Ceu.js`；134 readiness 使用 `https://193.112.107.134/medkernel/actuator/health`
  返回 HTTP 200 / `{"status":"UP","groups":["liveness","readiness"]}`，响应时间头
  `Date=Sat, 04 Jul 2026 02:39:36 GMT`，`X-Trace-Id=fdc2112c-fde8-48d7-8fd3-73ba9a5f201a`。
  134 映射仍按当前已核定事实保持：后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、
  前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`，不要把本地 `9ce90c04` 误写为已上线。
- 后续继续长目标：不再开子代理，不中途咨询；继续广度优先核查真实前台、职责旅程、菜单分布、构建门禁、
  134 证据映射和最终远程收口条件。

## 最新阶段交接（2026-07-04 全视角真实前台体验优化第七十二批·质量管理前台入口口径收敛）

- 本批继续按全局菜单、医疗产品体验和全角色真实前台复核。结论：第四十四批全局菜单命名与顺序仍成立；
  `docs/audit/product-function-catalog.md`、职责旅程和 `frontend/src/shared/config/routes.ts` 均将
  `/qc/alerts` 定义为 `质量问题与整改`、将 `/qc/insurance` 定义为 `医保审核`。但页面首屏仍分别显示
  `质量问题`、`医保智能审核`，且空态和统计卡混用 `真实质量问题` / `真实医保问题`。这会让医疗引擎运营员、
  医保审核员和院方质量管理人员误以为进入的是单纯问题列表或营销化智能工具，而不是质量问题确认、整改派发、
  医保问题核查和处置闭环。本批按功能目录“确认问题、派发整改、复核并闭环”和“核查医保问题、依据和处置结果”
  收敛页面入口；不扩大菜单顺序、路由、权限和数据契约。
- 已本地提交 `b10c3d9e00e3cc48c5385bd9f992c54f05e50e83`
  （`fix: 收敛质量管理前台入口口径`）：
  - `frontend/src/pages/quality/QcAlerts.tsx` 将 PageShell 标题改为 `质量问题与整改`，说明改为
    “确认质量问题、派发整改、复核并闭环”；首屏空态和列表空态改为 `当前筛选下暂无待整改质量问题`，
    列表标题改为 `质量问题与整改列表`。
  - `frontend/src/pages/quality/InsuranceAudit.tsx` 将 PageShell 标题改为 `医保审核`，说明改为
    “核查医保问题、依据和处置结果”；首屏空态和列表空态改为 `当前筛选下暂无医保问题`，总数卡改为
    `医保问题总数`。
  - `frontend/src/pages/quality/QcAlerts.test.tsx`、`frontend/src/pages/quality/InsuranceAudit.test.tsx`
    和 `frontend/src/pages/pages.smoke.test.tsx` 同步锁定新入口，并反向拦截 `医保智能审核`、
    `真实医保问题总数`、`当前筛选下暂无真实医保问题`、`当前筛选下暂无真实质量问题` 回流。
  - 未改变菜单顺序、路由路径、权限、后端 API、数据库、目录生成脚本或 134 发布配置。
- 本地验证：
  - 红绿核验：`npm --prefix frontend test -- QcAlerts.test.tsx -t "renders real quality alerts|keeps the table usable"`
    在旧页面下先失败，明确显示可访问 heading 仍是 `质量问题`；`npm --prefix frontend test -- InsuranceAudit.test.tsx -t "renders real insurance issues|keeps the audit flow usable"`
    在旧页面下先失败，明确显示可访问 heading 仍是 `医保智能审核`。
  - 定点回归：`npm --prefix frontend test -- QcAlerts.test.tsx -t "renders real quality alerts|uses an honest empty state"`
    通过，`2` 项；`npm --prefix frontend test -- InsuranceAudit.test.tsx -t "renders real insurance issues|keeps the audit flow usable|uses an honest empty state"`
    通过，`3` 项；`npm --prefix frontend test -- pages.smoke.test.tsx -t "renders the quality qc-alerts|renders the quality insurance-audit"`
    通过，`2` 项。
  - 旧词扫描：`rg -n "医保智能审核|真实医保问题总数|当前筛选下暂无真实医保问题|按真实结算事实核查病案|按真实预警处置整改|当前筛选下暂无真实质量问题|质量问题列表" frontend/src docs/audit -g '*.ts' -g '*.tsx' -g '*.md'`
    仅命中测试中的反向断言。
  - 关联配置回归：`npm --prefix frontend test -- QcAlerts.test.tsx InsuranceAudit.test.tsx pages.smoke.test.tsx productCatalog.test.ts routes.test.ts menu.test.ts`
    通过，`6` 个测试文件 / `107` 项。
  - 生成一致性：`node scripts/audit/export-product-capabilities.mjs --check` 通过。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `952` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `QcAlerts-c3t54jRj.js`、`InsuranceAudit-CYtgqTdc.js`、
    `Quality-CyanocAS.css`、`index-BbEskb4R.js` 等前端产物。
  - `bash scripts/check-comment-zh.sh --mode=full` 通过；`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项；`git diff --check`、应用提交前 `git diff --cached --check` 均通过。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 公网首页
  `https://193.112.107.134/medkernel/` HTTP 200，`Date=Sat, 04 Jul 2026 02:30:46 GMT`，
  `Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，`Content-Length=832`，外部 `index.html` 仍指向
  `/assets/index-DYTh-Ceu.js`；134 readiness 使用 `https://193.112.107.134/medkernel/actuator/health`
  返回 HTTP 200 / `{"status":"UP","groups":["liveness","readiness"]}`，响应时间头
  `Date=Sat, 04 Jul 2026 02:30:46 GMT`，`X-Trace-Id=555acd84-41a8-46e9-ab12-4338078b46f9`。
  134 映射仍按当前已核定事实保持：后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、
  前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`，不要把本地 `b10c3d9e` 误写为已上线。
- 后续继续长目标：不再开子代理，不中途咨询；继续广度优先核查真实前台、职责旅程、菜单分布、构建门禁、
  134 证据映射和最终远程收口条件。

## 最新阶段交接（2026-07-04 全视角真实前台体验优化第七十一批·评价指标前台入口口径收敛）

- 本批继续按全局菜单、医疗产品体验和全角色真实前台复核。结论：第四十四批全局菜单命名与顺序仍成立；
  质量管理域的功能目录、路由和菜单均将 `/qc/eval/sets` 定义为 `评价指标`，但页面首屏仍显示
  `评估指标库`，总数卡、加载态和新建弹窗也混用 `评估指标`。这会让院方质量管理人员误以为进入另一个
  “指标库”资产页，而不是质量管理域下的评价指标配置与发布任务。按功能目录“维护评价指标、影响分析和发布状态”
  的职责，本批将页面入口、统计、加载/错误和新建弹窗收敛为 `评价指标`；`评估主体`、`仿真评估` 等表示动作或主体的词仍保留。
- 已本地提交 `609d49f37f9c364d9b55beb01a0e754da8e5fa70`
  （`fix: 收敛评价指标前台入口口径`）：
  - `frontend/src/pages/quality/QcEvalSets.tsx` 将 PageShell 标题改为 `评价指标`，说明改为
    “维护质控评价指标、影响分析和发布状态”；加载态、读取失败、创建成功/失败、总数卡、空态和新建弹窗同步改用
    `评价指标` 口径。
  - `frontend/src/pages/quality/QcEvalSets.test.tsx` 锁定首屏标题、总数卡和新建弹窗新口径，并反向拦截
    `评估指标库`、`真实评估指标总数`、`新建评估指标` 回流。
  - `frontend/src/pages/pages.smoke.test.tsx` 同步质量指标 smoke，要求页面首屏为 `评价指标`。
  - 未改变菜单顺序、路由路径、权限、后端 API、数据库、目录生成脚本或 134 发布配置。
- 本地验证：
  - 红绿核验：`npm --prefix frontend test -- QcEvalSets.test.tsx -t "loads real indicators"` 在旧页面下先失败，
    明确显示可访问 heading 仍是 `评估指标库`；页面修正后与创建弹窗测试一起通过。
  - 定点回归：`npm --prefix frontend test -- QcEvalSets.test.tsx -t "loads real indicators|creates a draft indicator"`
    通过，`2` 项；`npm --prefix frontend test -- pages.smoke.test.tsx -t "renders the quality qc-eval-sets simulation"`
    通过，`1` 项。
  - 关联配置回归：`npm --prefix frontend test -- QcEvalSets.test.tsx pages.smoke.test.tsx productCatalog.test.ts routes.test.ts menu.test.ts`
    通过，`5` 个测试文件 / `100` 项。
  - 生成一致性：`node scripts/audit/export-product-capabilities.mjs --check` 通过。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `952` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `QcEvalSets-Qi0C_C4P.js`、`Quality-CyanocAS.css`、
    `KnowledgeGovernance-BmyUUwpZ.js`、`index-bR--gQWo.js` 等前端产物。
  - `bash scripts/check-comment-zh.sh --mode=full` 通过；`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项；`git diff --check`、应用提交前 `git diff --cached --check` 均通过。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 公网首页 HTTP 200，
  `Date=Sat, 04 Jul 2026 02:22:53 GMT`，`Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，`Content-Length=832`；
  134 readiness 使用 `https://193.112.107.134/medkernel/actuator/health` 返回 HTTP 200 /
  `{"status":"UP","groups":["liveness","readiness"]}`，响应时间头 `Date=Sat, 04 Jul 2026 02:22:54 GMT`，
  `X-Trace-Id=320c115e-247d-4ec2-a0a9-8a5591ce6cce`。134 映射仍按当前已核定事实保持：
  后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、
  前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`，不要把本地 `609d49f3` 误写为已上线。
- 后续继续长目标：不再开子代理，不中途咨询；继续广度优先核查真实前台、职责旅程、菜单分布、构建门禁、
  134 证据映射和最终远程收口条件。

## 最新阶段交接（2026-07-04 全视角真实前台体验优化第七十批·知识关系前台入口口径收敛）

- 本批继续按全局菜单、医疗产品体验和全角色真实前台复核。结论：第四十四批全局菜单命名与顺序仍成立；
  `临床路径库` 当前生产页面、路由和功能目录均未再使用 `临床路径模板`，不会被误解为引用模板；仅
  `PathwayTemplates.test.tsx` 保留反向断言防止旧“临床路径模型/基础模板”回流。新发现的真实体验不一致在
  `/advanced/graph`：菜单、路由和功能目录均为 `知识关系`，但页面首屏仍显示 `图谱查询` 与
  `关系库权威源的可重建投影`，会让临床专家和医疗引擎运营员误以为进入技术查询工具。按核心“客户面业务任务优先、
  图数据库仅投影”的边界，本批将首屏入口统一为 `知识关系`，并把“投影”保留在同步、重建和证据边界中。
- 已本地提交 `050278b0c4a77576293462224ca1eb39e808d8d2`
  （`fix: 收敛知识关系前台入口口径`）：
  - `frontend/src/pages/advanced/GraphExplore.tsx` 将加载、无权限、错误和正常态 PageShell 统一为
    `知识关系`，说明改为“查看知识之间的来源、适应证、禁忌和相互作用关系”；筛选入口由 `投影目标`
    收敛为 `关系范围`，空态、分页摘要和部分成功说明改用知识关系/关系证据语言。
  - `frontend/src/pages/advanced/ProjectionGraphCanvas.tsx` 将画布可访问名称、空态和缩放按钮从图谱/投影关系口径
    收敛为 `知识关系图`、`知识关系`、`缩小关系图` / `放大关系图`。
  - `frontend/src/shared/config/routes.ts` 将 `/advanced/graph` 的职责边界从 `图谱关系` / `图谱投影`
    收敛为 `知识关系` / `知识关系投影`，仍保留图投影不可用时诚实降级的产品边界。
  - `GraphExplore.test.tsx`、`pages.smoke.test.tsx` 和 `routes.test.ts` 同步锁定新口径，防止旧
    `图谱查询`、`投影目标`、`投影关系图` 回流。
  - 未改变菜单顺序、路由路径、权限、后端 API、数据库、目录生成脚本或 134 发布配置。
- 本地验证：
  - 红绿核验：`npm --prefix frontend test -- GraphExplore.test.tsx -t "renders real projection facts"`
    在旧页面下先失败，明确显示首屏 heading 仍是 `图谱查询`；页面与画布修正后通过。
  - 红绿核验：`npm --prefix frontend test -- routes.test.ts -t "为上线配置与知识建模入口登记全视角职责边界"`
    在旧路由体验下先失败，差异明确显示仍使用 `图谱关系`、`图谱投影`、`图谱不可用`；配置修正后通过。
  - Smoke 回归：`npm --prefix frontend test -- pages.smoke.test.tsx -t "renders the knowledge graph page"`
    同步为新首屏标题和说明后通过。
  - 关联配置回归：`npm --prefix frontend test -- GraphExplore.test.tsx routes.test.ts menu.test.ts productCatalog.test.ts`
    通过，`4` 个测试文件 / `70` 项。
  - 生成一致性：`node scripts/audit/export-product-capabilities.mjs --check` 通过。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `952` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `GraphExplore-C-qUbX4H.js`、`KnowledgeGovernance-Duusw07M.js`、
    `PathwayTemplates-BXuRio-2.js`、`index-BSfJfzSB.js` 等前端产物。
  - `npm --prefix frontend run format:check` 通过；`git diff --check`、应用提交前 `git diff --cached --check` 均通过。
  - `bash scripts/check-comment-zh.sh --mode=full` 通过；`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 readiness 使用
  `https://193.112.107.134/medkernel/actuator/health` 返回 HTTP 200 /
  `{"status":"UP","groups":["liveness","readiness"]}`，响应时间头 `Date=Sat, 04 Jul 2026 02:14:12 GMT`，
  `X-Trace-Id=07ad4989-200e-4e0c-b8d9-0cb7ea15c1ba`；公网首页 HTTP 200，
  `Date=Sat, 04 Jul 2026 02:14:12 GMT`，`Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，`Content-Length=832`。
  134 映射仍按当前已核定事实保持：后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、
  前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`，不要把本地 `050278b0` 误写为已上线。
- 后续继续长目标：不再开子代理，不中途咨询；继续广度优先核查真实前台、职责旅程、菜单分布、构建门禁、
  134 证据映射和最终远程收口条件。

## 最新阶段交接（2026-07-04 全视角真实前台体验优化第六十九批·职责旅程随访协同菜单快照校准）

- 本批继续按全局菜单、医疗产品职责和全角色真实前台复核。结论：第四十四批全局菜单命名与顺序仍成立；
  `诊断知识库`、`临床路径库`、`随访模板`、`医疗引擎运营员` 等当前口径不需要再改名。新发现的事实缺口在
  `docs/audit/product-role-journeys.md`：医疗引擎运营员的完整菜单快照漏掉 `clinical-followup`。后端
  `DefaultPermissionPolicyTest` 已把该入口授予医疗引擎运营员，因为随访模板发布、影响范围确认和版本治理属于引擎运营职责；
  临床使用者继续通过同一入口处理患者随访任务。该缺口会误导职责菜单评审，需同步文档并加测试防回归。
- 已本地提交 `14a62a587c58517482b891cd71f07249d045d677`
  （`fix: 校准职责旅程随访协同菜单快照`）：
  - `docs/audit/product-role-journeys.md` 将医疗引擎运营员菜单快照补齐 `clinical-followup`，并保持后端权限目录顺序。
  - `frontend/src/shared/config/productRoleJourneys.test.ts` 新增后端默认权限快照解析与职责旅程文档快照解析，
    要求四职责完整菜单快照与 `DefaultPermissionPolicyTest` 同步。
  - 未改变前台菜单名称、菜单顺序、路由、后端权限策略、API、数据库或 134 发布配置；本批只校准职责旅程事实和防回归门禁。
- 本地验证：
  - 红绿核验：`npm --prefix frontend test -- productRoleJourneys.test.ts -t "keeps the complete menu snapshots"`
    在旧文档下先失败，差异明确显示医疗引擎运营员缺少 `clinical-followup`；补齐文档并修正测试辅助类型后通过。
  - 关联配置回归：`npm --prefix frontend test -- productRoleJourneys.test.ts productCatalog.test.ts menu.test.ts routes.test.ts`
    通过，`4` 个测试文件 / `75` 项。
  - 权限目录定点回归：`mvn -q -Dtest=DefaultPermissionPolicyTest,MenuPermissionCatalogTest test`
    在 `medkernel-backend` 目录下通过；根目录 `-pl medkernel-backend` 不适用当前 Maven reactor，已改用模块目录执行。
  - 生成一致性：`node scripts/audit/export-product-capabilities.mjs --check` 通过。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `952` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `KnowledgeGovernance-Ce9gV4kY.js`、`KnowledgeProduction-leGk0UQ2.js`、
    `Followup-BOT-XlB9.js`、`index-C5ETtOXr.js` 等前端产物。
  - `npm --prefix frontend run format:check` 通过；`git diff --check`、应用提交前 `git diff --cached --check` 均通过。
  - `bash scripts/check-comment-zh.sh --mode=full` 通过；`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 readiness 使用
  `https://193.112.107.134/medkernel/actuator/health` 返回 HTTP 200 /
  `{"status":"UP","groups":["liveness","readiness"]}`，响应时间头 `Date=Sat, 04 Jul 2026 02:01:21 GMT`，
  `X-Trace-Id=457cad2a-9f25-445b-add4-c05ff230d44c`；公网首页 HTTP 200，
  `Date=Sat, 04 Jul 2026 02:01:21 GMT`，`Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，`Content-Length=832`。
  134 映射仍按当前已核定事实保持：后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、
  前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`，不要把本地 `14a62a58` 误写为已上线。
- 后续继续长目标：不再开子代理，不中途咨询；继续广度优先核查真实前台、职责旅程、菜单分布、构建门禁、
  134 证据映射和最终远程收口条件。

## 最新阶段交接（2026-07-04 全视角真实前台体验优化第六十八批·产品目录知识生产业务域校准）

- 本批继续按全局菜单、权威功能目录和职责旅程复核。结论：第四十四批全局菜单命名与顺序仍成立；
  `routeSections`、`menu.test.ts`、`routes.test.ts` 和职责旅程已经明确主导航是 8 个业务域，其中“知识生产”是独立域。
  新发现的事实缺口在生成的 `product-function-catalog.md`：库存结论仍写成 7 个目标客户业务域，漏掉“知识生产”，
  虽然下方路由和后端菜单行已归属到知识生产。该缺口会误导后续菜单评审和产品范围核查，必须由生成器而不是手改文档修正。
- 已本地提交 `9b4c4daab336c5a890fe5b7f0928a4035ff2fac2`
  （`fix: 校准产品目录知识生产业务域`）：
  - `scripts/audit/export-product-capabilities.mjs` 新增 `extractRouteSections()`，从 `routes.ts` 的主导航分组读取中文域名，
    `renderCatalog()` 不再硬编码目标客户业务域列表。
  - `frontend/src/shared/config/productCatalog.test.ts` 新增库存结论域名断言，要求产品目录中的目标客户业务域与 `menuSections`
    完全同序一致。
  - 重新生成 `docs/audit/product-function-catalog.md`，库存结论现为“工作台、机构与人员、知识治理、知识生产、临床协同、
    质量管理、合规安全、系统运维”。
  - 未改变前台菜单顺序、权限、路由、后端 API、数据库或 134 发布配置，只校准权威目录事实与生成器防回归。
- 本地验证：
  - 红绿核验：`npm --prefix frontend test -- productCatalog.test.ts -t "summarizes every primary sidebar domain"`
    在旧实现下先失败，收到 7 个域且缺少“知识生产”；实现与重新生成目录后通过。
  - 关联配置回归：`npm --prefix frontend test -- productCatalog.test.ts menu.test.ts routes.test.ts` 通过，
    `3` 个测试文件 / `66` 项。
  - 生成一致性：`node scripts/audit/export-product-capabilities.mjs --check` 通过；目录 diff 只改变库存结论中的“知识生产”域。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `951` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `KnowledgeGovernance-Ce9gV4kY.js`、`KnowledgeProduction-leGk0UQ2.js`、
    `index-C5ETtOXr.js` 等前端产物。
  - `npm --prefix frontend run format:check` 通过；`git diff --check`、应用提交前 `git diff --cached --check` 均通过
    （Git 对触碰的脚本文件提示下次按仓库规则转 LF，实际 diff 未膨胀）。
  - `bash scripts/check-comment-zh.sh --mode=full` 通过；`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 readiness 使用
  `https://193.112.107.134/medkernel/actuator/health` 返回 HTTP 200 /
  `{"status":"UP","groups":["liveness","readiness"]}`，响应时间头 `Date=Sat, 04 Jul 2026 01:53:14 GMT`，
  `X-Trace-Id=7b25d62e-b415-482c-a0bc-a89801e2d014`；公网首页 HTTP 200，
  `Date=Sat, 04 Jul 2026 01:53:13 GMT`，`Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，`Content-Length=832`。
  134 映射仍按当前已核定事实保持：后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、
  前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`，不要把本地 `9b4c4daa` 误写为已上线。
- 后续继续长目标：不再开子代理，不中途咨询；继续广度优先核查真实前台、职责旅程、菜单分布、构建门禁、
  134 证据映射和最终远程收口条件。

## 最新阶段交接（2026-07-04 全视角真实前台体验优化第六十七批·知识生产候选分流前台口径收敛）

- 本批继续按医疗产品体验、全角色真实前台、菜单分布和职责旅程复核。结论：第四十四批全局菜单命名与顺序仍成立；
  `诊断知识库`、`临床路径库`、`随访模板`、`医疗引擎运营员` 等名称与角色口径继续符合权威功能目录和职责旅程。
  新发现的真实体验缺口在知识生产：页面和产品目录仍把前台任务称为“八类状态分流 / 八类状态队列”。“八类状态”是底层候选治理事实，
  但对平台知识生产、平台治理、医疗引擎运营员和院内知识维护人员而言，默认菜单与任务区应读到“候选分流”，
  原始八类分类只应留在实现、数据和证据追溯语义里。
- 已本地提交 `01b6c6270e8b1b2b9138e80c2bd60ce321a99898`
  （`fix: 收敛知识生产候选分流前台口径`）：
  - `KnowledgeGovernance` 将生产页说明、进度摘要、卡片标题和队列标题统一收敛为“候选分流 / 候选分流队列”，
    表格列从“八类状态”收敛为“分流结果”，错误摘要从“八类状态分流”收敛为“候选分流”。
  - `KnowledgeGovernance.test.tsx` 增加默认层不得出现“八类状态分流 / 八类状态队列”的断言，
    继续保留新资产、冲突仲裁、进入审核等业务状态验证。
  - `scripts/audit/export-product-capabilities.mjs` 与生成的 `docs/audit/product-function-catalog.md` 同步将
    `/knowledge/production` 任务描述中的“八类状态”更新为“候选分流”，避免功能目录和前台口径分叉。
  - 未改变知识生产 API、后端数据契约、候选状态枚举、影子评测或 134 发布配置，只调整默认前台任务命名和产品目录映射。
- 本地验证：
  - 红绿核验：`npm --prefix frontend test -- KnowledgeGovernance.test.tsx -t "renders the knowledge production center|shows agent progress"`
    在旧实现下先失败于找不到“候选分流 / 候选分流队列”，实现后通过；完整
    `npm --prefix frontend test -- KnowledgeGovernance.test.tsx` 通过，`1` 个测试文件 / `37` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `950` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `KnowledgeGovernance-Ce9gV4kY.js`、`KnowledgeProduction-leGk0UQ2.js`、
    `index-C5ETtOXr.js` 等前端产物。
  - `npm --prefix frontend run format:check` 通过；`git diff --check`、应用提交前 `git diff --cached --check` 均通过。
  - `node scripts/audit/export-product-capabilities.mjs --check` 通过；`bash scripts/check-comment-zh.sh --mode=full`
    通过；`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项。
  - 前台旧口径扫描：
    `rg -n "八类状态|8 态分流|8 态队列" frontend/src docs/PRODUCT_SCOPE.md docs/EXPERIENCE_CONTRACT.md docs/audit/product-function-catalog.md docs/audit/product-role-journeys.md scripts/audit/export-product-capabilities.mjs --glob '!**/*.test.ts' --glob '!**/*.test.tsx'`
    无输出；旧词仅保留在测试负断言中，防止回退。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 readiness 使用
  `https://193.112.107.134/medkernel/actuator/health` 返回 HTTP 200 /
  `{"status":"UP","groups":["liveness","readiness"]}`，响应时间头 `Date=Sat, 04 Jul 2026 01:45:16 GMT`，
  `X-Trace-Id=f6fe381f-8f38-4f87-a4ce-86d5a3718afb`；公网首页 HTTP 200，
  `Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，`Content-Length=832`。
  134 映射仍按当前已核定事实保持：后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、
  前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`，不要把本地 `01b6c627` 误写为已上线。
- 后续继续长目标：不再开子代理，不中途咨询；继续广度优先核查真实前台、职责旅程、菜单分布、构建门禁、
  134 证据映射和最终远程收口条件。

## 最新阶段交接（2026-07-04 全视角真实前台体验优化第六十六批·知识生产分流状态前台口径收敛）

- 本批继续按医疗产品体验、全角色真实前台和证据详情契约复核。结论：第四十四批全局菜单命名与顺序仍成立；
  `诊断知识库`、`临床路径库`、`随访模板`、`医疗引擎运营员` 等当前名称与角色口径继续符合权威功能目录和职责旅程。
  新发现的真实体验缺口在知识生产：`KnowledgeGovernance` 的“八类状态队列”默认直接展示 `NEW_ASSET`、`CONFLICT`
  等分流状态、`SUBMIT_REVIEW` 等分流动作，依据中还可能暴露 `content_hash`，影子评测状态也会默认展示 `PASSED`
  等低频枚举。对平台知识生产、平台治理、医疗引擎运营员和院内知识维护人员而言，默认应读到“全新资产 / 冲突仲裁 /
  进入审核 / 通过 / 内容摘要”等业务语言；原始枚举只应在证据详情中服务追溯。
- 已本地提交 `08e3cc4d7cc6801ca850f543c8ce3c475a5f4c7c`
  （`fix: 收敛知识生产分流状态前台口径`）：
  - `KnowledgeGovernance` 新增分流状态、分流动作、分流依据和生产状态前台展示函数；默认隐藏八类分流原始状态、
    原始动作枚举、`content_hash` 和影子评测原始状态。
  - 证据详情开启后展示“冲突仲裁 · CONFLICT”“进入审核 · SUBMIT_REVIEW”等业务标签加原始枚举的可追溯组合；
    默认生产中心仍保持一页一目标、技术对象收进证据详情的体验层级。
  - 未改变知识生产任务、候选分流、共存替换、影子评测或后端数据契约，只调整默认前台状态语言。
- 本地验证：
  - 红绿核验：`npm --prefix frontend test -- KnowledgeGovernance.test.tsx -t "shows agent progress"`
    在旧实现下先失败于默认仍展示 `NEW_ASSET`；实现后通过。随后修正既有证据详情断言，完整
    `npm --prefix frontend test -- KnowledgeGovernance.test.tsx` 通过，`1` 个测试文件 / `37` 项。
  - 格式与空白：`npm --prefix frontend run format:check` 通过；`git diff --check`、应用提交前
    `git diff --cached --check` 均通过。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `950` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `KnowledgeGovernance-MnUZ69J3.js`、`KnowledgeProduction-BkxEMPxg.js`、
    `index-Ca4epHuC.js` 等前端产物。
  - `node scripts/audit/export-product-capabilities.mjs --check` 通过；`bash scripts/check-comment-zh.sh --mode=full`
    通过；`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项。
  - 知识生产分流裸露扫描：
    `rg -n "NEW_ASSET|DUPLICATE|MINOR_REVISION|MAJOR_UPGRADE|CONFLICT|DOWNGRADE|DEPRECATION|UNCERTAIN|SUBMIT_REVIEW|SKIP_DUPLICATE|MERGE_REVIEW|UPGRADE_REVIEW|CONFLICT_REVIEW|DOWNGRADE_REVIEW|RETIREMENT_REVIEW|MANUAL_REVIEW|content_hash|>PASSED<|>RUNNING<|>FAILED<" frontend/src/pages/quality/KnowledgeGovernance.tsx`
    仅命中映射常量、条件判断和证据转换函数，未发现默认层直接裸露这些低频枚举。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 readiness 使用
  `https://193.112.107.134/medkernel/actuator/health` 返回 HTTP 200 /
  `{"status":"UP","groups":["liveness","readiness"]}`，响应时间头 `Date=Sat, 04 Jul 2026 00:25:54 GMT`，
  `X-Trace-Id=a3b90d35-39b2-499e-ba96-c8b231a724f1`；公网首页 HTTP 200，
  `Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，`Content-Length=832`。
  134 映射仍按当前已核定事实保持：后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、
  前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`，不要把本地 `08e3cc4d` 误写为已上线。
- 后续继续长目标：不再开子代理，不中途咨询；继续广度优先核查真实前台、职责旅程、菜单分布、构建门禁、
  134 证据映射和最终远程收口条件。

## 最新阶段交接（2026-07-04 全视角真实前台体验优化第六十五批·系统接入区域来源状态前台口径收敛）

- 本批继续按医疗产品体验、全角色真实前台和证据详情契约复核。结论：第四十四批全局菜单命名与顺序仍成立；
  `诊断知识库`、`临床路径库`、`随访模板`、`医疗引擎运营员` 等当前名称与角色口径继续符合权威功能目录和职责旅程。
  新发现的真实体验缺口在系统接入：`AdapterHub` 的“区域来源”状态列默认直接展示 `ACTIVE` 等低频技术枚举。
  对医疗引擎运营员、实施人员、平台治理人员和院内管理员而言，默认应读到“启用中 / 已挂起”等业务状态；原始枚举只应在
  证据详情中服务追溯。
- 已本地提交 `8b7a01c3dee997b65011f731e98bb168e9db5c5d`
  （`fix: 收敛区域来源状态前台口径`）：
  - `AdapterHub` 新增区域来源状态前台展示函数，默认把 `ACTIVE` 收敛为“启用中”、`SUSPENDED` 收敛为“已挂起”，
    其余状态沿用客户语言枚举转换。
  - 证据详情开启后展示“启用中（ACTIVE）”这类业务标签加原始枚举的可追溯组合；默认区域来源、适配器和接入申请摘要仍保持
    一页一目标、技术对象收进证据详情的体验层级。
  - 未改变区域来源登记、适配器绑定、接入申请、健康检查、质量报告或后端数据契约，只调整默认前台状态语言。
- 本地验证：
  - 红绿核验：`npm --prefix frontend test -- AdapterHub.test.tsx -t "默认展示业务接入摘要"`
    在旧实现下先失败于默认仍展示 `ACTIVE`；实现后通过。
  - 页面回归：`npm --prefix frontend test -- AdapterHub.test.tsx` 通过，`1` 个测试文件 / `22` 项。
  - 格式与空白：`npm --prefix frontend run format:check` 通过；`git diff --check`、应用提交前
    `git diff --cached --check` 均通过。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `950` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `AdapterHub-1KxAOZKT.js`、`KnowledgeGovernance-D6vUceXx.js`、
    `index-DDCA04c2.js` 等前端产物。
  - `node scripts/audit/export-product-capabilities.mjs --check` 通过；`bash scripts/check-comment-zh.sh --mode=full`
    通过；`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项。
  - 区域来源状态裸露扫描：
    `rg -n "\\{value\\}</Tag>|>ACTIVE<|>SUSPENDED<|ACTIVE\\)" frontend/src/pages/tenant/AdapterHub.tsx`
    仅命中协议列的标准协议展示（REST/FHIR 等），未发现区域来源状态默认裸露 `ACTIVE` / `SUSPENDED`。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 readiness 使用
  `https://193.112.107.134/medkernel/actuator/health` 返回 HTTP 200 /
  `{"status":"UP","groups":["liveness","readiness"]}`，响应时间头 `Date=Sat, 04 Jul 2026 00:12:47 GMT`，
  `X-Trace-Id=6b8a508a-8507-4a0f-bd16-e1f3d451cf7c`；公网首页 HTTP 200，
  `Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，`Content-Length=832`。
  134 映射仍按当前已核定事实保持：后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、
  前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`，不要把本地 `8b7a01c3` 误写为已上线。
- 后续继续长目标：不再开子代理，不中途咨询；继续广度优先核查真实前台、职责旅程、菜单分布、构建门禁、
  134 证据映射和最终远程收口条件。

## 最新阶段交接（2026-07-04 全视角真实前台体验优化第六十四批·沙盘机构生效版本前台口径收敛）

- 本批继续按医疗产品体验、全角色真实前台和证据详情契约复核。结论：第四十四批全局菜单命名与顺序仍成立；
  `诊断知识库`、`临床路径库`、`随访模板` 等当前名称继续符合功能语义。新发现的真实体验缺口在全真体验沙盘：
  当前机构运行摘要、运行结果、历史重放和版本对比默认可能展示 `runtime-platform-1`、`runtime-sandbox-1`、
  `runtime-current-2`、`sha256:old-7` 等低频运行发布引用。对临床医生、质控人员、医疗引擎运营员和实施人员而言，
  默认应读到“当前机构生效版本 · 第 N 版”或清晰来源加版本；原始运行发布引用只应在证据详情中服务追溯。
- 已本地提交 `d8c376de2f700078f3b73877049942dbec3ecaba`
  （`fix: 收敛沙盘机构生效版本前台口径`）：
  - `SandboxHost` 新增机构生效版本展示函数，默认用来源与版本号呈现运行版本，证据详情开启后再组合展示
    `runtime-sandbox-1 · 第 7 版`、`sha256:old-7 · 第 4 版` 等原始追溯引用。
  - 当前机构运行摘要默认从 `第 9 版 · runtime-platform-1` 收敛为“当前机构生效版本 · 第 9 版”；
    沙盘运行结果、不可变历史重放和历史/当前对比同样默认隐藏运行发布引用。
  - 未改变沙盘运行、历史重放、版本对比、运行结果或后端运行版本契约，只调整默认前台语言层级。
- 本地验证：
  - 红绿核验：
    `npm --prefix frontend test -- SandboxHost.test.tsx -t "runs the selected scenario|current institution effective version|immutable historical|compares historical"`
    在旧实现下先失败于找不到“当前机构生效版本 · 第 7 版 / 第 9 版”且仍展示原始运行发布引用；实现后通过。
  - 页面回归：`npm --prefix frontend test -- SandboxHost.test.tsx` 通过，`1` 个测试文件 / `11` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `950` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `SandboxHost-CgTRFBF7.js`、`KnowledgeGovernance-C6CJIs_z.js`、
    `index-C_Ts6tef.js` 等前端产物。
  - `node scripts/audit/export-product-capabilities.mjs --check` 通过；`bash scripts/check-comment-zh.sh --mode=full`
    通过；`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项；`git diff --check`、应用提交前 `git diff --cached --check` 均通过。
  - 运行发布引用裸露扫描：
    `rg -n "runtimeReleaseId|runtimeReleaseRef|runtime-[A-Za-z0-9_-]+|sha256:old|第 [0-9?]+ 版 · runtime|runtime.*第" frontend/src/pages/sandbox frontend/src/features/sandbox --glob '!**/*.test.ts' --glob '!**/*.test.tsx'`
    仅命中沙盘状态字面量 `runtime-check` 与 `SandboxHost` 中作为证据详情输入的 `runtimeReleaseId` /
    `runtimeReleaseRef`，未发现默认前台展示模式。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 readiness 使用
  `https://193.112.107.134/medkernel/actuator/health` 返回 HTTP 200 /
  `{"status":"UP","groups":["liveness","readiness"]}`，响应时间头 `Date=Sat, 04 Jul 2026 00:03:07 GMT`，
  `X-Trace-Id=2a2aef02-9c4c-42f4-8461-787f322edd31`；公网首页 HTTP 200，
  `Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，`Content-Length=832`。
  134 映射仍按当前已核定事实保持：后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、
  前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`，不要把本地 `d8c376de` 误写为已上线。
- 后续继续长目标：不再开子代理，不中途咨询；继续广度优先核查真实前台、职责旅程、菜单分布、构建门禁、
  134 证据映射和最终远程收口条件。

## 最新阶段交接（2026-07-04 全视角真实前台体验优化第六十三批·知识生产校验状态前台口径收敛）

- 本批继续按医疗产品体验、全角色真实前台和证据详情契约复核。结论：第四十四批全局菜单命名与顺序仍成立；
  `诊断知识库`、`临床路径库`、`随访模板` 等当前名称继续符合功能语义。新发现的真实体验缺口在知识生产工作台：
  生产安全校验结果默认可能直出 `SOURCE_ANCHOR` 等低频校验编码，缺省项使用“未返回门禁”，并在八类状态分流区域展示
  “8 态分流 / 8 态队列”。对平台知识生产人员、医疗引擎运营员和实施人员而言，默认应读到来源锚点、影子评测、
  八类状态等医疗治理语言；原始校验编码只应在证据详情中服务追溯。
- 已本地提交 `d3b64d1b3b42cc7e93586856e1dbf12b1b7245ec`
  （`fix: 收敛知识生产校验状态前台口径`）：
  - `KnowledgeGovernance` 新增生产安全校验业务标签映射，默认把 `SOURCE_ANCHOR`、`SHADOW_READY`、
    `PUBLICATION_QUALITY_RECORD` 等编码收敛为“来源锚点 / 影子评测 / 发布质量记录”等前台语言；证据详情开启后展示
    `来源锚点 · SOURCE_ANCHOR` 这类可追溯组合。
  - “未返回门禁”收敛为“未返回校验项”；“8 态分流 / 8 态队列 / 8 态”统一收敛为“八类状态分流 / 八类状态队列 /
    八类状态”，避免客户前台读取内部简称。
  - `customerLanguageGate` 增加 `8\s*态` 禁止项，阻断后续客户可见字符串回流；未改变后端生产任务、校验结果、
    分流状态或影子评测契约。
- 本地验证：
  - 红绿核验：`npm --prefix frontend test -- KnowledgeGovernance.test.tsx -t "production center|agent progress"`
    在旧实现下先失败于找不到“八类状态分流 / 八类状态队列”且仍展示 `8 态分流`；实现后通过。
  - 相关回归：`npm --prefix frontend test -- KnowledgeGovernance.test.tsx customerLanguageGate.test.ts`
    通过，`2` 个测试文件 / `42` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `950` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `KnowledgeGovernance-B8kpGqno.js`、`KnowledgeProduction-BnXNCY3I.js`、
    `InstitutionKnowledge-D7J2rJgU.js`、`index-EU-4ROwL.js` 等前端产物。
  - `node scripts/audit/export-product-capabilities.mjs --check` 通过；`bash scripts/check-comment-zh.sh --mode=full`
    通过；`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项；`git diff --check`、应用提交前 `git diff --cached --check` 均通过。
  - 旧口径裸露扫描：
    `rg -n "8\\s*态|未返回门禁|门禁" frontend/src/pages frontend/src/features frontend/src/shared frontend/src/widgets --glob '!**/*.test.ts' --glob '!**/*.test.tsx' --glob '!**/*.d.ts'`
    仅命中 `frontend/src/shared/config/customerLabels.ts` 中把“门禁”翻译为“校验”的替换表，不是默认前台露出；
    `rg -n "SOURCE_ANCHOR|SHADOW_READY|SOURCE_PRESENT|PUBLICATION_QUALITY_RECORD" ...` 的生产命中仅为业务标签映射。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 readiness 使用
  `https://193.112.107.134/medkernel/actuator/health` 返回 HTTP 200 /
  `{"status":"UP","groups":["liveness","readiness"]}`，响应时间头 `Date=Fri, 03 Jul 2026 23:51:56 GMT`；
  公网首页 HTTP 200，`Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，`Content-Length=832`。
  134 映射仍按当前已核定事实保持：后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、
  前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`，不要把本地 `d3b64d1b` 误写为已上线。
- 后续继续长目标：不再开子代理，不中途咨询；继续广度优先核查真实前台、职责旅程、菜单分布、构建门禁、134 证据映射和最终远程收口条件。

## 最新阶段交接（2026-07-04 全视角真实前台体验优化第六十二批·质量域组织范围前台口径收敛）

- 本批继续按医疗产品体验、全角色真实前台和证据详情契约复核。结论：第四十四批全局菜单命名与顺序仍成立；
  `诊断知识库`、`临床路径库`、`随访模板` 等当前名称继续符合功能语义。新发现的真实体验缺口在质量域组织范围：
  `QcDashboard` 与 `InsuranceAudit` 在只具备 `tenantId` 服务机构范围、且组织目录未返回可读名称时，默认把范围显示为“当前租户”。
  按体验契约中客户前台使用“服务机构 / 机构”语言的要求，质控人员和医保审核人员默认应读到“当前服务机构”；
  原始 `tenantId` 只在证据详情中服务追溯。
- 已本地提交 `1c87961e779ecaf125bc49c23670e6c1589ffffa`
  （`fix: 收敛质量域组织范围前台口径`）：
  - `QcDashboard` 的质控热力图和主动提醒组织范围兜底从“当前租户”收敛为“当前服务机构”，证据详情开启后仍显示
    `当前服务机构 · tenant-rehearsal` 这类可追溯组合。
  - `InsuranceAudit` 的范围筛选默认项和医保问题列表组织范围兜底同样收敛为“当前服务机构”，避免医保审核人员在主链路读到租户技术口径。
  - 未改变后端 `dataScope.tenantId` 契约、查询参数、分页或审核链路，只调整默认前台语言层级。
- 本地验证：
  - 红绿核验：`npm --prefix frontend test -- QcDashboard.test.tsx InsuranceAudit.test.tsx -t "仅有服务机构范围"`
    在旧实现下先失败于找不到“当前服务机构”且仍展示“当前租户”；实现后通过，`2` 项。
  - 相关回归：
    `npm --prefix frontend test -- QcDashboard.test.tsx InsuranceAudit.test.tsx QcAlerts.test.tsx QcEvalResults.test.tsx QcEvalSets.test.tsx`
    通过，`5` 个测试文件 / `38` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `950` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `QcDashboard-C_OEa8u_.js`、`InsuranceAudit-D5R-FNg-.js`、
    `Quality-CyanocAS.css`、`index-Dd6dzlHu.js` 等前端产物。
  - `node scripts/audit/export-product-capabilities.mjs --check` 通过；`bash scripts/check-comment-zh.sh --mode=full`
    通过；`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项；`git diff --check`、应用提交前 `git diff --cached --check` 均通过。
  - 旧口径裸露扫描：
    `rg -n "当前租户" frontend/src/pages frontend/src/widgets frontend/src/shared/config --glob '!**/*.test.ts' --glob '!**/*.test.tsx'`
    无生产前台命中。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 readiness 使用
  `https://193.112.107.134/medkernel/actuator/health` 返回 HTTP 200 /
  `{"status":"UP","groups":["liveness","readiness"]}`，响应时间头 `Date=Fri, 03 Jul 2026 23:41:04 GMT`；
  公网首页 HTTP 200，`Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，`Content-Length=832`。
  134 映射仍按当前已核定事实保持：后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、
  前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`，不要把本地 `1c87961e` 误写为已上线。
- 后续继续长目标：不再开子代理，不中途咨询；继续广度优先核查真实前台、职责旅程、菜单分布、构建门禁、134 证据映射和最终远程收口条件。

## 最新阶段交接（2026-07-03 全视角真实前台体验优化第六十一批·质控指标影响范围前台口径收敛）

- 本批继续按医疗产品体验、全角色真实前台和证据详情契约复核。结论：第四十四批全局菜单命名与顺序仍成立；
  `诊断知识库`、`临床路径库`、`随访模板` 等当前名称继续符合功能语义。新发现的真实体验缺口在质控评估指标库：
  默认指标台账、七步影响预览、详情抽屉和新建表单仍可能直出 `responsibleDepartmentId`、`DISCHARGE+24H`、
  `p5-hospital` 等低频契约值。对质控人员、医疗引擎运营员和实施人员而言，默认应读到责任科室名称、临床时间窗和机构范围；
  原始契约值只在证据详情中服务追溯。
- 已本地提交 `8652321c49d23f7a160699e9445293811080c9bc`
  （`fix: 收敛质控指标影响范围前台口径`）：
  - 新增 `qualityEvaluationCatalog` 作为质控评价时间窗和组织范围的共享业务选项目录，避免在业务页内联硬编码选项数组。
  - `QcEvalSets` 默认用组织目录把责任科室身份显示为科室名称；时间窗显示为“出院后 24 小时”等临床语言；
    组织范围显示为“当前医院 / 全院 / 当前服务机构”等业务语言，无法识别的低频契约值默认降级为“已配置”。
  - 新建指标表单把时间窗口和组织范围改为业务选项；提交给后端的 `timeWindow`、`organizationScope` 契约值保持稳定，
    不改变真实评估指标创建、发布和快照试运行链路。
  - 证据详情开启后仍可追溯原始指标身份、时间窗、组织范围和 trace 字段。
- 本地验证：
  - 红绿核验：`npm --prefix frontend test -- QcEvalSets.test.tsx -t "默认用业务语言展示责任科室"`
    在旧实现下先失败于找不到“骨科”；实现后通过。
  - 补充约束：`npm --prefix frontend test -- QcEvalSets.test.tsx -t "默认用业务语言展示责任科室|creates a draft indicator"`
    通过，`2` 项，覆盖默认详情展示与新建表单业务选项，同时确认 payload 仍提交稳定契约值。
  - 页面回归：`npm --prefix frontend test -- QcEvalSets.test.tsx` 通过，`8` 项；
    `npm --prefix frontend test -- QcEvalSets.test.tsx QcEvalResults.test.tsx QcAlerts.test.tsx QcDashboard.test.tsx`
    通过，`4` 个测试文件 / `28` 项。
  - 完整前端门禁：首次 `npm --prefix frontend run verify` 因页面内 `*_OPTIONS` 数组触发
    `medkernel/no-page-mock`，已改为共享配置目录后重跑；最终通过，`114` 个测试文件 / `948` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `QcEvalSets-Bsmy26hD.js`、`Quality-CyanocAS.css`、
    `index-BR_r-Tgd.js` 等前端产物。
  - `node scripts/audit/export-product-capabilities.mjs --check` 通过；`bash scripts/check-comment-zh.sh --mode=full`
    通过；`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项；`git diff --check`、应用提交前 `git diff --cached --check` 均通过。
  - 裸露扫描：
    `rg -n 'dept-ortho|DISCHARGE\+24H|p5-hospital|responsibleDepartmentId\}|responsibleDepartmentId</|responsibleDepartmentId\s*·|timeWindow\}|organizationScope\}' frontend/src/pages/quality --glob '!**/*.test.ts' --glob '!**/*.test.tsx'`
    剩余命中均为共享选项目录/表单提交初值或 `QcEvalResults`、`QcAlerts` 的幂等键函数，不是默认可见前台文案。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 readiness 使用
  `https://193.112.107.134/medkernel/actuator/health` 返回 HTTP 200 /
  `{"status":"UP","groups":["liveness","readiness"]}`，响应时间头 `Date=Fri, 03 Jul 2026 15:29:00 GMT`；
  公网首页 HTTP 200，`Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，`Content-Length=832`。
  134 映射仍按当前已核定事实保持：后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、
  前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`，不要把本地 `8652321c` 误写为已上线。
- 后续继续长目标：不再开子代理，不中途咨询；继续广度优先核查真实前台、职责旅程、菜单分布、构建门禁、134 证据映射和最终远程收口条件。

## 最新阶段交接（2026-07-03 全视角真实前台体验优化第六十批·导出与批量任务编号层级收敛）

- 本批继续按医疗产品体验、全角色真实前台和证据详情契约复核。结论：菜单命名与顺序仍沿第四十四批保持；
  `诊断知识库`、`临床路径库`、`随访模板` 等当前名称仍符合各自功能语义。新发现的真实体验缺口在审计导出、术语导出和资产批量处理：
  共用 `AsyncExportAction` 默认直接展示导出 `jobId`，`AuthoringBatchDrawer` 默认成功提示、结果标题和任务记录列直接展示批量任务 `jobId`。
  对审计员、实施人员、医疗引擎运营员而言，任务编号属于低频证据，不应成为默认主链路；但在证据详情开启时应保留可追溯编号。
- 已本地提交 `4de1116aa2b4cf13d5a22b3ce8b6c83701232fdb`
  （`fix: 收敛导出与批量任务编号层级`）：
  - `AsyncExportAction` 默认展示“导出任务已登记”，只在当前视图 `evidenceDetailsEnabled=true` 时展示“导出任务编号：...”，
    仍使用真实 `jobId` 轮询和下载，不改变后端契约。
  - `AuthoringBatchDrawer` 新增 `evidenceDetailsEnabled` 入参；批量任务成功提示、结果标题和任务记录列默认展示“批量任务已登记 / 批量任务执行结束”，
    证据详情开启后展示“批量任务编号：...”。
  - `AuthoringAssets` 将当前页面证据详情状态传入批量处理抽屉，保持资产库、批量任务和字段配置的证据层级一致。
- 本地验证：
  - 红绿核验：`npm --prefix frontend test -- AsyncExportAction.test.tsx -t "job id|evidence details"` 在旧实现下先失败于默认仍显示
    `job-1` 且证据详情未显示“导出任务编号：job-evidence”；实现后通过，`2` 项。
  - 红绿核验：`npm --prefix frontend test -- AuthoringBatchDrawer.test.tsx -t "job identifiers|generates one rule"` 在旧实现下先失败于默认仍显示
    `abj-generate / abj-record`；实现后通过，`2` 项。
  - 相关回归：`npm --prefix frontend test -- AsyncExportAction.test.tsx` 通过，`6` 项；
    `npm --prefix frontend test -- AuthoringBatchDrawer.test.tsx` 通过，`6` 项；
    `npm --prefix frontend test -- AsyncExportAction.test.tsx AuthoringBatchDrawer.test.tsx TerminologyMapping.test.tsx AdminAudit.test.tsx`
    通过，`38` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `947` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `AuthoringAssets-DlalhOgX.js`、`TerminologyMapping-tJuz-86E.js`、
    `AdminAudit-C-PkQbq6.js`、`KnowledgeGovernance-ByZ2ykkJ.js`、`index-DqgAVJAd.js` 等前端产物。
  - `node scripts/audit/export-product-capabilities.mjs --check` 通过；`bash scripts/check-comment-zh.sh --mode=full` 通过；
    `node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项；`git diff --check`、应用提交前 `git diff --cached --check` 均通过。
  - 旧编号裸露扫描：
    `rg -n "<Text>任务编号|任务号|批量任务 [^\n$]*\\$\\{|批量任务 [A-Za-z0-9_-]+|导出任务编号：job|批量任务编号：abj" frontend/src/shared frontend/src/pages --glob '!**/*.test.ts' --glob '!**/*.test.tsx'`
    无命中。剩余知识生产 `生产任务编号` 仅在证据详情或详情展开中展示，符合当前证据层级。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 readiness 使用
  `https://193.112.107.134/medkernel/actuator/health` 返回 HTTP 200 /
  `{"status":"UP","groups":["liveness","readiness"]}`，响应时间头 `Date=Fri, 03 Jul 2026 15:04:55 GMT`；
  公网首页 HTTP 200，`Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，`Content-Length=832`。
  134 映射仍按当前已核定事实保持：后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、
  前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`，不要把本地 `4de1116a` 误写为已上线。
- 后续继续长目标：不再开子代理，不中途咨询；继续广度优先核查真实前台、职责旅程、菜单分布、构建门禁、134 证据映射和最终远程收口条件。

## 最新阶段交接（2026-07-03 全视角真实前台体验优化第五十九批·验收与生产目录前台口径收敛）

- 本批继续按全局菜单、医疗产品体验和全角色职责旅程复核。结论：第四十四批菜单命名与顺序仍成立；
  `诊断知识库` 不退回“诊断知识维护”，`临床路径库` 不改成“临床路径模板”，避免把路径版本治理误读为引用模板。
  `Provenance` 的迁移后继身份 ID 已在证据详情开关下展示，不是本批缺口。新发现的真实体验缺口在路由职责视图与生成的
  功能目录：`/workbench/readiness-validation` 与 `/knowledge/production` 的客户任务仍外露
  `Provider`、`readiness`、`job`、`门禁`、`运行版本` 等工程化口径。按 `EXPERIENCE_CONTRACT` 的医院语言要求，
  默认前台和功能目录应表达为“模型服务 / 知识生产准备 / 生产任务 / 安全校验 / 机构生效版本 / 发布校验”。
- 已本地提交 `ae2557ba4b2772934b055bae120a9dc8be12004e`
  （`fix: 收敛验收与生产目录前台口径`）：
  - `customerLanguageGate` 将路由体验的 `responsibility`、`boundary` 纳入前台语言门禁，避免菜单/职责矩阵继续漏检技术口径。
  - `routes` 将资产库边界从“运行版本”改为“机构生效版本”，诊断知识和知识治理从“发布门禁”改为“发布校验”，
    上线准备复核从 `Provider / readiness` 改为“模型服务、备份恢复、知识生产准备和权限阻塞”。
  - `export-product-capabilities` 与 `product-function-catalog` 将知识生产工作台任务从
    `知识生产 readiness、生产 job、门禁、8 态` 收敛为“知识生产准备、生产任务、安全校验、八类状态”。
- 本地验证：
  - 红绿核验：`npm --prefix frontend test -- customerLanguageGate.test.ts productCatalog.test.ts routes.test.ts -t "hospital-facing language|raw technical tokens|剩余真实前台"`
    在旧实现下先失败于 `复核 Provider、备份、知识 readiness 和权限阻塞`、`知识生产 readiness`、`生产 job`、
    `发布门禁`、`运行版本` 等旧口径；实现后通过，`3` 项。
  - 相关回归：`npm --prefix frontend test -- customerLanguageGate.test.ts productCatalog.test.ts routes.test.ts` 通过，`64` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `945` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `ReadinessValidation-DPP-wmFJ.js`、
    `KnowledgeProduction-Bqgr-42r.js`、`PathwayTemplates-Deg1hr9z.js`、`index-Qu5-rLEy.js` 等前端产物。
  - `node scripts/audit/export-product-capabilities.mjs --check` 通过；`bash scripts/check-comment-zh.sh --mode=full` 通过；
    `node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项；`git diff --check`、应用提交前 `git diff --cached --check` 均通过。
  - 旧口径扫描：
    `rg -n "复核 Provider|知识 readiness|生产 job|发布门禁|资产库不直接发布运行版本|门禁、8 态" frontend/src/shared/config/routes.ts docs/audit/product-function-catalog.md scripts/audit/export-product-capabilities.mjs`
    无命中；测试侧保留防回归反向断言，脚本内部正则和客户标签映射不是默认前台展示。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 readiness 使用
  `https://193.112.107.134/medkernel/actuator/health` 返回 HTTP 200 /
  `{"status":"UP","groups":["liveness","readiness"]}`，响应时间头 `Date=Fri, 03 Jul 2026 14:56:37 GMT`；
  公网首页 HTTP 200，`Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，`Content-Length=832`。
  134 映射仍按当前已核定事实保持：后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、
  前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`，不要把本地 `ae2557ba` 误写为已上线。
- 后续继续长目标：不再开子代理，不中途咨询；继续广度优先核查真实前台、职责旅程、菜单分布、构建门禁、134 证据映射和最终远程收口条件。

## 最新阶段交接（2026-07-03 全视角真实前台体验优化第五十八批·临床路径时窗校验口径收敛）

- 本批继续按全局菜单、医疗产品体验和全角色职责旅程复核。结论：第四十四批菜单命名与顺序仍成立；
  `临床路径库` 不改成“临床路径模板”，避免把当前路径版本治理入口误解为单纯引用模板；`医疗引擎运营员` 仍是固定职责角色名。
  新发现的真实体验缺口在 `/pathway/templates` 的时窗建模与后端路径校验：默认表单和错误消息仍可见
  `SLA基准`、`计时 clock`、`clockSla`、`clock 或 timeWindowMinutes` 等内部字段口径。按
  `EXPERIENCE_CONTRACT` 的“路径时窗用时窗校验”和 `CONSTITUTION` §14 客户面隐藏技术对象要求，
  前台与服务错误应表达为“时窗校验 / 计时规则”；内部字段和契约仍保持 `clockSla`、`clock`、`timeWindowMinutes`。
- 已本地提交 `5e406100cfc808a327d42fc4dd668eb7529915b8`
  （`fix: 收敛临床路径时窗校验口径`）：
  - `PathwayTemplates` 将时窗配置表单标签从 `SLA基准` 改为“时窗校验基准”，等待计时配置从 `计时 clock`
    改为“计时规则”，占位示例改为医疗可读的“24 小时后提醒”。
  - `PathwayTemplates` 的路径时窗错误与节点配置摘要从 `SLA / min / target / max` 收敛为“时窗校验分钟 / 最早 / 目标 / 最晚”，
    默认摘要展示“时窗校验已配置”，证据详情展示中文基准事件。
  - `PathwayEngineService` 与 `PathwayProgressor` 面向调用方的等待计时、关键时窗、结构化配置和基准事件错误消息不再外露
    `clockSla`、`targetMinutes`、`clock 或 timeWindowMinutes`；`ClinicalClockEscalationLevel` 中文 Javadoc 同步改为“路径时窗校验展示”。
- 本地验证：
  - 红绿核验：`npm --prefix frontend test -- PathwayTemplates.test.tsx -t "路径建模表单"` 在旧实现下先失败于找不到
    “时窗校验基准”；实现并格式化后通过，`1` 项。
  - 后端红绿核验：在 `medkernel-backend` 目录执行
    `/Users/zhikunzheng/local/apache-maven-3.9.9/bin/mvn -Dtest=PathwayEngineServiceTest#createTemplateRejectsTimedNodeWithoutClinicalClockSla test`
    在旧实现下先失败于错误消息仍为“关键时钟节点 ASSESS 缺少 clockSla”；实现后通过，`1` 项。
  - 前端相关回归：`npm --prefix frontend test -- PathwayTemplates.test.tsx` 通过，`10` 项。
  - 后端路径相关回归：在 `medkernel-backend` 目录执行
    `/Users/zhikunzheng/local/apache-maven-3.9.9/bin/mvn -Dtest=PathwayEngineServiceTest,PathwayRuntimeContractTest,PathwayProgressorTest test`
    通过，`88` 项。
  - 完整前端门禁：首次 `npm --prefix frontend run verify` 在 Prettier 检查提示两个触碰文件需格式化，已用 Prettier 修正后重跑；
    最终 `npm --prefix frontend run verify` 通过，`114` 个测试文件 / `944` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `PathwayTemplates-BJXTPwe4.js`、`index-CB2yioFH.js`、
    `index-XMjG4gr3.css` 等前端产物。
  - 在 `medkernel-backend` 目录执行 `/Users/zhikunzheng/local/apache-maven-3.9.9/bin/mvn -DskipTests package` 通过，
    生成 `medkernel-backend/target/medkernel-backend-1.0.0-SNAPSHOT.jar` 与 SBOM。
  - `bash scripts/check-comment-zh.sh --mode=full` 通过；`node scripts/audit/export-product-capabilities.mjs --check` 通过；
    `node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项；`git diff --check`、应用提交前 `git diff --cached --check` 均通过。
  - 旧口径扫描：
    `rg -n "SLA基准|计时 clock|临床时钟 SLA|SLA 时限|clock 或 timeWindowMinutes|缺少 clockSla|的 clockSla|targetMinutes 必须|SLA 基准事件|SLA 已配置|SLA " frontend/src/pages/tenant/PathwayTemplates.tsx medkernel-backend/src/main/java/com/medkernel/engine/pathway medkernel-backend/src/test/java/com/medkernel/engine/pathway/PathwayEngineServiceTest.java --glob '!**/target/**'`
    无命中；内部类名、字段名、事件名仍按既有契约保留。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 readiness 使用
  `https://193.112.107.134/medkernel/actuator/health` 返回 HTTP 200 /
  `{"status":"UP","groups":["liveness","readiness"]}`，响应时间头 `Date=Fri, 03 Jul 2026 14:28:13 GMT`；
  公网首页 HTTP 200，`Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，`Content-Length=832`。
  134 映射仍按当前已核定事实保持：后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、
  前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`，不要把本地 `5e406100` 误写为已上线。
- 后续继续长目标：不再开子代理，不中途咨询；继续广度优先核查真实前台、职责旅程、菜单分布、构建门禁、134 证据映射和最终远程收口条件。

## 最新阶段交接（2026-07-03 全视角真实前台体验优化第五十七批·临床路径责任分工口径收敛）

- 本批继续按全局菜单、医疗产品体验和全角色职责旅程复核。结论：第四十四批菜单命名与顺序仍成立；
  `临床路径库` 用作路径版本治理入口，不改成“模板”口径，避免让临床与质控角色误读为单纯引用模板；
  `医疗引擎运营员` 是权威职责矩阵和后端权限中的固定角色名，本批不改。新发现的真实体验缺口在
  `/pathway/templates` 路径详情节点表：默认列名 `RACI` 对路径维护员、质控人员和科室负责人过于技术化，应表达为医疗团队可直接理解的
  “责任分工”；内部字段与契约仍保持 `raci`。
- 已本地提交 `1b47acb632319ac0b1ce980d4a580c2018a65ed5`
  （`fix: 收敛临床路径责任分工口径`）：
  - `PathwayTemplates` 节点表默认列名从 `RACI` 改为“责任分工”，测试断言默认出现“责任分工”且不再出现 `RACI`。
  - `PathwayEngineService` 面向调用方的错误信息从“RACI 角色无法解析”改为“路径节点责任分工角色无法解析”；
    `PathwayNode` 中文 Javadoc 同步收敛为“责任分工角色”。
- 本地验证：
  - 红绿核验：`npm --prefix frontend test -- PathwayTemplates.test.tsx -t "路径详情节点画布"` 在旧实现下先失败于缺少“责任分工”；
    实现后通过，`1` 项；`npm --prefix frontend test -- PathwayTemplates.test.tsx` 通过，`10` 项。
  - 后端相关回归：首次从仓库根目录执行 Maven 精确测试因根目录无 POM 失败，确认是命令工作目录错误；随后在
    `medkernel-backend` 目录执行
    `/Users/zhikunzheng/local/apache-maven-3.9.9/bin/mvn -Dtest=PathwayEngineServiceTest,PathwayRuntimeContractTest,PathwayProgressorTest test`
    通过，`88` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `944` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `PathwayTemplates-DtvEbIET.js`、`index-B8jgaPeL.js`、
    `index-XMjG4gr3.css` 等前端产物。
  - 在 `medkernel-backend` 目录执行 `/Users/zhikunzheng/local/apache-maven-3.9.9/bin/mvn -DskipTests package` 通过，
    生成 `medkernel-backend/target/medkernel-backend-1.0.0-SNAPSHOT.jar` 与 SBOM。
  - `bash scripts/check-comment-zh.sh --mode=full` 通过；`node scripts/audit/export-product-capabilities.mjs --check` 通过；
    `node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项；`git diff --check` 与应用提交前 `git diff --cached --check` 均通过。
  - 旧口径扫描：
    `rg -n "RACI" frontend/src medkernel-backend/src/main/java medkernel-backend/src/test/java --glob '!**/target/**'`
    仅命中 `PathwayTemplates.test.tsx` 的反向断言；接力文档保留本批复演记录，生产前台和后端中文消息不再默认展示 `RACI`。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 readiness 使用
  `https://193.112.107.134/medkernel/actuator/health` 返回 HTTP 200 /
  `{"status":"UP","groups":["liveness","readiness"]}`，响应时间头 `Date=Fri, 03 Jul 2026 14:19:49 GMT`；
  公网首页 HTTP 200，`Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，`index.html` 仍指向
  `/assets/index-DYTh-Ceu.js`、`/assets/index-XMjG4gr3.css`、
  `/assets/vendor-data-D9EFEnEk.js`、`/assets/vendor-react-C5ap-Sga.css`、`/assets/vendor-react-bdrMx_IT.js`。
  134 映射仍按当前已核定事实保持：后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、
  前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`，不要把本地 `1b47acb6` 误写为已上线。
- 后续继续长目标：不再开子代理，不中途咨询；继续广度优先核查真实前台、职责旅程、菜单分布、构建门禁、134 证据映射和最终远程收口条件。

## 最新阶段交接（2026-07-03 全视角真实前台体验优化第五十六批·临床事件协同链路口径收敛）

- 本批继续按医疗场景菜单命名、全角色职责旅程和真实前台可读性做广度复核。结论：
  第四十四批菜单名称与顺序、第五十四批“临床路径版本编号”口径仍成立；`随访模板`、`人员导入模板` 仍是明确模板资产语义。
  新发现的真实体验缺口不在菜单，而在临床同步通知、FHIR 回流结果和审计摘要中仍把面向临床/集成用户的链路称为
  “临床事件引擎”“标准引擎”“规则引擎已接收”。这些表述容易让医生、医技、信息科和实施角色误读为内部技术组件；
  前台和半可见审计语义应表达为“临床事件协同链路”“标准临床事件链路”“临床规则已接收”，内部英文类名和适配器契约保持不变。
- 已本地提交 `67a15e48bc0c3d56f84b79e63f7f75add6e08261`
  （`fix: 收敛临床事件协同链路口径`）：
  - `WorkflowCollaborationService` 的临床同步通知从“进入临床事件引擎”改为“进入临床事件协同链路”，
    `Notifications.test.tsx` 与后端通知单测同时断言旧口径不再默认出现。
  - `FhirFacadeService` 的 OperationOutcome 与外部补偿摘要改为“进入临床事件协同链路”“回流标准临床事件链路”，
    保留 `NOT_CONNECTED` 等真实集成状态，不隐藏断连证据。
  - 临床事件派发、适配器、仓储和审计摘要的中文 Javadoc / 消息从“规则引擎、下游引擎、engines=”收敛为
    “临床规则、下游能力、capabilities=”，内部 `ClinicalEventEngine*` 类名和 `engine()` 字段不改，避免破坏既有接口契约。
- 本地验证：
  - 红绿核验：`/Users/zhikunzheng/local/apache-maven-3.9.9/bin/mvn -Dtest=WorkflowCollaborationServiceTest#projectProcessedClinicalEventCreatesSyncNotification,FhirFacadeServiceTest#createsObservationThroughCanonicalResourceClinicalEventAndIntegrationBus test`
    在旧实现下先失败于 FHIR OperationOutcome 缺少“进入临床事件协同链路”；实现后相关后端回归
    `/Users/zhikunzheng/local/apache-maven-3.9.9/bin/mvn -Dtest=WorkflowCollaborationServiceTest,FhirFacadeServiceTest,ClinicalEventEngineAdapterTest,ClinicalEventProcessorTest test`
    通过，`48` 项。
  - 前端相关回归：`npm --prefix frontend test -- Notifications.test.tsx -t "clinical sync event"` 通过，`1` 项；
    `npm --prefix frontend test -- Notifications.test.tsx` 通过，`15` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `944` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `Notifications-ClILaWCf.js`、`index-Bb9kSRL2.js`、
    `index-XMjG4gr3.css` 等前端产物。
  - `/Users/zhikunzheng/local/apache-maven-3.9.9/bin/mvn -DskipTests package` 通过，生成
    `medkernel-backend/target/medkernel-backend-1.0.0-SNAPSHOT.jar` 与 SBOM。
  - `bash scripts/check-comment-zh.sh --mode=full` 通过；`node scripts/audit/export-product-capabilities.mjs --check` 通过；
    `node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项；`git diff --check` 与应用提交前 `git diff --cached --check` 均通过。
  - 生产口径扫描：
    `rg -n "临床事件引擎|标准引擎|规则引擎已接收|下游引擎不可用|engines=" medkernel-backend/src/main/java frontend/src/pages frontend/src/widgets frontend/src/shared/config --glob '!**/*.test.ts' --glob '!**/*.test.tsx' --glob '!**/target/**'`
    无命中；测试侧仅保留防回归的反向断言。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 readiness 使用
  `https://193.112.107.134/medkernel/actuator/health` 返回 HTTP 200 /
  `{"status":"UP","groups":["liveness","readiness"]}`；公网首页 HTTP 200，`Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`。
  134 映射仍按当前已核定事实保持：后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、
  前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`，不要把本地 `67a15e48` 误写为已上线。
- 后续继续长目标：不再开子代理，不中途咨询；继续按权威文档、菜单分布、职责旅程、真实前台截图与自动化证据，
  广度优先核查全角色可读性、六态、低频证据层级、构建门禁、134 映射和最终远程收口条件。

## 最新阶段交接（2026-07-03 全视角真实前台体验优化第五十五批·安全配置运行环境口径收敛）

- 本批继续按全局菜单、医疗场景名称和全角色体验要求复核权威目录、职责旅程与真实前台。结论：
  第四十四批确定的菜单名称与顺序仍成立，真实缺口不在菜单，而在 `/compliance/security-baseline` 安全配置页默认摘要仍把
  `snapshot.environment / snapshot.deploymentMode` 直接展示为 `container / docker-core`。对平台治理、实施运维和医院安全管理员而言，
  默认页应表达为可确认的运行环境与部署形态；原始运行标识只属于证据详情。
- 已本地提交 `01bf139f2fb5e8ef90a24e17defb4e40a4e1a13f`
  （`fix: 收敛安全配置运行环境口径`）：
  - `SecurityBaseline` 的运行环境摘要默认改为“容器运行环境 / 容器化部署”等客户可读口径，未知值降级为可确认状态；
    证据详情打开后仍保留 `container / docker-core` 等原始证据，满足审计追溯。
  - `SecurityBaseline.test.tsx` 增加默认可读口径、默认隐藏 `container` / `docker-core`、证据详情打开后展示原始值的断言，
    防止运行环境技术标识再次回流到默认前台。
- 本地验证：
  - 红绿核验：`npm --prefix frontend test -- SecurityBaseline.test.tsx -t "unifies runtime baseline|reveals security identifiers"`
    在旧实现下先失败于“运行环境：容器运行环境 / 容器化部署”缺失；实现后通过，`2` 项。
  - 前端相关回归：`npm --prefix frontend test -- SecurityBaseline.test.tsx` 通过，`10` 项；
    `npm --prefix frontend test -- SecurityBaseline.test.tsx SystemProviders.test.tsx operationalControlPages.test.tsx` 通过，
    `3` 个测试文件 / `24` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `944` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `SecurityBaseline-Dtxf-Fu8.js`、`index-Bb9kSRL2.js`、
    `index-XMjG4gr3.css` 等前端产物。
  - `/Users/zhikunzheng/local/apache-maven-3.9.9/bin/mvn -DskipTests package` 通过，生成
    `medkernel-backend/target/medkernel-backend-1.0.0-SNAPSHOT.jar` 与 SBOM。
  - `bash scripts/check-comment-zh.sh --mode=full` 通过；`node scripts/audit/export-product-capabilities.mjs --check` 通过；
    `node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项；`git diff --check` 与应用提交前 `git diff --cached --check` 均通过。
  - 同类扫描：`rg -n "container / docker-core|运行环境：\$\{snapshot\.environment\}|snapshot\.environment\} / \$\{snapshot\.deploymentMode\}|部署模式\" value=\{data\.deploymentMode\}" frontend/src/pages frontend/src/widgets --glob '!**/*.test.ts' --glob '!**/*.test.tsx'`
    仅命中 `SystemProviders` 的证据详情分支；`SystemProviders.test.tsx` 已覆盖默认隐藏实现细节、打开证据详情后展示低频诊断。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 readiness 使用
  `https://193.112.107.134/medkernel/actuator/health` 返回 HTTP 200 /
  `{"status":"UP","groups":["liveness","readiness"]}`；公网首页 HTTP 200，`Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，
  `index.html` 指向 `/assets/index-DYTh-Ceu.js`、`/assets/index-XMjG4gr3.css`、
  `/assets/vendor-data-D9EFEnEk.js`、`/assets/vendor-react-C5ap-Sga.css`、`/assets/vendor-react-bdrMx_IT.js`。
  134 映射仍按当前已核定事实保持：后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、
  前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`，不要把本地 `01bf139f` 误写为已上线。
- 后续继续长目标：不再开子代理，不中途咨询；继续广度优先核查真实前台、职责旅程、菜单分布、构建门禁、134 证据映射和最终远程收口条件。

## 最新阶段交接（2026-07-03 全视角真实前台体验优化第五十四批·患者路径证据口径收敛）

- 本批接续用户对“临床路径模板是否像引用模板”和“菜单名称需符合医疗场景”的全局线索，继续复核真实前台、角色证据层级和临床路径相关页面。
  结论：第四十四批确定的菜单名称与顺序仍成立，`临床路径库` 作为知识治理入口不需要改名；真实缺口在患者 360 已打开证据详情时，
  活跃患者路径仍以“模板：{templateId}”展示路径版本身份。虽然该信息已经收进证据详情，但对临床使用者、质控人员和实施人员仍容易误读为
  “引用模板”而不是“当前患者路径对应的临床路径版本”，因此本批将患者 360 的路径证据口径与临床路径办理详情统一为“临床路径版本编号”。
- 已本地提交 `691a5397b2ca50f60b4a3636ef3216f0d5c657f9`
  （`fix: 收敛患者路径证据口径`）：
  - `Mpi` 患者 360 详情中，活跃患者路径在证据详情打开后从“模板：{pathway.templateId}”改为
    “临床路径版本编号：{pathway.templateId}”，保留默认收起低频证据和 `templateId` 字段兼容身份。
  - `Mpi.test.tsx` 在“仅打开证据详情后展示 MPI 审计标识”用例中新增正向断言
    “临床路径版本编号：tpl-stroke-v1”和反向断言“模板：tpl-stroke-v1”不可见，防止旧口径回流。
- 本地验证：
  - 红绿核验：`npm --prefix frontend test -- Mpi.test.tsx -t "reveals MPI audit identifiers"` 在旧实现下先失败于
    新“临床路径版本编号：tpl-stroke-v1”缺失且旧“模板：tpl-stroke-v1”仍可见；实现后通过，`1` 项。
  - 前端相关回归：`npm --prefix frontend test -- Mpi.test.tsx` 通过，`12` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `944` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `Mpi-DPK-9uQc.js`、`PatientPathways-CotJFH4c.js`、
    `index-xGJTjQLZ.js`、`index-XMjG4gr3.css` 等前端产物。
  - `/Users/zhikunzheng/local/apache-maven-3.9.9/bin/mvn -DskipTests package` 通过，生成
    `medkernel-backend/target/medkernel-backend-1.0.0-SNAPSHOT.jar` 与 SBOM。
  - `bash scripts/check-comment-zh.sh --mode=full` 通过；`node scripts/audit/export-product-capabilities.mjs --check` 通过；
    `node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项；`git diff --check` 与应用提交前 `git diff --cached --check` 均通过。
  - 旧口径扫描：`rg -n "模板：|路径模板|临床路径模板" frontend/src/pages/clinical frontend/src/pages/tenant frontend/src/shared/config --glob '!**/*.test.ts' --glob '!**/*.test.tsx'`
    无生产命中；`rg -n "临床路径版本编号|templateId" frontend/src/pages/clinical/Mpi.tsx frontend/src/pages/clinical/PatientPathways.tsx frontend/src/pages/clinical/Mpi.test.tsx`
    显示患者 360 与患者路径详情已使用同一“临床路径版本编号”口径，测试保留旧“模板”反向断言。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 readiness 使用
  `https://193.112.107.134/medkernel/actuator/health` 返回 HTTP 200 /
  `{"status":"UP","groups":["liveness","readiness"]}`；公网首页 HTTP 200，`Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，
  `index.html` 指向 `/assets/index-DYTh-Ceu.js`、`/assets/index-XMjG4gr3.css`、
  `/assets/vendor-data-D9EFEnEk.js`、`/assets/vendor-react-C5ap-Sga.css`、`/assets/vendor-react-bdrMx_IT.js`。
  134 映射仍按当前已核定事实保持：后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、
  前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`，不要把本地 `691a5397` 误写为已上线。
- 后续继续长目标：不再开子代理，不中途咨询；继续广度优先核查真实前台、职责旅程、菜单分布、构建门禁、134 证据映射和最终远程收口条件。

## 最新阶段交接（2026-07-03 全视角真实前台体验优化第五十三批·临床规则与提醒推荐前台口径收敛）

- 本批继续按“菜单名称需从全局视角、医疗产品体验和全角色评估”的要求，复核权威目录与真实前台。结论：
  第四十四批确定的一级菜单顺序和 `诊断知识库`、`临床路径库`、`临床规则`、`提醒与推荐`、`知识资产` 等入口名仍成立；
  真实缺口在用户可见页面和运行诊断公开服务目录中仍残留“规则引擎 / 推荐引擎 / 引擎资产 / 智能推荐”工程口径。
  这些词会让临床使用者、医疗引擎运营员和平台运维角色把业务任务误读成后台模块或算法引擎，因此本批只收敛客户可见中文，
  保留 `RuleEngine*`、`/api/v1/engine/rule` 等内部类名、英文路径和兼容字段。
- 已本地提交 `ea76cbb69f6d39115ca32608161ad8c470ff7430`
  （`fix: 收敛临床规则与推荐前台口径`）：
  - `RuleValidate` 页面描述从“向规则引擎输入”改为“使用真实脱敏上下文试运行临床规则”，锁定 `/rule/validate`
    属于知识治理下的临床规则试运行任务。
  - `CdssFatigue` 推荐详情与触发评估弹窗将“规则引擎 / 推荐引擎 / 红线检查”收敛为
    “临床规则 / 提醒与推荐 / 安全红线”，保持模型不可用时走确定性规则链路的医疗安全说明。
  - `EmbedLaunch` 嵌入式空状态从“确认推荐引擎已生成建议”改为“确认提醒与推荐已生成有效建议”，避免 HIS/EMR 前台露出后台模块名。
  - `SandboxHost` 将执行能力标签从“规则引擎 / 智能推荐”改为“临床规则 / 提醒与推荐”，与沙盘入口的医疗智能协同口径一致。
  - `AuthoringAssets` 页面说明从“复用全部引擎资产”改为“复用医疗知识与配置资产”，匹配功能目录中的知识资产边界。
  - 运行诊断服务目录将 `rule` 服务标题从“规则引擎服务”改为“临床规则服务”；规则 API 缺统一入参的公开错误详情改为
    “临床规则 API 缺少统一入参字段”。英文路径、权限码和内部控制器名保持不变。
- 本地验证：
  - 红绿核验：`npm --prefix frontend test -- RuleValidate.test.tsx CdssFatigue.test.tsx EmbedLaunch.test.tsx AuthoringAssets.test.tsx SandboxHost.test.tsx operationalControlPages.test.tsx`
    在旧实现下先失败于“推荐引擎 / 引擎资产 / 规则引擎 / 智能推荐”等旧称仍可见或新称缺失；实现并同步测试预期后通过，
    `6` 个测试文件 / `49` 项。
  - 红绿核验：`/Users/zhikunzheng/local/apache-maven-3.9.9/bin/mvn -Dtest=RuntimeDiagnosticsControllerTest#apiContractDirectoryReturnsSanitizedServiceContracts,RuleEngineApiContractTest#createRequiresUnifiedContextFields test`
    在 `medkernel-backend` 目录下先失败于运行诊断仍返回“规则引擎服务”和规则 API 错误详情仍使用旧称；实现后通过，`2` 项。
  - 后端相关回归：`/Users/zhikunzheng/local/apache-maven-3.9.9/bin/mvn -Dtest=RuntimeDiagnosticsControllerTest,RuleEngineApiContractTest test`
    通过，`16` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `944` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `RuleValidate-FfO52N4I.js`、`CdssFatigue-Vrk0eejM.js`、
    `EmbedLaunch-LkvUqUiR.js`、`SandboxHost-6qUEB8aR.js`、`AuthoringAssets-BEnN4R6y.js`、
    `RuntimeDiagnostics-DFGngJ7a.js`、`index-Cx3Axzie.js`、`index-XMjG4gr3.css` 等前端产物。
  - `/Users/zhikunzheng/local/apache-maven-3.9.9/bin/mvn -DskipTests package` 通过，生成
    `medkernel-backend/target/medkernel-backend-1.0.0-SNAPSHOT.jar` 与 SBOM。
  - `bash scripts/check-comment-zh.sh --mode=full` 通过；`node scripts/audit/export-product-capabilities.mjs --check` 通过；
    `node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项；`git diff --check` 与应用提交前 `git diff --cached --check` 均通过。
  - 旧口径扫描：`rg -n "规则引擎|推荐引擎|引擎资产|向规则引擎|智能推荐" frontend/src/pages frontend/src/shared/config frontend/src/features frontend/src/widgets --glob '!**/*.test.ts' --glob '!**/*.test.tsx'`
    无生产命中；`rg -n "规则引擎服务|规则引擎 API 缺少统一入参字段|推荐引擎服务|引擎资产" medkernel-backend/src/main/java medkernel-backend/src/test/java frontend/src --glob '!**/target/**'`
    仅命中测试反向断言，生产前台与公开服务契约无本批旧口径残留。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 readiness 使用
  `https://193.112.107.134/medkernel/actuator/health` 返回 HTTP 200 /
  `{"status":"UP","groups":["liveness","readiness"]}`；公网首页 HTTP 200，`Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，
  `index.html` 指向 `/assets/index-DYTh-Ceu.js`、`/assets/index-XMjG4gr3.css`、
  `/assets/vendor-data-D9EFEnEk.js`、`/assets/vendor-react-C5ap-Sga.css`、`/assets/vendor-react-bdrMx_IT.js`。
  134 映射仍按当前已核定事实保持：后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、
  前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`，不要把本地 `ea76cbb6` 误写为已上线。
- 后续继续长目标：不再开子代理，不中途咨询；继续广度优先核查真实前台、职责旅程、菜单分布、构建门禁、134 证据映射和最终远程收口条件。

## 最新阶段交接（2026-07-03 全视角真实前台体验优化第五十二批·批量规则基准资产口径收敛）

- 本批继续按“菜单名称、临床路径模板含义和全角色医疗产品体验需整体评审”的要求，回看一级菜单、路由职责、功能目录与真实前台。
  结论：第四十四批确定的一级菜单顺序仍成立，`知识治理`、`知识生产`、`临床协同` 等分组和 `诊断知识库`、`临床路径库`
  等入口名继续符合当前产品范围；真实缺口在 `/authoring/assets` 的批量规则生成抽屉和后端 authoring 公开中文仍使用
  “模板规则资产 / 已审核模板规则 / 模板参数 / 自动继承模板 / 规则模板”等说法。该功能实际是选择一条已审核规则作为基准，再按参数表批量生成独立规则草稿，不应暗示引用模板或套用临时模板。
- 已本地提交 `2a97de1738226b46e528e4b0445af4348227bf42`
  （`fix: 收敛批量规则基准资产口径`）：
  - `AuthoringBatchDrawer` 将可见表单、提示、占位符和校验从“模板规则资产 / 模板参数 / 自动继承模板”收敛为
    “基准规则资产 / 批量参数 / 沿用基准规则的触发绑定”，并补充反向断言，防止旧称回流。
  - `routes.ts` 将 `/authoring/assets` 实施工程师职责从“复用模板加速机构上线配置”改为“复用基准资产加速机构上线配置”，仍保留
    “复用后需机构适配和验证”的医疗安全边界。
  - 后端 authoring 批量生成控制器、服务、请求、参数行和规则适配器的公开 Javadoc / 错误详情统一改为“基准规则 / 批量参数”，保留
    `templateRuleId`、`AuthoringBatchRuleTemplate` 等 API 与内部兼容身份，不把兼容字段名升级为前台术语。
- 本地验证：
  - 红绿核验：`npm --prefix frontend test -- AuthoringBatchDrawer.test.tsx -t "generates one rule draft"` 先失败于旧实现仍显示
    “模板规则资产 / 模板参数 / 已审核模板规则”，实现后通过，`1` 项。
  - 前端相关回归：`npm --prefix frontend test -- AuthoringBatchDrawer.test.tsx AuthoringAssets.test.tsx routes.test.ts productRoleJourneys.test.ts`
    最终通过，`4` 个测试文件 / `71` 项；首次相关回归只暴露 `routes.test.ts` 仍期待旧“复用模板”职责，已同步修正后复跑通过。
  - 后端相关回归：`/Users/zhikunzheng/local/apache-maven-3.9.9/bin/mvn -Dtest=AuthoringBatchJobServiceTest,AuthoringBatchJobControllerTest test`
    通过，`7` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `943` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `AuthoringAssets-BpThNdlT.js`、`PathwayTemplates-Gi2TBzX1.js`、
    `RuntimeDiagnostics-BjMfisFG.js`、`SandboxHost-KlOaVBHV.js`、`index-CX1R1xpD.js`、`index-XMjG4gr3.css` 等前端产物。
  - `/Users/zhikunzheng/local/apache-maven-3.9.9/bin/mvn -DskipTests package` 通过，生成
    `medkernel-backend/target/medkernel-backend-1.0.0-SNAPSHOT.jar` 与 SBOM。
  - `bash scripts/check-comment-zh.sh --mode=full` 通过；`node scripts/audit/export-product-capabilities.mjs --check` 通过；
    `node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项；`git diff --check` 与 `git diff --cached --check` 均通过。
  - 旧口径扫描：`rg -n "复用模板|模板规则|规则模板|模板参数|自动继承模板" frontend/src/pages frontend/src/shared/config medkernel-backend/src/main/java/com/medkernel/engine/authoring docs/audit/product-function-catalog.md docs/audit/product-role-journeys.md --glob '!**/*.test.ts' --glob '!**/*.test.tsx' --glob '!**/target/**'`
    无生产命中；更宽扫描仅命中测试反向断言、LLM 模型矩阵测试夹具和 `_HANDOFF` 历史描述，生产前台与 authoring 公开契约无旧口径残留。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 readiness 使用
  `https://193.112.107.134/medkernel/actuator/health` 返回 HTTP 200 /
  `{"status":"UP","groups":["liveness","readiness"]}`；公网首页 HTTP 200，`Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，
  `index.html` 指向 `/assets/index-DYTh-Ceu.js`、`/assets/index-XMjG4gr3.css`、
  `/assets/vendor-data-D9EFEnEk.js`、`/assets/vendor-react-C5ap-Sga.css`、`/assets/vendor-react-bdrMx_IT.js`。
  134 映射仍按当前已核定事实保持：后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、
  前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`，不要把本地 `2a97de17` 误写为已上线。
- 后续继续长目标：不再开子代理，不中途咨询；继续广度优先核查真实前台、职责旅程、菜单分布、构建门禁、134 证据映射和最终远程收口条件。

## 最新阶段交接（2026-07-03 全视角真实前台体验优化第五十一批·全真体验沙盘智能协同口径收敛）

- 本批继续按“菜单名称与顺序需从医疗产品和全角色视角评估”的要求复核权威文档、功能目录、职责旅程与真实前台。结论：
  第四十四批确定的一级菜单顺序仍成立，`诊断知识库`、`临床路径库` 等入口名继续符合产品范围；真实缺口在 `/sandbox`
  作为临床与平台用户可见的全真体验入口时，路由体验、页面文案、后端服务契约和推荐摘要仍残留
  “真实引擎 / 引擎编排 / 引擎调用 / 引擎版本 / 运行真实引擎链路”等工程态口径。本批将客户可见表述统一收敛为
  “医疗智能协同 / 真实医疗智能链路 / 真实协同链路”，保留内部 `engine-orchestration`、`DomainFacadeEngine`
  等代码边界用于兼容和架构语义。
- 已本地提交 `e4720e932c8f53f99ccfa3f3b8d4e16ad3777560`
  （`fix: 收敛沙盘智能协同口径`）：
  - `SandboxHost` 将服务线、入口标题、运行按钮、成功提示和默认能力标签从引擎口径收敛为医疗智能协同口径，并用反向断言防止客户前台再出现
    “引擎编排 / 真实引擎 / 引擎能力 / 引擎处置建议”。
  - `routes.ts` 将 `/sandbox` 体验目标改为“以院内业务系统视角验证真实医疗智能链路、嵌入终端和反馈闭环”，职责边界改为验证规则、路径、推荐等能力版本和场景证据，避免把内部引擎版本作为用户任务。
  - 后端运行诊断服务目录将沙盘审计目的改为“按当前机构生效版本编排真实医疗智能链路并记录复演轨迹”；沙盘推荐摘要改为
    “沙盘智能协同根据标准上下文生成可追溯的人工确认建议。”
  - 功能目录与 `scripts/audit/export-product-capabilities.mjs` 同步更新 `/sandbox` 客户唯一任务，保持文档、生成脚本和前台契约一致。
- 本地验证：
  - 红绿核验：`npm --prefix frontend test -- SandboxHost.test.tsx -t "hospital-facing collaboration"` 先失败于旧实现仍显示
    “真实引擎调用 / 引擎编排入口 / 运行真实引擎链路”，实现后通过，`1` 项。
  - 红绿核验：`npm --prefix frontend test -- routes.test.ts -t "sandbox route experience"` 先失败于旧路由体验目标仍写
    “真实引擎调用”，实现后通过，`1` 项。
  - 红绿核验：`/Users/zhikunzheng/local/apache-maven-3.9.9/bin/mvn -Dtest=RuntimeDiagnosticsControllerTest#apiContractDirectoryReturnsSanitizedServiceContracts,SandboxOrchestrationServiceTest#compositeRecommendationCarriesTraceableSuggestOrderCandidate test`
    先失败于后端服务契约和推荐摘要仍使用“真实引擎链路 / 沙盘引擎编排”，实现后通过，`2` 项。
  - 前端相关回归：`npm --prefix frontend test -- SandboxHost.test.tsx routes.test.ts menu.test.ts productRoleJourneys.test.ts sandboxScenarios.test.ts`
    通过，`5` 个测试文件 / `81` 项。
  - 后端相关回归：`/Users/zhikunzheng/local/apache-maven-3.9.9/bin/mvn -Dtest=RuntimeDiagnosticsControllerTest,ServiceContractGovernanceTest,SandboxOrchestrationServiceTest,SandboxScenarioCatalogTest test`
    通过，`23` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `943` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `SandboxHost-CnsQxDhV.js`、`SandboxHost-CFnthEd-.css`、
    `PathwayTemplates-BSn-PgSA.js`、`RuntimeDiagnostics-ZENZjmgE.js`、`index-B10JCVNs.js`、`index-XMjG4gr3.css` 等前端产物。
  - `/Users/zhikunzheng/local/apache-maven-3.9.9/bin/mvn -DskipTests package` 通过，生成
    `medkernel-backend/target/medkernel-backend-1.0.0-SNAPSHOT.jar` 与 SBOM。
  - `bash scripts/check-comment-zh.sh --mode=full` 通过；`node scripts/audit/export-product-capabilities.mjs --check` 通过；
    `node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项；`git diff --check` 与 `git diff --cached --check` 均通过。
  - 旧口径扫描：`rg -n "真实引擎|引擎编排|运行真实引擎链路|引擎处置建议|引擎能力|引擎调用|引擎版本" frontend/src medkernel-backend/src/main/java medkernel-backend/src/test/java docs/audit/product-function-catalog.md scripts/audit/export-product-capabilities.mjs --glob '!**/target/**'`
    仅命中测试反向断言和内部 `DomainFacadeEngine*` 架构 Javadoc，生产沙盘前台、公开服务契约与功能目录无客户可见旧口径残留。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 与本地 `main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 readiness 使用
  `https://193.112.107.134/medkernel/actuator/health` 返回 HTTP 200 /
  `{"status":"UP","groups":["liveness","readiness"]}`；公网首页 HTTP 200，`Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，
  `index.html` 指向 `/assets/index-DYTh-Ceu.js`、`/assets/index-XMjG4gr3.css`、
  `/assets/vendor-data-D9EFEnEk.js`、`/assets/vendor-react-C5ap-Sga.css`、`/assets/vendor-react-bdrMx_IT.js`。
  134 映射仍按当前已核定事实保持：后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、
  前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`，不要把本地 `e4720e93` 误写为已上线。
- 后续继续长目标：不再开子代理，不中途咨询；继续广度优先核查真实前台、职责旅程、菜单分布、构建门禁、134 证据映射和最终远程收口条件。

## 最新阶段交接（2026-07-03 全视角真实前台体验优化第五十批·临床路径公开口径收敛）

- 本批接续用户对“临床路径模板是否像引用模板”和“菜单名称需符合医疗场景”的全局线索，在第四十九批前台口径基础上继续复核
  `pathway` 后端服务契约、规则影响、互操作、安全撤回、沙盘前台标签和公开错误信息。结论：第四十四批菜单顺序和
  `临床路径库` 入口仍成立；真实缺口是后端公开中文、运行诊断服务标题、规则影响原因、沙盘执行能力标签和 API 错误仍残留
  “路径模板 / 路径引擎”等技术口径，容易让客户误解为引用模板或后台引擎模块。本批将客户可见和公开契约中文统一收敛为
  “临床路径 / 路径版本 / 路径编码”，保留 `PathwayTemplate`、`PATHWAY_TEMPLATE`、`/pathway-templates`
  等英文代码、对象类型和 API 路径作为兼容身份，不作为菜单或前台展示名。
- 已本地提交 `34bb45bc50591d57671f3dbbc426f63368ac342d`
  （`fix: 收敛临床路径公开口径`）：
  - 运行诊断服务契约将 `pathway` 标题从“路径引擎服务”收敛为“临床路径服务”，审计目的同步为
    “创建临床路径草稿和患者路径 / 发布临床路径版本”。
  - 路径域公开 Javadoc、错误详情、审计摘要、发布校验、机构生效版本解析、互操作导入导出、安全撤回、沙盘编排和规则影响原因统一使用
    “临床路径”业务口径；规则影响中患者在径说明改为“临床路径 <路径编码> 受规则引用影响”。
  - 全真体验沙盘的 `pathway` 执行能力标签由“路径引擎”改为“临床路径”，并补充路径协同场景测试，防止客户前台再次露出工程态能力名。
  - 集成指南中 FHIR 互操作示例的路径草稿说明同步为“临床路径草稿”，避免文档与后端公开合同脱节。
- 本地验证：
  - 红绿核验：`/Users/zhikunzheng/local/apache-maven-3.9.9/bin/mvn -Dtest=RuntimeDiagnosticsControllerTest,RelationalRuleImpactIndexTest test`
    先失败于旧实现仍返回“路径引擎服务 / 路径模板节点引用规则”，实现后通过，`3` 项。
  - 红绿核验：`npm --prefix frontend test -- SandboxHost.test.tsx -t "uses clinical pathway wording"` 先失败于沙盘路径场景找不到“临床路径”，
    实现并补足路径协同场景夹具后通过。
  - 前端沙盘回归：`npm --prefix frontend test -- SandboxHost.test.tsx` 通过，`9` 项。
  - 后端相关回归：`/Users/zhikunzheng/local/apache-maven-3.9.9/bin/mvn -Dtest=RuntimeDiagnosticsControllerTest,RelationalRuleImpactIndexTest,RelationalRuleImpactIndexRepositoryTest,PathwayEngineServiceTest,PathwayEngineApiContractTest,PathwayEngineControllerSecurityTest,InteroperabilityMappingServiceTest,InteroperabilityControllerSecurityTest,ClinicalSafetyGuardTest,SandboxOrchestrationServiceTest test`
    通过，`104` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `941` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `SandboxHost-gQKzmCwC.js`、`PatientPathways-MQN0rv6B.js`、
    `PathwayTemplates-i8BHLzRr.js`、`RuntimeDiagnostics-DoBU59AJ.js`、`index-COU56ji3.js` 等前端产物。
  - `/Users/zhikunzheng/local/apache-maven-3.9.9/bin/mvn -DskipTests package` 通过，生成
    `medkernel-backend/target/medkernel-backend-1.0.0-SNAPSHOT.jar` 与 SBOM。
  - `bash scripts/check-comment-zh.sh --mode=full` 通过；`node scripts/audit/export-product-capabilities.mjs --check` 通过；
    `node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项；`git diff --check` 通过。
  - 旧口径扫描：`rg -n "路径引擎" medkernel-backend/src/main/java medkernel-backend/src/test/java frontend/src docs --glob '!**/target/**'`
    仅命中 `SandboxHost.test.tsx` 和 `RuntimeDiagnosticsControllerTest` 的反向断言；`rg -n "路径模板|临床路径模板|路径包、模板" ...`
    仅命中 `_HANDOFF` 历史描述和测试反向断言，生产前台与后端公开契约无旧路径口径残留。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 readiness 使用
  `https://193.112.107.134/medkernel/actuator/health` 返回 HTTP 200 /
  `{"status":"UP","groups":["liveness","readiness"]}`；公网首页 HTTP 200，`Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，
  `index.html` 指向 `/assets/index-DYTh-Ceu.js`、`/assets/index-XMjG4gr3.css`、
  `/assets/vendor-data-D9EFEnEk.js`、`/assets/vendor-react-C5ap-Sga.css`、`/assets/vendor-react-bdrMx_IT.js`。
  134 映射仍按当前已核定事实保持：后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、
  前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`，不要把本地 `34bb45bc` 误写为已上线。
- 后续继续长目标：不再开子代理，不中途咨询；继续广度优先核查真实前台、职责旅程、菜单分布、构建门禁、134 证据映射和最终远程收口条件。

## 最新阶段交接（2026-07-03 全视角真实前台体验优化第四十九批·菜单服务与临床路径前台口径收敛）

- 本批继续按用户对“诊断知识维护”“临床路径模板”和整体菜单适配医疗场景的连续反馈做全局复审。先回读
  `CONSTITUTION`、`PRODUCT_SCOPE`、`EXPERIENCE_CONTRACT`、功能目录和职责旅程后判断：当前产品结构和第四十四批菜单顺序仍成立，
  真实缺口在局部服务契约、错误态、导出原因、权限展示名和临床路径推进文案仍残留旧称或“模板”暗示，容易让客户误解为引用模板或临时维护项。
- 已本地提交 `d109291c85dfa973b5362e8ff97e769537da79b0`
  （`fix: 收敛菜单服务与临床路径前台口径`）：
  - 临床路径前台将患者路径推进提示从“按模板出边”收敛为“按临床路径出边”，`TEMPLATE` 范围默认显示“全路径”，避免把已发布路径误读为引用模板。
  - 质量页面错误态统一为“质量管理概览读取失败”；术语字典导出原因、页面测试夹具和 API hook 注释统一为“术语字典 / 术语映射”分层口径。
  - 运行诊断服务契约将 `diagnosis-knowledge`、`quality-dashboard`、`terminology`、`workflow-notification` 分别统一为
    “诊断知识库服务 / 质量管理概览服务 / 术语字典服务 / 消息通知服务”，权限展示名同步为“发布术语字典版本 / 查看消息通知 / 访问术语字典资产”。
  - 后端公开 Javadoc、控制器说明、审计目的和契约测试同步收敛“诊断知识库、质量管理概览、术语字典、消息通知”，保留内部端口 `TerminologyMappingPort`
    等实现名用于结构边界，不把低层映射名暴露为前台菜单名。
- 本地验证：
  - 红绿核验：`npm --prefix frontend test -- PatientPathways.test.tsx -t "shows pathway clocks"` 先失败于旧实现缺少
    “系统会按临床路径出边计算下一步并刷新关键时钟。”，实现后通过。
  - 红绿核验：`npm --prefix frontend test -- QcDashboard.test.tsx -t "uses the current quality overview name"` 先失败于旧错误态仍显示
    “质控驾驶舱读取失败”，实现后通过。
  - 红绿核验：`npm --prefix frontend test -- TerminologyMapping.test.tsx -t "submits async export"` 先失败于导出原因仍为
    “导出字典映射核查结果”，实现后通过。
  - 红绿核验：`/Users/zhikunzheng/local/apache-maven-3.9.9/bin/mvn -Dtest=TerminologyApiContractTest#generateCandidatesRejectsMissingStandardContext test`
    先失败于 API 错误详情仍为“字典映射 API 缺少统一入参字段”，实现后通过。
  - 前端相关回归：`npm --prefix frontend test -- QcDashboard.test.tsx TerminologyMapping.test.tsx AppLayout.test.tsx hooks.test.ts PageExperienceShell.test.tsx PatientPathways.test.tsx PathwayTemplates.test.tsx routes.test.ts menu.test.ts productRoleJourneys.test.ts`
    通过，`10` 个测试文件 / `265` 项。
  - 后端相关回归：`/Users/zhikunzheng/local/apache-maven-3.9.9/bin/mvn -Dtest=RuntimeDiagnosticsControllerTest,ServiceContractGovernanceTest,OpenApiContractConfigurationTest,MenuPermissionCatalogTest,DefaultPermissionPolicyTest,DiagnosisKnowledgeApiContractTest,TerminologyApiContractTest test`
    通过，`45` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `940` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `PatientPathways-B8WVqMLh.js`、`QcDashboard-CB1dqM9y.js`、
    `TerminologyMapping-B4uQ3FZS.js`、`RuntimeDiagnostics-BAVwR-AB.js`、`index-DrlIlfF0.js` 等前端产物。
  - `/Users/zhikunzheng/local/apache-maven-3.9.9/bin/mvn -DskipTests package` 通过，生成
    `medkernel-backend/target/medkernel-backend-1.0.0-SNAPSHOT.jar` 与 SBOM。
  - `node scripts/audit/export-product-capabilities.mjs --check` 通过。
  - `node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项。
  - `bash scripts/check-comment-zh.sh --mode=full` 通过；`git diff --check` 通过。
  - 旧口径扫描
    `rg -n "质控驾驶舱|导出字典映射核查结果|读取字典映射|维护字典映射|字典映射内容|字典映射 API|字典映射应用服务|字典映射 hook|诊断知识维护服务|诊断知识维护 API|临床通知中心服务|临床通知中心控制器|查看通知中心|统一通知中心|发布字典映射版本|访问字典映射资产|按模板出边|模板拓扑|return \"模板\"" frontend/src medkernel-backend/src/main/java medkernel-backend/src/test/java --glob '!**/target/**'`
    仅命中测试中的反向断言 / 旧名黑名单，生产前台与后端公开契约无这些旧口径残留。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 readiness HTTP 200 /
  `{"status":"UP"}`，公网首页 HTTP 200，`Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，
  `index.html` 指向 `/assets/index-DYTh-Ceu.js`、`/assets/index-XMjG4gr3.css`、
  `/assets/vendor-data-D9EFEnEk.js`、`/assets/vendor-react-C5ap-Sga.css`、`/assets/vendor-react-bdrMx_IT.js`。
  本轮 SSH 重读 manifest 被服务器权限拒绝（`Permission denied`），因此不新增远端 manifest 证据；134 映射仍按当前已核定事实保持：
  后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`，
  不要把本地 `d109291c` 误写为已上线。
- 后续继续长目标：不再开子代理，不中途咨询；继续广度优先核查真实前台、职责旅程、构建门禁、134 证据映射和最终远程收口条件。

## 最新阶段交接（2026-07-03 全视角真实前台体验优化第四十八批·全真体验沙盘当前机构口径收敛）

- 本批继续按全局医疗产品体验复核“演练/沙盘”相关前台与引擎口径。`/sandbox` 作为功能目录中的
  “全真体验沙盘”入口本身仍是合规产品能力；`历史重放清单`、`演练/复演`作为验证场景概念也需要保留。真实问题在于
  “演练机构”被用于默认错误提示、运行基线注释、历史重放归属校验和迁移 COMMENT，容易让客户误解为系统使用虚构机构或演示租户，
  与当前 1.0 契约中 `rehearsal` 是完整上线演练机构块的事实不一致。因此本批只收敛该误导词，保留沙盘、历史重放与当前机构生效版本的真实边界。
- 已本地提交 `d6e6db93f361bb8ffe6f750a75e9143445ebdf1b`
  （`fix: 收敛沙盘当前机构口径`）：
  - `frontend/src/pages/sandbox/SandboxHost.tsx` 将运行准备不足提示从“演练机构尚未发布可用版本”收敛为
    “当前机构尚未发布可用版本”，并同步 `SandboxHost.test.tsx` 断言默认层不得出现“演练机构”。
  - `medkernel-backend/src/main/java/com/medkernel/engine/sandbox/**` 将沙盘目录、运行基线解析、历史重放和绑定模式中的客户可见 /
    可审计说明统一为“当前机构 / 当前机构生效版本”；历史重放跨机构错误改为“历史重放清单不属于当前机构”。
  - 五方言基线迁移与 `medkernel.schema.json` 将 `mk_sandbox_run.replay_case_id` COMMENT 收敛为
    “HISTORICAL_EXACT 或 COMPARE 使用的当前机构历史重放清单标识”，保持数据库权威注释与后端契约一致。
  - `SandboxScenarioControllerSecurityTest`、`SandboxRuntimeBaselineResolverTest` 补齐当前机构口径回归，并防止“演练机构”回流。
- 本地验证：
  - 红绿核验：`npm --prefix frontend test -- SandboxHost.test.tsx -t "uses dynamic runtime readiness"` 先失败于旧实现缺少
    “当前机构尚未发布可用版本”，实现后通过。
  - 红绿核验：`/Users/zhikunzheng/local/apache-maven-3.9.9/bin/mvn -Dtest=SandboxScenarioControllerSecurityTest,SandboxRuntimeBaselineResolverTest test`
    先失败于后端目录说明与历史重放错误仍使用“演练机构”，实现后 `9` 项通过。
  - `npm --prefix frontend test -- SandboxHost.test.tsx routes.test.ts menu.test.ts productRoleJourneys.test.ts`
    通过，`4` 个测试文件 / `74` 项。
  - `/Users/zhikunzheng/local/apache-maven-3.9.9/bin/mvn -Dtest='Sandbox*Test' test` 通过，`44` 项。
  - `npm --prefix frontend run verify` 通过，`114` 个测试文件 / `939` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `SandboxHost-Ceue0Cru.js` 与 `index-DR6cLAbA.js` 等前端产物。
  - `/Users/zhikunzheng/local/apache-maven-3.9.9/bin/mvn -DskipTests package` 通过，生成
    `medkernel-backend/target/medkernel-backend-1.0.0-SNAPSHOT.jar` 与 SBOM。
  - `node scripts/audit/export-product-capabilities.mjs --check` 通过。
  - `node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项。
  - `bash scripts/check-comment-zh.sh --mode=full` 通过；`git diff --check` 通过。
  - `rg -n "演练机构" frontend/src/pages/sandbox medkernel-backend/src/main/java/com/medkernel/engine/sandbox medkernel-backend/src/main/resources/db medkernel-backend/src/test/java/com/medkernel/engine/sandbox --glob '!**/target/**'`
    仅命中测试中的反向断言，生产前台、引擎代码与迁移 COMMENT 无“演练机构”残留。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 readiness HTTP 200 /
  `{"status":"UP"}`，后端 manifest 仍为 `source=3ddd979b3151e3eb1d40712e76b513e4cdce260c`、
  `commit=3ddd979b3151e3eb1d40712e76b513e4cdce260c`、`deployedAt=2026-07-03T12:38:13+08:00`、
  `jarSha256=37da0f4b8d42e040408ab530714f68228e8060a21f6690146d8cb58126ca96ec`，服务 `active/enabled`、
  `MainPID=3600701`、`NRestarts=0`。公网首页 `Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，
  `index.html` 指向 `/assets/index-DYTh-Ceu.js`，`/assets/index-DYTh-Ceu.js` HTTP 200 / `876520` 字节，
  `/assets/KnowledgeProduction-ClNuDXyb.js` HTTP 200 / `20735` 字节；因此 134 仍是
  后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`，
  不要把本地 `d6e6db93` 误写为已上线。
- 后续继续长目标：不再开子代理，不中途咨询；继续广度优先核查真实前台、职责旅程、构建门禁、134 证据映射和最终远程收口条件。

## 最新阶段交接（2026-07-03 全视角真实前台体验优化第四十七批·未找到页面工程态文案收敛）

- 本批在继续全角色前台体验核查时，从路由、页面标题和工程态旧词扫描发现 `/demo/step-flow` 等已移除路径的兜底页仍显示
  “此功能待 W3 业务域任务实装”。这是客户可见的阶段/工程话术，会误导为功能未交付，且与 `routes.ts` 的“未找到页面”权威标题不一致。
- 已本地提交 `eed002f1eeb880217c30525b1843c26bd4436e9c`
  （`fix: 收敛未找到页面工程态文案`）：
  - `frontend/src/pages/NotFound.tsx` 将 AntD `Result` 从 `info` 改为 `404`，标题收敛为语义二级标题“未找到页面”，说明改为
    “当前地址没有对应的业务页面，请返回工作台或通过菜单进入已授权功能。”，保留“返回工作台”恢复路径。
  - `frontend/src/app/router.test.tsx` 锁定已移除演示路径进入可恢复 404 页，并阻断 `W3 / 业务域任务实装` 工程话术回流。
- 本地验证：
  - 红绿核验：`npm --prefix frontend test -- router.test.tsx -t "routes a removed StepFlow demo URL"` 先失败于旧实现找不到“未找到页面”语义标题，
    实现后通过，`1` 项。
  - `npm --prefix frontend test -- router.test.tsx routes.test.ts menu.test.ts productRoleJourneys.test.ts AppLayout.test.tsx`
    通过，`5` 个测试文件 / `96` 项。
  - `npm --prefix frontend run verify` 通过，`114` 个测试文件 / `939` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `NotFound-Df-knVZ0.js` 与 `index-DokjENyf.js` 等前端产物。
  - `node scripts/audit/export-product-capabilities.mjs --check` 通过。
  - `node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项。
  - `bash scripts/check-comment-zh.sh --mode=full` 通过；`git diff --check` 通过。
  - `rg -n "此功能待 W3|业务域任务实装|待 W3|W3" frontend/src/pages frontend/src/app frontend/src/widgets docs/PRODUCT_SCOPE.md docs/CONSTITUTION.md docs/EXPERIENCE_CONTRACT.md docs/audit/product-function-catalog.md docs/audit/product-role-journeys.md --glob '!docs/_HANDOFF.md'`
    只命中 `router.test.tsx` 的反向断言，生产前台和权威文档无客户可见残留。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。134 仍按当前主线事实保持：
  后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`，前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`；不要把本地
  `eed002f1` 误写为已上线。
- 后续继续长目标：不再开子代理，不中途咨询；继续广度优先核查真实前台、职责旅程、构建门禁、134 证据映射和最终远程收口条件。

## 最新阶段交接（2026-07-03 全视角真实前台体验优化第四十六批·知识生产候选治理与规则路径前台文案收敛）

- 本批接续第四十五批，按用户关于“诊断知识维护”“临床路径模板是否像引用模板”“是否全部功能都评审和调整”的连续线索，回到
  `CONSTITUTION`、`PRODUCT_SCOPE`、`EXPERIENCE_CONTRACT`、功能目录与职责矩阵做全局判断。结论仍是：只改真实客户可见误解点，
  不把内部 API、受控配置、互操作对象、测试黑名单或确有模板含义的技术概念做无差别改名。
- 已本地提交 `24687d0fcb9cc48173a2005c490456008e8a7cad`
  （`fix: 收敛知识生产与规则路径前台文案`）：
  - `/knowledge/production` 将阶段和区块“开始生产”收敛为“候选治理”，匹配“大模型只生成候选、正式知识进入统一治理”的医疗安全边界；
    模型服务密钥变更/移除提示中的“探活”收敛为“健康检查”，保持与系统接入、模型服务治理的客户语言一致。
  - `/pathway/templates` 与 `/clinical/patient-pathways` 将客户面“临床路径模型 / 基础模板 / 平台标准模板 / 全模板 /
    稳定路径模型身份”等易误解表述收敛为“临床路径 / 基础信息 / 平台标准路径 / 全路径 / 稳定临床路径身份”；
    保留内部 `templateCode`、路径配置文本、结局绑定枚举等真实契约，不破坏版本生效与审计追溯。
  - `/rule/definitions` 将默认动作和一级页签“新建规则模板 / L1 模板”收敛为“新建临床规则 / L1 基础信息”，并把创建提示改为
    “规则原型只提供规则结构”；保留“受控配置文本 / 规则配置文本 / 解释模板 / 模板占位符”等确有高级配置或文本模板含义的术语。
  - 同步补齐 `KnowledgeProductionPage`、`ProviderSetupPanel`、`PathwayTemplates`、`PatientPathways`、`RuleDefinitions` 与
    `ruleLayeredEditor` 相关回归断言，旧词仅允许留在测试反向断言或黑名单中。
- 本地验证：
  - 红绿核验先覆盖目标断言：`候选治理`、`必须重新健康检查、评测并受控启用`、`新建临床路径`、
    `稳定临床路径身份`、`全路径`、`新建临床规则`、`L1 基础信息` 均先由目标测试暴露旧实现，再实现后通过。
  - `node scripts/audit/export-product-capabilities.mjs --check` 通过。
  - `npm --prefix frontend test -- routes.test.ts menu.test.ts productRoleJourneys.test.ts KnowledgeProductionPage.test.tsx ProviderSetupPanel.test.tsx PathwayTemplates.test.tsx RuleDefinitions.test.tsx PatientPathways.test.tsx WorkbenchPanel.test.tsx`
    通过，`9` 个测试文件 / `144` 项。
  - `npm --prefix frontend run verify` 通过，`114` 个测试文件 / `939` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `KnowledgeProduction-uCFbb5G9.js`、`PatientPathways-EaYebLkd.js`、
    `PathwayTemplates-BNcsNlMO.js`、`RuleDefinitions-Dj3O8w8D.js` 等前端产物。
  - `node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项。
  - `bash scripts/check-comment-zh.sh --mode=full` 通过；`git diff --check` 通过。
  - 旧词复扫 `诊断知识维护|临床路径模板|临床路径模型|路径模型|基础模板|平台标准模板|医院模板|科室模板|专科模板|全模板|开始生产|探活|发布治理|术语与字典|国产化自检|运行核查|新建规则模板|L1 模板`
    在生产前端、后端安全菜单、权威文档和审计脚本中无客户可见残留；命中仅为测试中的 `not.toBeInTheDocument()` / 黑名单断言。
- 134 证据映射：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮核实
  `origin/main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 readiness HTTP 200 /
  `{"status":"UP"}`，后端 manifest 仍为 `source=3ddd979b3151e3eb1d40712e76b513e4cdce260c`、
  `commit=3ddd979b3151e3eb1d40712e76b513e4cdce260c`、`deployedAt=2026-07-03T12:38:13+08:00`、
  `jarSha256=37da0f4b8d42e040408ab530714f68228e8060a21f6690146d8cb58126ca96ec`，服务 `active/enabled`、
  `MainPID=3600701`、`NRestarts=0`。公网首页 `Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，
  `index.html` 指向 `/assets/index-DYTh-Ceu.js`，`/assets/KnowledgeProduction-ClNuDXyb.js` HTTP 200 /
  `20735` 字节；因此 134 仍是后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、
  前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`，不要把本地 `24687d0f` 误写为已上线。
- 后续继续长目标：不再开子代理，不中途咨询；继续基于当前工作区和权威文件做全角色真实前台体验、134 证据映射、测试构建、
  部署和远程 `main` 收口核查。下一轮继续按“全局评审、只改真实误解点、保留真实契约术语”的口径推进。

## 最新阶段交接（2026-07-03 全视角真实前台体验优化第四十五批·国产化适配自检权威文案收敛）

- 本批接续第四十四批菜单命名收敛继续做权威口径复扫：应用主导航已统一，但
  `PRODUCT_SCOPE`、`CONSTITUTION`、第三方集成指南和运行保障证据仍残留“发布治理”“术语与字典”“国产化自检”等旧入口/短名。
  这些不是内部类名，而会影响后续接力、集成方阅读和运行保障报告，因此按当前医疗产品语言继续收敛。
- 已本地提交 `54a5161befb248970bc24ab645ebf97b73bbcfbf`
  （`fix: 收敛国产化适配自检权威文案`）：
  - `docs/PRODUCT_SCOPE.md` 将六层能力中的“发布治理”收敛为“版本生效治理”，
    将模型赋能矩阵“术语与字典”收敛为“术语字典”，将 S5/S6 场景名收敛为“临床规则 / 临床路径”，
    将平台管理客户任务中的“国产化自检”收敛为“国产化适配自检”。
  - `docs/CONSTITUTION.md` 入口承载规则同步“国产化适配自检”；`docs/contracts/integration/third-party-integration-guide.md`
    将集成方可见的“术语与字典页面”同步为“术语字典页面”。
  - `RuntimeProperties`、`RuntimeOperationsService`、`RuntimeOperationsController`、`application.yml`、
    `application-govcloud.yml`、前端导出校验和相关测试同步“国产化适配自检报告 / 证据”。
  - 保留“模型服务”“路径模板”“规则配置文本 / 路径配置文本”等仍有真实技术或受控配置含义的术语；
    未对内部 API、互操作标准对象、数据模型或测试黑名单做破坏性改名。
- 本地验证：
  - `rg -n "发布治理|术语与字典|规则配置|路径配置|国产化自检" docs --glob '!docs/_HANDOFF.md'`
    无命中。
  - `rg -n "国产化自检" frontend/src medkernel-backend/src/main medkernel-backend/src/test docs --glob '!docs/_HANDOFF.md'`
    无命中。
  - `npm --prefix frontend test -- operationalControlPages.test.tsx SystemProviders.test.tsx SecurityBaseline.test.tsx hooks.test.ts`
    通过，`4` 个测试文件 / `151` 项。
  - `/Users/zhikunzheng/local/apache-maven-3.9.9/bin/mvn -Dtest=RuntimeOperationsServiceTest,RuntimeOperationsControllerTest test`
    通过，`8` 项。
  - `npm --prefix frontend run verify` 通过，`114` 个测试文件 / `939` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `/Users/zhikunzheng/local/apache-maven-3.9.9/bin/mvn -DskipTests package` 通过，生成
    `medkernel-backend-1.0.0-SNAPSHOT.jar`。
  - `node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项。
  - `bash scripts/check-comment-zh.sh --mode=full` 通过；`git diff --check` 通过。
- 远端状态核查：本批只做本地提交，没有发布到 134、没有推送远程、没有合并 `main`。本轮读取核实
  `origin/main` 仍为 `1561ba6bef8777dcef76432696f43de4277fdd3f`；134 公网首页
  `https://193.112.107.134/medkernel/` HTTP 200，`Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`；
  正确 readiness 路径 `https://193.112.107.134/medkernel/actuator/health/readiness` HTTP 200 /
  `{"status":"UP"}`。134 仍是后端/JAR=`3ddd979b3151e3eb1d40712e76b513e4cdce260c`、
  前端 dist=`95bb816292f59833005df4761866dd9d89886cb4`；不要把本地 `54a5161b` 误写为已上线。
- 后续继续长目标：不再开子代理，不中途咨询；下一轮继续从全角色真实前台、权威文档、测试构建、134 证据映射和最终远程收口
  广度优先核查。若继续发现客户可见旧入口词，先判断是否为真实客户体验问题，再按最小上线级改动处理。

## 最新阶段交接（2026-07-03 全视角真实前台体验优化第四十四批·全局菜单命名与顺序收敛）

- 用户连续追问“诊断知识维护”菜单名、“临床路径模板”是否像引用模板、是否全部功能都评审和调整。结论：
  本批按 `CONSTITUTION`、`PRODUCT_SCOPE`、`EXPERIENCE_CONTRACT`、功能目录与职责矩阵做全局评审，覆盖所有主导航、
  职责旅程和客户可见一级入口；实际改动只落在客户容易误解的菜单名、顺序、页面标题、权限菜单与目录证据上，
  没有把内部 API、服务契约、历史场景编号、测试黑名单或高级证据字段做无差别改名。
- 已本地提交 `b80735791243e989c1253ae94a19aa25fa289553`
  （`fix: 优化全局菜单命名与顺序`）：
  - 知识治理菜单顺序统一为：`知识审核发布中心` → `机构生效版本` → `机构知识库` → `诊断知识库` →
    `术语字典` → `临床规则` → `临床路径库` → `来源与血缘` → `知识关系`。
  - 知识生产菜单统一为：`知识生产工作台` → `模型能力`；系统运维菜单统一为：
    `实施与验收` → `系统接入` → `运行保障` → `运行诊断` → `国产化适配自检`。
  - `诊断知识维护` 收敛为 `诊断知识库`，表达为知识域资产入口而不是后台维护动作；
    `临床路径模板`/`路径配置` 的客户可见入口收敛为 `临床路径库`，页面动作使用“新建临床路径”“临床路径版本”，
    避免误读成可被引用的模板库。`临床规则`、`术语字典`、`机构生效版本`、`知识审核发布中心`同步前端、
    后端权限菜单、功能目录和职责矩阵。
  - 保留 `模型能力` 与 `运行诊断`：前者符合模型供应、评测、脱敏和外调边界的能力治理语义；
    后者符合医疗系统运行证据与故障定位语义。未恢复旧式“模型服务”“运行核查”。
- 主要变更范围：
  - `frontend/src/shared/config/routes.ts`、`menu.test.ts`、`routes.test.ts`、`productRoleJourneys.ts` 与相关页面测试，
    统一主导航文案、导航排序和角色旅程断言。
  - `frontend/src/pages/quality/*`、`frontend/src/pages/tenant/*`、`frontend/src/pages/clinical/PatientPathways.tsx`、
    `frontend/src/pages/system/RuntimeDiagnostics.tsx`、`frontend/src/pages/advanced/DomesticCheck.tsx`、`frontend/src/widgets/WorkbenchPanel.tsx`
    同步页面标题、按钮、空态、抽屉和默认业务文案。
  - `medkernel-backend/src/main/java/com/medkernel/engine/security/MenuPermissionCatalog.java` 与
    `PermissionCode.java` 同步菜单权限展示名；`MenuPermissionCatalogTest`、`DefaultPermissionPolicyTest` 补齐菜单名与顺序回归。
  - `scripts/audit/export-product-capabilities.mjs` 与 `docs/audit/product-function-catalog.md`、`docs/audit/product-role-journeys.md`
    同步权威功能目录和角色旅程。
- 本地验证：
  - `node scripts/audit/export-product-capabilities.mjs --check` 通过。
  - `npm --prefix frontend test -- routes.test.ts menu.test.ts productRoleJourneys.test.ts AppLayout.test.tsx CommandPalette.test.tsx pages.smoke.test.tsx DiagnosisKnowledgeMaintenance.test.tsx KnowledgeProductionPage.test.tsx WorkbenchPanel.test.tsx KnowledgeGovernance.test.tsx operationalControlPages.test.tsx ReleaseGovernance.test.tsx PathwayTemplates.test.tsx RuleDefinitions.test.tsx Followup.test.tsx QcEvalSets.test.tsx RulePathwayCleanliness.test.ts`
    通过，`17` 个测试文件 / `268` 项。
  - `/Users/zhikunzheng/local/apache-maven-3.9.9/bin/mvn -Dtest=MenuPermissionCatalogTest,DefaultPermissionPolicyTest test`
    通过，`16` 项；`/Users/zhikunzheng/local/apache-maven-3.9.9/bin/mvn -Dtest=PermissionCodeTest,PermissionDimensionModelTest test`
    通过，`8` 项。
  - `npm --prefix frontend run verify` 通过，`114` 个测试文件 / `939` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过。
  - `/Users/zhikunzheng/local/apache-maven-3.9.9/bin/mvn test` 通过，`Tests run: 3063, Failures: 0, Errors: 0, Skipped: 7`；
    `7` 个跳过项仍为本地 Docker/Testcontainers 不可用导致的既有受控跳过。
  - `/Users/zhikunzheng/local/apache-maven-3.9.9/bin/mvn -DskipTests package` 通过，生成
    `medkernel-backend-1.0.0-SNAPSHOT.jar`。
  - `node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs`
    通过，`71` 项。
  - `bash scripts/check-comment-zh.sh --mode=full` 通过；`git diff --check` 通过。
  - 旧菜单名扫描仅剩 `frontend/src/shared/config/productRoleJourneys.test.ts` 中用于防回归的黑名单断言。
- 本批没有发布到 134，也没有推送远程或合并 `main`。134 仍按当前主线记录保持：
  后端/JAR 为 `3ddd979b3151e3eb1d40712e76b513e4cdce260c`，前端 dist 为
  `95bb816292f59833005df4761866dd9d89886cb4` 的前端-only 发布；不能把本地 `b8073579` 误写为已上线。
- 后续继续长目标：不再开子代理，不中途咨询；继续基于当前工作区和权威文件做全角色真实前台体验、134 证据映射、
  测试、构建、部署和远程 main 收口核查。下一轮如继续涉及客户可见 IA，仍按“全局评审、只改真实误解点”的口径处理。

## 最新阶段交接（2026-07-03 全视角真实前台体验优化第四十三批·知识生产工作区层级收敛）

- 用户关于“诊断知识维护和整体知识管理是否不契合、知识治理是否满足全链路核心知识生产管理”的问题只作为全局体验线索，
  不代表现行设计错误。本批回到 `PRODUCT_SCOPE`、功能目录与职责矩阵核对：诊断知识维护仍是统一知识治理下的内容域入口；
  `/knowledge/production` 承载来源登记、人工维护、模型候选、确定性校验、审核发布前的统一生产流水；模型只生成候选，
  正式知识不得绕过统一治理链。因此本批没有拆第二套知识管理，没有新增旧式“专家模式”，也没有片面改 IA。
- 真实可优化点落在 `/knowledge/production` 页面层级：页面把 `KnowledgeProductionWorkspace` 再包进标题为“开始生产”的
  `Card`，而工作区内部已经按“公域来源治理 / 双形态生产分区 / 初始化发行批次 / 模型生产上线准备”等业务分区自带卡片。
  这个外层卡片形成卡片套卡片，也让统一知识生产页面像临时拼接模块，不符合当前体验契约。
- 已本地提交 `95bb816292f59833005df4761866dd9d89886cb4`
  （`fix: 收敛知识生产工作区层级`）：
  - `frontend/src/pages/knowledge-production/KnowledgeProductionPage.tsx` 移除“开始生产”外层 `Card`，
    `section#production` 直接承载 `KnowledgeProductionWorkspace`，保留步骤条、上线准备、模型服务、医学评测和工作区内部业务分区。
  - `frontend/src/pages/knowledge-production/KnowledgeProductionPage.test.tsx` 增加红绿断言：
    生产工作区不得再被外层 `.ant-card` 包裹。
- 本地验证：
  - 目标测试先红灯：`npm --prefix frontend test -- KnowledgeProductionPage.test.tsx -t "does not wrap the production workspace"`
    在旧实现下命中外层 `.ant-card`；修复后同命令通过。
  - `npm --prefix frontend test -- KnowledgeProductionPage.test.tsx` 通过，`2` 项。
  - `npm --prefix frontend run verify` 通过，`114` 个测试文件 / `939` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `/assets/index-DYTh-Ceu.js` 与
    `/assets/KnowledgeProduction-ClNuDXyb.js`。
  - `git diff --check` 通过。
- 134 前端-only 发布与验证：
  - 已执行 `deploy/onprem/mk-publish.sh --frontend --source 95bb816292f59833005df4761866dd9d89886cb4`；
    远端备份 `/zoesoft/medkernel/backups/deploy-20260703-144653`。本次只更新前端 dist，后端/JAR manifest 仍是
    `/zoesoft/medkernel/manifest.properties` 记录的 `source=3ddd979b3151e3eb1d40712e76b513e4cdce260c`、
    `commit=3ddd979b3151e3eb1d40712e76b513e4cdce260c`、`deployedAt=2026-07-03T12:38:13+08:00`、
    `jarSha256=37da0f4b8d42e040408ab530714f68228e8060a21f6690146d8cb58126ca96ec`。
  - 远端服务 `active/enabled`、`MainPID=3600701`、`NRestarts=0`；公网 readiness HTTP 200 /
    `{"status":"UP"}`。外部 `index.html` `Last-Modified=Fri, 03 Jul 2026 06:46:50 GMT`，指向
    `/assets/index-DYTh-Ceu.js`；`/assets/KnowledgeProduction-ClNuDXyb.js` HTTP 200 / `20735` 字节。
- 134 真实前台复演与页面层级回证：
  - 首次复跑 `stakeholder-view-rehearsal.spec.ts` 未带 READY `E2E_ROLE_CREDENTIALS_FILE`，触发默认账号登录
    `401 ENG-AUTH-001`；根因是验证输入错误，不是产品流程失败。随后确认
    `/tmp/medkernel-e2e-codex3/secure/current-launch.json` 为 `schemaVersion=1.0.0`、`status=READY`，
    `platform.accounts` 与 `rehearsal.accounts` 均包含四职责账号。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-95bb8162-ready`
    通过 134 HTTPS 入站运行 `stakeholder-view-rehearsal.spec.ts --project=chromium`，`1 passed (1.3m)`；
    report stats 为 `expected=1`、`skipped=0`、`unexpected=0`、`flaky=0`、`duration=78602.672ms`。
    运行记录覆盖 `12` 类业务视角，浏览器错误、HTTP 错误、网络失败均为 `0`。
  - 直连 `/knowledge/production` DOM 检查确认：`section#production` 存在，直接子元素不是 `.ant-card`，
    默认页面不存在“开始生产”外层卡片标题，仍可见“模型生产上线准备”；工作区内部业务卡片保留。
- 后续继续主线全局体验优化：本批只收敛知识生产工作区的页面层级，不改变统一知识治理与诊断知识维护的产品归属判断。
  下一轮继续从全角色真实前台、知识 11 域生产/治理、模型公网/院内双模式与患者敏感信息处理、医保质控闭环、系统接入、
  迁移、文档、测试、构建和部署证据广度优先核查；用户提问是线索，仍需按原始产品诉求和权威文档全局判断。

## 最新阶段交接（2026-07-03 全视角真实前台体验优化第四十二批·系统接入健康检查默认文案收敛）

- 本批继续按原始产品目标和权威文档全局判断：不因“诊断知识维护/知识治理是否怪”的疑问拆出第二套知识系统；
  也不恢复旧式“专家模式”。在 134 复演、页面截图和同口径可见文本扫描后，真实可优化点落在 `/adapter/hub`：
  信息科长与实施工程师默认视角仍看到 `RTT`、`最近探活/探活事实` 等工程排障词，容易把系统接入工作台误读为技术日志页。
  `HTTP`、`FHIR`、`Webhook`、`WebService`、`REST` 属于系统接入契约必要术语，本批保留。
- 已本地提交 `2dbd668fde81540a4fc19ce9acd38b931cf7c2d2`
  （`fix: 收敛系统接入健康检查默认文案`）：
  - `frontend/src/pages/tenant/AdapterHub.tsx` 将默认表格、连通性结果、质量报告空态和字段映射面板里的
    `RTT` / `最近探活` / `探活事实` 收敛为 `响应耗时` / `最近健康检查` / `健康检查事实`；
    连接状态说明改为“实时健康检查”，保留协议名和高级证据字段。
  - `frontend/src/pages/tenant/AdapterHub.test.tsx` 增加红绿断言：默认工作台必须出现“响应耗时”“最近健康检查”，
    且不再出现 `RTT`、`最近探活`。
- 本地验证：
  - 目标测试先红灯：`npm --prefix frontend test -- AdapterHub.test.tsx -t "renders the unified adapter workspace"`
    在旧实现下找不到“响应耗时”；修复后同命令通过。
  - `npm --prefix frontend test -- AdapterHub.test.tsx` 通过，`22` 项。
  - `npm --prefix frontend run verify` 通过，`114` 个测试文件 / `938` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。
  - `npm --prefix frontend run build` 通过，生成 `/assets/index-DQOxo0OJ.js` 与
    `/assets/AdapterHub-Dx-tUxAO.js`。
  - `git diff --check` 通过。
- 134 前端-only 发布与验证：
  - 已执行 `deploy/onprem/mk-publish.sh --frontend --source 2dbd668fde81540a4fc19ce9acd38b931cf7c2d2`；
    远端备份 `/zoesoft/medkernel/backups/deploy-20260703-135615`。本次只更新前端 dist，后端/JAR manifest 保持
    `3ddd979b3151e3eb1d40712e76b513e4cdce260c` 与 jar 指纹
    `37da0f4b8d42e040408ab530714f68228e8060a21f6690146d8cb58126ca96ec`。
  - 远端服务 `active/enabled`、`MainPID=3572266`、`NRestarts=0`；公网 readiness HTTP 200 /
    `{"status":"UP"}`。外部 `index.html` `Last-Modified=Fri, 03 Jul 2026 05:56:08 GMT`，指向
    `/assets/index-DQOxo0OJ.js`；`/assets/AdapterHub-Dx-tUxAO.js` HTTP 200 / `41243` 字节。
- 134 真实前台复演与扫描回证：
  - 直连 `/adapter/hub` 页面文本检查确认 `hasResponseDuration=true`、`hasLatestHealthCheck=true`、
    `hasRtt=false`、`hasOldProbeText=false`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-2dbd668f`
    通过 134 HTTPS 入站运行 `stakeholder-view-rehearsal.spec.ts --project=chromium`，`1 passed (1.4m)`；
    report stats 为 `expected=1`、`skipped=0`、`unexpected=0`、`flaky=0`、`duration=82105.601ms`。
    十二类业务视角运行记录均无浏览器错误、HTTP 错误、网络失败；信息科长与实施工程师均完成系统接入数据质量报告生成动作。
  - 可见文本扫描 `/tmp/medkernel-e2e-codex3/visible-technical-scan-2dbd668f.json` 覆盖 `16` 个页面，
    `completed=16`、`pagesWithErrors=0`、`pagesWithMatches=0`、`unacceptedMatches=0`；仅 `/adapter/hub`
    命中 `HTTP`、`FHIR`、`Webhook`、`WebService`、`REST` 五个已接受协议术语。
- 后续继续主线全局体验优化：第四十二批只是系统接入默认语言的一处真实收敛，不代表长目标完成。下一轮继续深读
  `PRODUCT_SCOPE`、功能目录、职责矩阵和现有真实复演证据，从知识生产/治理是否覆盖全链路核心知识、模型公网/院内双模式、
  医患护药技质控医保信息实施院长等角色体验、迁移、文档、构建和部署证据继续广度优先核查；用户问题只作为线索，
  不片面推翻已符合原始诉求的设计。

## 最新阶段交接（2026-07-03 全视角真实前台体验优化第四十一批·模型外调核心标识遮蔽与134全量复演）

- 用户补充“大模型双模式与公网部署也可能使用患者信息”，并再次强调诊断知识维护/知识治理疑问只是全局考量输入，
  不代表现行设计错误。本批先回到 `CONSTITUTION`、`PRODUCT_SCOPE`、`EXPERIENCE_CONTRACT`、功能目录与职责矩阵：
  诊断知识维护仍属于统一知识治理；知识生产/维护/来源/诊断知识不应拆成第二套知识系统，也不应恢复旧式“专家模式”。
  真实缺口在模型网关外调安全：公网模型可在授权场景使用患者上下文，但必须最小化并遮蔽核心敏感标识；院内本地模型可使用必要敏感信息，
  但同样要经过授权、边界和审计。
- 已本地提交 `3ddd979b3151e3eb1d40712e76b513e4cdce260c`
  （`fix: 强化模型外调核心标识遮蔽`）：
  - `ModelEgressGuard` 原有直接标识字段覆盖 `patientId`、`mpiId` 等常见字段，但未覆盖院内真实结构化上下文常见写法
    `patientMpi`、`mrn`、`medicalRecordNo`、`encounterId`、`visitNo` 等；当脱敏操作配置为 `NONE` 时，这些字段可能作为
    结构化上下文原样外调。
  - `medkernel-backend/src/main/java/com/medkernel/engine/llm/egress/ModelEgressGuard.java`
    扩展直接标识字段集，覆盖 `patientmpi`、`patientno`、`patientcode`、`patientnumber`、`mpi`、`mrn`、
    `medicalrecordno`、`medicalrecordnumber`、`chartno`、`recordno`、`encounterid`、`encounterno`、
    `visitid`、`visitno`、`admissionno`、`inpatientno`、`outpatientno`，以及中文 `院内患者编号`、`病历号`、
    `就诊号`、`住院号`、`门诊号`。
  - `medkernel-backend/src/test/java/com/medkernel/engine/llm/egress/ModelEgressGuardTest.java`
    增加红绿用例 `noneOperatorMasksHospitalPatientIdentifiersInStructuredContext`：结构化 `clinicalContext` 在规则为
    `NONE` 时仍必须清空院内患者/就诊核心标识，同时保留 `ageYears`、`diagnosisText` 等必要非核心敏感临床上下文。
- 本地验证：
  - 目标测试先红灯：`mvn -Dtest=ModelEgressGuardTest#noneOperatorMasksHospitalPatientIdentifiersInStructuredContext test`
    在旧实现下失败，返回 payload 仍含 `patientMpi=mpi-20260703001` 等标识；修复后同命令通过。
  - `mvn -Dtest=ModelEgressGuardTest test` 通过，`16` 项。
  - `mvn -Dtest=ModelGatewayServiceTest,KnowledgeProductionReadinessServiceTest,ModelKnowledgeProducerTest,ModelEgressGovernanceServiceTest test`
    通过，`72` 项。
  - `mvn test` 通过，`Tests run: 3062, Failures: 0, Errors: 0, Skipped: 7`；`7` 个跳过项仍为本地
    Docker/Testcontainers 不可用导致的既有受控跳过。
  - T-GATE：`node --test scripts/authenticity-guard.test.mjs` 通过 `51` 项；
    `node --test scripts/config-boundary-guard.test.mjs` 通过 `2` 项；
    `node --test scripts/migration-convention-guard.test.mjs` 通过 `14` 项；
    `node --test scripts/performance-contract-guard.test.mjs` 通过 `4` 项；`git diff --check` 通过；
    `bash scripts/check-comment-zh.sh --mode=full` 通过；`mvn -DskipTests package` 通过并生成
    `medkernel-backend-1.0.0-SNAPSHOT.jar`。
- 134 全量发布与外部入口验证：
  - 已执行 `deploy/onprem/mk-publish.sh --source 3ddd979b3151e3eb1d40712e76b513e4cdce260c`；
    远端备份 `/zoesoft/medkernel/backups/deploy-20260703-123810`，jar 指纹
    `37da0f4b8d42e040408ab530714f68228e8060a21f6690146d8cb58126ca96ec`，manifest 与前端 dist 均绑定
    `3ddd979b3151e3eb1d40712e76b513e4cdce260c`。
  - 远端服务 `active/enabled`、`MainPID=3529053`、`NRestarts=0`；本机和公网 readiness 均 HTTP 200 /
    `{"status":"UP"}`。`https://193.112.107.134/medkernel/` HTTP 200，`Last-Modified=Fri, 03 Jul 2026 04:36:46 GMT`，
    安全响应头包含 `X-Content-Type-Options`、`X-Frame-Options`、`Referrer-Policy`、`Strict-Transport-Security`。
  - 线上关键资源均 HTTP 200：`index-BncdEiiY.js` `876520` 字节，`AiWorkflows-CCyQ-Uj6.js` `21053` 字节，
    `DiagnosisKnowledgeMaintenance-C-32OJv0.js` `24561` 字节，`TerminologyMapping-DBXa5VBu.js` `25829` 字节，
    `AdminAudit-D9bSrb46.js` `21901` 字节，`Provenance-jJCIbbSY.js` `12662` 字节。
- 134 真实前台复演：
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-3ddd979b-134-final-core`
    通过 134 HTTPS 入站运行
    `d6-ai-workflows.spec.ts`、`diagnosis-knowledge-maintenance.spec.ts`、`real-frontdesk-rehearsal.spec.ts`、
    `stakeholder-view-rehearsal.spec.ts`，`5 passed (2.5m)`；report stats 为 `expected=5`、`skipped=0`、
    `unexpected=0`、`flaky=0`、`duration=149951.092ms`。
  - 全前台运行记录覆盖 `11` 段真实页面提交路线：系统接入适配器、接入申请、知识值集草稿、模型安全边界、脱敏患者 MPI、
    随访模板创建/发布、当前就诊上下文与医保结算事实快照、医保审核联动质量整改、CDSS 推荐评估、随访计划/问卷/异常回院；
    浏览器错误、HTTP 错误、网络失败均为 `0`。
  - 十二类业务视角运行记录覆盖医生、护士、药师、医技、质控、患者代理、平台管理员、医疗引擎运营员、审计员、信息科长、
    实施工程师、院长；每个视角均有真实前台动作，浏览器错误、HTTP 错误、网络失败均为 `0`。
- 后续继续主线全局体验优化：本批确认知识治理/诊断知识维护总体方向不拆分，并补齐模型外调核心标识遮蔽的真实缺口；
  长目标仍未完成。下一轮继续从全角色真实前台、知识生产/治理、模型公网/院内双模式、患者敏感信息处理、质量医保整改、
  系统接入、权限职责、迁移、文档、构建和部署证据继续广度优先核查，不因单个用户疑问片面改结构。

## 最新阶段交接（2026-07-03 全视角真实前台体验优化第四十批·默认可见低频标识清零）

- 用户明确“知识治理/诊断知识维护的问题只是疑问，不代表当前设计实现错误”，本批继续按 `CONSTITUTION`、
  `PRODUCT_SCOPE`、`EXPERIENCE_CONTRACT`、功能目录、职责矩阵和 134 真实前台证据判断；未拆分第二套知识管理，
  未回退统一知识治理，也未新建旧式“专家模式”。真实问题来自同口径可见文本扫描：默认页面层仍有少量前台演练后缀
  或内部临床对象标识泄漏，应按“默认业务可读、证据详情可追溯”的统一体验原则收敛。
- 已本地提交 `70a0925cda2b642665ac930396c2d1e3eec06db1`
  （`fix: 收敛审计摘要临床内部标识`）：
  - 基于 `/tmp/medkernel-e2e-codex3/visible-technical-scan-f2590325.json` 复扫，发现 `/admin/audit`
    默认审计事项中出现 `patient=mpi-*`、`quality=VALID` 等标准上下文内部线索。
  - `frontend/src/pages/compliance/AdminAudit.tsx` 仅调整默认摘要展示：`quality=VALID` 显示为“质量已通过”，
    `patient=mpi-*` 显示为“患者已关联”，通用临床内部对象显示为“已记录临床对象”；详情抽屉仍保留原始摘要、
    业务对象和追踪字段，审计追溯不丢。
  - `frontend/src/pages/compliance/AdminAudit.test.tsx` 增加红绿用例，确认默认层不暴露 `quality=VALID` /
    `mpi-*`，打开“证据详情”后仍可追溯原始值。
- 已本地提交 `8889efc754b6c192708ddb118a5b9fa7d03cb28e`
  （`fix: 隐藏来源血缘演练版次标识`）：
  - 70a 发布后同口径扫描 `/tmp/medkernel-e2e-codex3/visible-technical-scan-70a0925c.json` 显示
    `/advanced/provenance` 默认版本沿革仍出现 `frontdesk-mr4azc3b`。这不是知识治理 IA 错误，而是既有
    技术版次识别规则未覆盖真实前台演练批次后缀。
  - `frontend/src/pages/advanced/Provenance.tsx` 将
    `patient_proxy-*` / `real_frontdesk-*` / `stakeholder-*` / `frontdesk-*`
    纳入现有技术版次识别规则；默认显示“候选版本 / 版本来源已记录”，证据详情打开后展示原始版次。
  - `frontend/src/pages/advanced/Provenance.test.tsx` 扩展红绿用例，覆盖前台演练后缀默认隐藏和详情追溯。
- 本地验证：
  - `npm --prefix frontend test -- AdminAudit.test.tsx -t "默认将审计摘要中的低频对象编号"` 红灯后转绿；
    `npm --prefix frontend test -- AdminAudit.test.tsx` 通过，`13` 项。
  - `npm --prefix frontend test -- Provenance.test.tsx -t "默认隐藏版本沿革中的技术版次和前台演练后缀"` 红灯后转绿；
    `npm --prefix frontend test -- Provenance.test.tsx` 通过，`3` 项。
  - 最终 `npm --prefix frontend run verify` 通过，`114` 个测试文件 / `938` 项；保留既有 AntD
    `Timeline.Item` deprecation warning。最终 `npm --prefix frontend run build` 通过，生成
    `/assets/AdminAudit-D9bSrb46.js` 与 `/assets/Provenance-jJCIbbSY.js`。
- 134 发布与现场验证：
  - 中间已发布 `70a0925c`，备份 `/zoesoft/medkernel/backups/deploy-20260703-111144`；随后最终发布
    `8889efc754b6c192708ddb118a5b9fa7d03cb28e`，命令
    `deploy/onprem/mk-publish.sh --frontend --source 8889efc754b6c192708ddb118a5b9fa7d03cb28e`。
  - 最终远端备份 `/zoesoft/medkernel/backups/deploy-20260703-112621`；readiness HTTP 200 /
    `{"status":"UP"}`；服务 `active/enabled`、`MainPID=3489204`、`NRestarts=0`；后端 manifest/JAR 仍为
    `ef662ced1ca68723bed92aabd66440a833fde4b3`，jar sha256 仍为
    `ef79a21c1ccc2700a787d67bd4d81685a8a4119d9b0612210e598b25ea47efce`。
  - 外部 HTTPS 入口验证：`/medkernel/actuator/health/readiness` 返回 `{"status":"UP"}`；
    `index.html` 指向 `/assets/index-BncdEiiY.js`；`/assets/AdminAudit-D9bSrb46.js` 与
    `/assets/Provenance-jJCIbbSY.js` 均 HTTP 200，`Last-Modified=2026-07-03 03:26:15 GMT`。
- 134 演练与扫描回证：
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-diagnosis-maintenance-8889efc7`
    通过 134 HTTPS 入站运行 `diagnosis-knowledge-maintenance.spec.ts --project=chromium`，`1 passed (12.3s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`、`duration=12323.155ms`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-8889efc7`
    通过 134 HTTPS 入站运行 `real-frontdesk-rehearsal.spec.ts --project=chromium`，`1 passed (39.5s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`、`duration=39519.27ms`；运行记录覆盖 `11`
    段真实前台数据路线，错误合计 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-8889efc7`
    通过 134 HTTPS 入站运行 `stakeholder-view-rehearsal.spec.ts --project=chromium`，`1 passed (1.3m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`、`duration=77951.433ms`；运行记录覆盖
    `12` 类业务视角（医生、护士、药师、医技、质控、患者代理、平台管理员、医疗引擎运营员、审计员、信息科长、
    实施工程师、院长），错误合计 `0`。
  - 最终复演后扫描 `/tmp/medkernel-e2e-codex3/visible-technical-scan-8889efc7-after-e2e.json`：
    `16` 个页面全部完成，`pagesWithErrors=0`、`pagesWithMatches=0`、`unacceptedMatches=0`；仅
    `/adapter/hub` 的协议术语 `Webhook` 作为既有系统接入术语接受，`acceptedMatches=1`。
- 后续继续主线全局体验优化：本批只证明默认可见低频技术/内部标识已按当前扫描口径清零，并不代表长目标完成。
  下一轮继续从医生、护士、患者/代理、药师、医技、医保办、质控、信息科、实施、院长、平台治理、知识生产运营视角，
  对知识生产/维护/来源/诊断知识、模型公网/内网双模式与患者敏感信息处理、质控医保整改闭环、系统接入阻断、
  权限职责、上线门禁、迁移和文档契约一致性做真实前台体验与代码核查。

## 最新阶段交接（2026-07-03 全视角真实前台体验优化第三十九批·诊断知识前台产数闭环）

- 用户强调“知识治理/诊断知识维护只是疑问，不代表当前设计实现错误”，本批继续按 `CONSTITUTION`、
  `PRODUCT_SCOPE`、`EXPERIENCE_CONTRACT`、功能目录和职责矩阵判断：诊断知识维护仍应归入统一知识治理，
  不是拆第二套知识管理；真实缺口在于诊断标准维护仍容易让运营员手输发现项编码，导致标准术语权威维护与诊断标准引用割裂。
  因此本批只补齐“前台登记标准术语 -> 诊断标准选择已生效标准术语 -> 验证病例复算”的真实产数闭环，
  不改变知识治理 IA、不新增旧式“专家模式”、不改后端知识身份归属。
- 已本地提交 `f461a1c50653725569c60e148808d69c5097f620`
  （`feat: 补齐标准术语前台登记链路`）：
  - `frontend/src/pages/tenant/TerminologyMapping.tsx` 新增“登记标准术语”前台弹窗，可维护标准体系、标准编码、
    术语类别、标准名称、规范名称、版本号和依据说明；共享 `TERM_CATEGORY_OPTIONS`，避免页面自建分类口径。
  - `frontend/src/shared/api/hooks.ts` 新增 `useRegisterStandardTerm`，调用
    `/engine/terminology/terms/standard` 并携带统一标准上下文；成功后刷新标准字典。
  - `frontend/src/pages/quality/DiagnosisKnowledgePanel.tsx` 将“标准发现项身份”从手输编码改为已生效标准术语远程搜索选择，
    默认只展示业务名称，证据详情中仍可追溯标准体系与术语编码。
  - `frontend/e2e/diagnosis-knowledge-maintenance.spec.ts` 改为先从“术语与字典”前台登记标准术语，再进入“诊断知识维护”
    选择该标准术语新增诊断标准和验证病例，避免演练数据绕过前台或依赖预置码。
- 线上首次部署 `f461a1c5` 后，诊断专项 E2E 在标准术语登记 POST 处返回 `400 ENG-API-001`；
  根因不是产品结构问题，而是后端 `spring.jackson.deserialization.fail-on-unknown-properties=true` 且术语写入 DTO
  未声明 `ward_id`，前端统一标准上下文把安全画像中的病区字段也带入请求。已本地提交
  `f259032508da61e1e466070cbbacab5e04cb37fb`（`fix: 对齐标准上下文严格契约`）：
  - 通用 `standardApiContext` 不再发送未被标准写入 DTO 声明的 `ward_id`。
  - 临床上下文快照请求仍显式保留 `ward_id`，因为 `ContextSnapshotRequest` 已声明该字段，且 `orgUnitId`
    继续按最细组织范围取病区。
  - `frontend/src/shared/api/hooks.test.ts` 增加病区场景断言：标准术语登记请求不带 `ward_id`，前台临床快照请求保留
    `ward_id` 与病区 `orgUnitId`。
- 已本地提交 `f98997cd2c1d2c94a7cf74b4f4fceef44c37a1ef`
  （`test: 稳定诊断知识前台演练确认按钮`）：诊断专项 E2E 兼容 AntD 默认确认按钮可访问名称 `确 定`。
  这是测试点击器稳定性修正，不改变产品代码；真实用户点击不受影响。
- 红绿验证与构建：
  - `useRegisterStandardTerm` 请求契约先在旧实现下红灯，暴露 `ward_id` 会进入严格写请求；修复后目标测试通过。
  - `npm --prefix frontend test -- hooks.test.ts` 通过，`127` 项。
  - `npm --prefix frontend test -- TerminologyMapping.test.tsx`、`npm --prefix frontend test -- DiagnosisKnowledgePanel.test.tsx`
    在本批相关改动后均通过；合计覆盖标准术语登记、诊断标准标准术语选择和页面交互。
  - `npm --prefix frontend run verify` 通过，`114` 个测试文件 / `938` 项；保留既有 AntD `Timeline.Item`
    deprecation warning。`npm --prefix frontend run build` 通过，生成
    `/assets/DiagnosisKnowledgeMaintenance-Bw9vAZIW.js` 与 `/assets/TerminologyMapping-yJ8Jhg-x.js`。
- 134 发布与现场验证：
  - 已执行 `deploy/onprem/mk-publish.sh --frontend --source f259032508da61e1e466070cbbacab5e04cb37fb`；
    这是前端-only 发布，远端 manifest 仍正确显示完整后端/JAR 部署提交
    `ef662ced1ca68723bed92aabd66440a833fde4b3`。
  - 远端备份 `/zoesoft/medkernel/backups/deploy-20260703-101136`；外部 readiness HTTP 200 /
    `{"status":"UP"}`；服务 `active/enabled`、`MainPID=3447816`、`NRestarts=0`；后端 jar sha256 仍为
    `ef79a21c1ccc2700a787d67bd4d81685a8a4119d9b0612210e598b25ea47efce`。
  - 线上 `index.html` 指向 `/assets/index-CoUz4TdF.js`；
    `/medkernel/assets/DiagnosisKnowledgeMaintenance-Bw9vAZIW.js` 与
    `/medkernel/assets/TerminologyMapping-yJ8Jhg-x.js` 均 HTTP 200，`Last-Modified=2026-07-03 02:11:30 GMT`。
- 134 演练回证：
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-diagnosis-maintenance-f2590325b`
    通过 134 HTTPS 入站运行 `diagnosis-knowledge-maintenance.spec.ts --project=chromium`，`1 passed (13.5s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`、`duration=13454.458ms`。证据截图：
    `.../diagnosis-knowledge-maintenance.png`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-f2590325`
    通过 134 HTTPS 入站运行 `real-frontdesk-rehearsal.spec.ts --project=chromium`，`1 passed (39.8s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`、`duration=39806.174ms`。运行记录覆盖
    `11` 段真实前台数据路线，错误合计 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-f2590325`
    通过 134 HTTPS 入站运行 `stakeholder-view-rehearsal.spec.ts --project=chromium`，`1 passed (1.3m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`、`duration=78361.47ms`。运行记录覆盖 `12`
    类业务视角，错误合计 `0`。
- 后续继续主线全局体验优化：不要把本批诊断知识前台闭环理解成只优化用户点名的问题；下一轮继续按医生、护士、
  患者/代理、药师、医技、医保办、质控、信息科、实施、院长、平台治理和知识生产运营视角，从最新 134 真实前台、
  全职责证据和可见文本扫描继续检查知识治理生产/维护/来源/诊断知识、模型公网/内网双模式与患者敏感信息处理、
  质控医保整改闭环、系统接入阻断、权限职责、默认信息层级、上线门禁和文档/契约一致性。本批未重跑独立可见技术词扫描；
  后续复用既有扫描口径执行，不临时发明第二套扫描规则。

## 最新阶段交接（2026-07-03 全视角真实前台体验优化第三十八批·审计导出记录默认标识收敛）

- 用户再次强调“刚才的问题只是疑问，不代表当前设计实现错误”，本批继续按 `CONSTITUTION`、`PRODUCT_SCOPE`、
  `EXPERIENCE_CONTRACT`、功能目录、职责矩阵和 134 真实前台证据判断：知识治理、诊断知识维护和来源血缘结构仍符合
  原始诉求，不因单个问题拆新体系；真正需要收敛的是 `/admin/audit` 导出记录列表默认层仍展示
  `exp-audit-event-...` 一类低频审计导出编号，会让审计员把业务导出记录误读成技术日志。原始确认编号、证据编号、
  export digest 必须继续保留在“证据详情”和导出证据弹窗内，供审计追溯。
- 已本地修复并提交 `720a0150abdadb7d67832289181bb18f40ce714e`
  （`fix: 收敛审计导出记录默认标识`）：
  - `frontend/src/pages/compliance/AdminAudit.tsx` 新增审计导出确认编号识别，将
    `exp-audit-event-<uuid>`、`exp-audit-event-<uuid>-export`、`evd-exp-audit-event-<uuid>-confirmation/export`
    默认显示为“审计导出任务”。
  - 导出记录列表副文本、生成导出文件按钮 `aria-label`、查看证据按钮 `aria-label` 均随证据详情状态切换：
    默认使用业务标签，开启“证据详情”后恢复完整 `confirmationId`。
  - 本批不改后端审计/导出 API、不改证据归档、不改确认导出和验签流程；只调整默认展示层和可访问名称。
- 红绿验证与构建：
  - 新增 `frontend/src/pages/compliance/AdminAudit.test.tsx` 用例
    “默认将导出记录中的审计导出编号收进证据详情”，先在旧实现下红灯，失败点为默认层找不到“审计导出任务”。
  - 目标用例修复后通过；`npm --prefix frontend test -- AdminAudit.test.tsx` 通过，`13` 项。
  - `npm --prefix frontend run typecheck`、`npm --prefix frontend run format:check` 通过。
  - `npm --prefix frontend run verify` 通过，`114` 个测试文件 / `934` 项；保留既有 Antd `Timeline.Item`
    deprecation warning。`npm --prefix frontend run build` 通过，生成审计 chunk `/assets/AdminAudit-BO1NBPCu.js`。
- 134 发布与现场验证：
  - 已执行 `deploy/onprem/mk-publish.sh --frontend --source 720a0150abdadb7d67832289181bb18f40ce714e`；
    这是前端-only 发布，远端 manifest 仍正确显示完整后端/JAR 部署提交
    `ef662ced1ca68723bed92aabd66440a833fde4b3`。
  - 远端备份 `/zoesoft/medkernel/backups/deploy-20260703-001946`；外部 readiness HTTP 200 /
    `{"status":"UP"}`；服务 `active/enabled`、`MainPID=3123231`、`NRestarts=0`。
  - 线上 `index.html` 指向 `/assets/index-Smc9w3rX.js`；`/assets/AdminAudit-BO1NBPCu.js`
    含“审计导出任务”、`exp-audit-event` 识别、`查看证据` 与 `生成导出文件` 新标签逻辑。
- 全角色演练契约同步：
  - 首次用 `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-720a0150`
    跑全角色 134 演练时，审计员步骤已通过，但后续信息科长视角失败；根因为审计员步骤打开了全局“证据详情”，同一浏览器上下文
    继续带到下一角色，导致系统接入默认层断言进入高级证据层。这不是系统接入页面能力退化。
  - 已本地提交 `598e3fc6fd75f121283aefcfd5edfe2a2f2272e5`
    （`test: 隔离全角色证据详情状态`）：每个角色视角进入页面后先复位“证据详情”为默认业务层；审计员步骤显式断言默认层不显示
    `confirmationId`，用“审计导出任务”完成生成文件，再打开证据详情断言完整 `confirmationId` 可追溯并完成验签。
  - `npm --prefix frontend run typecheck`、`npm --prefix frontend run format:check` 对该 E2E 契约修改通过。
- 134 全流程回证：
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-720a0150-rerun`
    通过 134 HTTPS 入站运行 `stakeholder-view-rehearsal.spec.ts --project=chromium`，`1 passed (1.3m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`、`duration=77065.305ms`。运行记录覆盖
    `PHYSICIAN`、`NURSE`、`PHARMACIST`、`MEDICAL_TECHNICIAN`、`QUALITY_CONTROLLER`、`PATIENT_PROXY`、
    `PLATFORM_ADMIN`、`ENGINE_OPERATOR`、`AUDITOR`、`IT_MANAGER`、`IMPLEMENTATION_ENGINEER`、
    `HOSPITAL_EXECUTIVE` 共 `12` 类视角；浏览器错误、HTTP 错误、网络失败均为 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-720a0150`
    通过 134 HTTPS 入站运行 `real-frontdesk-rehearsal.spec.ts --project=chromium`，`1 passed (36.8s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`、`duration=36776.065ms`。运行记录覆盖
    `11` 段真实前台数据路线：系统接入适配器、接入申请、知识值集草稿、模型安全边界、脱敏患者 MPI、随访模板创建/发布、
    当前就诊上下文与医保结算事实快照、医保审核联动质量整改、CDSS 推荐评估、随访计划/问卷/异常回院；错误合计 `0`。
  - 重新运行可见技术词扫描，文件 `/tmp/medkernel-e2e-codex3/visible-technical-scan-720a0150.json`；`16` 个页面全部完成，
    页面错误 `0`，非接受匹配 `0`；仅 `/adapter/hub` 保留协议词 `Webhook`，按产品和路由契约接受。
- 后续继续主线全局体验优化：不要把“知识治理是否奇怪”的疑问当作拆体系结论；继续按全角色、全真实前台、全链路证据检查
  知识治理生产/维护/来源/诊断知识、模型公网/内网双模式与患者敏感信息处理、临床医生/护士/患者代理/药师/医技路径、
  质控医保整改闭环、系统接入阻断、权限职责、默认信息层级、上线门禁、文档/契约/迁移一致性。

## 最新阶段交接（2026-07-03 全视角真实前台体验优化第三十七批·审计摘要默认标识收敛）

- 用户强调“知识治理疑问只是疑问，不能盲目片面修改”，本批继续先对照 `CONSTITUTION`、`PRODUCT_SCOPE`、
  `EXPERIENCE_CONTRACT`、功能目录、职责矩阵和路由契约判断：`/admin/audit` 是平台治理审计入口，默认层应服务信息科、
  实施、审计员、平台治理和院长快速理解“谁在什么业务对象上做了什么”，不应默认暴露低频对象编号、长 hash、模型运行号或
  CDSS 上下文 ID；原始标识仍必须保留在证据详情中供审计追溯。`/adapter/hub` 默认出现 `Webhook` 属于受支持接入协议，
  与产品范围和接入契约一致，本批不改。
- 基于第三十六批后 134 线上可见文本扫描继续做全角色默认层检查，扫描文件
  `/tmp/medkernel-e2e-codex3/visible-technical-scan-cb768abf.json` 覆盖 `16` 个页面且页面错误为 `0`；
  其中 `/admin/audit` 默认摘要仍可见 `exp-audit-event-...`、`FUP.STAKEHOLDER...`、`CDSS-MANUAL-...`、
  `stakeholder-ollama-...` 等真实前台操作产生的底层标识。这会误导业务用户把审计摘要理解成技术日志，不符合
  “技术对象默认收进高级信息/证据详情”的新定义；但审计证据归属和后端契约本身没有错误。
- 已本地修复并提交 `5f06c4241bc632727aa1fb133612b833c6c0c7e4`
  （`fix: 收敛审计摘要默认标识`）：
  - `frontend/src/pages/compliance/AdminAudit.tsx` 新增默认摘要清洗，将审计导出任务、随访模板、临床推荐评估、
    报告解读任务、院外模型服务、运行批次、对象编号和校验值类技术标识替换为业务文案。
  - 原始 `summary`、`resourceId`、trace/event/context/model provider 标识继续保留；开启“证据详情”后仍显示原始证据。
  - 本批不改后端 API、不改审计事件身份、不改模型网关或患者敏感信息策略、不拆第二套审计日志。
- 红绿验证与构建：
  - 新增 `frontend/src/pages/compliance/AdminAudit.test.tsx` 用例
    “默认将审计摘要中的低频对象编号收进证据详情”，先在旧实现下红灯，再在修复后通过。
  - `npm --prefix frontend test -- AdminAudit.test.tsx` 通过，`12` 项。
  - `npm --prefix frontend run typecheck`、`npm --prefix frontend run format:check`、`git diff --check` 均通过。
  - `npm --prefix frontend run verify` 通过，`114` 个测试文件 / `933` 项；保留既有 Antd `Timeline.Item`
    deprecation warning。`npm --prefix frontend run build` 通过，生成审计 chunk `/assets/AdminAudit-CQc2pDzw.js`。
- 134 发布与现场验证：
  - 已执行 `deploy/onprem/mk-publish.sh --frontend --source 5f06c4241bc632727aa1fb133612b833c6c0c7e4`；
    这是前端-only 发布，远端 manifest 仍正确显示完整后端/JAR 部署提交
    `ef662ced1ca68723bed92aabd66440a833fde4b3`。
  - 远端备份 `/zoesoft/medkernel/backups/deploy-20260702-235936`；外部 readiness HTTP 200 /
    `{"status":"UP"}`；SSH 只读复核服务 `active/enabled`、`MainPID=3112035`、`NRestarts=0`。
  - 线上 `index.html` 指向 `/assets/index-C43jXdDJ.js`；`/assets/AdminAudit-CQc2pDzw.js`
    含“审计导出任务”“随访模板”“临床推荐评估”“院外模型服务”等默认文案和证据详情原始标识展示逻辑。
  - 审计摘要在线专项检查通过，证据目录
    `/tmp/medkernel-e2e-codex3/evidence-admin-audit-summary-label-5f06c424`；
    `admin-audit-default.png` 为 `1440x2943`，默认层 `defaultRawCount=0` 且可见
    “审计导出任务”“随访模板”“临床推荐评估”“院外模型服务”；`admin-audit-evidence-details.png` 为
    `1440x4296`，开启证据详情后 `evidenceRawCount=13`。
  - 重新运行 134 可见文本扫描，文件
    `/tmp/medkernel-e2e-codex3/visible-technical-scan-5f06c424.json`；`16` 个页面错误为 `0`，
    仅 `/adapter/hub` 保留协议词 `Webhook`，按产品和路由契约接受。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-5f06c424`
    通过 134 HTTPS 入站运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (40.6s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`、`duration=40555.854ms`，
    `11` 段真实前台运行记录错误合计 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-5f06c424`
    通过 134 HTTPS 入站运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.3m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`、`duration=80811.627ms`，
    `12` 类业务视角运行记录错误合计 `0`。
- 后续继续主线全局体验优化：当前知识治理目录、诊断知识维护、来源血缘和审计治理仍符合原始诉求；下一轮继续按医生、
  护士、患者/代理、药师、医技、医保办、质控、信息科、实施、院长、平台治理和知识生产运营视角，从最新 134 真实前台、
  全职责证据和可见文本扫描继续检查知识治理全链路、患者信息与模型安全、随访异常闭环、质量整改、系统接入阻断、
  权限职责、默认信息层级、上线门禁和文档/契约一致性。

## 最新阶段交接（2026-07-02 全视角真实前台体验优化第三十六批·来源血缘版本沿革默认展示收敛）

- 用户强调“疑问只是线索，不代表当前设计实现错误”，本批先对照 `CONSTITUTION`、`PRODUCT_SCOPE`、
  `EXPERIENCE_CONTRACT`、功能目录、职责矩阵和路由契约判断：`/advanced/provenance` 仍是统一知识治理、
  来源血缘、版本沿革和审计证据的权威入口；版本技术标识应进入证据详情，默认层服务医生、护士、实施、信息科、
  知识生产运营和院长理解“当前权威版本/历史版本/来源已记录”。本批不拆第二套血缘系统、不新增旧式“专家模式”，
  也不改变知识资产、诊断知识、模型任务或来源血缘后端契约。
- 基于第三十五批后 134 线上截图和可见文本扫描继续做全角色默认层检查，扫描文件为
  `/tmp/medkernel-e2e-codex3/visible-technical-scan-3bda5070.json`。`/authoring/assets`、
  `/knowledge/diagnosis`、知识生产和模型能力页面已收敛；`/advanced/provenance` 的“版本沿革”仍默认显示
  `ai-draft-task-...`。这会让业务用户误以为权威知识版本直接由模型任务命名，违反“模型只产候选不产事实”和
  “低频技术对象默认收进证据详情”的体验边界；但不构成来源血缘归属或知识治理结构错误。
- 已本地修复并提交 `cb768abf3810e7259bb7d529c924317986f3b143`
  （`fix: 收敛来源血缘版本默认展示`）：
  - `frontend/src/pages/advanced/Provenance.tsx` 识别 `ai-draft-task`、`model-task`、长 hash 和 UUID 类技术版次。
  - 默认版本沿革优先显示业务 `versionLabel` 或业务 `versionNo`；两者均为技术标识时按状态展示
    “当前权威版本”“历史版本”等业务文案，副文本展示“版本来源已记录”。
  - 原始 `versionNo`、模型任务标识和技术版次不丢失；开启“证据详情”后仍显示原始标识，供审计、实施排障和血缘核查使用。
  - 本批不改后端 API、不改版本身份、不改模型生成知识、不改患者敏感信息策略、不改变来源血缘的审计证据保存方式。
- 红绿验证与构建：
  - 新增 `frontend/src/pages/advanced/Provenance.test.tsx` 用例
    “默认隐藏版本沿革中的模型任务标识，证据详情才展示原始版次”，先在旧实现下红灯，暴露默认层找不到
    “版本来源已记录”且仍展示 `ai-draft-task`。
  - `npm --prefix frontend test -- Provenance.test.tsx -t "默认隐藏版本沿革中的模型任务标识"` 通过。
  - `npm --prefix frontend test -- Provenance.test.tsx` 通过，`3` 项。
  - `npm --prefix frontend run typecheck`、`npm --prefix frontend run format:check`、`git diff --check` 均通过。
  - `npm --prefix frontend run verify` 通过，`114` 个测试文件 / `932` 项；保留既有 Antd `Timeline.Item`
    deprecation warning。`npm --prefix frontend run build` 通过。
- 134 发布与现场验证：
  - 已执行 `deploy/onprem/mk-publish.sh --frontend --source cb768abf3810e7259bb7d529c924317986f3b143`；
    这是前端-only 发布，远端 manifest 仍正确显示完整后端/JAR 部署提交
    `ef662ced1ca68723bed92aabd66440a833fde4b3`。
  - 远端备份 `/zoesoft/medkernel/backups/deploy-20260702-233840`；外部 readiness HTTP 200 /
    `{"status":"UP"}`；SSH 只读复核服务 `active/enabled`、`MainPID=3100253`、`NRestarts=0`，
    本机健康 `{"status":"UP","groups":["liveness","readiness"]}`。
  - 线上 `index.html` 指向 `/assets/index-DUDHd3oN.js`；`/assets/Provenance-xLhXC7ea.js`
    含“版本来源已记录”默认文案、技术版次识别和证据详情原始标识展示逻辑。
  - 来源血缘在线专项检查通过，证据目录
    `/tmp/medkernel-e2e-codex3/evidence-provenance-version-label-cb768abf`；
    `provenance-default.png` 为 `1440x1221`，默认层显示“版本来源已记录”且不显示 `ai-draft-task`；
    `provenance-evidence-details.png` 为 `1440x1392`，开启证据详情后显示原始 `ai-draft-task-...` 标识。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-cb768abf`
    通过 134 HTTPS 入站运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (42.7s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`、`duration=42666.296ms`，
    `11` 段真实前台运行记录错误合计 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-cb768abf`
    通过 134 HTTPS 入站运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.3m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`、`duration=80131.44ms`，
    `12` 类业务视角运行记录错误合计 `0`。
- 后续继续主线全局体验优化：当前知识治理目录、诊断知识维护和来源血缘仍符合原始诉求；下一轮继续按医生、护士、
  患者/代理、药师、医技、医保办、质控、信息科、实施、院长、平台治理和知识生产运营视角，从最新 134 真实前台、
  全职责证据和可见文本扫描继续检查知识治理全链路、患者信息与模型安全、随访异常闭环、质量整改、系统接入阻断、
  权限职责、默认信息层级、上线门禁和文档/契约一致性。

## 最新阶段交接（2026-07-02 全视角真实前台体验优化第三十五批·知识资产随访模板默认名收敛）

- 本批继续按用户强调的“疑问只是线索，不代表当前设计实现错误”执行，先对照 `CONSTITUTION`、`PRODUCT_SCOPE`、
  `EXPERIENCE_CONTRACT`、功能目录和职责矩阵判断：`/authoring/assets` 是统一知识资产库，随访模板作为可复用资产在此编目、
  收藏、标签维护和批量处理，仍应与 `/clinical/followup` 的业务展示层一致；本批不拆出第二套随访资产库、不新增旧式
  “专家模式”，也不改变随访模板发布、计划生成或患者异常回院闭环。
- 基于第三十四批后 134 线上截图和可见文本扫描继续做医生、护士、患者代理、实施、信息科、知识生产运营和院长视角检查，
  发现 `/authoring/assets` 默认资产列表仍显示真实前台操作生成的随访模板原始名，例如
  `全角色患者代理随访模板（上线复演 ...） patient_proxy-...`，而 `/clinical/followup` 已默认展示业务名。
  这会让护士、实施和知识运营误把演练批次/运行后缀当成模板业务名称，违反“技术对象默认隐藏到证据详情”的体验边界；
  但不构成知识治理结构错误。
- 已本地修复并提交 `3bda50704de9ca101d13b0a88dca82149095ed4a`
  （`fix: 收敛知识资产随访模板默认名`）：
  - `frontend/src/pages/tenant/AuthoringAssets.tsx` 对 `FOLLOWUP` 资产默认清理“上线复演”批次和运行后缀，
    默认展示“全角色患者代理随访模板”等业务名。
  - 原始资产名称、资产编码和真实创建数据继续保留；开启“证据详情”后仍展示原始名与 `FUP.STAKEHOLDER...` 资产编码。
  - 本批不改后端契约、不改资产身份、不改随访模板发布状态、不改患者敏感信息或模型网关策略。
- 红绿验证与构建：
  - 新增 `frontend/src/pages/tenant/AuthoringAssets.test.tsx` 用例
    “默认隐藏随访模板资产的演练批次和运行后缀，证据详情才展示原始标识”，先在旧实现下红灯，再在修复后通过。
  - `npm --prefix frontend test -- AuthoringAssets.test.tsx` 通过，`5` 项。
  - `npm --prefix frontend run typecheck`、`npm --prefix frontend run format:check`、`git diff --check` 均通过。
  - `npm --prefix frontend run verify` 通过，`114` 个测试文件 / `931` 项；`npm --prefix frontend run build` 通过。
- 134 发布与现场验证：
  - 已执行 `deploy/onprem/mk-publish.sh --frontend --source 3bda50704de9ca101d13b0a88dca82149095ed4a`；
    这是前端-only 发布，远端 manifest 仍正确显示完整后端/JAR 部署提交
    `ef662ced1ca68723bed92aabd66440a833fde4b3`。
  - 远端备份 `/zoesoft/medkernel/backups/deploy-20260702-232141`；外部 readiness HTTP 200 /
    `{"status":"UP"}`；SSH 只读复核服务 `active/enabled`、`MainPID=3090601`、`NRestarts=0`，
    本机健康 `{"status":"UP","groups":["liveness","readiness"]}`。
  - 线上 `index.html` 指向 `/assets/index-D0_B-_0m.js`；`/assets/AuthoringAssets-IlvuMrMU.js`
    含随访资产默认名清洗逻辑。
  - 知识资产在线专项检查通过，证据目录
    `/tmp/medkernel-e2e-codex3/evidence-authoring-assets-followup-name-3bda5070`；
    `authoring-assets-default.png` 与 `authoring-assets-evidence-details.png` 均为 `1440x1145`，
    默认层不再显示“上线复演”、`patient_proxy-...` 和 `FUP.STAKEHOLDER...`，证据详情开启后展示原始标识。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-3bda5070`
    通过 134 HTTPS 入站运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (42.4s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`、`duration=42363.06ms`，
    `11` 段真实前台运行记录错误合计 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-3bda5070`
    通过 134 HTTPS 入站运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.4m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`、`duration=81828.898ms`，
    `12` 类业务视角运行记录错误合计 `0`。
- 后续继续主线全局体验优化：当前知识治理目录和诊断知识维护仍符合原始诉求；下一轮继续按医生、护士、患者/代理、
  药师、医技、医保办、质控、信息科、实施、院长、平台治理和知识生产运营视角，从最新 134 真实前台与全职责证据继续检查
  知识治理全链路、患者信息与模型安全、随访异常闭环、质量整改、系统接入阻断、权限职责、默认信息层级和上线门禁。

## 最新阶段交接（2026-07-02 全视角真实前台体验优化第三十四批·诊断知识技术版次默认展示收敛）

- 用户再次强调“疑问只是线索，不代表当前设计实现错误”，本批按 `CONSTITUTION`、`PRODUCT_SCOPE`、
  `EXPERIENCE_CONTRACT`、功能目录和职责矩阵复核后确认：`/knowledge/diagnosis` 仍是统一知识治理下的诊断语义资产
  专业维护入口；诊断身份、诊断标准、鉴别诊断、验证病例、来源证据、机构生效版本和模型候选仍走统一知识生产/审核/发布链。
  本批不拆第二套知识系统、不新增旧式“专家模式”，只处理真实前台默认层暴露技术版次的问题。
- 基于第三十三批后 134 线上截图继续做医生、护士、患者代理、医疗引擎运营员、信息科、实施、质控和院长视角检查，
  发现 `/knowledge/diagnosis` 版本选择器默认显示 `ai-draft-task-... · 已生效`。该值是模型生产任务技术标识，
  默认暴露会让业务用户误以为诊断知识版本由模型任务直接命名，违反“技术对象默认隐藏到证据详情”和“模型只产候选不产事实”的体验边界；
  但不构成诊断知识归属结构错误。
- 已本地修复并提交两批应用代码：
  - `65ae62415315a12bf238e1535d6051d8cc58a65c`（`fix: 收敛诊断知识版本默认展示`）：先处理
    `versionLabel` 为技术标识、`versionNo` 为业务版次时的默认展示，将技术值只放入证据详情。
  - `85178d98189bb68971bdf8a9c1fe8d16c8006985`（`fix: 处理诊断知识技术版次兜底`）：线上 API 证明
    `versionNo` 与 `versionLabel` 可能同时为 `ai-draft-task-...`；默认展示改为按状态兜底，例如“当前生效版本”，
    原始技术标识仅在证据详情开启后披露。
- 代码与测试范围：
  - `frontend/src/pages/quality/DiagnosisKnowledgePanel.tsx` 识别 AI draft、model task、hash/UUID 类技术版次；
    默认优先使用业务 `versionLabel`，其次业务 `versionNo`，两者都不可读时使用状态化业务文案。
  - `frontend/src/pages/quality/DiagnosisKnowledgePanel.test.tsx` 覆盖“技术 versionLabel + 业务 versionNo”和
    “versionNo/versionLabel 同为模型任务标识”两种真实风险。
  - 本批不改后端契约、不改诊断资产身份模型、不改审核发布、不改来源血缘、不改患者敏感信息或模型网关策略。
- 红绿验证与构建：
  - 两个新增用例均先在旧实现下红灯，再在修复后通过。
  - `npm --prefix frontend test -- DiagnosisKnowledgePanel.test.tsx` 通过，`19` 项。
  - `npm --prefix frontend run lint` 通过；`npm --prefix frontend run typecheck` 通过；
    `npm --prefix frontend run format:check` 通过。
  - `npm --prefix frontend run verify` 通过，`114` 个测试文件 / `930` 项。
  - `npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与现场验证：
  - 已执行 `deploy/onprem/mk-publish.sh --frontend --source 85178d98189bb68971bdf8a9c1fe8d16c8006985`；
    这是前端-only 发布，后端/JAR manifest 仍显示完整部署提交 `ef662ced1ca68723bed92aabd66440a833fde4b3`。
  - 远端备份 `/zoesoft/medkernel/backups/deploy-20260702-230434`；readiness HTTP 200 /
    `{"status":"UP"}`；服务 `active/enabled`、`MainPID=3080865`、`NRestarts=0`。
  - 线上 `index.html` 指向 `/assets/index-BD-VYSKB.js`；诊断知识 chunk
    `/assets/DiagnosisKnowledgeMaintenance-ClrLytd6.js` 含“当前生效版本”兜底逻辑。
  - 诊断知识在线专项检查通过，证据目录
    `/tmp/medkernel-e2e-codex3/evidence-diagnosis-version-label-85178d98`；截图
    `diagnosis-knowledge-default.png` 显示版本选择器为“当前生效版本 · 已生效”，默认页面未再出现
    `ai-draft-task` 技术标识。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-85178d98`
    通过 134 HTTPS 入站运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (41.6s)`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-85178d98`
    通过 134 HTTPS 入站运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.3m)`。
- 后续继续主线全局体验优化：诊断知识结构当前仍符合原始诉求；下一轮继续按医生、护士、患者/代理、药师、医技、
  医保办、质控、信息科、实施、院长、平台治理和知识生产运营视角，从最新 134 真实前台与全职责证据继续检查
  知识治理全链路、患者信息与模型安全、随访异常闭环、质量整改、系统接入阻断、权限职责、默认信息层级和上线门禁。

## 最新阶段交接（2026-07-02 全视角真实前台体验优化第三十三批·人员详情抽屉证据稳定性）

- 本批继续从第三十二批 134 真实前台与全职责截图做全角色筛查，先按 `CONSTITUTION`、`PRODUCT_SCOPE`、
  `EXPERIENCE_CONTRACT` 判断是否属于产品缺陷：知识治理、诊断知识维护、模型能力、系统接入、患者索引、
  医保审核、CDSS、随访和质量驾驶舱当前默认结构与原始诉求相符，未因单点疑问拆出第二套知识系统或旧式专家模式。
- 筛查发现 `stakeholder-platform_admin.png` 中“人员与账号”详情抽屉只显示右侧窄条。回查当前源码与已发布
  `ef662ced` 确认 `AdminUsers` 人员详情抽屉已有 `width={760}`，产品代码并非窄抽屉；根因是全职责演练脚本
  在抽屉打开动画尚未完全落入视口时就进入截图阶段，证据图会误导后续接力判断平台管理员页面可用性。
- 已本地修复并提交 `aa372e0a12cdcd6d5d9e22b5fc33ff4c2085ecd9`
  （`test: 等待人员详情抽屉落入视口`）：
  - 复用已有 `expectDrawerSettledInViewport` 等待平台管理员“人员详情抽屉”完全落入 1440 宽视口后再继续断言和截图。
  - 本批不修改 `AdminUsers` 应用代码、不改变人员建档、任职、账号、身份来源、角色授权或审计契约。
  - 该批当时不需要重发 134；后续前端 dist 已在第三十四批更新，当前状态以本文件顶部为准。
- 验证与复演：
  - `npm --prefix frontend run typecheck` 通过。
  - `npm --prefix frontend run format:check` 通过。
  - `git diff --check` 通过。
  - 使用 `E2E_EXTERNAL_DEPLOYMENT=1` 通过 134 HTTPS 入站运行
    `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，证据目录
    `/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-c076fea4-personnel-drawer-settled`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`、`duration=75952.579ms`，
    `12` 类业务视角运行记录错误合计 `0`。
  - 新截图 `stakeholder-platform_admin.png` 尺寸 `1440x960`，人员详情抽屉完整显示“人员档案”和
    “账号与身份来源”，可读地展示姓名、院内人员身份、主要任职、人员类型、账号状态、登录名和身份绑定状态。
- 后续继续主线全局体验优化：本批只修正会误导接力的全职责证据截图时机，不改变应用发布状态。下一轮继续从
  最新 134 真实前台与全职责证据中检查知识治理全链路、患者信息与模型安全、随访异常闭环、系统接入阻断、
  质量整改、权限职责、默认信息层级和上线门禁的剩余缺口。

## 最新阶段交接（2026-07-02 全视角真实前台体验优化第三十二批·临床提醒采纳率口径）

- 用户强调“疑问只是线索，不能片面盲改；当前目标是完美实现原始诉求”，本批继续先回查 `CONSTITUTION`、
  `PRODUCT_SCOPE` 与 `EXPERIENCE_CONTRACT`：CDSS 的核心是低打扰、医师确认、反馈闭环和价值指标，
  不允许自动开嘱或替代医生确认。由此确认本批只澄清默认指标口径，不改推荐算法、不改后端统计、不改医师反馈流程。
- 基于第三十一批 134 真实前台截图继续按医生、药师、护士、医技、质控和院长视角检查，发现
  `/cdss/fatigue` 顶部指标同时显示“待处理 45、已采纳 61、不采纳 0、采纳率 100%”。后端采纳率实际是
  已作出采纳/不采纳决定中的采纳比例，未处理提醒不进入分母；默认标题只写“采纳率”会让医生、药师和管理者误读为
  全部提醒已采纳，削弱“待处理”与医师确认的医疗安全含义。
- 已本地修复并提交 `ef662ced1ca68723bed92aabd66440a833fde4b3`
  （`fix: 澄清临床提醒采纳率口径`）：
  - 指标卡标题从“采纳率”改为“已处理采纳率”。
  - 指标值下方新增“待处理 N 项不计入”小字说明，明确分母只来自已采纳 + 不采纳的已处理闭环。
  - 本批不改变 `acceptanceRatePercent` 后端口径、推荐卡状态、医生采纳/不采纳、药师复核、报告解读、
    患者上下文或 CDSS 医疗安全边界。
- 红绿验证：
  - 红灯：`npm --prefix frontend test -- CdssFatigue.test.tsx -t "renders clinical reminder cards"`
    在旧实现下失败，暴露页面找不到“已处理采纳率”和“待处理 1 项不计入”。
  - 绿灯：同一目标用例通过；`npm --prefix frontend test -- CdssFatigue.test.tsx` 通过，`11` 项；
    保留既有 Antd `Timeline.Item` deprecation warning。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `928` 项；其中保留既有
    Antd `Timeline.Item` deprecation warning；`npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source ef662ced1ca68723bed92aabd66440a833fde4b3`
    完整发布到 134；远端备份 `/zoesoft/medkernel/backups/deploy-20260702-214224`，manifest
    `deployedAt=2026-07-02T21:42:27+08:00`，
    `jarSha256=ef79a21c1ccc2700a787d67bd4d81685a8a4119d9b0612210e598b25ea47efce`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=3035812`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-ef662ced-cdss-acceptance-rate`
    通过 134 HTTPS 入站运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (42.2s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`，`11` 段真实前台运行记录错误合计 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-ef662ced-cdss-acceptance-rate`
    通过 134 HTTPS 入站运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.6m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`，`12` 类业务视角运行记录错误合计 `0`。
  - 截图复核：`real-frontdesk-cdss-recommendation.png` 尺寸 `1440x1841`，默认指标显示“提醒总数 169、
    待处理 46、已采纳 62、不采纳 0、已处理采纳率 100%”，且小字标明“待处理 46 项不计入”；
    `stakeholder-physician.png` 与 `stakeholder-pharmacist.png` 均为 `1440x1256`。
- 后续继续主线全局体验优化：本批只澄清临床提醒默认统计口径，不改变 CDSS 推荐、医师确认、药师复核、
  医技报告解读或人机反馈。下一轮继续按医生、护士、患者/代理、药师、医技、医保办、质控、信息科、
  实施、院长等全视角，从最新 134 真实前台截图和操作流继续检查知识治理、患者资源、质量整改、
  系统接入、模型安全边界、权限职责、默认层级和运行可达性的剩余缺口。

## 最新阶段交接（2026-07-02 全视角真实前台体验优化第三十一批·随访模板演练批次默认展示）

- 用户强调“当前是完美实现原始诉求的所有目标，疑问不能片面盲改”，本批继续按 `CONSTITUTION`、
  `PRODUCT_SCOPE`、`EXPERIENCE_CONTRACT` 和真实前台证据判断：不拆改知识治理、不新增旧式“专家模式”，
  只修复真实演练暴露的随访模板默认展示层级问题。
- 基于第三十批 134 真实前台与全职责截图继续按护士、患者/代理、医生、实施和信息科视角检查，
  发现 `/clinical/followup` 的随访模板列表、模板选择器和随访计划办理抽屉虽已隐藏随机运行后缀，
  但真实演练批次名仍以“上线复演 07月02日 21时14分58秒”等形式出现在默认业务界面。
  这会让护士和患者代理把批次时间理解为模板名称的一部分，也会让实施验收误判前台默认层仍在展示测试标识。
- 已本地修复并提交 `ca58c7ab1b1a8e86e9888c94b3e5dd4790b5afc4`
  （`fix: 收敛随访模板演练批次展示`）：
  - 对真实演练模板前缀“真实前台”“全角色”补齐批次标识清洗，默认只展示业务名和版本，例如
    “真实前台慢病随访模板（第 1 版）”“全角色患者代理随访模板（第 1 版）”。
  - 原始模板名、批次时间、运行后缀仍作为创建数据、接口身份和证据追溯保留；不改变后端契约、模板发布、
    随访计划生成、患者敏感信息处理或审计证据。
  - 本批不把所有括号内容机械清空，只针对当前演练命名模式收敛默认显示，避免误伤真实业务模板名称。
- 随后本地提交 `8a02caa3c28b035a891efcc969c9e288bdc29649`
  （`test: 对齐随访模板默认名演练`）：E2E 仍用带批次的原始名从前台创建真实模板，但默认定位和断言改为业务名，
  并断言计划办理抽屉不出现“上线复演”和原始运行名；该提交只更新演练脚本，不需要重发 134 应用包。
- 红绿验证：
  - 红灯：`npm --prefix frontend test -- Followup.test.tsx -t "随访计划默认隐藏演练模板运行后缀"`
    在旧实现下失败，暴露批次名仍阻断“全角色患者代理随访模板（第 1 版）”的默认业务展示。
  - 绿灯：同一目标用例通过；`npm --prefix frontend test -- Followup.test.tsx` 通过，`19` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `928` 项；其中保留既有
    Antd `Timeline.Item` deprecation warning；`npm --prefix frontend run build` 通过；`git diff --check` 通过。
  - E2E 脚本提交前后补跑 `npm --prefix frontend run typecheck`、`npm --prefix frontend run format:check`
    与 `git diff --check` 均通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source ca58c7ab1b1a8e86e9888c94b3e5dd4790b5afc4`
    完整发布到 134；远端备份 `/zoesoft/medkernel/backups/deploy-20260702-212459`，manifest
    `deployedAt=2026-07-02T21:25:01+08:00`，
    `jarSha256=4df6192f497b5b0bc0849a056b79352aebe8514fe3f46c4db0d1e796855d0490`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=3026008`、`NRestarts=0`。
  - 首次通过 134 HTTPS 入站复跑
    `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-ca58c7ab-followup-template-batch-display`
    暴露旧脚本仍用“真实前台慢病随访模板（上线复演 ...）”查找默认行，结果为 `expected=0`、
    `unexpected=1`、`flaky=0`、`duration=32470.359ms`。截图 `test-failed-1.png` 显示产品前台已只展示
    “真实前台慢病随访模板 / 第 1 版”，因此根因是演练脚本契约未随默认展示调整，不是产品回归。
  - 对齐脚本后，
    `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-ca58c7ab-followup-template-batch-display-rerun`
    通过 134 HTTPS 入站运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (41.1s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`，`11` 段真实前台运行记录错误合计 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-ca58c7ab-followup-template-batch-display`
    通过 134 HTTPS 入站运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.3m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`，`12` 类业务视角运行记录错误合计 `0`。
  - 截图复核：`real-frontdesk-followup-template-published.png` 尺寸 `1440x2512`，默认搜索和表格行只显示
    “真实前台慢病随访模板”“第 1 版”；`stakeholder-patient_proxy.png` 与 `stakeholder-nurse.png`
    均为 `1440x968`，随访计划办理抽屉展示“全角色患者代理随访模板（第 1 版）”，未在默认层展示“上线复演”批次名。
- 后续继续主线全局体验优化：本批只收敛演练批次默认展示和演练脚本定位契约，不改变随访模板的真实创建、
  发布、计划生成、患者问卷回收或异常回院登记流程。下一轮继续按医生、护士、患者/代理、药师、医技、
  医保办、质控、信息科、实施、院长等全视角，从真实前台操作和截图中检查知识治理、临床随访、患者资源、
  质量整改、系统接入、模型安全边界、权限职责、默认层级和运行可达性的剩余缺口。

## 最新阶段交接（2026-07-02 全视角真实前台体验优化第三十批·模型能力默认表格层级）

- 用户强调“知识治理/模型能力问题只是疑问，不能片面盲改”，本批先回查 `CONSTITUTION`、`PRODUCT_SCOPE`
  与 `EXPERIENCE_CONTRACT`：模型能力属于知识生产公共能力，必须经统一模型能力网关接入且无模型 B0 可运行；
  不创建独立模型入口、独立发布流程、独立证据格式或独立权限体系；客户可见默认表格列数需收敛，追溯字段、
  长文本、审计字段进入详情。由此确认本批只做默认层级优化，不改知识治理结构、不新增旧式“专家模式”。
- 基于第二十九批 134 真实前台截图继续按信息科、实施、知识生产运营、医生/护士安全边界和院长治理视角检查，
  发现 `/advanced/ai-workflows` 的模型能力表默认展示“数据保护、结构约束、降级顺序、策略来源”等低频治理列，
  导致能力身份与“模型安全边界”动作被横向技术列隔开；多行无模型基线看起来像重复的“无模型外调”，
  容易让实施人员误判页面是在展示技术审计宽表，而不是统一模型能力网关下的能力清单和安全动作。
- 已本地修复并提交 `1d3a0c248d6cc79f3596d68422ced1be23828071`
  （`fix: 收敛模型能力默认表格层级`）：
  - 默认表格保留“能力、运行方式、数据边界、当前状态、模型安全边界”，把业务分类并入能力单元格标签。
  - “数据保护、结构约束、降级顺序、策略来源、能力编码、schema、fallback order”等低频治理与追溯信息继续留在行展开和证据详情中。
  - 本批不改变模型能力后端契约、公网/院内模型安全策略、患者敏感信息处理、知识生产入口、发布治理或无模型 B0 主链。
- 红绿验证：
  - 红灯：`npm --prefix frontend test -- AiWorkflows.test.tsx -t "默认模型能力表聚焦能力身份"`
    在旧实现下失败，暴露默认列仍包含“数据保护”等技术列。
  - 绿灯：同一目标用例通过；`npm --prefix frontend test -- AiWorkflows.test.tsx` 通过，`11` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `928` 项；其中保留既有
    Antd `Timeline.Item` deprecation warning；`npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source 1d3a0c248d6cc79f3596d68422ced1be23828071`
    完整发布到 134；远端备份 `/zoesoft/medkernel/backups/deploy-20260702-211236`，manifest
    `deployedAt=2026-07-02T21:12:38+08:00`，
    `jarSha256=b7c0478394d4bf9ea45f53aa1a6fd2a37be5554e642194be0b5a725947a937d8`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=3019006`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-1d3a0c24-model-capability-table-focus`
    通过 134 HTTPS 入站运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (44.6s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`，运行记录错误合计 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-1d3a0c24-model-capability-table-focus`
    通过 134 HTTPS 入站运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.4m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`，`12` 类业务视角运行记录错误合计 `0`。
  - 聚焦现场校验：机构 `engine-operator` 登录 134 后直达 `/advanced/ai-workflows`，默认列为
    “详情、能力、运行方式、数据边界、当前状态、模型安全边界”，不再有“数据保护、结构约束、降级顺序、策略来源”
    默认列；前三行可直接看到“临床知识关联发现、正式医学知识生产、电子病历语义实体提取”等能力身份及其无模型基线。
    截图 `/tmp/medkernel-e2e-codex3/evidence-model-capability-table-focused-1d3a0c24/model-capability-table-focus.png`
    尺寸为 `1440x1587`。
- 后续继续主线全局体验优化：下一轮继续按医生、护士、患者/代理、药师、医技、医保办、质控、信息科、实施、
  院长等全视角，从真实前台操作和截图中检查知识生产、系统接入、临床随访、患者资源、质量整改、
  模型安全边界、权限职责、默认层级和运行可达性的剩余缺口。

## 最新阶段交接（2026-07-02 全视角真实前台体验优化第二十九批·质量概览待办密度）

- 基于第二十八批 134 全职责截图继续按院长、质控、信息科、实施和医务管理视角检查，发现
  `/qc/dashboard` 的“待处置问题”卡片直接展示后端驾驶舱返回的前 `20` 条 `activeAlerts`。后端同时提供
  `summary.activeAlerts` 全量计数，完整工单页 `/qc/alerts` 已具备服务端分页；因此驾驶舱不应变成第二个长列表。
  回查 `PRODUCT_SCOPE` 与 `EXPERIENCE_CONTRACT` 后确认，质量概览页面目标是指标、风险热力、下钻证据与少量最高优先行动，
  完整问题处理应回到质量问题列表。当前实现会让院长和质控人员在首屏里被重复长待办淹没，也会误以为驾驶舱承担完整工单处理。
- 用户关于“诊断知识维护与整体知识管理是否契合”的问题继续作为全局判断线索处理，不作为片面改动指令：
  文档确认诊断知识是统一知识治理下的专业维护入口，本批不改变知识治理结构、不新增第二套知识系统，也不新增旧式“专家模式”。
- 已本地修复并提交 `d08c3319f53350360c98cfe7ab4b4a522f872c36`
  （`fix: 收敛质量概览待办密度`）：
  - 质量概览卡片标题从“待处置问题”调整为“最高优先问题”，默认只展示前 `5` 条最高优先行动。
  - 卡片标题区展示“共 N 条待处置问题，当前展示 x 条”，并提供“查看全部质量问题”链接跳转 `/qc/alerts`。
  - 本批不改变后端驾驶舱契约、质量下钻、质量问题服务端分页、整改闭环、知识治理、患者敏感信息或模型安全边界。
- 红绿验证：
  - 红灯：`npm --prefix frontend test -- QcDashboard.test.tsx -t "keeps dashboard actions concise"`
    在旧实现下失败，暴露找不到“最高优先问题”和“共 N 条待处置问题，当前展示 5 条”摘要，驾驶舱仍把第 `6` 条问题显示在主卡片内。
  - 绿灯：同一目标用例通过；`npm --prefix frontend test -- QcDashboard.test.tsx` 通过，`9` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `927` 项；其中保留既有
    Antd `Timeline.Item` deprecation warning；`npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source d08c3319f53350360c98cfe7ab4b4a522f872c36`
    完整发布到 134；远端备份 `/zoesoft/medkernel/backups/deploy-20260702-205258`，manifest
    `deployedAt=2026-07-02T20:53:00+08:00`，
    `jarSha256=3f5210db07f5ca8d8d2643fc68c1137cf8b8d759a8a89a09607f0f932bcfc425`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=3008175`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-d08c3319-qc-dashboard-action-preview`
    通过 134 HTTPS 入站运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (48.6s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`，运行记录错误合计 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-d08c3319-qc-dashboard-action-preview`
    通过 134 HTTPS 入站运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.3m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`，`12` 类业务视角运行记录错误合计 `0`。
  - 聚焦现场校验：上线凭据契约只有四职责账号，院长/质控质量视角按既有演练使用 `engine-operator` 进入；
    曾用不存在的 `hospital-executive` 凭据键登录失败，确认为脚本角色键错误而非产品缺陷。机构 `engine-operator`
    登录 134 后直达 `/qc/dashboard`，断言“最高优先问题”显示
    “共 22 条待处置问题，当前展示 5 条”，且“查看全部质量问题”可跳转 `/qc/alerts`。截图
    `/tmp/medkernel-e2e-codex3/evidence-qc-dashboard-action-preview-focused-d08c3319/qc-dashboard-action-preview.png`
    尺寸为 `1440x1897`。
- 后续继续主线全局体验优化：本批只收敛质量概览驾驶舱行动密度，不改变质量问题完整处理页。
  下一轮继续按医生、护士、患者/代理、药师、医技、医保办、质控、信息科、实施、院长等全视角，从真实前台截图和操作流里检查
  知识生产、系统接入、临床随访、患者资源、模型安全边界、权限职责、默认层级和运行可达性的缺口。

## 最新阶段交接（2026-07-02 全视角真实前台体验优化第二十八批·质量问题服务端翻页）

- 基于第二十七批 134 真实前台与全职责截图继续按质控、医务、院长、医生、护士和信息科视角检查，
  发现 `/qc/alerts` 的质量问题列表虽然后端支持 `page/size`，但前端一直以 `page=1` 拉取并无分页控件。
  真实前台多轮演练后，质量问题会随医保审核、病历质控和整改链路累积；只显示第一页会让质控人员和院长误以为
  后续问题不存在，也会影响整改责任闭环。回查 `PRODUCT_SCOPE`、`EXPERIENCE_CONTRACT` 和功能目录后确认，
  质量问题与整改属于 10 万级风险列表，必须服务端分页、显示当前范围，并且默认指标不能把当前页数量误写成全量。
- 用户关于“诊断知识维护与整体知识管理是否契合”的问题已作为全局判断线索处理，不作为片面改动指令：
  文档确认 11 类知识内容中包含 `DIAGNOSIS`，`/knowledge/diagnosis` 是知识治理下的诊断身份、诊断标准、
  鉴别诊断、验证病例与来源证据专业入口；知识生产、审核发布、机构生效版本、来源血缘和模型赋能仍走统一链路。
  因此本阶段不拆出第二套知识系统，也不新增“专家模式”，继续按统一治理下的专业维护入口审视体验。
- 已本地修复并提交 `ab108aba45e38e71c18489055131c86e098840b6`
  （`fix: 补齐质量问题服务端翻页`）：
  - 质量问题页新增页码状态，默认每页 `20` 条；筛选变化后回到第一页，并通过 Ant `Pagination` 驱动服务端
    `useQualityAlerts({ page, size })`。
  - 列表标题区展示“共 N 条质量问题，当前显示 x-y 条”；指标改为“当前筛选问题总数”“当前页待处置”“当前页医疗安全”，
    避免把当前页统计误导为全量统计。
  - 本批不改变质量预警、整改派发、医保审核、评价结果来源、患者敏感信息、后端接口契约或诊断知识治理结构。
- 红绿验证：
  - 红灯：`npm --prefix frontend test -- QcAlerts.test.tsx -t "keeps accumulated quality alerts reachable through server pagination"`
    在旧实现下失败，暴露找不到“当前筛选问题总数”和服务端分页范围文案，翻页后仍只请求第一页。
  - 绿灯：同一目标用例通过；`npm --prefix frontend test -- QcAlerts.test.tsx` 通过，`6` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `926` 项；其中保留既有
    Antd `Timeline.Item` deprecation warning；`npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source ab108aba45e38e71c18489055131c86e098840b6`
    完整发布到 134；远端备份 `/zoesoft/medkernel/backups/deploy-20260702-203005`，manifest
    `deployedAt=2026-07-02T20:30:08+08:00`，
    `jarSha256=edbc1db248ef9eb2097923f94b465e83200402dc13fe3dc30c4ff6893525a620`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=2995486`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-ab108aba-qc-alerts-pagination`
    通过 134 HTTPS 入站运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (42.5s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`，运行记录错误合计 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-ab108aba-qc-alerts-pagination`
    通过 134 HTTPS 入站运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.3m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`，`12` 类业务视角运行记录错误合计 `0`。
  - 为满足现场分页数据量条件，额外用真实前台页面补量复演
    `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-ab108aba-qc-alerts-pagination-seed2`
    通过，`1 passed (39.0s)`；report stats 为 `expected=1`、`unexpected=0`、`flaky=0`，运行记录错误合计 `0`。
  - 聚焦现场校验：机构 `engine-operator` 登录 134 后直达 `/qc/alerts`，将“预警时间”切为“全量”、
    “预警级别”切为“全部级别”。补量前全量未处置质量问题正好 `20` 条，分页控件不出现；补量复演后为
    `21` 条，第一页显示“共 21 条质量问题，当前显示 1-20 条”，点击第 2 页后显示
    “共 21 条质量问题，当前显示 21-21 条”。截图
    `/tmp/medkernel-e2e-codex3/evidence-qc-alerts-pagination-focused-ab108aba/qc-alerts-pagination-page2.png`
    尺寸为 `1440x960`。
- 后续继续主线全局体验优化：本批只补齐质量问题列表的服务端分页和默认指标语义，不改变知识治理、诊断知识、
  质量整改或患者隐私边界。下一轮继续按全角色从真实前台与全职责截图检查知识生产、系统接入、质量概览、
  临床随访、患者代理和模型安全边界等高频页面的分类、流程、默认层级、隐私处理和运行可达性。

## 最新阶段交接（2026-07-02 全视角真实前台体验优化第二十七批·接入阻塞项默认文案）

- 基于第二十六批 134 新截图继续按实施工程师、信息科长和平台管理员视角检查，发现 `/adapter/hub`
  的“接入向导”标签页在阻塞项列默认展示后端原始枚举，例如 `NOT_CONNECTED`。此前质量报告和默认摘要层已收敛，
  但接入申请表格仍原样渲染 `record.blockers`；真实实施验收时会把“未接通”理解成技术枚举或误以为需要按枚举配置。
  回查 `EXPERIENCE_CONTRACT` 与现有 `customerDisplayText` 契约后确认，应把阻塞项纳入同一默认业务语言/证据详情原文机制。
- 已本地修复并提交 `98ebed20c08303e3b216afe9e96d2919a8bbc347`
  （`fix: 收敛接入阻塞项默认文案`）：
  - 接入向导阻塞项默认使用 `customerDisplayText`，将 `NOT_CONNECTED`、`MISCONFIGURED` 等原始状态转换为
    “未接通”“配置不完整”等业务语言。
  - 打开“证据详情”后仍展示后端原始阻塞值，保留实施排障与审计追溯能力。
  - 本批不改变接入申请、适配器健康检查、字段映射、数据质量报告或后端契约；只补齐默认层级防线。
- 红绿验证：
  - 红灯：`npm --prefix frontend test -- AdapterHub.test.tsx -t "keeps onboarding blockers in business language"`
    在旧实现下失败，暴露接入向导找不到“未接通/配置不完整”业务文案，阻塞项仍为原始枚举。
  - 绿灯：同一目标用例通过；`npm --prefix frontend test -- AdapterHub.test.tsx` 通过，`22` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `925` 项；其中保留既有
    Antd `Timeline.Item` deprecation warning；`npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source 98ebed20c08303e3b216afe9e96d2919a8bbc347`
    完整发布到 134；远端备份 `/zoesoft/medkernel/backups/deploy-20260702-201601`，manifest
    `deployedAt=2026-07-02T20:16:03+08:00`，
    `jarSha256=77da944171329643daf238ec5f726ea1d4a2b98a2b2b5f63c78acfe3e70d2059`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=2987652`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-98ebed20-onboarding-blockers-business-text`
    通过 134 HTTPS 入站运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (40.8s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`，运行记录错误合计 `0`；接入向导截图
    `real-frontdesk-onboarding.png` 尺寸为 `1440x4359`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-98ebed20-onboarding-blockers-business-text`
    通过 134 HTTPS 入站运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.3m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`，`12` 类业务视角运行记录错误合计 `0`；
    实施工程师截图 `stakeholder-implementation_engineer.png` 尺寸为 `1440x2317`。
  - 聚焦现场校验：机构 `platform-admin` 登录 134 后直达 `/adapter/hub` 并切到“接入向导”，断言默认表格中
    `NOT_CONNECTED|MISCONFIGURED` 计数为 `0`，且能看到“未接通/配置不完整”业务文案。截图
    `/tmp/medkernel-e2e-codex3/evidence-onboarding-blockers-focused-98ebed20/onboarding-blockers-business-text.png`
    尺寸为 `1440x4359`。
- 后续继续主线全局体验优化：本批只修默认阻塞项表达，不改变断连诚实降级。下一轮继续按全角色截图检查
  系统接入、知识生产、质量概览、临床随访和患者代理等高频页面的默认层级、流程可达性和隐私呈现。

## 最新阶段交接（2026-07-02 全视角真实前台体验优化第二十六批·系统接入质量报告单一呈现）

- 基于第二十五批 134 真实前台与全职责截图继续按信息科长、实施工程师、平台管理员和院内运维视角检查，
  发现 `/adapter/hub` 生成“数据质量报告”后，同一份报告同时出现在主流程影响区和“数据质量看板”标签页中。
  这不是系统接入设计方向错误，也不需要把质量报告拆成新专家模式；回查 `PRODUCT_SCOPE` 与
  `EXPERIENCE_CONTRACT` 后确认，数据质量报告属于接入上线验收的单一证据，质量看板应承载未连接、配置非法、
  字段映射覆盖等汇总指标。重复展示会让信息科和实施人员误以为存在两份报告或两个验收来源。
- 已本地修复并提交 `ba69a8e9e4fc6118da75072725de1542d201afe2`
  （`fix: 收敛系统接入质量报告重复展示`）：
  - 保留生成后的 `QualityReportCard` 在主流程区域作为唯一报告入口；
    “数据质量看板”在报告生成后不再重复渲染同一报告，也不再显示“尚未生成本轮数据质量报告”的空态。
  - 质量看板仍展示“未连接”“配置非法”“字段映射覆盖”等业务指标；未生成报告时仍保留原有引导空态。
  - 本批不改变系统接入、字段映射、接入申请、质量报告后端契约、知识治理结构或高级信息呈现机制；
    用户关于知识治理契合度的疑问继续作为全局判断线索，不作为片面结构改动依据。
- 红绿验证：
  - 红灯：`npm --prefix frontend test -- AdapterHub.test.tsx -t "keeps the generated data quality report in one place"`
    在旧实现下失败，暴露切到“数据质量看板”后 exact “数据质量报告”标题出现 `2` 次。
  - 绿灯：同一目标用例通过；`npm --prefix frontend test -- AdapterHub.test.tsx` 通过，`21` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `924` 项；其中保留既有
    Antd `Timeline.Item` deprecation warning；`npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source ba69a8e9e4fc6118da75072725de1542d201afe2`
    完整发布到 134；短哈希曾被发布脚本拒绝，随后按完整 40 位哈希发布成功。远端备份
    `/zoesoft/medkernel/backups/deploy-20260702-200426`，manifest
    `deployedAt=2026-07-02T20:04:29+08:00`，
    `jarSha256=fa2356ab2a8f9b13b6a704254e52cdddfdfa6d3a01dd16caa836b4d3cb064002`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=2981026`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-ba69a8e9-adapter-quality-report-single`
    通过 134 HTTPS 入站运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (41.3s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`，运行记录 `11` 段真实前台链路均由页面提交产生，
    浏览器/服务端/网络错误合计 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-ba69a8e9-adapter-quality-report-single`
    通过 134 HTTPS 入站运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.3m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`，`12` 类业务视角运行记录错误合计 `0`。
    信息科长与实施工程师截图均为 `1440x2317`，生成质量报告后不再出现同一报告上下重复呈现。
  - 聚焦现场校验：机构 `platform-admin` 登录 134 后直达 `/adapter/hub`，切到“数据质量看板”并点击
    “生成质量报告”，断言 exact “数据质量报告”标题计数为 `1`，且“尚未生成本轮数据质量报告”计数为 `0`。
    截图 `/tmp/medkernel-e2e-codex3/evidence-adapter-quality-report-focused-ba69a8e9/adapter-quality-report-single.png`
    尺寸为 `1440x2317`。
- 后续继续主线全局体验优化：本批只收敛系统接入质量报告的单一证据呈现，不改变接入工作台的信息架构。
  下一轮继续从真实前台与全职责截图里按医生、护士、患者/代理、药师、医技、医保办、质控、信息科、实施、院长等
  全视角寻找分类、流程、默认层级、隐私处理和运行可达性的缺口。

## 最新阶段交接（2026-07-02 全视角真实前台体验优化第二十五批·医保问题服务端翻页）

- 基于第二十四批 134 真实前台截图继续按医保办、医生、质控、信息科和院长视角检查，发现
  `/qc/insurance` 的“医保问题列表”不是设计方向错误：它仍按真实病案快照、B0 医保审核、整改闭环和证据详情工作。
  但回查 `PRODUCT_SCOPE` 与 `EXPERIENCE_CONTRACT` 后确认，医保问题属于列表/审核队列，必须服务端分页并给出当前范围；
  当前实现虽然调用 `useInsuranceIssues({ page, size })`，却把前端页码固定为 `page: 1, size: 20`，且无翻页控件。
  真实前台多轮演练后，已派整改问题会累积，医保办和质控人员无法到达第二页，容易误以为只有第一页数据。
- 已本地修复并提交 `0c273f55c0020410fb136419b1dd8a281fd503c7`
  （`fix: 补齐医保问题服务端翻页`）：
  - 新增医保问题页码状态，默认每页 `10` 条；列表底部展示
    “共 N 条医保问题，当前显示 x-y 条”，并通过独立 `Pagination` 驱动服务端 `page` 参数，避免 Antd List 客户端二次切片。
  - 问题状态、时间、级别筛选变化和执行医保审核后回到第一页；“未处理问题”指标改为“当前页未处理”，避免
    总量大于当前页时误导为全量未处理数。
  - 本批不改变医保审核、DRG/DIP 分组、病案内涵质控、质量整改、证据详情、患者敏感信息和后端接口契约；
    不把用户关于知识治理的疑问转成片面结构调整。
- 红绿验证：
  - 红灯：`npm --prefix frontend test -- InsuranceAudit.test.tsx -t "keeps accumulated insurance issues reachable"`
    在旧实现下失败，暴露最后一次 `useInsuranceIssues` 仍收到 `page: 1, size: 20`，找不到服务端分页范围文案。
  - 绿灯：同一目标用例通过；`npm --prefix frontend test -- InsuranceAudit.test.tsx` 通过，`8` 项。
  - 完整前端门禁：首次 `npm --prefix frontend run verify` 停在 Prettier 格式检查，已用
    `npx prettier --write src/pages/quality/InsuranceAudit.tsx src/pages/quality/InsuranceAudit.test.tsx` 修正；
    复跑 `npm --prefix frontend run verify` 通过，`114` 个测试文件 / `923` 项；`npm --prefix frontend run build` 通过；
    `git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source 0c273f55c0020410fb136419b1dd8a281fd503c7`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260702-194804`，manifest
    `deployedAt=2026-07-02T19:48:07+08:00`，
    `jarSha256=aba8cb65ef48748b05ed498e81110e5a3e727e7b95ebe462aed1b66ff953c5a2`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=2971969`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-0c273f55-insurance-pagination`
    通过 134 HTTPS 入站运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (42.1s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`，运行记录 `11` 段真实前台链路均由页面提交产生，
    `errors=0`。截图 `real-frontdesk-insurance-quality-rectification.png` 尺寸为 `1440x3291`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-0c273f55-insurance-pagination`
    通过 134 HTTPS 入站运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.3m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`，运行记录 `12` 类业务视角、`errors=0`。
  - 聚焦现场校验：机构 `engine-operator` 登录 134 后直达 `/qc/insurance`，默认“未处理”筛选当前为 `0` 条，这是因为
    本轮真实演练生成的问题已进入“已派整改”状态，不是分页回归；切换到“已派整改”后断言页面显示
    “共 17 条医保问题，当前显示 1-10 条”，截图
    `/tmp/medkernel-e2e-codex3/evidence-insurance-pagination-focused-0c273f55.png` 尺寸为 `1440x2814`。
- 后续继续主线全局体验优化：本批收敛的是医保问题列表可达性和信息密度，不改医保审核业务边界。
  下一轮继续按医生、护士、患者/代理、药师、医技、医保办、质控、信息科、实施、院长等全视角从真实前台截图和操作流里找
  分类、流程、默认层级、隐私处理和运行可达性的缺口；用户关于知识治理契合度的问题继续作为全局判断线索，不作为盲改依据。

## 最新阶段交接（2026-07-02 全视角真实前台体验优化第二十四批·系统接入字段映射缺口分页摘要）

- 基于第二十三批 134 真实前台截图继续按信息科长、实施工程师、平台治理员和院内运维视角检查，
  发现 `/adapter/hub` 的“字段映射与缺口”面板把所有接入来源逐项展开为 `Descriptions`。
  真实前台多轮演练后，系统接入截图高度达到 `1440x13234`，首屏已有必接系统、数据接入契约和适配器目录，
  但字段缺口长展开会稀释“断连、映射缺口、健康诊断、质量报告”这一页主目标。回查 `PRODUCT_SCOPE`
  与 `EXPERIENCE_CONTRACT` 后确认：系统接入能力不能删，接入申请普通列表默认 20 条仍符合契约；
  应收敛的是字段映射缺口的默认信息层级。
- 已本地修复并提交 `3c6fe153241ebba6245241109b130eb2a9df19a2`
  （`fix: 收敛系统接入字段映射缺口展示`）：
  - “字段映射与缺口”改为小表格，默认每页 `10` 个接入来源，保留总量文案
    “共 N 个接入来源，当前显示 x-y 个”。
  - 默认列展示接入来源、健康状态、映射字段、最近探活和“接入缺口”；每行只摘要前 `2` 项缺口，
    更多缺口显示剩余数量，避免重复来源把页面无限拉长。
  - 本批不改变适配器、字段映射、接入申请、回调通道、区域来源、健康检查、死信和数据质量后端契约；
    不新增专家模式，不把系统接入能力拆出当前工作台。
- 红绿验证：
  - 红灯：`npm --prefix frontend test -- AdapterHub.test.tsx -t "keeps field mapping gaps paginated"`
    在旧实现下失败，暴露找不到字段映射分页总量文案，且第 11 个来源仍默认展开在第一页。
  - 绿灯：同一目标用例通过；`npm --prefix frontend test -- AdapterHub.test.tsx`
    通过，`20` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `922` 项；
    `npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source 3c6fe153241ebba6245241109b130eb2a9df19a2`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260702-193110`，manifest
    `deployedAt=2026-07-02T19:31:15+08:00`，
    `jarSha256=dc9dcce5a7f9143c3f7687528daf51dcec1dbd4b6e7df4b67fa82bc182834e16`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=2962539`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-3c6fe153-adapter-field-mapping-pagination-https`
    通过 134 HTTPS 入站运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (44.2s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`，运行记录 `11` 段真实前台链路均由页面提交产生，
    `errors=0`。截图 `real-frontdesk-adapter.png` 尺寸降为 `1440x4348`，字段映射缺口面板显示
    “共 72 个接入来源，当前显示 1-10 个”，页面不再被逐来源长描述拉到一万多像素。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-3c6fe153-adapter-field-mapping-pagination`
    通过 134 HTTPS 入站运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.3m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`，`12` 类业务视角截图均已生成。
- 后续继续主线全局体验优化：系统接入长页候选已处理为“字段映射缺口分页摘要”而非删除能力。
  下一轮继续从真实前台与全职责截图里找影响医生、护士、患者/代理、信息科、实施、院长等角色理解和操作效率的问题。

## 最新阶段交接（2026-07-02 全视角真实前台体验优化第二十三批·随访办理底部操作区）

- 基于第二十二批 134 真实前台截图继续按护士、患者/代理、医生、院内实施和信息科视角检查，
  发现 `/clinical/followup` 的“随访计划办理”抽屉在 1440×968 视口下，异常回院登记表的
  “登记异常回院”按钮贴在视口底部并被裁掉一截。虽然 E2E 能点击成功，但护士真实办理异常回院时
  会以为按钮未完整加载或需要额外滚动，属于临床闭环操作可见性问题。
- 已本地修复并提交 `f2eb3683a94361d39acba5c55ea29ff438d611a8`
  （`fix: 固定随访办理底部操作区`）：
  - 问卷提交与异常回院登记按钮分别进入 `role="group"` 的“问卷回收操作”和“异常回院登记操作”语义区。
  - 新增 `.drawerActionBar`，在抽屉内容中使用 `position: sticky; bottom: 0`、底部安全留白和容器背景，
    保证长表单内主操作完整可见；移动窄屏下按钮自动铺满宽度。
  - 本批不改变随访计划生成、问卷回收、异常回院后端契约，不新增患者隐私字段，不改变医护责任边界。
- 红绿验证：
  - 红灯：`npm --prefix frontend test -- Followup.test.tsx -t "默认用业务结果展示异常回院登记证据|随访办理抽屉用固定底部操作区"`
    在旧实现下失败，暴露找不到“异常回院登记操作”语义组，且 CSS 中不存在 `.drawerActionBar` sticky 底部合约。
  - 绿灯：同一目标用例通过，`2` 项；`npm --prefix frontend test -- Followup.test.tsx`
    通过，`19` 项。
  - 受影响临床集合：`npm --prefix frontend test -- Followup.test.tsx CdssFatigue.test.tsx PatientPathways.test.tsx WorkflowTodos.test.tsx`
    通过，`4` 个文件 / `61` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `921` 项；
    `npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source f2eb3683a94361d39acba5c55ea29ff438d611a8`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260702-022813`，manifest
    `deployedAt=2026-07-02T02:28:15+08:00`，
    `jarSha256=959bd1a294c925014463bd31769a517f95dd19c584c1a3f8b4691edc8f3d2cf0`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=2414696`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-f2eb3683-followup-action-bar`
    直接访问 134 运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (40.8s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`，运行记录 `11` 段真实前台链路均由页面提交产生，
    `errors=0`。截图 `real-frontdesk-followup-plan-questionnaire-abnormal-812a675e7fa1def06b0ec1162d43efe3f89d260e.png`
    显示“登记异常回院”按钮完整固定在右下操作区，底部未再被视口裁切。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-f2eb3683-followup-action-bar`
    直接访问 134 运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.3m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`，运行记录 `12` 类视角、`errors=0`。
- 第二十三批扫描发现的系统接入/字段映射长页问题已在第二十四批处理；后续以第二十四批交接为准。

## 最新阶段交接（2026-07-02 全视角真实前台体验优化第二十二批·诊断知识统一治理边界）

- 针对用户提出的“诊断知识维护和整体知识治理是否不契合、知识治理是否满足全链路核心知识管理及生产”
  的疑问，已先深读 `CONSTITUTION`、`PRODUCT_SCOPE`、`ARCHITECTURE`、`EXPERIENCE_CONTRACT`
  与当前路由/页面实现再判断。结论：诊断知识维护本身属于知识治理下的诊断语义资产维护入口，
  复用 `KnowledgeIdentity` / `KnowledgeVersion` 和发布链路；它不应被盲目并入知识审核工作台，
  也不应删除为“旧兼容”。需要收敛的是页面与职责契约的表达，让操作者明确诊断维护不能绕过知识审核、
  平台标准版本或机构生效版本。
- 已本地修复并提交 `3f8e1be7b2ac22f378f26d9720e9a52b07840068`
  （`fix: 明确诊断知识统一治理边界`）：
  - `/knowledge/diagnosis` 页面说明改为“在统一知识治理下维护诊断身份、诊断标准、鉴别诊断、验证病例与来源证据；
    发布后再进入平台标准版本或机构生效版本。”
  - 路由体验契约改为“在统一知识治理下维护诊断身份、诊断标准、鉴别关系、验证病例和来源证据”；
    医疗引擎运营员职责改为“管理诊断语义资产、版本和统一发布门禁”，边界改为
    “诊断维护不绕过知识审核、平台标准版本或机构生效版本”。
  - 本批不改菜单层级、不合并诊断维护与知识审核、不新增专家模式；只把已有统一治理关系写进可见页面和路由契约。
- 红绿验证：
  - 红灯：`npm --prefix frontend test -- DiagnosisKnowledgeMaintenance.test.tsx routes.test.ts -t "hosts manual diagnosis maintenance|separates knowledge review from manual diagnosis"`
    在旧实现下失败，暴露页面说明和路由目标仍是普通“诊断身份/标准/证据”维护口径，缺少统一治理与平台/机构生效边界。
  - 绿灯：`npm --prefix frontend test -- DiagnosisKnowledgeMaintenance.test.tsx routes.test.ts -t "hosts manual diagnosis maintenance|separates knowledge review from manual diagnosis|为高风险治理与运维页面登记全视角职责边界"`
    通过，`3` 项；`npm --prefix frontend test -- routes.test.ts DiagnosisKnowledgeMaintenance.test.tsx`
    通过，`53` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `920` 项；
    `npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source 3f8e1be7b2ac22f378f26d9720e9a52b07840068`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260702-021233`，manifest
    `deployedAt=2026-07-02T02:12:35+08:00`，
    `jarSha256=175570b0852df5f40ebac08421a884029947d2589860da16088e2705ff77bf4b`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=2405856`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-3f8e1be7-diagnosis-governance-boundary`
    直接访问 134 运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.3m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`，运行记录 `12` 类视角、`errors=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-3f8e1be7-diagnosis-governance-boundary`
    直接访问 134 运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (35.4s)`；
    运行记录 `11` 段真实前台链路均由页面提交产生，`errors=0`。
  - 聚焦真实页面校验：机构 `engine-operator` 登录 134 后直达 `/knowledge/diagnosis`，断言
    “诊断知识维护”标题、统一治理说明、“诊断知识身份”下拉和“新建诊断资产”按钮均可见；
    截图 `/tmp/medkernel-e2e-codex3/evidence-diagnosis-governance-page-3f8e1be7.png`
    显示该页仍位于“知识治理”菜单下，说明文案无截断，主表单与只读状态可见。
- 后续继续主线全局体验优化：用户的知识治理疑问已处理为“边界口径明确”而非结构性推倒重做。
  如后续再发现知识治理不能覆盖某类核心知识生产/发布链路，应先回到权威文档与真实前台流程证据判断，再做结构调整。

## 最新阶段交接（2026-07-02 全视角真实前台体验优化第二十一批·协同任务列表可读性）

- 基于第二十批 134 全职责截图继续按医技、医生、护士、信息科和实施验收视角核查，发现
  `/workflow/todos` 协同任务在多轮真实前台演练后出现近 300 条报告解读待办。旧表格主列过窄导致
  医技读报告任务时需要逐行扫很高的行块；首轮修正 `a1c9f2bcbbbddf2176a8882b1f66f8efb89a788a`
  发布到 134 后，截图复核又发现右侧操作列在默认视口被截断。因此 `a1c9f2bc` 只作为中间问题证据，
  不得作为最终上线验收或交接引用。
- 已本地修复并提交：
  - `a1c9f2bcbbbddf2176a8882b1f66f8efb89a788a`（`fix: 优化协同任务列表可读性`）：
    初步为协同任务表格设置固定布局、主待办阅读列、业务摘要行高和横向滚动宽度。
  - `325afd3c9ac8312960c13d25d3fbddb18df750f9`（`fix: 收敛协同任务默认列宽`）：
    将默认滚动宽度收敛为 `1040`，待办主列保留 `340`，来源、患者、责任岗位、截止、优先级、
    状态和操作列按默认桌面视口重新分配，确保报告上下文动作与完成/转交图标在默认画面可见。
    本批不改变协同任务业务契约、证据详情权限、服务端分页或真实数据来源。
- 红绿验证：
  - 红灯：`npm --prefix frontend test -- WorkflowTodos.test.tsx -t "keeps report interpretation rows scannable|keeps the workflow todo table contained"`
    在 `a1c9f2bc` 宽度下失败，暴露仍为 `scroll={{ x: 1200 }}` 与 `width: 380`。
  - 绿灯：同一目标用例通过，`2` 项；`npm --prefix frontend test -- WorkflowTodos.test.tsx`
    通过，`20` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `920` 项；
    `npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source 325afd3c9ac8312960c13d25d3fbddb18df750f9`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260702-020115`，manifest
    `deployedAt=2026-07-02T02:01:18+08:00`，
    `jarSha256=2d88e0e042df019c586ff084db30c8b6d4e2ad6f7d9673ef1d0777d4205207b2`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=2399435`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-325afd3c-workflow-readable-fit`
    直接访问 134 运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.3m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`，运行记录 `12` 类视角、`12` 次真实动作、
    `errors=0`。截图 `stakeholder-medical_technician.png` 显示协同任务主列可读，右侧
    “打开报告上下文”和操作图标完整可见，未再出现截断。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-325afd3c-workflow-readable-fit`
    直接访问 134 运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (35.9s)`；
    运行记录 `11` 段真实前台链路均由页面提交产生，`errors=0`。
- 用户关于“诊断知识维护与整体知识管理是否契合、知识治理是否满足全链路核心知识管理及生产”的问题，
  已登记为后续全局产品一致性校验点；这不是当前结论。继续主线时需先按
  `CONSTITUTION` / `PRODUCT_SCOPE` / 功能目录 / 职责矩阵深读判断，再决定是否需要调整知识治理结构，
  禁止把单点疑问直接改成片面设计变更。

## 最新阶段交接（2026-06-30 全视角真实前台体验优化第三批）

- 基于 134 真实前台截图继续体验，发现多轮演练后两类重复数据难辨认：
  - 值集维护页出现大量同名“值集资产已登记”草稿，缺少维护时间与分页，平台运营、实施、信息科长无法快速辨认最新前台提交。
  - 系统接入页“接入向导”已有服务端分页，但同类接入申请只显示“接入申请已登记 / 字段映射已配置 / 未接通”，缺少最近更新时间和总量说明。
- 已本地修复并提交 `57eec00c742bc3b8a7981521e09b1ddc93b3b40a`
  （`fix: 优化演练数据列表可辨识性`）：
  - 配置资产维护表按最近维护时间倒序展示，补“维护时间”列，显示“最新维护 yyyy年MM月dd日 HH:mm”，并开启每页 10 条分页与总量说明。
  - 接入向导表补“最近更新”列，显示“最近更新 yyyy年MM月dd日 HH:mm”，并在分页上显示“共 N 条接入申请，当前显示 x-y 条”。
  - 未新增后端契约，不删除真实演练数据；用已有 `updatedAt` 让重复前台数据可区分，保留证据详情开关原有行为。
- 红绿验证：
  - 红灯：`npm --prefix frontend test -- DeclarativeAssetWorkbench.test.tsx -t "keeps repeated"`
    失败于首行找不到“最新维护 2026年06月30日 23:18”；
    `npm --prefix frontend test -- AdapterHub.test.tsx -t "keeps repeated onboarding"`
    失败于首行找不到“最近更新 2026年06月30日 23:18”。
  - 绿灯：上述两个目标用例均通过；
    `npm --prefix frontend test -- DeclarativeAssetWorkbench.test.tsx AdapterHub.test.tsx`
    通过，`2` 个文件 / `25` 项。
  - 完整前端验证：`npm --prefix frontend run verify` 通过，`113` 个测试文件 / `904` 项；
    `npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source 5cc9faddee2bebbf626908c9d33e08b14f3eb8b3`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260630-233935`，
    readiness HTTP 200 / `{"status":"UP"}`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-5cc9fadd-deployed-direct134`
    直接访问 134 运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.4m)`；
    `12` 个职责动作均 `actions=1`，浏览器错误、服务端错误、网络失败均为 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-5cc9fadd-onboarding-e2e-fixed4`
    直接访问 134 运行补强后的 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (35.9s)`；
    运行记录从 `9` 段扩展为 `10` 段，新增“前台创建系统接入申请”，全部
    `browserErrors/serverErrors/networkFailures=0`。
  - 截图复核：`real-frontdesk-value-set.png` 显示“维护时间”列、按最新维护时间降序，以及
    “共 30 条配置资产，当前显示 1-10 条”；`real-frontdesk-onboarding.png` 显示“最近更新”列、
    “共 1 条接入申请，当前显示 1-1 条”，接入申请确由前台弹窗提交产生。
- 本阶段继续补强 E2E 演练脚本：`frontend/e2e/real-frontdesk-rehearsal.spec.ts`
  在平台接入后新增前台“接入申请”创建步骤，复用真实组织目录与前台 Select 搜索。
  调试中确认 134 组织名称不同于单测示例、Ant Select 下拉 role 不稳定，已按真实 DOM 改为可见文本选择。
  该补强只影响本地测试/演练证据，不改变 134 已部署应用行为。
- E2E 补强验证：补强后的 `real-frontdesk-rehearsal.spec.ts --project=chromium` 直接访问 134 通过；
  补强后再次运行 `npm --prefix frontend run verify` 通过，`113` 个测试文件 / `904` 项；`git diff --check` 通过。

## 最新阶段交接（2026-07-01 全视角真实前台体验优化第四批·本地验证与134复演）

- 基于第三批 134 真实前台截图与全角色复演，继续按医生、护士、患者/代理、药师、医技、
  质控、信息科、实施工程师、审计员、院长等视角核查，发现日期时间表达仍有全局体验风险：
  部分页面直接使用浏览器默认 `toLocaleString()` 或本地 `Intl.DateTimeFormat`，在不同浏览器/地区可能显示为
  `6/4/2026`、`2026/6/4` 或同页混合格式；随访截止、路径准入、CDSS 决策审计、系统接入、
  模型供应商、来源血缘、审计详情、租户上线和发布治理都会受影响。
- 已新增统一前台显示 helper `frontend/src/shared/lib/dateTimeText.ts`：
  - 业务/临床默认使用 `Asia/Shanghai`、`yyyy年MM月dd日 HH:mm`。
  - 纯日期使用 `yyyy年MM月dd日`。
  - 审计与运行诊断保留秒级 `yyyy年MM月dd日 HH:mm:ss`。
- 已将日期时间显示统一接入医生/护士临床链路、患者路径与随访、CDSS、工作台、系统接入、
  配置资产、模型供应商、知识来源、图谱/血缘、系统保障、安全基线、身份绑定、运行诊断、
  租户开通、规则定义与发布治理等入口；高级信息仍通过既有证据详情机制展开，不新增“专家模式/技术细节”式孤立入口。
- 已补强前端断言：
  - `Followup.test.tsx` 覆盖随访截止日期为中文日期，且不出现 `6/8/2026`。
  - `PatientPathways.test.tsx` 覆盖路径准入与变异时间为中文临床时间，且不出现斜杠日期。
  - `CdssFatigue.test.tsx` 覆盖医生反馈审计时间为中文临床时间。
  - `AdapterHub.test.tsx` 覆盖系统接入、主数据同步和回调通道时间为中文临床时间。
  - `vitestRuntimeBudget.test.ts` 覆盖完整 `verify` 门禁使用既有 CI 测试预算运行全量 Vitest，
    保留普通 `npm test` 的本地 5 秒快速反馈。
- 验证证据：
  - 目标页面集合：`npm --prefix frontend test -- WorkbenchPanel.test.tsx ... SourceInfo.test.tsx`
    通过，`22` 个测试文件 / `209` 项。
  - 首轮 `npm --prefix frontend run verify` 在裸 `npm test` 的本地 5 秒预算下暴露
    `Followup` 与 `KnowledgeGovernance` 两个复杂交互用例全量并发超时；两个用例单独复现分别
    `1.327s`、`0.881s` 通过。根因是完整门禁未启用项目已有 CI 预算，不是功能断言失败。
  - 已将 `frontend/package.json` 的 `verify` 末尾改为 `CI=true npm test`，并用红绿测试固化：
    `npm --prefix frontend test -- vitestRuntimeBudget.test.ts` 先红后绿。
  - 重新运行 `npm --prefix frontend run verify` 通过，`113` 个测试文件 / `905` 项；过程中仍有既有
    `antd Timeline.Item` 弃用警告。
  - `npm --prefix frontend run build` 通过；`git diff --check` 通过。
  - 反查 `rg "toLocaleString\\(|toLocaleDateString\\(|new Intl\\.DateTimeFormat"`：
    日期时间格式仅剩统一 helper；其余 `toLocaleString` 均为金额/计数数字格式。
- 本地提交 `936aa955b998a0912fa4c569f7bb6bc1dd3d4598`
  （`fix: 统一前台临床日期时间表达`）已生成，暂不推送远程。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source 936aa955b998a0912fa4c569f7bb6bc1dd3d4598`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260701-120414`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-936aa955-deployed-direct134`
    直接访问 134 运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.3m)`；
    运行记录 `12` 个职责视角均有真实动作，浏览器错误、服务端错误、网络失败均为 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-936aa955-date-e2e`
    直接访问 134 运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (34.4s)`；
    运行记录 `10` 段真实前台链路均由页面提交产生，浏览器错误、服务端错误、网络失败均为 `0`。
  - 关键截图复核：`real-frontdesk-onboarding.png` 显示“最近更新 2026年07月01日 12:06”；
    `real-frontdesk-value-set.png` 显示“最新维护 2026年07月01日 12:06”；
    `real-frontdesk-followup-plan-questionnaire-abnormal.png` 的随访截止日期均为中文日期；
    可见入口未再出现浏览器地区化斜杠日期，表格换行可读且未发现重叠。

## 最新阶段交接（2026-07-01 全视角真实前台体验优化第五批·质量整改截止时间）

- 基于第四批 134 复演继续按质控、医保审核、院长质量下钻和实施验收视角核查，发现
  `a1a55176d45518d01a132593f8e35a303e5c79e1` 虽已把整改截止时间从裸 ISO 文本改为日期输入，
  但浏览器原生 `datetime-local` 在 134 可见渲染为 `07/15/2026, 08:30 AM`，会造成院内中文时间口径
  与前台真实操作不一致。坏例截图已留在
  `/tmp/medkernel-e2e-codex3/evidence-quality-dueat-field-a1a55176/insurance-dueat-field.png`，
  该提交不得作为最终交付证据引用。
- 已本地修复并提交 `ce36f55c3e4b4e88e0f83ee23472ab3d9132f6ba`
  （`fix: 使用中文院内时间填写整改截止`）：
  - `RectificationDueAtField` 改为受控中文院内时间输入，提示为“例如 2026年07月15日 08:30”，
    统一校验“yyyy年MM月dd日 HH:mm”格式，避免浏览器按地区显示斜杠日期或 AM/PM。
  - `dateTimeText` 将整改截止时间输入格式统一为中文临床时间，提交时换算为 UTC ISO；
    保留旧 `YYYY-MM-DDTHH:mm` 解析兜底，避免已有表单状态或自动填充破坏后端契约。
  - 质控预警、评价结果派发整改和医保审核派整改三条流程均继续向后端提交同一 UTC ISO，
    前台不再展示技术 ISO 或浏览器地区化时间。
- 红绿验证：
  - 红灯：`npm --prefix frontend test -- dateTimeText.test.ts QcAlerts.test.tsx QcEvalResults.test.tsx InsuranceAudit.test.tsx`
    在旧实现下失败，暴露 `formatClinicalDateTimeInputValue` 仍输出 `2026-06-08T08:00`，
    `clinicalDateTimeInputToIso` 对中文院内时间原样透传。
  - 绿灯：`npm --prefix frontend test -- dateTimeText.test.ts QcAlerts.test.tsx QcEvalResults.test.tsx InsuranceAudit.test.tsx QcDashboard.test.tsx`
    通过，`5` 个文件 / `21` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `907` 项；
    `npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source ce36f55c3e4b4e88e0f83ee23472ab3d9132f6ba`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260701-123246`，
    manifest `deployedAt=2026-07-01T12:32:48+08:00`，
    `jarSha256=4f4a8098720bab50a2e7319731b63c5869eee7ae017f9d8541c2c6747b5e060d`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=1965458`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-ce36f55c-quality-dueat-cn`
    直接访问 134 运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.4m)`；
    运行记录 `12` 个职责视角均有真实动作，浏览器错误、服务端错误、网络失败均为 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-ce36f55c-quality-dueat-cn`
    直接访问 134 运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (37.5s)`；
    运行记录 `10` 段真实前台链路均由页面提交产生，浏览器错误、服务端错误、网络失败均为 `0`。
  - 针对问题本身的人工式浏览器核查：
    `/tmp/medkernel-e2e-codex3/evidence-quality-dueat-field-ce36f55c/insurance-dueat-field-cn.png`
    显示医保审核页“整改截止时间”为 `2026年07月15日 08:30`，输入 `type=text`，
    placeholder 为 `例如 2026年07月15日 08:30`，旧斜杠日期 / ISO 可见值为空，字段周边未发现遮挡。

## 最新阶段交接（2026-07-01 全视角真实前台体验优化第六批·模型安全边界）

- 基于用户补充的院外/院内大模型双模式要求，以及第五批 134 复演后的模型能力页截图继续核查，发现原页面把
  无模型规则链路、院内本地模型和公网外部模型都归入“外调安全”，会误导医疗引擎运营员、信息科长、
  实施工程师和审计员：无模型状态应是预设边界，院内本地模型应体现授权边界，公网模型应体现严格脱敏边界。
  后端 `ModelEgressGuard` 当前仍会对直接核心标识做强处理，本批前台不宣称院内模型可原文外泄核心敏感信息。
- 已本地修复并提交：
  - `609842c7f4947c3b3cc1519a3beaa30fdf55fb77`（`fix: 区分模型安全边界配置语义`）：
    模型能力页改为按运行方式展示“安全边界已预设 / 院内授权已配置 / 公网安全已配置”，对应操作为
    “预设安全边界 / 配置或调整院内授权 / 配置或调整公网安全”，弹窗标题与保存按钮同步区分。
  - `fe59c5732ce3007be79646c6e25b0e3d80f62cd6`（`test: 适配模型能力真实安全边界`）：
    单测与 D6 验收改为断言真实安全边界语义，不再寻找旧“外调安全”入口。
  - `767fa18f50fc02408f7ecc58a9fb57caa20e50a7`（`test: 更新真实演练模型安全边界流程`）：
    真实前台与全角色演练脚本改用“模型安全边界”流程，并修复 Ant Select 隐藏选项命中导致的真实浏览器不稳定。
- 体验口径：
  - 公网模型：可在授权用途内使用患者上下文，但姓名、证件号、手机号、地址、患者编号等核心标识字段先遮蔽。
  - 院内本地模型：按授权使用必要患者信息，日志与证据不保留患者明文，并保留敏感信息处理边界。
  - 无模型规则链路：只预设未来切换模型前的安全字段与责任确认，不改变当前 B0 规则链路。
  - 旧“外调结果 / 外调允许字段 / 外调安全”前台文案已替换为“模型使用结果 / 模型允许字段 / 模型安全边界”。
- 本地验证证据：
  - 红灯：旧实现下 `npm --prefix frontend test -- AiWorkflows.test.tsx` 失败于旧“配置外调安全”入口与旧弹窗标题。
  - 绿灯：`npm --prefix frontend test -- AiWorkflows.test.tsx` 通过，`10` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `908` 项；
    `npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source 767fa18f50fc02408f7ecc58a9fb57caa20e50a7`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260701-130501`，
    manifest `deployedAt=2026-07-01T13:05:03+08:00`，
    `jarSha256=3e5d581263aed00fda2a01d9ea46edf325b7e2981e82da6f7cd750dd314dfe81`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=1983099`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-d6-ai-workflows-767fa18f-safety-boundary`
    直接访问 134 运行 `d6-ai-workflows.spec.ts --project=chromium` 通过，`2 passed (17.5s)`；
    report stats 为 `expected=2`、`unexpected=0`、`flaky=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-767fa18f-model-boundary`
    直接访问 134 运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.3m)`；
    运行记录 `12` 类视角（医生、护士、药师、医技、质控、患者代理、平台管理员、医疗引擎运营员、
    审计员、信息科长、实施工程师、院长）均有 `actions=1`，浏览器错误、服务端错误、网络失败均为 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-767fa18f-model-boundary`
    直接访问 134 运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (35.0s)`；
    运行记录 `10` 段真实前台链路均由页面提交产生，包含“前台配置模型安全边界策略”，全部
    `browserErrors/serverErrors/networkFailures=0`。
  - 截图复核：`ai-workflows-desktop.png` 与 `ai-workflows-mobile.png` 显示“患者上下文模型使用边界”
    说明，移动端纵向可读；`real-frontdesk-model-safety-boundary.png` 显示保存反馈
    “模型安全边界已保存”，列表列名为“模型安全边界”，未再出现旧“外调安全”误导文案。

## 最新阶段交接（2026-07-01 第七批·134 mimoModel 运行配置接入）

- 基于用户补充“院外支持的大模型信息维护在 134 服务器 `/zoesoft/mimoModel`”继续主线核查。
  该文件是受控运行配置，不提交仓库；本批只记录脱敏形态与运行结果，不输出或保存真实密钥。
- 已本地修复并提交 `07ff36c3de53433f43ea3bad281e1ff5827667b3`
  （`fix: 接入mimo模型运行配置`）：
  - `scripts/release/model-provider-launch-lib.mjs` 支持 `LAUNCH_MODEL_PROFILE_FILE` 从仓库外读取
    `mimoModel` 风格配置，兼容三行裸值、`key=value`、`key: value`、键值与裸凭据混合格式。
  - Provider 类型从只允许 `OLLAMA` 扩展到 `OLLAMA / OPENAI_COMPATIBLE / CLAUDE / DIFY`；
    公网 Provider 默认要求 HTTPS，凭据走 `/model-providers/{code}/credential` 受管保存。
  - OpenAI 兼容端点归一化为后端适配器需要的根地址：配置文件若给出 `/v1/chat/completions`、
    `/v1/models` 或 `/v1`，发布脚本保存为后端拼接 `/v1/...` 前的根路径，避免重复 `/v1`。
  - 公网模型能力策略使用 `EXTERNAL_MODEL` + `MASK_ALL` + `EXTERNAL_MASKED` 证据描述；
    院内/本地模型保持 `LOCAL_MODEL` + `LOCAL_ONLY` 路线，不把凭据写入上线证据。
- 真实 134 配置解析证据（脱敏）：
  - `/zoesoft/mimoModel` 已复制到本机临时受控路径并设为 `0600`；文件内容未打印。
  - 解析结果为 `providerCode=mimo-public`、`providerType=OPENAI_COMPATIBLE`、
    `endpointProtocol=https:`、`endpointPath=/`、`credentialPresent=true`；
    模型版本只确认已解析，不记录真实值。
- 真实 134 Provider 上线结果：
  - 使用 `node --use-system-ca scripts/release/model-provider-launch.mjs` 访问
    `https://193.112.107.134/medkernel/api/v1`，脚本按预期先登记 Provider 并保存受管凭据，
    但后端健康检查后停止，错误为“Provider 状态必须为 enabled=false, status=HEALTHY”。
  - 134 关系库脱敏视图：`mimo-public` 当前 `enabled=false`、`status=NOT_CONNECTED`、
    `credentialConfigured=true`、`endpointPath=/`；Provider 未启用，未进入医学回归、策略发布或版本组合发布。
  - 根因复核：修正前后端日志显示旧路径为 HTTP 404；本批端点修正后上游返回 HTTP 401。
    使用同一解析器直接探测上游 `/v1/models` 与 `/v1/chat/completions` 均为 HTTP 401，
    响应体错误为 `Invalid API Key`。因此当前阻断是 134 文件中的外部模型凭据无效或已过期，
    不是发布脚本端点解析、后端 Provider 保存或前台模型安全边界问题。
  - 已登记外部环境待处理项 `DEFER-003`；拿到有效凭据前不得强行启用公网 Provider，也不得伪造模型上线通过。
- 验证证据：
  - 红灯：`node --test scripts/release/model-provider-launch.test.mjs` 在旧端点归一化下失败，
    三种 `mimoModel` 输入均错误保留 `/v1`。
  - 绿灯：`node --test scripts/release/model-provider-launch.test.mjs` 通过，`9` 项。
  - 回归组合：`node --test scripts/release/model-provider-launch.test.mjs scripts/release/full-system-rehearsal.test.mjs scripts/release/runtime-resilience-rehearsal.test.mjs`
    通过，`19` 项。
  - `git diff --check` 通过；敏感扫描只命中 `example.com` 测试假数据和环境变量名。
- 第七批没有变更后端/前端应用包，因此当时未重新部署 134；随后第八批应用变更曾重新发布。
  该历史部署锚点已被后续阶段取代，当前 134 版本只以本文“当前主线”和最新阶段交接为准。

## 最新阶段交接（2026-07-01 第八批·质量报告处理摘要与复演）

- 基于第七批后的“质量报告长明细阅读负担”和全角色体验继续核查，发现两类上线级体验缺口：
  - 质量管理概览在部分指标不可计算时只给低层指标说明，质控、院长和信息科长难以快速判断当前筛选页应该先处理什么。
  - 系统接入的数据质量报告生成后只有指标和缺口明细，实施工程师和信息科长需要自行推断是否暂缓上线、先处理断连还是字段映射。
- 已本地修复并提交：
  - `190dcddb3b46796ac6d959a624bb61fea85a77c1`（`fix: 强化质量报告处理摘要`）：
    `QcDashboard` 增加“当前页处理摘要”，按当前页严重程度、状态和科室筛选给出处理顺序；
    `AdapterHub` 在数据质量报告中增加“上线判断：暂缓上线 / 可继续接入验收”和先后处理顺序。
  - `662a19b373e1fb6025cb4d84e482db3892e301b4`（`fix: 避免质量报告摘要标签冲突`）：
    修正 `190dcddb` 引入的行动摘要文案冲突；报告详情字段继续叫“缺口摘要”，上方行动判断改为“报告缺口”，避免用户和严格 E2E 都被同一卡片内的重复标签误导。
- 红绿与本地验证：
  - 红灯：`npm --prefix frontend test -- QcDashboard.test.tsx AdapterHub.test.tsx`
    在旧实现下失败于找不到“当前页处理摘要”和“上线判断：暂缓上线”。
  - 绿灯：上述目标测试通过，`2` 个文件 / `24` 项。
  - 标签冲突红灯：`npm --prefix frontend test -- AdapterHub.test.tsx -t "默认展示业务接入摘要"`
    在旧文案下失败于找不到“报告缺口：HIS 断连，LIS 配置非法”。
  - 标签冲突绿灯：同一目标用例通过；`npm --prefix frontend test -- AdapterHub.test.tsx`
    通过，`19` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `908` 项；
    过程中仍只有既有 `antd Timeline.Item` 弃用警告。
  - `npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 曾用 `190dcddb3b46796ac6d959a624bb61fea85a77c1` 发布到 134；
    备份 `/zoesoft/medkernel/backups/deploy-20260701-134031`，
    manifest `deployedAt=2026-07-01T13:40:33+08:00`，
    `jarSha256=33d1e466e5eedd08a68c33c2d85529244068b1a97db4cd055f794be2db73ccd0`，
    readiness HTTP 200 / `{"status":"UP"}`。该版本在全职责 E2E 暴露“缺口摘要”文案重复导致的严格定位歧义，
    只作为根因证据，不作为最终交付证据。
  - 已用 `deploy/onprem/mk-publish.sh --source 662a19b373e1fb6025cb4d84e482db3892e301b4`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260701-134901`，
    manifest `deployedAt=2026-07-01T13:49:03+08:00`，
    `jarSha256=522522403195c6e8b94a7a5c75cc1b2536d69b98a24cfbbd10e77733561f8b37`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=2006750`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-662a19b3-quality-summary-fixed`
    直接访问 134 运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.4m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `12` 类视角均有 `actions=1`，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-662a19b3-quality-summary-fixed`
    直接访问 134 运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (34.6s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `10` 段真实前台链路均为页面提交产生，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - 截图复核：`stakeholder-it_manager.png` 显示数据质量报告行动区使用“报告缺口”，详情字段仍保留“缺口摘要”；
    `stakeholder-quality_controller.png` 与 `stakeholder-hospital_executive.png` 显示质量概览处理摘要可见且未遮挡；
    `real-frontdesk-onboarding.png` 显示前台新增接入申请与最近更新时间；
    `real-frontdesk-value-set.png` 显示前台新增值集按最新维护时间排序；
    `real-frontdesk-model-safety-boundary.png` 显示模型安全边界仍按患者上下文脱敏 / 院内授权口径呈现。

## 最新阶段交接（2026-07-01 第九批·协同任务技术摘要收敛）

- 基于第八批 134 全职责截图继续按医技、医生、护士、患者代理、药师等真实角色体验核查，发现
  `stakeholder-medical_technician.png` 的协同任务默认列表直接展示
  `runtime-*` 运行版本与 `result-review` 触发点。该信息对医技/医生处理报告待办没有决策价值，
  会把低频追溯对象暴露到主任务摘要，违反“技术对象收进高级信息 / 证据详情”的体验契约。
- 已本地修复并提交 `0ecfe3eca3412e7470314c1ed6a96d176a2ad6e0`
  （`fix: 收起协同任务技术摘要`）：
  - `WorkflowTodos` 默认按来源类型给出业务摘要；当原始摘要含 `runtime-*`、触发点或 trigger 标识时，
    默认显示“报告结果需要结合患者上下文完成辅助解读，处理结论需由医技或医生确认”等业务语句。
  - 证据详情打开后仍显示原始摘要、追踪号、来源对象、患者和流转证据，保证审计与实施排障可追溯，
    不新增旧式孤立“专家模式 / 技术细节”入口。
- 红绿与本地验证：
  - 红灯：`npm --prefix frontend test -- WorkflowTodos.test.tsx -t "keeps runtime release"`
    在旧实现下失败于默认列表找不到业务摘要。
  - 绿灯：同一目标用例通过；`npm --prefix frontend test -- WorkflowTodos.test.tsx`
    通过，`19` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `909` 项；
    过程中仍只有既有 `antd Timeline.Item` 弃用警告。
  - `npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source 0ecfe3eca3412e7470314c1ed6a96d176a2ad6e0`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260701-143550`，
    manifest `deployedAt=2026-07-01T14:35:52+08:00`，
    `jarSha256=34a7d07b34535be3e2488b358cb8b824b66af9cdbb6487679e3b5f9b014c71d7`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=2031483`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-0ecfe3ec-workflow-summary`
    直接访问 134 运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.3m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `12` 类视角均有真实动作，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-0ecfe3ec-workflow-summary`
    直接访问 134 运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (34.0s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `10` 段真实前台链路均由页面提交产生，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - 截图复核：`stakeholder-medical_technician.png` 的协同任务默认列表已显示业务报告解读摘要，
    未再裸露 `runtime-*` 或 `result-review`；`real-frontdesk-model-safety-boundary.png` 仍按公网患者上下文脱敏、
    院内授权使用必要信息的模型安全边界呈现；`real-frontdesk-followup-plan-questionnaire-abnormal.png`
    暴露出下一批待处理问题：随访页患者过滤器仍显示 `mpi-*` 原始患者标识。

## 最新阶段交接（2026-07-01 第十批·随访患者过滤器原始标识收敛）

- 基于第九批真实前台复演继续按护士、患者代理、医生和随访实施视角核查，发现
  `real-frontdesk-followup-plan-questionnaire-abnormal.png` 的“按患者线索检索”输入框在生成随访计划后显示
  `mpi-*` 原始患者标识。该标识虽然用于后端精确过滤，但不应作为默认前台可见文本暴露给普通临床操作路径。
- 已本地修复并提交 `89a641fabd5b5e48c326b2183cde8dfd43232dfd`
  （`fix: 隐藏随访患者过滤原始标识`）：
  - `Followup` 将患者过滤器拆为真实查询值与可见业务文本；生成随访计划后继续用后端返回的真实 `patientId`
    查询计划和统计，但输入框显示“已筛选刚生成计划的患者”。
  - 手工输入仍按用户输入作为患者线索查询；清空输入会同步清空真实查询值。
  - 表格和抽屉继续沿用“患者已关联 / 就诊已关联”的默认业务摘要，证据详情打开后仍可追溯计划、患者、模板和任务原始标识。
- 红绿与本地验证：
  - 红灯：`npm --prefix frontend test -- Followup.test.tsx -t "keeps generated plan patient identifiers"`
    在旧实现下失败，输入框实际值为 `mpi-01KWE6CK9MD11VREALFILTER`，而不是业务文本。
  - 绿灯：同一目标用例通过；`npm --prefix frontend test -- Followup.test.tsx`
    通过，`16` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `910` 项；
    过程中仍只有既有 `antd Timeline.Item` 弃用警告。
  - `npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source 89a641fabd5b5e48c326b2183cde8dfd43232dfd`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260701-144721`，
    manifest `deployedAt=2026-07-01T14:47:23+08:00`，
    `jarSha256=84d2c8bc2d1b461309577b41336a3e653c0cb6043a0f9cd3b9a2d944d5cadd37`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=2037866`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-89a641fa-followup-filter`
    直接访问 134 运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.3m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `12` 类视角均有真实动作，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-89a641fa-followup-filter`
    直接访问 134 运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (34.6s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `10` 段真实前台链路均由页面提交产生，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - 截图复核：`real-frontdesk-followup-plan-questionnaire-abnormal.png` 中患者过滤器显示
    “已筛选刚生成计划的患者”，不再显示 `mpi-*`；随访计划列表仍正常展示患者/就诊已关联、病种、方案、
    任务进度与办理入口。

## 最新阶段交接（2026-07-01 第十一批·随访办理焦点收敛）

- 基于第十批真实前台截图继续按护士、患者代理、医生和随访实施视角体验，发现
  `real-frontdesk-followup-plan-questionnaire-abnormal.png` 中“随访计划办理”抽屉打开后，背景随访计划表仍透过遮罩清晰可读。
  护士登记异常回院、患者代理代填问卷时会被底部计划列表抢视线，也容易误解为当前表单的一部分。
- 处理中曾本地提交并短暂发布 `3ffeaa61a11a18b2adcf66ad6307421258bbeb7c`
  （`fix: 提升随访办理抽屉层级`），但 134 截图复核显示背景计划表仍然可读；该提交只保留为根因证据，
  **不是完成态，不作为后续接力依据**。
- 最终已本地修复并提交 `c9ac9ea2226952aa6133e72925a5fc11bda8bff4`
  （`fix: 办理随访时收起背景列表`）：
  - `Followup` 统一计算随访办理抽屉打开状态，并在抽屉打开时隐藏背景“随访计划列表”区域。
  - 背景计划列表新增明确 `aria-label="随访计划列表"`，方便无障碍边界与自动化验收；抽屉关闭后列表恢复。
  - 抽屉仍保留显式层级 `1200`，但完成态以“背景列表不可见”为验收标准，而不是仅调层级。
- 红绿与本地验证：
  - 红灯：`npm --prefix frontend test -- Followup.test.tsx -t "办理抽屉打开后收起背景计划列表"`
    在旧实现下失败，页面没有可识别的“随访计划列表”边界，也无法保证抽屉打开时背景列表不可见。
  - 绿灯：同一目标用例通过；`npm --prefix frontend test -- Followup.test.tsx`
    通过，`17` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `911` 项；
    过程中仍只有既有 `antd Timeline.Item` 弃用警告。
  - `npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source c9ac9ea2226952aa6133e72925a5fc11bda8bff4`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260701-151012`，
    manifest `deployedAt=2026-07-01T15:10:14+08:00`，
    `jarSha256=19633f0c98db574fee758a24a7bab7bce1365e74bff03e65cf4415cc5ca10dac`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=2050289`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-c9ac9ea2-followup-drawer-focus`
    直接访问 134 运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.3m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `12` 条职责视角记录、`4` 类登录职责覆盖，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-c9ac9ea2-followup-drawer-focus`
    直接访问 134 运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (34.1s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `10` 段真实前台链路均由页面提交产生，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - 截图复核：`real-frontdesk-followup-plan-questionnaire-abnormal.png` 中办理抽屉打开后不再显示底部随访计划表，
    左侧背景仅保留弱化的页面上下文；患者过滤器继续显示“已筛选刚生成计划的患者”，未回退到 `mpi-*`。

## 最新阶段交接（2026-07-01 第十二批·医保结算到质控整改数据路线）

- 按用户要求先沿现有演练模式把基础数据路线走通，暂不跳到全视角大改。真实前台演练暴露医保结算声明、
  医保审核、内涵质控和整改任务之间存在多处基础阻塞：前台声明插入语义不稳、缺科室/指标主数据时不能形成
  真实问题、手工医保规则归档选项命名歧义，以及没有生效质控指标时医保审核无法继续派整改。
- 已本地修复并提交：
  - `c8811583d`（`fix: 打通前台医保结算与质控整改`）：补通前台医保结算、审核和整改联动。
  - `aff61347`（`fix: 修复前台医保声明插入语义`）：修复 `mk_clinical_claim` assigned-id 插入语义。
  - `ec8baca3`（`fix: 解除医保审核主数据空目录阻塞`）：缺责任科室或指标目录时使用当前组织和手工规则兜底。
  - `082802d9`（`fix: 避免医保规则归档选项命名冲突`）：将“按本次医保规则依据归档”收敛为“按本次规则归档”。
  - `1d76bd6a`（`fix: 医保审核缺指标时跳过内涵质控`）：无生效质控指标时先跳过病例质控扫描，继续 DRG 与医保审核。
  - `0c4d0868`（`fix: 支持医保手工规则直接派整改`）：后端支持 `INSURANCE_RULE_MANUAL` 直接生成
    `quality_finding` 与 `rectification_task`，并把医保问题置为已派整改。
- 验证证据：
  - `mvn -Dtest=InsuranceQualityServiceTest#insuranceAuditManualRuleCreatesDirectRectificationWithoutActiveIndicator test`
    通过；`mvn -Dtest=InsuranceQualityServiceTest test` 通过，`7` 项。
  - `git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source 0c4d08686d71db8640548abe0817ac41d13949c6`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260701-161705`，
    manifest `deployedAt=2026-07-01T16:17:07+08:00`，
    `jarSha256=e33badb698b9289e6d00c6982488bbba49540fd31b2f0e5b5b3df7ea01455b2a`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=2087387`、`NRestarts=0`。
  - 直接访问 134 复跑 `real-frontdesk-rehearsal.spec.ts --project=chromium` 与
    `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过；真实前台证据目录
    `/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-0c4d0868-insurance-quality`，
    全角色证据目录 `/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-0c4d0868`。
  - 截图复核显示基础数据路线已走通，但医保/质量默认页面仍有低频追溯字段和原始组织标识暴露，
    作为第十三批继续收敛的问题来源。

## 最新阶段交接（2026-07-01 第十三批·质量医保默认信息层级）

- 基于第十二批 134 复演继续按院长、质控、医保审核员和实施视角核查，发现功能已走通但默认信息层级不够业务化：
  医保问题列表默认展示原始组织 ID，质量管理概览待处置问题摘要默认展示 `claim-*`、医保规则编号和阈值追溯。
  这些字段对默认业务判断帮助有限，应进入证据详情，避免误导普通角色。
- 已本地修复并提交：
  - `8e650db5`（`fix: 收敛质量默认信息层级`）：先收敛质量默认摘要和组织显示；该版本复演通过，
    但截图继续发现医保列表仍有原始组织 ID，保留为中间证据。
  - `3e343b69`（`fix: 收起质量医保追溯字段`）：`InsuranceAudit` 使用组织范围显示“当前机构”等业务标签，
    原始组织 ID 仅在证据详情展示；`QcDashboard` 默认用业务摘要表达医保质控待处置问题，证据详情打开后追溯
    `claim`、规则编号、阈值等低频字段。
- 红绿与本地验证：
  - 红灯：`npm --prefix frontend test -- InsuranceAudit.test.tsx QcDashboard.test.tsx`
    在旧实现下失败于默认找不到“当前机构”、证据详情找不到“当前机构 · hospital-rehearsal”，以及质量概览默认摘要未收敛。
  - 绿灯：同一命令通过，`2` 个文件 / `13` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `914` 项；
    `npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source 3e343b695e24d49e17029a235d6390c84510aa43`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260701-220355`，
    manifest `deployedAt=2026-07-01T22:03:58+08:00`，
    `jarSha256=f5ce56d62091892997b0425e4b9f36ee33ee143e4bdabff18b12a48bc3530268`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=2270111`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-3e343b69-quality-evidence-defaults`
    直接访问 134 运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (42.7s)`；
    运行记录 `11` 段，浏览器错误、服务端错误、网络失败均为 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-3e343b69-quality-evidence-defaults`
    直接访问 134 运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.4m)`；
    运行记录 `12` 类视角，浏览器错误、服务端错误、网络失败均为 `0`。
  - 截图复核：`real-frontdesk-insurance-quality-rectification.png` 的医保问题列表默认显示“当前机构”、
    “规则依据已关联”和“证据已记录”；`stakeholder-hospital_executive.png` 的待处置问题默认摘要不再裸露
    `claim-*` 或医保规则编号。

## 最新阶段交接（2026-07-01 第十四批·知识生产治理语义）

- 用户提出“诊断知识维护与整体知识管理是否怪怪的、知识治理是否已满足全链路”的疑问；该疑问只作为全局风险假设，
  不等于当前诊断知识设计错误。已按原始权威文档核查：`CONSTITUTION`、`PRODUCT_SCOPE` 与 `ARCHITECTURE`
  均要求人工维护、来源解析、确定性校验、审核发布和无模型 B0 主链可运行，大模型只生成候选、草稿或解释，
  不能直接成为正式知识，也不能是唯一生产方式。
- 基于上述权威约束，仅修复一个明确冲突的前台口径，不扩展重构诊断知识维护：
  - 本地提交 `d3d6138eb29a572a69a22684dedfc6ec00291cca`
    （`fix: 收敛知识生产模型唯一表述`）。
  - `/knowledge/production` 从“正式知识只允许大模型生产”改为“正式知识不得绕过统一治理链”；
    说明人工维护、来源解析和模型生成都只能形成草稿或候选，无模型时仍可完成来源登记、人工维护、
    确定性校验和审核发布。
  - 生产前校验中的 `EGRESS_GOVERNANCE` 前台标签从“外调允许范围”收敛为“模型使用边界”，和模型安全边界口径一致。
- 红绿与本地验证：
  - 红灯：`npm --prefix frontend test -- KnowledgeProductionPage.test.tsx ProductionReadinessPanel.test.tsx`
    在旧实现下失败于找不到“正式知识不得绕过统一治理链”和“6. 模型使用边界”。
  - 绿灯：同一命令通过，`2` 个文件 / `2` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `914` 项；
    `npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source d3d6138eb29a572a69a22684dedfc6ec00291cca`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260701-221752`，
    manifest `deployedAt=2026-07-01T22:17:55+08:00`，
    `jarSha256=4a0da314f707cd69db03bdc97a70956cf59fb1a8a2a424239ad51ee0c860d6ea`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=2277761`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-d3d6138e-knowledge-production-wording`
    直接访问 134 运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (43.6s)`；
    运行记录 `11` 段，浏览器错误、服务端错误、网络失败均为 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-d3d6138e-knowledge-production-wording`
    直接访问 134 运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.4m)`；
    运行记录 `12` 类视角，浏览器错误、服务端错误、网络失败均为 `0`。
  - 截图复核：`stakeholder-engine_operator.png` 显示“正式知识不得绕过统一治理链”，并说明人工维护、
    来源解析、模型生成都进入草稿或候选治理；`stakeholder-hospital_executive.png` 与
    `real-frontdesk-insurance-quality-rectification.png` 显示第十三批质量/医保默认信息层级未回退。

## 最新阶段交接（2026-07-01 第十五批·知识生产统一入口与证据层级）

- 继续按用户原始诉求和权威文档复核“知识治理是否满足全链路、诊断知识维护是否契合统一知识管理”：
  诊断知识维护仍按统一知识治理下的专业工作区理解，不因疑问本身直接改结构；当前代码已有共同身份、
  版本、发布、机构生效、运行引用和诊断选择器链路，未发现必须推翻的结构证据。
  已确认的真实冲突是旧公共生产策略把正式入口收窄为 API_MODEL，并阻断 `/generate` 的来源/模板候选生成，
  与 `PRODUCT_SCOPE` 中“人工维护、来源解析、确定性校验、模型候选、审核发布、无模型 B0 可运行”不一致。
- 已本地修复并提交 `e1675b64e8ee2749d5ece7b37fdc11cb08f828ea`
  （`fix: 打通知识生产统一入口与证据层级`）：
  - `ProductionReadinessPanel` 默认只显示“证据已记录 / 证据待补齐”，来源路径、模型 Provider、能力、
    策略、版本三元组等低频追溯字段进入统一“证据详情”开关；不再恢复旧“专家模式 / 技术细节”表达。
  - 生产前校验和演练文档统一使用“模型使用边界 / 患者上下文模型使用边界”；公网模型可使用患者上下文，
    但核心敏感信息需屏蔽并保留责任确认；院内本地模型支持必要患者信息处理且不记录明文敏感信息。
  - `FormalKnowledgeProductionPolicy` 改为校验所有生产方式都进入统一候选/草稿治理链；
    `/jobs` 支持 `MANUAL`、来源解析和受控模型等 `KnowledgeProducer`，资产类型按可发布运行配置校验；
    `/generate` 不再被旧策略直接拒绝，模型生成仍受 readiness、模型网关和安全边界控制。
  - 医保手工规则派整改不再由 `quality.insurance` 直接写 `quality_finding`、`rectification_task`；
    新增 `ManualQualityRectificationBridge`，在 `engine.evaluation` owner 边界内幂等创建问题和整改任务。
- 红绿与验证证据：
  - 红灯：旧实现下 `ProductionReadinessPanel.test.tsx` 会直接暴露 `file:///medkernel-data/`、
    `mimo-public`、能力和版本三元组；`KnowledgeProductionReadinessServiceTest` 仍使用“外调允许范围”；
    `FormalKnowledgeProductionPolicyTest` 和 `KnowledgeProductionControllerSecurityTest` 仍要求拒绝人工/B0 入口；
    完整 `mvn test` 暴露 `DomainOwnershipContractTest`，指出 `InsuranceQualityService` 写入 evaluation owner 表。
  - 绿灯：`mvn -Dtest=FormalKnowledgeProductionPolicyTest,KnowledgeProductionControllerSecurityTest,KnowledgeProductionReadinessServiceTest,ModelKnowledgeProducerTest test`
    通过，`63` 项；`mvn -Dtest=DomainOwnershipContractTest,InsuranceQualityServiceTest test` 通过，`10` 项；
    完整 `mvn test` 通过，`3061` 项、`0` 失败、`0` 错误、`7` 跳过。
  - 前端：`npm --prefix frontend test -- AiWorkflows.test.tsx ProductionReadinessPanel.test.tsx ReadinessValidation.test.tsx`
    通过，`23` 项；完整 `npm --prefix frontend run verify` 通过，`114` 个测试文件 / `915` 项；
    `npm --prefix frontend run build` 通过。
  - 收尾核查：`git diff --check` 通过；旧误导表述
    `正式知识生产仅允许|正式知识生产不再接受|只允许通过受控模型|外调允许范围缺失，已降级|外调安全策略|模型外调安全策略`
    在 `frontend/src`、`medkernel-backend/src`、`docs` 中无命中；医保域直接写整改 owner 表的 SQL 扫描无命中。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source e1675b64e8ee2749d5ece7b37fdc11cb08f828ea`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260701-230309`，
    manifest `deployedAt=2026-07-01T23:03:12+08:00`，
    `jarSha256=0ab178da5adcf1ff9ea65807124538b7ff95b5fbd030cb0faeec52a3dc7875b7`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=2301801`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-e1675b64-knowledge-unified-production-ready`
    直接访问 134 运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (40.8s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `11` 段真实前台链路，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-e1675b64-knowledge-unified-production-ready`
    直接访问 134 运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.3m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `12` 类职责视角，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - 截图复核继续暴露下一处真实体验问题：
    `stakeholder-engine_operator.png` 中知识生产页底部“模型生产上线准备”仍默认展示
    `LITERATURE_ROOT`、`MODEL_PROVIDER`、`VERSION_TRIPLE`、`file:///...` 和能力编码等低频追溯字段，
    作为第十六批继续收敛的问题来源。

## 最新阶段交接（2026-07-01 第十六批·知识生产上线准备默认证据收敛）

- 用户关于知识治理/诊断知识维护的提问只作为全局风险假设处理，不直接认定当前设计错误。
  本批先回读 `CONSTITUTION`、`PRODUCT_SCOPE` 与 `EXPERIENCE_CONTRACT`：统一知识生产仍要求人工维护、
  来源解析、模型候选、审核发布和无模型 B0 主链并存；技术对象、路径、能力编码、模型版本和追溯字段
  默认应进入“证据详情”，普通职责视图使用可解释业务语言。
- 已本地修复并提交 `79c2201e84281509d4fd45f5d9c81b2141b16ab4`
  （`fix: 收敛知识生产上线准备证据`）：
  - `KnowledgeGovernance` 的“模型生产上线准备”表格使用业务前置项名称：
    文献资料库、部署形态、模型服务、医学验证用例、医学验证评测、模型使用边界、能力策略、
    提示词/工具/模型版本。
  - 默认只显示“证据已记录 / 证据待补齐”；打开“证据详情”后才展示原始 readiness code、
    来源路径、能力编码、模型 Provider 和版本三元组。
  - 下游证据为空态和正常态复用同一列定义，避免同一页面上半区已收敛、底部表格又回到原始字段。
- 红绿与本地验证：
  - 红灯：`npm --prefix frontend test -- KnowledgeGovernance.test.tsx -t "默认用业务语言展示模型生产上线准备"`
    在旧实现下失败，页面默认找不到“文献资料库”，并继续暴露原始前置项编码。
  - 绿灯：同一目标用例通过；`npm --prefix frontend test -- KnowledgeGovernance.test.tsx ProductionReadinessPanel.test.tsx`
    通过，`39` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `916` 项；
    `npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source 79c2201e84281509d4fd45f5d9c81b2141b16ab4`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260701-232032`，
    manifest `deployedAt=2026-07-01T23:20:34+08:00`，
    `jarSha256=12da4e2adcaca869ad4746a770ab50a26058e1feb671b05757fbbfb61798ef1c`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=2311462`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-79c2201e-knowledge-readiness-evidence`
    直接访问 134 运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (42.2s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `11` 段真实前台链路，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-79c2201e-knowledge-readiness-evidence`
    直接访问 134 运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.2m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `12` 类职责视角，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - 截图复核：`stakeholder-engine_operator.png` 中“生产前校验 / 模型生产上线准备”默认显示
    “文献资料库、部署形态、模型服务、医学验证用例、模型使用边界、提示词/工具/模型版本”等业务名称，
    证据列为“证据已记录”；未再默认展示 `LITERATURE_ROOT`、`MODEL_PROVIDER`、`VERSION_TRIPLE`、
    `file:///...` 或 `knowledge.production.knowledge`。

## 最新阶段交接（2026-07-01 第十七批·医保审核快照默认标识收敛）

- 基于第十六批 134 真实前台截图继续按医保审核员、质控、实施工程师和信息科视角核查，发现
  `real-frontdesk-insurance-quality-rectification.png` 的“医保病案审核输入”在选中病案快照后默认把
  原始患者/就诊标识显示进“患者信息 / 就诊信息”输入框。该标识对业务操作没有帮助，容易让普通角色误以为
  需要手工理解 `mpi-*`、`enc-*` 这类内部追溯值；真实查询仍必须保留，原始标识应进入证据详情。
- 已本地修复并提交 `39e8c298dd52f065eee377678cbd0320ff34e8ea`
  （`fix: 隐藏医保审核快照原始标识`）：
  - `InsuranceAudit` 拆分真实查询值与默认可见值：选中病案快照后继续用真实 patient/encounter
    标识查询和提交，但输入框默认显示“已关联患者 / 已关联就诊”。
  - `ContextSnapshotSelector` 在医保审核页只有打开“追溯证据”时才展示原始患者、就诊和快照标识；
    默认病案卡片仅保留“病案快照已生效 / 质量状态 / 证据”等业务信息。
  - 真实前台 E2E 已补充断言：选择病案快照后，“患者信息”值为“已关联患者”，存在就诊时“就诊信息”值为
    “已关联就诊”，防止后续回退为裸标识。
- 红绿与本地验证：
  - 红灯：`npm --prefix frontend test -- InsuranceAudit.test.tsx -t "选中病案快照后默认用业务文本展示患者与就诊线索"`
    在旧实现下失败，输入框仍显示 `patient-ins`。
  - 绿灯：同一目标用例通过；`npm --prefix frontend test -- InsuranceAudit.test.tsx` 通过，`7` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `917` 项；
    `npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source 39e8c298dd52f065eee377678cbd0320ff34e8ea`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260701-233410`，
    manifest `deployedAt=2026-07-01T23:34:12+08:00`，
    `jarSha256=5d25f9dc56bce3e67a944ef757ae54c602490c78b37631b1b882f2ba864e3b9f`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=2318953`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-39e8c298-insurance-snapshot-evidence`
    直接访问 134 运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (40.9s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `11` 段真实前台链路，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-39e8c298-insurance-snapshot-evidence`
    直接访问 134 运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.3m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `12` 类职责视角，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - 截图复核：`real-frontdesk-insurance-quality-rectification.png` 中“患者信息 / 就诊信息”输入框默认显示
    “已关联患者 / 已关联就诊”；医保问题列表仍显示“当前机构、规则依据已关联、证据已记录”等业务摘要，
    未再默认裸露患者/就诊原始标识。

## 最新阶段交接（2026-07-02 第十八批·随访模板默认展示与演练证据收敛）

- 基于第十七批 134 全职责截图继续按患者代理、护士和临床随访办理视角核查，发现
  `stakeholder-patient_proxy.png` 的随访计划办理抽屉默认展示了
  `全角色患者代理随访模板 patient_proxy-mr28o43q · v1`。该后缀是演练运行标识，不应出现在普通业务视图；
  版本也应使用院内可读表达。原始模板身份仍需保留在证据详情和接口断言中。
- 已本地修复并提交 `c2552dcabc03264995cdf6f2eadb1ec9a747d26e`
  （`fix: 收敛随访模板默认展示`）：
  - `Followup` 将随访模板默认展示收敛为业务名称和“第 N 版”，不再默认展示运行后缀或 `· vN`。
  - 随访模板列表、已发布模板选择器和随访计划办理抽屉复用同一业务展示；证据详情打开后仍可追溯
    `templateId/templateCode/versionNo`。
  - 新增单测覆盖“随访计划默认隐藏演练模板运行后缀，证据详情才展示模板原始标识”。
- 复演脚本进一步本地提交 `7980cb36`（`test: 收敛随访模板演练证据`）：
  - 真实前台与全职责 E2E 创建随访模板时使用中文业务批次名，例如
    “上线复演 07月02日 01时00分16秒”，避免截图中出现 `patient_proxy-*` 或 base36 运行码。
  - 发布模板时先用业务展示名检索，再定位本轮“待发布”行；发布后重新确认“可用于计划生成”行，
    避免历史重复数据误导脚本。
  - 生成随访计划时也用业务展示名搜索；原始 `template.name` 只用于接口返回和“默认视图不得出现原始名”的断言。
- 红绿与本地验证：
  - 红灯：`npm --prefix frontend test -- Followup.test.tsx -t "随访计划默认隐藏演练模板运行后缀"`
    在旧实现下失败，页面找不到“全角色患者代理随访模板（第 1 版）”。
  - 绿灯：同一目标用例通过；`npm --prefix frontend test -- Followup.test.tsx` 通过，`18` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `918` 项；
    `npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source c2552dcabc03264995cdf6f2eadb1ec9a747d26e`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260701-235217`，
    manifest `deployedAt=2026-07-01T23:52:20+08:00`，
    `jarSha256=06c2f3e170d209ed76227740c4f99c69543e18f02506f26db7bea422a493e499`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=2328873`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-c2552dca-followup-template-display-cn-batch`
    直接访问 134 运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (34.1s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `11` 段真实前台链路，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-c2552dca-followup-template-display-cn-batch`
    直接访问 134 运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.2m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `12` 类职责视角，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - 截图复核：`real-frontdesk-followup-template-published.png` 显示“真实前台慢病随访模板（上线复演
    07月02日 01时00分16秒）/ 第 1 版 / 已发布 / 可用于计划生成”；`stakeholder-patient_proxy.png`
    显示“全角色患者代理随访模板（上线复演 07月02日 01时01分42秒）（第 1 版）”，默认视图未再显示
    `patient_proxy-*`、裸 `templateId` 或 `· v1`。

## 最新阶段交接（2026-07-02 第十九批·系统接入默认技术信息收敛）

- 基于第十八批 134 全职责截图继续按信息科长、实施工程师、平台管理员和院长视角核查，发现
  `stakeholder-it_manager.png` 的系统接入页默认展示 `allergyIntolerance.category`、
  `allergyIntolerance.code`、`allergyIntolerance.codeSystem` 和 `NOT_CONNECTED 适配器`。
  这些是契约字段路径与原始枚举，普通职责视图应显示业务接入口径；原始字段仍需保留在统一“追溯证据”开关中。
- 已本地修复并提交 `7dc7a847ff8a0fbc192a21a616dc932a1b2f23f4`
  （`fix: 收敛系统接入默认技术信息`）：
  - `AdapterHub` 的数据接入契约默认显示“患者信息 / 过敏与不良反应 / 可由外部系统接入 /
    平台自动派生”等业务文案；打开“追溯证据”后才展示 resource、schema、字段路径、payload key 与数据类型。
  - 数据质量报告默认将 `NOT_CONNECTED` 转为“未接通适配器”，报告行动建议和缺口摘要统一业务表达；
    追溯证据打开后仍可看到原始 gap summary。
  - `AdapterHub.test.tsx` 覆盖默认隐藏技术字段路径、证据详情展示原始字段，以及质量报告默认隐藏原始枚举。
- 已补充演练脚本防回归并本地提交 `c972408f1a38b8dacebccfea49076bbe0047f8fe`
  （`test: 固化系统接入默认层级演练`）：
  - 全职责 E2E 在信息科长系统接入动作中断言默认层必须出现业务接入口径。
  - 同时断言默认层和数据质量报告不得出现 `allergyIntolerance.*` 或 `NOT_CONNECTED`。
  - 首次补断言复跑的 `...adapter-default-evidence-asserted` 目录失败于 Playwright strict mode：
    “未接通适配器”同时出现在行动建议和缺口摘要，属于测试断言命中多个可见业务文案，不是产品回退；
    已改为首个可见业务文案 + 原始枚举为 0 后复跑通过。
- 红绿与本地验证：
  - 红灯：`npm --prefix frontend test -- AdapterHub.test.tsx -t "默认展示业务接入摘要|loads the data contract summary"`
    在旧实现下失败，页面找不到“患者信息 · 可由外部系统接入”。
  - 绿灯：同一目标用例通过，`2` 项；`npm --prefix frontend test -- AdapterHub.test.tsx`
    通过，`19` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `918` 项；
    `npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source 7dc7a847ff8a0fbc192a21a616dc932a1b2f23f4`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260702-011747`，
    manifest `deployedAt=2026-07-02T01:17:49+08:00`，
    `jarSha256=b2d40353650caeb198e56b63d6bacf38a3fb4bb0f6b5928a5142b885c59228ec`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=2374979`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-7dc7a847-adapter-default-evidence`
    直接访问 134 运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (40.7s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `11` 段，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-7dc7a847-adapter-default-evidence`
    直接访问 134 运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.2m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `12` 类职责视角，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - 补强脚本后再次用
    `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-7dc7a847-adapter-default-evidence-asserted2`
    直接访问 134 运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.2m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `12` 类职责视角，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - 首次无显式 E2E 环境变量运行失败于 `E2E_API_BASE_URL 未配置`，根因是 shell 环境缺失，不是产品失败；
    后续均显式使用 READY 状态 `E2E_ROLE_CREDENTIALS_FILE` 与 134 API 地址复跑。
  - 截图复核：`stakeholder-it_manager.png` 显示“过敏与不良反应 · 可由外部系统接入”、
    “未接通适配器：67”，默认“追溯证据”关闭；未再默认展示 `allergyIntolerance.*` 或 `NOT_CONNECTED`。

## 最新阶段交接（2026-07-02 第二十批·质量下钻默认追溯信息收敛）

- 基于第十九批 134 全职责截图继续按质控、院长、实施工程师和信息科视角核查，发现
  `stakeholder-quality_controller.png` 的质量下钻抽屉截图起初像是只露出窄条。经浏览器探针复现，
  根因是 Playwright 在 Ant Drawer 进入动画未结束时截图：打开瞬间抽屉仍在视口外，约 700ms 后布局正常。
  该问题属于演练取证时序，不是产品布局缺陷；已在全职责 E2E 等抽屉完全进入视口后再截图。
- 同一复核继续发现真实产品问题：质量下钻默认层展示 `整改任务 rct-ins-*` 等原始整改任务编号，
  且证据包说明使用前端期望的 `itemCount`，而后端真实 `QualityEvidenceExport` 契约只提供
  `exportId/generatedAt/scopeDigest/items`，导致默认文案可能出现 `undefined 项证据`。按体验契约，
  原始追溯编号应进入“追溯证据”，普通质控/管理视角应看到业务状态和真实页内条数。
- 已本地修复并提交 `bc74aba34157c7540a877c6ac466886ee3516eb4`
  （`fix: 收敛质量下钻默认追溯信息`）：
  - `QcDashboard` 默认将含追溯 token 的下钻标题转为“整改任务 · 已派发”等业务名称，
    证据摘要转为“整改任务证据已关联，责任科室需按当前状态复核闭环。”。
  - 证据包说明改为“当前页 N 项，共 M 项”，使用真实 `items.length` 与 `total`，不再依赖不存在的
    `itemCount` 字段；`QualityEvidenceExport.itemCount` 类型改为可选以匹配后端契约。
  - 打开“追溯证据”后仍保留原始标题、sourceId、traceId、exportId、scopeDigest 等完整追溯字段。
  - 全职责 E2E 增加抽屉视口稳定等待，避免截图在动画中间截取导致误判。
- 红绿与本地验证：
  - 红灯：`npm --prefix frontend test -- QcDashboard.test.tsx -t "默认隐藏下钻整改任务原始编号"`
    在旧实现下失败，默认层找不到“整改任务 · 已派发”，并暴露原始整改任务编号。
  - 绿灯：同一目标用例与既有“默认用业务语言打开下钻证据 / 证据详情打开后展示”用例通过，`3` 项；
    `npm --prefix frontend test -- QcDashboard.test.tsx` 通过，`8` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `919` 项；过程中仅有既有
    `antd Timeline.Item` 弃用警告。
  - `npm --prefix frontend run build` 通过；`git diff --check` 通过；全职责 E2E 脚本 Prettier 检查通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source bc74aba34157c7540a877c6ac466886ee3516eb4`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260702-014352`，
    manifest `deployedAt=2026-07-02T01:43:54+08:00`，
    `jarSha256=331e9028cffada0a1c68c55dac31586f169ddc9b80415d0b5b7916c272cece79`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=2389329`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-bc74aba3-quality-drilldown-business`
    直接访问 134 运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.3m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `12` 类职责视角均有真实动作，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-bc74aba3-quality-drilldown-business`
    直接访问 134 运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (36.8s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `11` 段真实前台链路，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - 截图复核：`stakeholder-quality_controller.png` 的“真实下钻证据”抽屉已完整进入视口，默认显示
    “整改任务 · 已派发”“当前页 11 项，共 11 项”，未再默认展示 `rct-ins-*`、`trace-rct-*`
    或 `undefined 项证据`；质量概览主页面和待处理问题列表仍保持业务可读。

## 下一步

1. 继续全视角真实操作与产品体验优化：重点回看患者、医生、护士、药师、医技、质控、信息科长、
   实施工程师、院长等剩余入口操作路径、宽表默认可读性、高级信息呈现方式、质量管理下钻与整改闭环，
   发现不合理产品设计直接按当前权威标准优化。
2. 继续复核真实前台演练截图里的医技协同任务、长表格和随访办理抽屉在窄屏 / 多任务量下的滚动与按钮可达性，
   发现遮挡、重复识别困难、操作路径过长或职责边界不清时直接按现行体验契约修复。
3. 继续按统一知识治理视角回看诊断知识维护、机构知识、来源血缘、发布治理和知识生产之间的边界；
   当前不因单个疑问盲目拆改，只有发现与原始权威文档冲突或真实前台体验误导时才调整结构。
4. `DEFER-003` 关闭前，公网 `mimo-public` 只保留“已受管登记但未启用”的诚实状态；拿到有效外部凭据后，
   重新运行 Provider 上线脚本并补充全知识生产真实模型证据。
5. 目标环境上线前仍需补跑 `DEFER-002` 中的 Docker/Testcontainers 或目标库迁移 smoke，并保留脱敏 surefire 证据。
6. 当前分支继续只做本地提交；最终全链路确认无问题后再统一处理远程 `main`。
