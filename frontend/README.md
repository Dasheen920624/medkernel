# MedKernel 前端

React 18 + TypeScript + Vite 的正式前端工程，覆盖医疗引擎、知识生产和平台管理三个产品空间。

## 本地运行

```bash
cd frontend
npm install
npm run dev
```

默认地址：`http://localhost:5173`。开发代理目标通过 `.env.local` 配置：

```text
VITE_API_PROXY_TARGET=http://localhost:18080
E2E_API_BASE_URL=http://localhost:18080/medkernel/api/v1
```

## 验证

```bash
npm test
npm run typecheck
npm run lint
npm run build
```

## 目录

```text
src/
├── app/        应用入口和路由
├── shared/     API、配置、状态与通用组件
├── widgets/    应用布局和工作台
├── features/   横切功能
├── entities/   领域类型
├── pages/      全部业务页面
└── test/       测试基础设施
```

## 约束

- 页面运行只连接真实后端；测试桩不得成为生产入口。
- 路由、菜单、面包屑和权限元数据保持单一来源。
- 前端只消费后端返回的权限和菜单，不使用角色名自行授权。
- 每页遵守一页一目标、最多一个主按钮、最多三个默认筛选和六态。
- 技术字段默认收进“高级信息”，并继续受后端权限控制。
- 大列表使用服务端分页；导出使用异步任务。
- 颜色、字号、间距和圆角使用主题 token；禁止散落硬编码颜色和 JSX 静态内联样式。
- API 请求统一处理 `traceId`、组织上下文和 `ApiError`。
- 浏览器存储只保存批准的界面偏好，不保存令牌、密钥或患者完整隐私。
- 模型、图谱或第三方系统不可用时必须展示诚实降级状态。

当前功能与职责以[功能目录](../docs/audit/product-function-catalog.md)、[职责矩阵](../docs/audit/product-role-journeys.md)和[体验契约](../docs/EXPERIENCE_CONTRACT.md)为准。
