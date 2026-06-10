# 集团医疗智能中枢 · MedKernel

> v1.0 GA 0 业务引擎全能力上线基线 · 2026-05-28
> 把指南、路径、规则和院内数据接起来，在临床现场提醒医生，在质控侧形成整改闭环，并留下合规证据。

---

## 一句话定位

MedKernel = **集团医疗智能中枢**，面向集团医院 / 多院区 / 三甲三乙 / 分院 / 社区卫生服务中心 / 街道卫生所。客户可见心智固定为 **4 条主路径 + 跨域服务包 + 隐藏高级工具**：

为了保持方案简单纯粹，v1.0 GA 按 **基础底座 + 服务能力** 两层理解：

| 层 | 说明 |
|---|---|
| 基础底座 | 组织、权限、数据、知识、规则、发布、审计、模型、部署、嵌入等共用能力 |
| 服务能力 | 试点准备、临床运行、质控改进、合规运维 4 条主路径；第三方业务接口、专病路径和专业协同作为跨域服务包；高级工具只给专家/实施/运维使用 |

当前执行方式是 **0 业务引擎全能力上线**：先打通知识、字典、规则、路径、推荐、评估、随访、包发布、嵌入、模型网关、大规模列表、审计证据和降级链路；引擎全能力验收后，再按试点准备、临床运行、质控改进、合规运维、专病路径和专业协同服务包包装业务。

业务接口口径固定：S0-S40 是业务能力目录，不是接口清单。文档只用 `API 归类` 说明当前引擎 API、后续 E6 业务包装 API、复用已有引擎 API、外部系统集成 API 或暂不设独立 API。第三方对接统一通过适配器、标准上下文、临床事件、FHIR/CDS Hooks 风格门面、嵌入、回调、包发布同步和审计证据链管理。

| 服务表达 | 客户能理解的话 |
|---|---|
| 试点准备 | 把医院、系统、路径、规则准备好 |
| 临床运行 | 患者进路径、医生收到提醒、任务有人处理 |
| 质控改进 | 看问题、派整改、看效果 |
| 合规运维 | 身份、审计、安全、监控、交付证据 |
| 第三方业务接口服务包 | 引擎验收后，将接入管理、字段映射、健康检查、互操作门面、Webhook 和证据交换统一包装 |
| 专病路径服务包 | 引擎验收后，将疾病来源、路径、规则、质控、随访和证据打包上线 |
| 专业协同服务包 | 引擎验收后，护理、药事、医技、手麻输血、营养康复心理疼痛、院感公卫等复用同一引擎 |

> 详见 [产品宪法](docs/CONSTITUTION.md)。

---

## 技术栈（v1.0 GA）

| 层 | 选型 |
|---|---|
| 后端 | JDK 21 LTS + Spring Boot 3.3 + Jakarta EE + Spring Security 6 + Spring Data JDBC + Hikari 5 + Flyway 10 |
| 加密 | BouncyCastle 1.78.1（SM2 / SM3 / SM4 + FIPS 路径预留） |
| 数据库 | PostgreSQL / Oracle 23ai / 达梦 8 / 人大金仓 V9 / H2（5 方言全支持） |
| 知识图谱 | 业务权威源在关系库；Neo4j 5.23 仅作可重建查询投影 |
| 监控 | Micrometer + OpenTelemetry 1.41 埋点；当前 Docker 平台提供 Prometheus + Grafana，Tempo / Loki 作为后续可选扩展 |
| 前端 | Node 20 LTS + React 18 + Antd 5 + Vite 5 + TypeScript 5.6 + React Query 5 + Zustand 5 |
| 部署 | 内外网双形态：内网（国产化栈）+ 外网（SaaS） |

---

## 仓库结构

```
medkernel/
├─ medkernel-backend/    ← Spring Boot 3 + JDK 21 + Jakarta EE
├─ frontend/             ← React 18 + Antd 5 + FSD（app/pages/widgets/features/entities/shared）
├─ docs/                 ← 文档中心（宪法核心 + 施工卡 cards/「实现唯一源」+ 体验契约 + 域简报 + 覆盖矩阵 + 任务台账；4 巨物迁移过渡期保留、非权威）
├─ openspec/             ← OpenSpec 当前稳定规格；已归档变更只作审计记录，不作当前事实源
└─ deploy/               ← Docker 部署平台 + 监控配置（PostgreSQL / Neo4j / Dify / Grafana / Prometheus）
```

---

## 启动

### 完整开发平台（Docker）

需要持久 PostgreSQL、Neo4j、监控或 Dify 时，使用容器化开发平台。运行数据默认保留在
`MEDKERNEL_RUNTIME_ROOT` 指定目录；未设置时脚本使用 `deploy/docker/scripts/common.sh` 中的默认运行目录，运行数据不会提交到仓库。首次使用先初始化运行环境：

```bash
./deploy/docker/scripts/bootstrap-runtime.sh
```

然后启动核心模式：

```bash
./deploy/docker/scripts/up.sh core
./deploy/docker/scripts/healthcheck.sh core
```

完整模式（附加 Prometheus、Grafana 和官方 Dify `v1.14.0`）：

```bash
./deploy/docker/scripts/up.sh full
./deploy/docker/scripts/healthcheck.sh full
```

