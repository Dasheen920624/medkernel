# 会话接力

> **开工先读本文件续接，别考古。** 本文件只保留当前仍会影响下一步执行的事实、边界和指针。已合并 PR 的长复盘查 git、卡片、计划或审计文档，不再堆在这里。

## 当前真相

- **最新主线**：`origin/main=8520b741`，已包含 #634「自主公域知识生产 + AI 工厂收尾 + 整体上线主计划」。不要再从 `399ed29f`、`a3c132de` 或 #633 合并前口径续接。
- **当前本地分支**：`codex/knowledge-fullflow-audit-production`，按用户要求执行长任务：先全面核查现有功能，发现问题直接优化，再推进知识生成到上线全流程；只本地提交，暂不合并远程 `main`。
- **当前验收口径**：仍处于 B0 第一阶段全功能核查与完美化后的接续推进；国产化真实环境本轮暂不处理，后续全面验收再处理。
- **主计划入口**：[`docs/superpowers/plans/2026-06-16-autonomous-knowledge-production-golive-master-plan.md`](superpowers/plans/2026-06-16-autonomous-knowledge-production-golive-master-plan.md)。
- **正确顺序**：P0-P8 建生产中心机器 → P9 部署 134 并配齐真实前置 → P10 在 134 真实生产首发知识 → P11 GA 总验收与试点医院上线。

## 本地进展

- 已完成知识生产链路首轮体检：
  - 知识生产后端目标测试通过。
  - 前端「知识生产」只读证据面测试通过。
  - CLI `agent submit-candidate` 受控回写测试通过。
- `scripts/b0-perfect-check.mjs` 曾报 2 个阻断；根因确认是 guard 对 Prettier 多行分页 hook 签名误报，业务代码已是服务端分页。
- 已按 TDD 修复 B0 guard：
  - 新增多行 hook 签名回归。
  - `hasSnippet` 支持空白归一与参数尾逗号。
  - `node --test scripts/b0-perfect-check.test.mjs`：96/96 通过。
  - `node scripts/b0-perfect-check.mjs`：阻断 0。
- `docs/backlog.md` 已补 #634 Phase 对照说明，但未把 pending 卡虚改为 done。

## 仍不可宣称

- **不得宣称正式知识生产已开放**：P6 独立验收、受管文献资料库根、真实 provider/凭据、真实医学基准评测、出域白名单、版本三元组和专家验收未全部现场闭环前，只能产受控候选和工程证据。
- **不得宣称 134 已部署最新主线**：134 当前已知运行 manifest 仍是旧收官现场证据；触碰 134 需要本会话重新点名授权、备份、隔离恢复、留痕和可回滚。
- **不得宣称 KNOWGEN 首发知识包或试点医院上线完成**：这些属于 P10/P11，必须发生在生产中心真实上线之后。
- **不得把模型 key 当作 P6 放行**：key 只满足「模型」一项，凭据只能走 `credential_ref`，不得写入对话、日志或仓库。

## 下一步

1. 从主计划 Phase 1 开始执行：文档原件资料库存储层。
2. 每个功能切片按 TDD：先失败测试 → 实现 → 验绿 → 门禁 → 本地提交。
3. 新增表/端点时同步五方言迁移、域归属、服务契约、产品目录和中文注释门禁。
4. 保持 `_HANDOFF` 短接力：只更新当前状态、下一步、阻断和证据摘要；不要恢复旧 PR 长段落。

## 常用指针

- 协作规则：`AGENTS.md`
- 产品红线：[`docs/CONSTITUTION.md`](CONSTITUTION.md)
- 体验契约：[`docs/EXPERIENCE_CONTRACT.md`](EXPERIENCE_CONTRACT.md)
- 质量基线：[`docs/audit/质量基线.md`](audit/质量基线.md)
- 当前计划：[`docs/superpowers/plans/2026-06-16-autonomous-knowledge-production-golive-master-plan.md`](superpowers/plans/2026-06-16-autonomous-knowledge-production-golive-master-plan.md)
- backlog Phase 对照：[`docs/backlog.md`](backlog.md)
