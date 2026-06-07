# 集团医疗智能中枢 MedKernel · 管理工作台前端

本目录是 MedKernel **正式前端工程**。当前执行为 **0 业务引擎全能力上线**：先做引擎控制台、路由菜单元数据、组织上下文、API client、查询缓存、六态模板、7 步流、状态机、发布审核、证据和降级状态；引擎验收前不按业务菜单拆页面闭环。

业务细节继续查 [`docs/MEDKERNEL_BUSINESS_SCENARIO_DETAIL_SPEC.md`](../docs/MEDKERNEL_BUSINESS_SCENARIO_DETAIL_SPEC.md)，全系统交互体验按 [`docs/MEDKERNEL_PRODUCT_EXPERIENCE_RULES.md`](../docs/MEDKERNEL_PRODUCT_EXPERIENCE_RULES.md) 固定执行；当前编码任务只按 [`docs/backlog.md`](../docs/backlog.md) 的 `GA-ENG-*` 引擎任务推进。

命名口径（v1.0 GA 主线校准）：对外统一称「**集团医疗智能中枢 · MedKernel**」，工作台称「**管理工作台**」。可部署在院内、专网、VPN/零信任网关后；外网 SaaS 形态以 [`docs/MEDKERNEL_IMPLEMENTATION_LANDING_PLAN.md`](../docs/MEDKERNEL_IMPLEMENTATION_LANDING_PLAN.md) 为准。

## 技术栈

| 类别     | 选型                                    | 备注                     |
| -------- | --------------------------------------- | ------------------------ |
| 框架     | React 18 + TypeScript 5                 | 严格模式                 |
| 构建     | Vite 5                                  | dev / build / preview    |
| UI 库    | Ant Design 5 + @ant-design/icons        | 中文 locale + brand 主题 |
| 路由     | react-router-dom 6                      | 嵌套路由                 |
| 数据请求 | axios + TanStack Query v5               | 自动 traceId + ApiError  |
| Mock     | MSW 2                                   | 浏览器 + Node 双侧       |
| 测试     | Vitest + @testing-library/react + jsdom | TDD                      |

## 快速开始

> 需 Node.js ≥ 18.18（建议 20.x，见 `.nvmrc`）。

### 1. 安装依赖

```powershell
cd frontend
npm install
```

内网部署若需私有 npm 源：

```powershell
npm config set registry https://your-internal-npm-mirror/
npm install
```

依赖版本已在 `package.json` 中固定为精确版本。私有镜像必须同步这些精确版本及其传递依赖；若出现 `vite-node`、`@testing-library/dom` 等包缺失，应先补齐镜像或临时指定完整 npm registry 后再安装。

### 2. 配置环境变量

复制 `.env.example` 为 `.env.local`，按需修改：

```text
VITE_API_PROXY_TARGET=http://localhost:18080
E2E_API_BASE_URL=http://localhost:18080/medkernel/api/v1
```

### 3. 运行 dev 服务器

```powershell
npm run dev
# → http://localhost:5173
```

启动前确认后端已运行：

```powershell
cd ../medkernel-backend
mvn spring-boot:run
# 健康检查使用 VITE_API_PROXY_TARGET 对应后端：/medkernel/actuator/health
```

### 4. 构建产物

```powershell
npm run build
# 产物在 dist/
npm run preview
```

### 5. 运行测试

```powershell
npm test              # 一次性
npm run test:watch    # 监听
npm run typecheck     # 类型检查
```

## 目录结构

```
frontend/
├── index.html              # Vite 入口
├── package.json            # 依赖与脚本
├── public/mockServiceWorker.js # 测试资源，不作为产品运行入口
├── vite.config.ts          # Vite + proxy + vitest 配置
├── tsconfig*.json          # TypeScript 配置（分 app / node 引用）
├── .env.example            # 环境变量样例
├── .nvmrc                  # Node 版本声明
└── src/
    ├── app/
    │   ├── main.tsx        # 入口
    │   ├── App.tsx         # 应用外壳
    │   ├── router.tsx      # 路由出口
    │   └── index.css       # 全局样式入口
    ├── shared/
    │   ├── api/            # axios client + hooks
    │   ├── config/         # routes/menu/theme 单一元数据
    │   ├── lib/            # 通用 store/lib
    │   └── ui/             # PageShell / StatusBadge / StepFlow / ColumnManager
    ├── widgets/            # AppLayout / WorkbenchPanel
    ├── features/           # 横切功能
    ├── entities/           # 领域实体类型与轻逻辑
    ├── pages/
    │   ├── tenant/
    │   ├── clinical/
    │   ├── quality/
    │   ├── compliance/
    │   └── advanced/
    └── test/
        └── setup.ts        # vitest setup
```

