# P5 幕3 · 知识治理诚实边界验证（跨角色）

> 执行日期：2026-06-13
> 环境：`https://193.112.107.134`，manifest `7f69c94617cc879304b6841edde95b3ba29a2778`
> 脚本：[scripts/drill/p5-act3-knowledge-honest-boundary.mjs](../../../../../scripts/drill/p5-act3-knowledge-honest-boundary.mjs)（成功判定一律服务端回查）
> 凭据：服务器受控文件本机副本，不入仓库

## 1. 目的

本幕不构造业务数据，专验「诚实降级优先」红线（CONSTITUTION §8）：系统在**零知识、无外部模型、未配置文献资料库**的真实基线下，必须诚实呈现空态与降级，而不是伪造可用性；正式知识生产的前置（文献资料库根地址）必须由边界守卫真实把守，非法值被拒、P6 阻断保持。

## 2. 旅程结构与结论

| 段 | 角色 | 页面 | 诚实边界断言 | 结论 |
|---|---|---|---|---|
| 1 | 机构知识治理员 | `/knowledge/governance` | 服务端知识身份 `total=0`，页面呈现「暂无待审核知识身份」空态，无示例/演示造数 | ✅ |
| 1 | 机构知识治理员 | `/advanced/ai-workflows` | 服务端 8 个 AI 能力**全部 `routeStrategy=BASELINE` 且 `fallbackAvailable=true`**，无一伪装 `EXTERNAL_MODEL`/`LOCAL_MODEL`，页面可见「基础规则能力 / 基线可用」降级状态 | ✅ |
| 2 | 平台知识治理员 | `/knowledge/governance` | 平台域知识身份 `total=0`，页面零知识空态，非「权限不足」 | ✅ |
| 3 | 平台治理管理员 | `/security/baseline` 系统配置 | 文献资料库根地址当前值长度 0、页面「未配置」高亮；真实前台填非法本机目录值 `/tmp/p5-literature-materials/` 被边界守卫拒绝且可见报错；服务端回查正式根地址仍未配置（P6 阻断保持） | ✅ |

## 3. 关键服务端回查（`00-act3-summary.json`，failures=[]）

- `knowledgeGovernor`：`knowledgeIdentityTotal=0`、`emptyStateVisible=true`、`allBaseline=true`、`allFallbackAvailable=true`、`externalModelStrategies=[]`、`baselineDegradeVisible=true`。
- `platformKnowledgeGovernor`：`knowledgeIdentityTotal=0`、`emptyStateVisible=true`、`forbidden=false`。
- `platformGovernanceAdmin`：`literatureLengthBefore=0`、`unconfiguredHintVisible=true`、`illegalValueRejectedVisible=true`、`literatureLengthAfter=0`、`p6StillBlocked=true`。
  - 拒绝文案（后端边界守卫，含 traceId）：「平台知识文献资料库根地址必须使用正式受管资料库 URI，保留 `/platform-knowledge/t-1/literature-materials/` 结构，且不能指向 tmp、本机文件或非加密 HTTP」。

## 4. 截图证据（全部带 URL 栏）

| 文件 | 内容 |
|---|---|
| `01-ui-knowledge-governance-empty.png` | 机构知识治理员：知识审核与发布零知识诚实空态 |
| `02-ui-ai-workflows-baseline-degrade.png` | 智能工作流：8 个能力全部基线降级，无外部模型伪装 |
| `03-ui-platform-knowledge-governance-empty.png` | 平台知识治理员：平台域零知识诚实空态 |
| `04-ui-security-baseline-literature-unconfigured.png` | 系统配置：文献资料库根地址「未配置」高亮 |
| `05-ui-security-baseline-illegal-value-form.png` | 填入非法本机目录值的编辑表单 |
| `06-ui-security-baseline-illegal-value-rejected.png` | 提交后弹窗保持打开（非法值未被接受），边界守卫拒绝 |

## 5. 诚实性说明

- 本幕全程以服务端回查为成功判定，前台仅作可见性佐证；非法配置走真实前台提交（携带 XSRF 双提交令牌），触发后端 `validateKnowledgeLiteratureMaterialRootUri` 校验拒绝（裸 API 调用会先被 CSRF 拦截，无法替代前台验证）。
- 未配置任何正式文献资料库根地址、未生成任何正式知识、未接入外部模型；P6 继续阻断。
