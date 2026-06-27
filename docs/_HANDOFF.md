# 会话接力

> 开工先读本文件。这里只保留当前可执行事实；产品范围见
> [PRODUCT_SCOPE](PRODUCT_SCOPE.md)，历史过程使用 Git 追溯，不在当前工作树保留第二套事实源。

## 当前主线

- 基线：最新权威为 PR #650「补齐完整上线覆盖审计门禁」。更早 PR、偏离设计、旧执行计划和旧接力事实只留在
  Git 历史，不作为当前事实入口。
- 当前分支：`codex/engine-core-golive`，本地领先 `origin/main`；只允许本地提交，禁止推送远程、禁止合并
  `main`。
- 当前 HEAD：`10f06bea22cace41856d3b3153ab387fb12b0230`（`修正真实前台演练随访字段口径`）。
- 当前目标：完成 MedKernel 全新项目上线级整体梳理与落地，统一平台权威版本与全链路能力，移除旧兼容和冗余设计，
  完善真实功能页面与统一迁移生成，完成代码、契约、前后端、文档、测试、构建核查，并在 134 清库重新部署完成
  全功能与全知识全流程演练。
- 当前阶段结论：现有模式八段全系统演练已在 134 用最新 HEAD 清库部署后通过；下一步进入全角色、全流程、
  全视角真实前台操作体验与全局产品优化，不能只围绕用户临时提到的单点修补。

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

## 产品不变量

- MedKernel 是集团医疗智能中枢，不是单独规则引擎、模型平台或知识库。
- 客户可分配职责只有平台管理员、医疗引擎运营员、临床使用者和审计员；医生、护士、药师、医技、质控、医保、
  患者等通过业务任职、组织范围和前台场景表达。
- 关系数据库是唯一权威业务事实源；图、缓存、搜索、Dify 和模型都是可关闭、可重建的投影或执行器。
- 大模型只产生候选、草稿或解释，不直接形成临床事实、机构生效版本或自动医嘱。
- 平台标准版本和机构生效版本都是不可变清单；沙盘 CURRENT 读取 `clinical_runtime_release`。
- 高级信息可以存在，但应表现为上下文证据详情、责任确认或渐进展开，不回到旧“专家模式/技术细节”孤岛。

## 134 当前事实

- 目标主机：`193.112.107.134`，hostname：`VM-0-13-opencloudos`。
- 当前运行候选：`10f06bea22cace41856d3b3153ab387fb12b0230`。
- 运行 manifest：`/zoesoft/medkernel/manifest.properties`：
  `source=10f06bea22cace41856d3b3153ab387fb12b0230`，
  `commit=10f06bea22cace41856d3b3153ab387fb12b0230`，
  `deployedAt=2026-06-27T10:09:45+08:00`，
  `jarSha256=6aadb21c09415c58449cff9bbf42dbe5c18993f26e35a1fd946afa11cdd551d9`。
- 最新清库部署证据：
  `/zoesoft/medkernel/backups/fresh-preclear-10f06bea22ca-20260627-100930/evidence`。
- readiness 正确路径：
  `https://193.112.107.134/medkernel/actuator/health/readiness`；`/medkernel/api/v1/actuator/...`
  返回 401 是 API 安全边界，不是健康失败。
- TLS：使用 `/zoesoft/medkernel/nginx/ssl/server.crt` 作为本轮可信 SAN 证书校验根；
  `MEDKERNEL_TLS_CA_FILE=/zoesoft/medkernel/nginx/ssl/server.crt` 严格 TLS、SAN、有效期和 readiness 已通过。
  ACME/HTTP-01 入站不是本轮阻塞项；绑定正式公网域名时再处理公网 CA 证书。
- Node/Playwright 只追加 `NODE_EXTRA_CA_CERTS=/zoesoft/medkernel/nginx/ssl/server.crt`；不要把 `SSL_CERT_FILE`
  指到该自签 SAN 证书，否则会覆盖系统 CA。
- 模型提供方：`ollama-launch`，类型 `OLLAMA`，端点 `http://127.0.0.1:11434`，
  模型版本 `medkernel-qwen25:1.5b-v1`。
