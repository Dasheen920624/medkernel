# 完整上线收敛实施任务

> 本清单只保留能改变软件、医疗资源或真实运行状态的结果任务。每项内部按 TDD 完成失败测试、实现、消费者回读、审计和验证，不再把测试、证据、页面、脚本或未来增强拆成独立串行项目。除医学责任审核、临床责任确认和 134 唯一一次破坏性确认外，不设置人工中间门禁。所有证据必须来自本次提交或 `run-id`，不得用静态布尔、历史截图、代表切片或部署器自报冒充完成。

## 命令别名

- OSV = `openspec validate converge-full-launch-and-knowledge-platform --strict --no-interactive`
- BE = `cd medkernel-backend && mvn -B -q`
- FE = `cd frontend && npm`
- DB = `node --test scripts/db/generate-migrations.test.mjs && node scripts/db/generate-migrations.mjs --check`
- TG = `node --test scripts/authenticity-guard.test.mjs && node scripts/authenticity-guard.mjs --mode=all`
- DEP = `for f in deploy/onprem/tests/validate-*.sh; do bash "$f"; done`
- DIFF = `git diff --check`

## 1. 基础与最小平台权威

- [x] 1.1 冻结并保全可复现 RC0。依赖：无；结果：输入锚点 `7217504ce82e1aa119c3402e3b5d054f9369e018` 已收敛为候选 `7674532fdb4f3db8373bb996369cfc1fe359c553`，RC0 bundle 按真实文件、原始命令、提交和摘要独立复验，并经成果保全 PR 合入 `main`；验证：独立目录复验 bundle、`origin/main` 包含合并提交；证据键：`rc0.clean.preserved-main`。
- [x] 1.2 建立 35 入口与十五项上线唯一总账。依赖：1.1；结果：唯一机器目录生成前后端消费者，`LAUNCH-01`～`LAUNCH-15` 固定 schema、证据来源约束、四类缺口和零 `FAILED/UNKNOWN/SKIPPED` 汇总合同已经落地；验证：入口生成检查及 `launch-coverage-audit`、`launch-gap-classifier` 全部测试；证据键：`launch.catalog-ledger.single-truth`。
- [x] 1.3 建立平台权威持久化骨架。依赖：1.2；结果：稳定 `authorityId`、独立 `issuerInstanceId/keyId`、外置 HSM/KMS 签名端口、不可泄露私钥边界及权威相关五方言 V1 已实现；验证：权威领域、稳定身份、issuer 独立密钥、密钥边界测试和 DB；证据键：`authority.foundation.five-dialects-external-key`。
- [x] 1.4 完成首发最小可信签发闭环。依赖：1.3；结果：由签名软件 manifest 或独立配置预置 `authorityId/rootFingerprint`，固定根锚定的 SM2 signer/verifier 拒绝 TOFU、未知/过期/吊销 key 和非活动 issuer，不可变包注册表幂等拒绝同序号异摘要；只保留未来冷迁移所需字段，不实现自动交接状态机或独立权威页面；主要接口：现有 authority 包、`SmCryptoService`、`SigningKeyPort`、包注册仓储；验证：定向单测覆盖空医院预置、篡改、自签、IP 冒信任、吊销、重放和私钥泄漏扫描，随后 BE 定向绿；证据键：`authority.launch-minimum.fixed-root-sm2-registry`。

> 1.4 完成后，第 2、3、4 结果包并行推进；后续依赖只表示真实产品数据流，不表示新增项目门禁。

## 2. 完整签名 `.mkp` 与空库导入

