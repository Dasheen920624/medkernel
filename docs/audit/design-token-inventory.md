# BASE-10 设计 Token 清单

> 用途：记录当前设计 token 单一真相源、主题模式和 CSS 约束。后续 AI / 人类修改前端样式时必须先读本清单，禁止恢复硬编码视觉债。

## 单一真相源

| 层 | 文件 | 规则 |
|---|---|---|
| Ant Design 运行时 token | `frontend/src/shared/config/theme.ts` | 唯一允许出现品牌色 hex 的位置；导出 `ThemeMode`、`THEME_MODE_OPTIONS`、`isThemeMode`、`createThemeConfig()` |
| 全局尺寸 token | `frontend/src/app/index.css` | 定义 `--mk-unit`、线宽、阴影和模糊 token；固定尺寸通过 `calc(var(--mk-unit) * n)` 表达 |
| 主题状态 | `frontend/src/shared/lib/themeStore.ts` | 只允许写 `medkernel.theme.mode`；非法主题模式回落 default |
| 远端偏好 | `frontend/src/shared/api/hooks.ts` + `medkernel-backend/src/main/java/com/medkernel/engine/experience` | 登录后同步 `/api/v1/experience/theme-preference`；登录页必须 `syncRemote={false}`，不依赖鉴权接口 |

## 五主题

| 模式 | 入口值 | 行为 | 验证 |
|---|---|---|---|
| 默认 | `default` | 使用基础医蓝 token | `theme.test.ts` |
| 老年医生 | `elder` | 正文字号 ≥22px 等效 token、登录页控件字号 24px、控件高度 52px | `theme.test.ts` + 浏览器验收 |
| 暗黑 | `dark` | 使用 Ant Design dark algorithm | `theme.test.ts` |
| 护眼 | `eye` | 在 `theme.ts` 内集中定义柔和背景 token | `theme.test.ts` |
| 跟随系统 | `system` | 根据系统深浅色偏好选择算法 | `theme.test.ts` |

## CSS 约束

1. `.module.css` 禁止出现 hex、rgb、rgba、hsl、hsla 和 `px` 字面量。
2. `.module.css` 颜色必须走 `var(--ant-*)`、`var(--mk-*)` 或组件注入的 CSS 变量。
3. `.module.css` 固定尺寸必须走 `calc(var(--mk-unit) * n)` 或语义 token；断点用 `em`。
4. 禁止为了临时修页面把内联 `style={{ color: "#..." }}`、本地硬编码主题色、页面私有色板带回生产代码。
5. 若新增主题模式，必须同时更新 `THEME_MODE_OPTIONS`、`isThemeMode`、后端 `ThemePreferenceRequest` 校验、服务端 allowlist、前端测试和本清单。

## 门禁

| 门禁 | 命令 | 作用 |
|---|---|---|
| 主题配置测试 | `npm test -- theme.test.ts themeStore.test.ts ThemeSwitcher.test.tsx` | 锁定五主题、老年模式和远端同步 |
| 视觉债测试 | `npm test -- visualDebtGuard.test.ts` | 扫描 `.module.css` 硬编码视觉债 |
| stylelint | `npm run stylelint` | CI 阻断 `.module.css` 颜色函数、hex 和 `px` 回流 |
| 搜索核查 | `rg "px\\b|#[0-9a-fA-F]{3,8}|rgb\\(|hsl\\(" frontend/src/**/*.module.css` | 应无命中；退出码 1 表示清零 |

## 浏览器验收记录

- 当前工作树本地服务：`http://127.0.0.1:5174/login`。
- 登录前主题切换：默认 / 老年医生 / 暗黑 / 护眼 / 跟随系统均可真实点击切换。
- 登录前远端偏好：浏览器资源记录未出现 `/theme-preference`，登录页保持 `syncRemote={false}`，不依赖鉴权接口。
- 老年医生模式：主题按钮显示 `主题模式：老年医生`；登录按钮和输入框实际字号 24px、高度 52px；页面无运行时错误。
- 截图说明：Codex Browser 截图能力在本机连续超时，本次不把截图伪造成证据；以 DOM/计算样式、控制台错误和资源请求记录作为验收证据。
