# 集团医疗智能中枢 · MedKernel

> v1.0 GA 完整上线基线 · 2026-06-22
> 把指南、路径、规则和院内数据接起来，在临床现场提醒医生，在质控侧形成整改闭环，并留下合规证据。

---

## 一句话定位

MedKernel = **集团医疗智能中枢**，面向集团医院、多院区、医院、分院和基层医疗机构。系统完整保留医疗引擎、知识生产和平台管理三个产品空间：

| 产品空间 | 完整能力 |
|---|---|
| 医疗引擎 | 知识、术语、规则、路径、患者上下文、推荐、任务、随访、质量和审计 |
| 知识生产 | 权威来源、文档解析、模型生成、安全门、影子验证、审核、发布和回滚 |
| 平台管理 | 机构、人员、身份、系统接入、安全配置、运行保障、国产化和部署 |

客户可分配职责只有平台管理员、医疗引擎运营员、临床使用者和审计员。MFA 默认关闭，需要时全局开启；高风险保留技术安全门、医师逐次确认、审计和回滚，不要求双签、委员会或独立专家。

第三方对接统一通过适配器、标准患者资源、临床事件、当前机构生效版本、FHIR/CDS Hooks
风格门面、嵌入、回调和审计证据链管理。临床调用方不传离线交付文件、领域或资产版本。

> 完整范围见 [产品范围](docs/PRODUCT_SCOPE.md)，硬约束见 [产品宪法](docs/CONSTITUTION.md)。

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
├─ docs/                 ← 当前产品、架构、数据库、部署、质量与接口文档
├─ openspec/             ← OpenSpec 变更提案工具；无活动变更时不保留规格副本
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

> 当前文档只描述现行产品、代码、部署和验收事实。历史设计与演练结果通过 Git 追溯，不作为当前实现依据。

| 文档 | 一句话 |
|---|---|
| [AGENTS.md](AGENTS.md) | 协作规则、分支与 PR 规范、会话接力 |
| [docs/CONSTITUTION.md](docs/CONSTITUTION.md) | 产品不变量、医疗安全红线和上线边界 |
| [docs/PRODUCT_SCOPE.md](docs/PRODUCT_SCOPE.md) | S0–S40、全医疗专业领域、完整功能与统一验收 |
| [docs/audit/product-function-catalog.md](docs/audit/product-function-catalog.md) | 完整页面、接口与能力目录 |
| [docs/audit/product-role-journeys.md](docs/audit/product-role-journeys.md) | 四个可分配职责的功能覆盖矩阵 |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | 三产品空间、模块边界与核心链路 |
| [docs/DATABASE_SCHEMA.md](docs/DATABASE_SCHEMA.md) | 单一模式源、五方言部署产物与递增迁移规则 |
| [docs/EXPERIENCE_CONTRACT.md](docs/EXPERIENCE_CONTRACT.md) | 共享页面、组件和交互契约 |
| [docs/DEPLOYMENT_AND_REHEARSAL.md](docs/DEPLOYMENT_AND_REHEARSAL.md) | 部署、清库、知识生成和演练流程 |
| [docs/audit/质量基线.md](docs/audit/质量基线.md) | 测试、T-GATE、部署和上线验收基线 |
| [docs/README.md](docs/README.md) | 当前文档中心 |

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

- 当前工作树只保留 v1.0 GA 需要的权威文档、代码和部署资产。
- 不保留旧版本历史归档目录、旧任务锁、旧分支策略或旧模板。
- 所有新增和修改文档使用简体中文。
- 远程长期分支只保留 `main`，不创建或保留 `develop`；所有功能完成后通过 PR 合并到 GitHub 远程 `main`。

---

**MedKernel · v1.0 GA · 发版日待引擎全能力验收后重新确认**