## 全局约定

### ApiResult 信封

所有后端接口返回 `ApiResult<T>`：

```ts
{
  success, code, message, data, trace_id;
}
```

`client.ts` 拦截器会：

- 自动给请求加 `X-Trace-Id`。
- 自动给请求加组织上下文 Header（`X-Tenant-Id` 等）。
- `success === false` 自动抛 `ApiError`，业务侧不重复判 `success`。
- HTTP 非 2xx 也抛 `ApiError` 并保留 `trace_id` 供排错。

业务代码用 `get<T>` / `post<T>` 直接拿 `data`：

```ts
const providers = await get<SystemProviders>("/system/providers");
```

### 组织上下文

- 通过 `useOrgContext()` hook 读取与修改。
- 持久化到受控 UI 偏好存储；生产代码不得直接访问 `localStorage` / `sessionStorage`。
- 任何请求都会自动把当前上下文加到 Header（Body 仍可覆盖）。
- 切换上下文示例：

```ts
const [org, setOrg] = useOrgContext();
setOrg({ ...org, hospital_code: "HOSPITAL_BETA" });
```

### 主题色

- Antd 主题 token 由 `src/shared/config/theme.ts` 管理，并与 `docs/CONSTITUTION.md` 保持一致。
- 主题模式由 `src/shared/lib/themeStore.ts` 和 `ThemeSwitcher` 管理，支持默认、老年医生、暗黑、护眼和跟随系统。
- 后期接租户配置时，只允许把后端主题包转换为统一 token，不在页面内写散落色值。

### CSS Modules 与样式门禁

所有**静态样式**必须放进同名 `<Component>.module.css`（Vite 默认支持，无需额外配置）或复用 `src/app/index.css` 中统一的 `mk-*` 样式类。JSX 内联 `style={{ ... }}` 已归零，并由 `medkernel/no-inline-style` ESLint error 与 `scripts/check-inline-style-count.ps1` baseline 0 双重阻断。

**正确做法**：

```tsx
// frontend/src/pages/Mpi/PatientList.tsx
import styles from "./PatientList.module.css";

export function PatientList() {
  return (
    <div className={styles.page}>
      <header className={styles.header}>...</header>
    </div>
  );
}
```

```css
/* frontend/src/pages/Mpi/PatientList.module.css */
.page {
  display: flex;
  flex-direction: column;
  gap: var(--mk-space-5);
  padding: var(--mk-space-6);
}
.header {
  border-bottom: 1px solid var(--mk-border-divider);
}
```

**强制 token**：颜色 / 字号 / 间距 / 圆角 / 阴影 / 字体一律走统一主题 token、Ant Design CSS 变量或 `mk-*` 类，严禁页面内散落 hex 色值。唯一允许写品牌色的文件：`src/shared/config/theme.ts`。

**动态样式处理**：运行时变量优先落到组件状态、Ant Design 组件属性或 CSS class 切换；确实需要动画变量时，应先新增清晰命名的 CSS 变量承载类，并在评审中说明原因，不得直接把对象写进 JSX `style`。

**示范**：`src/pages/Login.tsx + Login.module.css`、`src/pages/Dashboard.tsx + Dashboard.module.css`。

### TanStack Query

- `QueryClient` 默认 `staleTime: 30s`、`retry: 1`、`refetchOnWindowFocus: false`。
- 看板/列表使用 `useQuery`；表单提交使用 `useMutation`。

### 产品体验底座接入规则