- 用户补充的 `/zoesoft/mimoModel` 在本轮 134 巡检中未发现；不阻塞当前八段演练，后续作为模型配置来源巡检项处理。
- 134 已安装 `google-noto-cjk-fonts` 并刷新字体缓存，这是浏览器 E2E 截图中文可读证据的环境依赖。

## 已通过证据

- 本地提交：
  `03d95b32`、`a239d223`、`246f9ac5`、`3b014313`、`12c8397b`、`10f06bea` 等均为本地提交，未推送。
- 本地关键验证：
  `npm run typecheck`、`npm test -- --run src/pages/clinical/Followup.test.tsx` 已在 `10f06bea` 前通过；
  修复方向是让真实前台演练使用“问卷模板标识 / 问题标识 / 依据来源”，不把旧三项内部字段标签加回临床页面。
- 构建候选：
  `mvn -q -f medkernel-backend/pom.xml -DskipTests package` 通过；
  `npm --prefix frontend run build` 通过；
  候选包 SHA256 在 134 校验通过，前端包包含 `dist/index.html`。
- 134 清库部署：
  业务表数 207、Flyway 版本 1；部署后服务 active/enabled，readiness HTTP 200。
- 八段全系统演练通过，日志：
  `/zoesoft/medkernel/var/evidence/rehearsal-logs/full-system-10f06bea22ca-20260627-101100.log`。
- 八段总证据：
  `/zoesoft/medkernel/var/evidence/current-launch/full-system.json`，
  `PASSED full-system-rehearsal`，`stages=8`。
- 全知识证据：
  `/zoesoft/medkernel/var/evidence/current-launch/full-knowledge.json`；
  11 个知识域全部发布：`GUIDELINE`、`DRUG`、`PATHWAY_KNOWLEDGE`、`NURSING`、`DIAGNOSTIC_ITEM`、
  `TCM`、`PROTOCOL`、`POLICY`、`LITERATURE`、`OTHER`、`DIAGNOSIS`；代表知识 V2 发布、回滚与恢复验证通过；
  模型任务 12 个。
- 运行韧性证据：
  `/zoesoft/medkernel/var/evidence/current-launch/runtime-resilience.json`；模型关闭诚实降级通过，B0 主链路
  `17/17`。
- 浏览器 E2E：
  Playwright `50 passed (13.6m)`；两套浏览器项目均覆盖真实前台数据链路。
- 前台真实操作演练证据：
  `/zoesoft/medkernel/var/evidence/current-launch/e2e/artifacts/real-frontdesk-rehearsal-*`；
  平台接入、知识值集、模型外调安全策略、MPI 患者和随访模板均由前台页面提交产生。
- 覆盖审计：
  `/zoesoft/medkernel/var/evidence/current-launch/launch-coverage.json`，阶段 8 通过。
- 发布后独立验收：
  `/zoesoft/medkernel/var/evidence/current-launch/release-acceptance.properties` 已写入；
  严格 TLS、八段证据结构、真实重启 readiness、登录、Provider、知识 readiness、关系库持久化、演练后备份与隔离恢复均通过。

## 模型与数据安全约束

- 公网模型和公网部署也允许使用患者上下文，但必须最小必要、核心敏感信息严格屏蔽、用途确认和审计留痕。
- 院内本地模型可以使用必要患者信息，但仍要处理敏感信息边界，禁止密钥、明文核心敏感信息进入日志、配置仓库或模型外调证据。
- 任何“模型不可用”必须诚实降级，B0 主链路优先；禁止用模型结果冒充关系库事实。

## 下一步

1. 从现有模式演练已通过的状态继续，进入真实前台全角色体验与全局优化。
2. 按医疗产品体验师、产品经理、实施工程师、信息科长、院长、医生、护士、患者、审计员等视角走全流程：
   功能分类、页面目标、空态/错态/权限态、流程完整性、操作复杂度、敏感信息处理、证据详情表达都要全局审计。
3. 优先发现并修复真实产品问题，而不是只优化用户临时指出的点；修复后仍需本地验证、必要时重新构建并在 134 复验。
4. 保持本地提交，不推送远程，不合并 `main`；不要提交未跟踪的 `.codex/config.toml`。