- [x] 2.1 定义确定性 FULL `.mkp` 和 13 类统一适配器。依赖：1.4；结果：规范化 manifest 绑定权威、issuer、key、发布序号、父摘要、兼容范围和逐文件 SM3，13 个 `VersionedAssetType` 全部登记 `export/validate/materialize`，相同输入跨宿主逐字节一致；验证：缺类型、重复类型、非确定字段、规则共享条件片段、路径共享子路径或循环均先红后绿；证据键：`mkp.full.manifest-adapters13`。
- [ ] 2.2 生成并下载真实自包含签名包。依赖：2.1；结果：复用现有知识导出和外置签名端口，流式生成真实 `.mkp`，包含 13 类正文、稳定身份、来源许可与锚点、精确依赖、测试向量、撤回和兼容信息，落盘重读后登记并提供下载；缺正文、许可、文件或摘要时不得完成；验证：真实文件重算 SM3/SM2、下载字节与注册表一致、无患者数据/凭据/私钥；证据键：`mkp.full.export-download-signed`。
- [ ] 2.3 完成真实文件上传、隔离和预检。依赖：1.4、2.1；结果：替换只提交 `evidenceId/releaseId` 的恢复旁路，文件流进入受管隔离区后校验路径逃逸、符号链接、大小/数量/解压比、固定信任、摘要、防重放、许可、兼容、13 类正文、依赖、测试和撤回影响，输出绑定 manifest 摘要的不可变预览；验证：篡改、自签、越界压缩、旧序号、同序号异摘要、不兼容和依赖缺失均零业务写入；证据键：`mkp.full.upload-quarantine-preflight`。
- [ ] 2.4 完成空库事务物化、CAS 激活与本地回滚。依赖：2.3；结果：仅有 V1 和预置信任的空医院可按稳定身份重建 t-1 来源、许可、13 类资产正文/版本/依赖/测试/撤回、平台发布和包登记；物化全有或全无，首次激活使用期望空状态，后续升级/回滚生成更高不可变机构修订；验证：中途失败、预检后换包、并发激活和已安全撤回内容回滚均保持原状态；证据键：`mkp.full.blank-v1-atomic-lifecycle`。
- [ ] 2.5 在发布治理入口闭环真实文件往返。依赖：2.2、2.4；结果：复用现有 `ReleaseGovernance` 承载平台导出下载、医院上传、六态预检、差异影响、责任确认、激活和回滚；在两个独立数据库/目录/实例身份间完成 FULL 包 V1 导出—介质复制—导入—运行—升级—回滚，不共享表或运行目录；验证：前后端单测、权限/审计、双实例集成及临床消费者服务端解析当前机构版本；证据键：`mkp.full.release-governance-roundtrip`。

## 3. 覆盖矩阵驱动的完整医疗资源生产

- [ ] 3.1 用现有管线建立唯一资源覆盖矩阵。依赖：1.4；结果：核对并复用来源采集、解析、候选、安全门、责任审核、版本、发布和 readiness 服务，把 13 类版本化资产、13 种患者资源、11 类知识、16 个医疗语义族、专业领域、S0～S40、专病十阶段、第三方消费者和交付形态映射到来源、许可、版本、依赖、责任人、消费者及缺口；不得新建并行生产体系；验证：矩阵 schema、唯一枚举来源和实际服务/表引用一致；证据键：`resource.factory.single-matrix-inventory`。
- [ ] 3.2 闭环来源许可、原件和引用锚点。依赖：3.1；结果：获准来源的原件或允许分发的结构化派生进入 `/zoesoft/medkernel-data` 内容寻址存储，关系库保存许可、原文指纹、精确引用、适用范围、更新检查和撤回事实；无许可或锚点失效只保持待处理；验证：文件账本重算、篡改/失效/不可分发案例和 B0 无模型生产路径；证据键：`resource.factory.licensed-source-ledger`。
- [ ] 3.3 统一 13 类资产生产、审核和生命周期。依赖：3.1；结果：现有生产链对全部 `VersionedAssetType` 执行类型校验、精确依赖、测试、影响分析、有资质责任审核、不可变发布、更新、替换、撤回和回滚；规则与路径自包含，关系库保持权威；验证：逐类型代表资源从来源到正式消费者和审计回读，模型关闭、图不可用时 B0/诚实降级；证据键：`resource.factory.asset-types13-lifecycle`。
- [ ] 3.4 分批生产完整基础资源矩阵。依赖：3.2、3.3；结果：按数据可并行批次生产并审核 13 类资产、13 种患者资源、11 类知识和 16 个语义族的规定内容，每一格具备真实正文、来源/许可、版本、依赖、测试、责任人、平台发布与运行消费者，不以名称、文件数或候选冒充完成；验证：矩阵查询与真实数据库/消费者回读零缺口；证据键：`resource.factory.core-matrix-complete`。
- [ ] 3.5 分批闭环全专业、S0～S40 与专病十阶段。依赖：3.4；结果：PRODUCT_SCOPE 定义的每个专业领域达到来源—结构化—审核—发布—机构生效—运行—审计代表闭环，S0～S40 的正常、异常、缺数、冲突、高危和降级路径具备精确资源依赖，专病十阶段及第三方消费者可运行；验证：按批次执行真实消费者回放和影响分析，最终矩阵零未解释缺口；证据键：`resource.factory.specialties-scenarios-complete`。
- [ ] 3.6 发布可交付的平台医疗资源基线。依赖：2.2、3.5；结果：readiness 从真实版本、许可、审核、依赖、测试和消费者状态计算，生成平台标准版本并由正式导出服务签发完整 FULL `.mkp`；未审核或任一必需矩阵缺口阻止发布；验证：平台版本、包登记、真实下载和 13 类内容重算一致；证据键：`resource.factory.baseline-full-mkp-ready`。