具体服务端口、备份和服务器迁移步骤见
[deploy/docker/README.md](deploy/docker/README.md)。

### 本地一键启动

只需要 B0 本地研发路径时，可以直接启动 H2 后端和 Vite 前端：

```powershell
.\scripts\start-local.ps1
```

脚本会用 JDK 21 打包并启动后端，按需安装前端依赖并启动 Vite；若 18080 / 5173 已被监听，则会复用现有进程。日志位置：

- `medkernel-backend/target/backend-dev.out.log`
- `medkernel-backend/target/backend-dev.err.log`
- `frontend/frontend-dev.out.log`
- `frontend/frontend-dev.err.log`

### 后端

```bash
cd medkernel-backend
mvn spring-boot:run
```

→ `http://localhost:18080/medkernel/api/v1/system/ping`
→ `http://localhost:18080/medkernel/actuator/health`
→ `http://localhost:18080/medkernel/swagger-ui.html`

### 前端

```bash
cd frontend
npm install
npm run dev
```

→ `http://localhost:5173`

---

## 关键文档

> 文档体系已重构为**自包含施工卡**：构建任一任务只读「核心 + 域简报 + 该施工卡」，不再通读巨物。完整权威顺序与会话接力见 [AGENTS.md](AGENTS.md)。

| 文档 | 一句话 |
|---|---|
| [AGENTS.md](AGENTS.md) | AI 协作权威：文档权威顺序 / 分支与 PR 规范 / 会话接力（各工具入口 CLAUDE.md·GEMINI.md 仅转交至此）|
| [docs/CONSTITUTION.md](docs/CONSTITUTION.md) | **核心**（恒读）：11 视角不变量 / 20 条硬约束 / 菜单 / 状态机 / 7 步流 / 6 阶段租户生命周期 |
| [docs/cards/](docs/cards/) | **施工卡 = 实现唯一源**：一卡 = 一可交付物（功能/契约/11 视角/验收）；[`_index.md`](docs/cards/_index.md) 按场景 S0–S40 找卡，[`_coverage-matrix.md`](docs/cards/_coverage-matrix.md) 旧锚点→卡保真 |
| [docs/EXPERIENCE_CONTRACT.md](docs/EXPERIENCE_CONTRACT.md) | 共享体验与组件契约（页面卡必读）|
| [docs/backlog.md](docs/backlog.md) | 单一任务台账：D0–D6 第一波 B0 真实纵向 + 第二波 AI 加深 + GA 验收 |
| [docs/AI_DEVELOPMENT_RESTART_PLAN.md](docs/AI_DEVELOPMENT_RESTART_PLAN.md) | AI 研发重启执行方案：开工流程、阶段闸门、PR 证据模板 |
| [docs/BUSINESS_IMPLEMENTATION_SCOPE_AUDIT.md](docs/BUSINESS_IMPLEMENTATION_SCOPE_AUDIT.md) | 业务实现范围核查：S0–S40、菜单目录权威、卡到代码、B0 主链路、wave2 消费点 |
| [docs/README.md](docs/README.md) | 文档中心导航（四层模型 + 目录树）|

**迁移过渡期保留（不再作为权威，全部域搬迁完成 P8 后退役）**：[FOUNDATION_AND_SERVICES](docs/MEDKERNEL_FOUNDATION_AND_SERVICES.md)（总览）· [PRODUCT_EXPERIENCE_RULES](docs/MEDKERNEL_PRODUCT_EXPERIENCE_RULES.md)（旧体验规范）· [IMPLEMENTATION_LANDING_PLAN](docs/MEDKERNEL_IMPLEMENTATION_LANDING_PLAN.md)（落地方案）· [BUSINESS_SCENARIO_DETAIL_SPEC](docs/MEDKERNEL_BUSINESS_SCENARIO_DETAIL_SPEC.md)（详细规范）—— 内容逐域迁入施工卡，保真见覆盖矩阵。语言规范见 [DOCUMENTATION_LANGUAGE_POLICY](docs/DOCUMENTATION_LANGUAGE_POLICY.md)。

---

## 国情合规底线

| 维度 | 标准 |
|---|---|
| 等级保护 | 等保 2.0 三级 |
| 商用密码 | GM/T 0054 + GB/T 39786 商密评测 |
| 个人信息 | 个保法 + GB/T 35273-2020 个人信息安全规范 |
| 数据出境 | 数据出境安全评估办法 |
| 医疗法规 | 电子病历应用管理规范 + 医师法 + 医疗卫生机构网络安全管理办法 |
| 备案 | ICP 备案 + 公安备案 + 算法备案 |

---

## 项目运行口径

本项目尚未正式上线，按全新项目运作：

- 当前工作树只保留 v1.0 GA 需要的权威文档、代码、部署资产和任务台账。
- 不保留旧版本历史归档目录、旧任务锁、旧分支策略或旧模板；`openspec/archive/` 仅用于已完成变更的审计追溯。
- 所有新增和修改文档使用简体中文。
- 远程长期分支只保留 `main`，不创建或保留 `develop`；所有功能完成后通过 PR 合并到 GitHub 远程 `main`。

---

**MedKernel · v1.0 GA · 发版日待引擎全能力验收后重新冻结**
