# 会话接力

> 开工先读本文件。这里只保留当前可执行事实；产品范围见
> [PRODUCT_SCOPE](PRODUCT_SCOPE.md)，历史过程使用 Git 追溯，不在当前工作树保留第二套事实源。

## 当前主线

- 基线：最新权威为 PR #650「补齐完整上线覆盖审计门禁」；更早 PR、偏离设计和旧执行计划只留在
  Git 历史，不作为当前事实入口。
- 当前分支：`codex/engine-core-golive`。
- 当前用户约束：不使用子代理；只允许本地提交；不得推送远程、不得合并 `main`；后续对话只是补充信息时，
  仍按本文和产品权威深读后继续主线。
- 当前目标：完成 MedKernel 全新项目上线级整体梳理与落地，统一平台权威版本与全链路能力，移除旧兼容
  和冗余设计，完善真实功能页面与统一迁移生成，完成代码/契约/前后端/文档/测试/构建核查，并在 134
  清库重新部署完成全功能与全知识全流程演练。
- 当前阶段结论：脚本模式的基础数据路线、134 清库部署、八段全系统演练和发布后独立验收已经走通；
  第一条全前台真实操作薄切片也已通过。下一阶段仍必须继续扩展到全角色、全流程、患者信息安全和
  产品体验优化，不能把薄切片误判为完整前台演练完成。

## 当前唯一权威

按需读取，不考古旧卡、旧计划和阶段审计：

1. [CONSTITUTION](CONSTITUTION.md)
2. [PRODUCT_SCOPE](PRODUCT_SCOPE.md)
3. [ARCHITECTURE](ARCHITECTURE.md)
4. [EXPERIENCE_CONTRACT](EXPERIENCE_CONTRACT.md)
5. [DATABASE_SCHEMA](DATABASE_SCHEMA.md)
6. [DEPLOYMENT_AND_REHEARSAL](DEPLOYMENT_AND_REHEARSAL.md)
7. [功能目录](audit/product-function-catalog.md)
8. [职责旅程](audit/product-role-journeys.md)
9. [质量基线](audit/质量基线.md)
10. [待处理问题](audit/deferred-issues.md)

## 当前产品模型

- MedKernel 是集团医疗智能中枢，不是单独规则引擎、模型平台或知识库。
- 产品按医疗引擎、知识生产、平台管理三类空间组织；空间只是分区，不裁剪真实功能。
- 客户可分配职责只有平台管理员、医疗引擎运营员、临床使用者和审计员；医生、护士、药师、医技、
  质控、医保、患者等通过业务任职、组织范围和前台场景表达。
- 系统超级管理员只用于首次接管和应急，不是客户日常四职责。
- 关系数据库是唯一权威业务事实源；图、缓存、搜索、Dify 和模型都是可关闭、可重建的投影或执行器。
- 大模型只产生候选、草稿或解释，不直接形成临床事实、机构生效版本或自动医嘱。
- 平台标准版本和机构生效版本都是不可变清单；沙盘 CURRENT 读取 `clinical_runtime_release`，
  不再维护独立 `mk_sandbox_runtime_binding`。
- 影子评测可接受状态是 `PASSED` 或 `PENDING_REVIEW` 且 `ready_for_review=true` 且无退化；
  `PENDING_REVIEW` 是医疗安全复核语义，不是失败。

## 134 当前事实

- 目标主机：`193.112.107.134`，hostname 为 `VM-0-13-opencloudos`。
- 当前运行候选：`611fc31e762c91dd6dff73e9906a449b8f3b457a`。
- 运行 manifest：`source=611fc31e762c91dd6dff73e9906a449b8f3b457a`，
  `commit=611fc31e762c91dd6dff73e9906a449b8f3b457a`，
  `jarSha256=c696a79c89cd22b83365152856a25b676350e3bcbe507b8cd17f9d876381dc5c`。
- `medkernel` 服务 active 且 enabled；严格 readiness 返回 `{"status":"UP"}`。
- 134 使用 `/zoesoft/medkernel/nginx/ssl/server.crt` 作为本轮可信 SAN 证书校验根，
  `MEDKERNEL_TLS_CA_FILE=/zoesoft/medkernel/nginx/ssl/server.crt` 下严格 TLS、SAN、有效期和 readiness 已通过。
  ACME/HTTP-01 入站不是本轮阻塞项；绑定正式公网域名时再处理公网 CA 证书。
- 本轮模型提供方：`ollama-launch`，类型 `OLLAMA`，端点 `http://127.0.0.1:11434`，
  模型版本 `medkernel-qwen25:1.5b-v1`。服务器模型信息目录为 `/zoesoft/mimoModel`；不得输出、提交或记录密钥。
- 134 已安装 `google-noto-cjk-fonts` 并刷新字体缓存；`fc-match ':lang=zh'` 命中 CJK 字体。
  这是浏览器 E2E 截图中文可读证据的环境依赖，后续清库或换机不能遗漏。

## 本轮已验证

- 本地候选 `611fc31e762c91dd6dff73e9906a449b8f3b457a` 已打包并上传到
  `/zoesoft/medkernel/incoming/candidate-611fc31e762c91dd6dff73e9906a449b8f3b457a/`；
  远端 `SHA256SUMS` 校验通过。
- 134 清库部署已成功，使用业务表数 207、Flyway 版本 1；部署备份目录：
  `/zoesoft/medkernel/backups/fresh-preclear-611fc31e762c-20260625-134052/evidence`。
- 八段全系统演练已通过，证据目录：
  `/zoesoft/medkernel/var/evidence/full-system-611fc31e-20260625-134250`；
  当前索引已复制到 `/zoesoft/medkernel/var/evidence/current-launch`。