## 4. 单一院内离线交付实现

- [ ] 4.1 补齐现有 onprem 的首发软件合同。依赖：1.4；结果：在 `deploy/onprem` 定义 openEuler 24.03 LTS x86_64/PostgreSQL 16 唯一首发 profile、software manifest、支持矩阵、公开信任根和无凭据 site overlay，锁定 JAR、前端、JRE、V1、模板及 RPM 的来源、版本、NEVRA、摘要和 SBOM；验证：缺项、摘要漂移、overlay 含秘密或未经 smoke 的组合均失败；证据键：`onprem.profile-manifest-overlay`。
- [ ] 4.2 构建签名断网软件包与 RPM 仓库。依赖：4.1；结果：同一候选构建不可变软件包，内含签名离线 RPM 仓库、JRE21、应用、唯一 V1、Nginx/systemd 模板、现有部署脚本和可选已批准模型；安装过程不访问公网；验证：包签名/SBOM/逐文件摘要重算、断网依赖解析、任何未锁定 RPM 或网络请求失败；证据键：`onprem.immutable-offline-bundle`。
- [ ] 4.3 收敛现有安装、升级、状态和回滚动作。依赖：4.2；结果：扩展而非复制 `medkernel-fresh-deploy.sh` 与 `medkernel-deploy.sh`，统一 `preflight/install/upgrade/status/rollback`，沿用范围化清库、备份、隔离恢复、严格 TLS、readiness 和失败回滚；重复执行幂等，中断不留下混合版本；验证：现有六组部署合同测试及新增离线仓库/manifest/site overlay 用例；证据键：`onprem.single-deployer-idempotent`。
- [ ] 4.4 闭环院内运行与灾备合同。依赖：4.3；结果：只公开 443、22 受限，数据库/应用/模型端口回环；受管资料盘故障诚实失败；无模型 B0 默认可运行；数据库、资料、配置、包注册、证书元数据和审计可加密备份、异机复制、联合恢复，私钥不进普通备份；验证：DEP、严格 SAN/TLS、端口、文件盘、B0、失败告警及两个独立恢复目标测试；证据键：`onprem.runtime-backup-restore-b0`。
- [ ] 4.5 在真实首发栈完成断网空机 smoke。依赖：4.4；结果：隔离 openEuler 24.03 LTS x86_64/PostgreSQL 16 空白主机断网完成安装、唯一 V1、首次接管、外部 HTTPS、B0、重启、备份恢复、升级、回滚及卸载重装，最终支持矩阵只登记真实通过组合；验证：真实主机命令与摘要绑定候选提交和 `run-id`，零 `UNKNOWN/SKIPPED`；证据键：`onprem.real-offline-clean-host-smoke`。

## 5. 本地完整验证与不可变 RC

- [ ] 5.1 完成软件与医疗包双实例空白复制。依赖：2.5、3.6、4.5；结果：用同一签名软件包建立独立平台和空医院，从平台完整生产结果导出 `.mkp`，经离线文件上传在医院导入、激活、运行、升级、回滚并完成联合备份恢复；医院不获得平台私钥、数据库快照、患者数据或运行证据；验证：两端身份/数据库/目录隔离、B0、NOT_CONNECTED、本地覆盖不反写 t-1；证据键：`local.blank-hospital-software-mkp-roundtrip`。
- [ ] 5.2 运行一次本地全系统演练并归零总账。依赖：5.1；结果：复用现有全系统 runner，在同一 `run-id` 覆盖基础治理、13 患者资源、互操作、11 知识、13 资产、16 语义族、专业领域、平台与两机构版本、S0～S40、专病十阶段、35 入口、六态、五种交付、七类组合、第三方、医疗安全/B0、运维灾备和完整范围审计；验证：`LAUNCH-01`～`LAUNCH-15` 在本地适用项零失败/未知/跳过，最终审计不自证；证据键：`local.full-system.single-run-ledger`。
- [ ] 5.3 同步现行文档与 AI 接力。依赖：5.2；结果：只更新架构、数据库、部署演练、质量基线、功能目录、职责矩阵、待处理问题和 `_HANDOFF` 中由真实实现改变的事实，不新增临时计划或历史审计文档；验证：文档中心零死链、部署/权限/资源事实与代码同提交、OSV、DIFF；证据键：`local.docs-handoff-current-truth`。
- [ ] 5.4 冻结并独立验证上线 RC。依赖：5.2、5.3；结果：从干净提交只构建一次 JAR、前端、V1、SBOM、onprem 软件包和平台 `.mkp`，所有制品绑定同一 40 位提交哈希并在隔离目录重算；不在发布阶段重建；验证：后端、前端、TG、DB、DEP、E2E、OpenSpec、双实例和真实首发栈证据均可独立回读；证据键：`rc.launch.immutable-same-commit`。
- [ ] 5.5 只读预检 134 并生成唯一确认输入。依赖：5.4；结果：不停止服务、不写数据库、不改目录地探测 134 主机身份、OS/架构、CPU/内存/磁盘、部署根、数据库、端口、CJK 字体、CA/SAN、候选摘要和备份容量，生成脱敏预检与唯一一次破坏性确认所需的精确主机、目录、数据库、删除边界、停机窗口、回滚备份和 `run-id`；验证：任何期望漂移均保持目标零变更并使确认输入无效；证据键：`target134.readonly-preflight-confirmation-input`。