- 任何带菜单入口的认证路由必须在 `src/shared/config/routes.ts` 配置 `experience`，声明主目标、默认角色视图、最多 3 个默认筛选、专家内容、数据规模和导出策略。
- 大规模列表必须使用 `ServerDataTable` 或提供等价的服务端分页、排序、列可见性快照与部分成功反馈能力，不得把全量数据加载到浏览器内分页。
- 页面详情与证据统一进入 `EvidenceDetailDrawer`；普通视图不显示技术追踪字段，获授权的专家模式才可展开。
- 异步导出入口必须使用 `AsyncExportAction`；接口、权限或审计闭环尚未接通时，应展示受控禁用态，不得伪造下载结果。
- 保存视图必须经 `experienceView.ts` 校验后进入受控 UI 偏好存储，禁止写入 token、患者标识等敏感内容。
- 当前真实样板页为“字典映射”，仅提供筛选、分页和证据详情的只读核查，不包含确认、发布、回滚或批量处理动作。

### 测试数据

- 页面运行必须连接真实后端；单测如需隔离网络，统一在 `src/test/setup.ts` 内封装测试桩，不作为产品入口。

## 与后端的对接

开发期：

- vite proxy：`/medkernel` → `VITE_API_PROXY_TARGET`，未配置时 dev server 直接失败。
- 当前 `apiClient` 固定使用 `/medkernel/api/v1`，与后端 servlet path 对齐。

内网部署：

- 通过 nginx 把 `dist/` 静态资源与 `/medkernel/api` 反代到同源。

第三方入口：

- 前端只展示适配器、嵌入、Provider、回调、同步和降级状态，不直接连接院内第三方系统。
- 外部系统故障必须有用户可见的受控降级态、traceId 和证据入口，不能用静态成功态掩盖失败。

## 框架自检

- [ ] route/menu/breadcrumb/permission 使用单一元数据。
- [ ] 页面外壳统一使用 PageShell 和六态模板。
- [ ] 状态展示统一走 StatusBadge 与 4 套状态机。
- [ ] 配置类页面只使用 7 步流外壳，不自创流程。
- [ ] 页面遵守一页一目标、1 主按钮、≤3 默认筛选、角色默认视图和专家模式。
- [ ] 知识、候选、规则、路径、日志和证据列表使用服务端分页/游标、详情抽屉、批量任务和异步导出。
- [ ] 临床嵌入提醒按低打扰规则展示，非红线风险不遮挡主流程。
- [ ] API client 统一处理 traceId、组织上下文和 ApiError。
- [ ] MSW 只作为开发和测试开关，不作为业务完成证据。
- [ ] `.env.local` 凭据不进 Git，内网私有 npm 源配置不写入源码。

## 后续任务

参见 [../docs/backlog.md](../docs/backlog.md) 的引擎上线任务。当前前端已完成 `GA-ENG-BASE-06`、`GA-ENG-BASE-08`、`GA-ENG-BASE-10`，并接入字典、规则、路径、推荐/CDSS、评估、随访等已落地引擎 API；后续重点仍是：

- `GA-ENG-PKG-01` 包发布、灰度、全量、同步、回滚和证据的执行链与页面闭环。
- `GA-ENG-EMBED-01` iframe/SDK/纯 API 嵌入与降级状态。
- `GA-ENG-EVID-01` 证据链展示和导出。
- `GA-ENG-INTEG-01` 第三方对接能力总线、接入健康、回调签名、字段映射和接口证据。
- `GA-ENG-INTEG-02` 第三方接口文档与契约模板，保证联调前已有接口契约、字段映射、安全、回调、降级和验收证据。
- `GA-ENG-DEGRADE-01` 模型、Dify、图投影和外部系统故障降级的用户可见状态。

## 维护约定

- 任何新页面需先更新 `src/shared/config/routes.ts` / `src/shared/config/menu.ts`，保持 route/menu/breadcrumb/permission 单一元数据。
- 任何新页面需先说明角色、主目标、默认筛选、数据规模、六态、降级、证据入口和是否需要专家模式。
- 任何新接口需在 `src/api/*` 单独文件封装，不在组件内直接 axios。
- 真正权限必须由后端校验，前端只做体验控制（菜单禁用 / 按钮隐藏）。
- UI 偏好持久化必须通过 `src/shared/lib/browserStorage.ts`，且只允许批准的 UI key。
- 不在前端代码或浏览器存储中存放 token / API Key / 数据库密码 / 患者完整隐私。
- 生产代码禁止 `console.*`；需要用户可感知提示时用页面状态、消息组件或统一事件。