- 八段包括：`account-bootstrap`、`model-provider`、`platform-baseline`、`sandbox`、`full-knowledge`、
  `runtime-resilience`、`browser-e2e`、`launch-coverage`。
- 浏览器 E2E 48 个用例通过；全知识 11 域、12 次模型知识生产、B0 降级、沙盘、覆盖矩阵均完成。
- 发布后独立验收已通过并写入
  `/zoesoft/medkernel/var/evidence/current-launch/release-acceptance.properties`：
  `release_status=PASSED`，`verified_at=2026-06-25T15:12:05+08:00`，
  `full_system_stage_count=8`，`database_restore_status=PASSED`。
- 发布后验收备份目录：
  `/zoesoft/medkernel/backups/launch-acceptance-611fc31e762c-20260625-151157`；
  数据库备份 SHA-256 为 `d62ed4e2a8ad15641648e21420bb414b8b55a422eff345adeb881e726f249a1c`。
- 验收数据库摘要：Flyway 成功 1、失败 0、public 基础表 208、上线身份 9、客户四职责分配 12、
  系统超级管理员分配 1、全知识身份 11、当前 ACTIVE 知识 11、模型成功任务 12、影子可复核任务 12、
  沙盘规则 10、测试用例 40、最新机构生效版本 ACTIVE 条目 12、审计事件 273。
- 本地已通过：
  `mvn -q -Dtest=CandidateCoexistenceServiceTest test`、
  `mvn -q -Dtest=KnowledgeProductionControllerSecurityTest#knowledgeReaderCanQueryCandidateCoexistence,KnowledgeProductionControllerSecurityTest#guestCannotQueryCandidateCoexistence test`、
  `mvn -q -DskipTests package`、
  `bash deploy/onprem/tests/validate-medkernel-post-rehearsal-verify.sh`。
- 134 已通过：
  `bash /zoesoft/medkernel/var/rehearsal/repo-611fc31e762c91dd6dff73e9906a449b8f3b457a/deploy/onprem/tests/validate-medkernel-post-rehearsal-verify.sh`
  和完整 `medkernel-post-rehearsal-verify.sh`。
- 第一条全前台真实操作薄切片已通过，证据目录：
  `/zoesoft/medkernel/var/evidence/frontdesk-real-611fc31e-fontfixed-20260625-150606`。
  用例为 `frontend/e2e/real-frontdesk-rehearsal.spec.ts`，三段均由前台页面提交产生数据：
  平台管理员创建系统接入适配器、医疗引擎运营员创建知识值集草稿、临床使用者创建随访模板。
  运行记录三段 `browserErrors/serverErrors/networkFailures` 均为空；截图在安装 CJK 字体后中文可读。

## 本轮落地内容

- 修复知识生产候选共存读态：非 `PENDING_REPLACEMENT_REVIEW` 候选在只读共存端点返回明确“不可替换复核”
  视图，不再用 409 阻断知识生产页面和演练。
- 发布后验收脚本升级为八段证据契约，核对完整覆盖矩阵，不再使用旧布尔字段。
- 发布后验收脚本的权限口径改为客户四职责 12 个有效分配 + 系统超级管理员 1 个保留分配，
  防止旧“8 个分配”误导。
- 发布后验收脚本的影子评测口径改为 `PASSED/PENDING_REVIEW + ready_for_review + 无退化`，
  符合医疗安全复核模型。
- 发布后验收脚本的沙盘 CURRENT 口径改查统一机构生效版本 `clinical_runtime_release` 和
  `clinical_runtime_release_item`，禁止旧 `mk_sandbox_runtime_binding` 回流。
- 新增全前台真实操作 E2E 门禁，覆盖平台接入、知识资产和临床随访三条写入链路，证明演练数据可以
  由前台页面产生，而不是只靠脚本/API 绕过页面。
- 发布后验收脚本增加中文 CJK 字体验证，缺字体时直接失败，避免 134 或其它验收机产出方块字截图。

## 模型与患者信息安全

- 公网部署的系统本身可以处理患者信息；调用公网 API 模型或外部模型时，允许在授权用途内使用患者上下文，
  但必须先做最小必要、核心敏感字段屏蔽、目的绑定、责任确认、租户/机构边界和审计。
- 院内本地模型可以在授权范围内使用必要敏感信息，但仍要标注敏感边界，限制留存，证据和日志不得含患者明文。
- 无论内外模型，模型输出只能是候选、草稿、解释或摘要，必须保留 AI 标识、模型版本、提示版本、
  输入/输出摘要、引用与校验结果；不得伪造模型调用或把模型输出直接变成医嘱/临床事实。

## 下一步

1. 完成本轮 E2E、验收脚本和文档改动的本地门禁、`git diff --check` 和本地提交；不得推送远程。
2. 扩展下一阶段全前台真实操作演练：尽可能通过前台创建机构、账号、来源、患者资源、字典映射、知识候选、
   版本发布、机构生效版本、沙盘运行、临床调用、审计与恢复证据；脚本只作为辅助校验，不再作为唯一数据来源。
3. 用全视角体验产品并优化：医生、护士、药师、医技、患者/随访、平台管理员、医疗引擎运营员、审计员、
   医疗实施工程师、信息科长、院长、医疗产品经理等。发现功能分类不合理、流程过长、理解困难、六态不足、
   页面空壳、权限误导或医疗安全表达不清时，直接按最符合真实医疗产品的方案优化。
4. 重点关注全知识/本地模型生成耗时期间的前台进度反馈、患者敏感信息进入外部模型前的遮蔽交互、
   以及真实前台操作生成的数据是否能完整走到 134 的同一验收链路。