## 6. 134 上线、稳定验证与医院复制

- [ ] 6.1 在一次确认内完成 134 备份恢复与部署。依赖：5.5；结果：先完整备份数据库、程序、配置、资料、包注册、证书元数据和审计并完成隔离联合恢复，私钥仅走密钥设施；随后只接受一次绑定主机/根目录/`medkernel`/候选/删除范围/窗口/备份/`run-id` 的破坏性确认，漂移即失效；在确认范围内清理并从空库唯一 V1 部署同字节 RC，失败自动回滚；验证：备份摘要、隔离恢复、范围化删除、严格 TLS/readiness 和真实目标独立回读；证据键：`target134.single-confirmation-deploy-restorable`。
- [ ] 6.2 在 134 生产并责任审核完整医疗资源。依赖：6.1；结果：初始化稳定平台权威和 134 issuer，在正式资源工厂导入获许可来源，生产并由有资质责任人审核 13 类资产、13 种患者资源、11 类知识、16 个语义族、全专业代表闭环、S0～S40 和专病十阶段所需资源；模型只产有来源和 AI 标识候选，未审核内容不发布；验证：目标矩阵、数据库、文件账本、责任审核、平台版本和消费者逐项真实回读；证据键：`target134.resource-matrix-reviewed-complete`。
- [ ] 6.3 签发完整包并完成一次目标全系统演练。依赖：6.2；结果：134 签发登记真实 FULL `.mkp`，复用全系统 runner 在同一目标 `run-id` 覆盖 PRODUCT_SCOPE 十个逻辑域并汇总十五项上线总账；不建设十套阶段编排器；验证：真实 hostname、提交、制品/包摘要、数据库、HTTPS 和证据目录独立重算，零 `FAILED/UNKNOWN/SKIPPED` 且审计不自证；证据键：`target134.full-mkp-system-rehearsal-ledger`。
- [ ] 6.4 完成重启、第二次恢复和连续稳定窗口。依赖：6.3；结果：重启后提交、迁移、平台标准和两机构当前版本不漂移；用演练后新备份在第二个隔离目标完成联合恢复和关键事件重放；随后连续观察预先固定的 48～72 小时窗口，readiness、TLS、DB、版本、B0、任务/死信、备份、审计、容量、错误率和延迟采样完整，可用性不低于 99.9%，异常则整窗重开；证据键：`target134.restart-second-restore-stability`。
- [ ] 6.5 用同一交付物完成空医院复制。依赖：6.4；结果：只使用同一签名软件 RC、完整 `.mkp`、公开信任材料和无凭据医院 overlay，在独立空白首发栈创建新实例/机构/密钥/审计身份，完成安装、V1、离线上传、验签、导入、激活、职责/组织边界、B0、诚实断连、本地覆盖隔离、回滚和备份恢复；不得携带 134 数据库、患者数据、平台私钥、凭据、主机配置或历史证据；证据键：`hospital.blank-copy-independent-b0-rollback`。
- [ ] 6.6 生成唯一上线结论并收尾主线。依赖：6.5；结果：以不可变软件包、完整 `.mkp` 和真实目标/医院运行三类事实归零 `LAUNCH-01`～`LAUNCH-15`，同步主规格和现行文档、更新 `_HANDOFF`、归档本 OpenSpec，经中文 PR/CI 合入最新 `main` 并清理已合分支/worktree；任何缺证、外部待办、秘密/患者明文或未完成任务均不得宣称上线完成；验证：全量仓库门禁、`openspec validate --all --strict --no-interactive`、归档后复验、远端主线包含合并提交；证据键：`project.full-launch.closed-on-main`。
