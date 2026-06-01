# INFRA-02 · 后端真实性门禁

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D0 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：质量基线 §6.3 后端 CI 脚本 · 核心 §13 真实性门禁 / #18。

## 身份
- 卡 ID：INFRA-02
- 域：D0 登录域 / 平台脊柱
- 关联场景：横切（后端 T-GATE，后续所有引擎/API 卡 done 前提）
- 依赖卡：无（应**最先落**，与 [INFRA-01](INFRA-01.md) 并列；存量清理在 [BASE-09](BASE-09.md)）
- 工作量：2d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标

交付**后端真实性门禁**：CI 脚本阻断 `Math.random` 造数、写死医学常量、catch 吞错返回成功、UUID 充哈希、占位 Javadoc 于生产路径——构成 T-GATE 后端半，是后续每张引擎/API 卡 `done` 的硬前提。

## 功能要求（原子可测条目）

- [x] **FR-1 阻断 Math.random**：`Math.random()` 出现在生产代码（非测试目录）被拒（防伪造 RTT/健康分/采纳率）。
- [x] **FR-2 阻断写死医学常量**：字符串 `"高血压"/"糖尿病"/"I10"/"E11"/"DRUG-001"/"肺炎"/"心梗"` 等于生产路径被拒（核心 #18）。
- [x] **FR-3 阻断 catch 吞错伪造**：`catch (...) { return success(...); }` 模式（吞错返回成功）被拒。
- [x] **FR-4 阻断 UUID 充哈希**：`UUID.randomUUID().toString()` 用作数据完整性 hash 被拒（应 SHA-256/SM3）。
- [x] **FR-5 阻断占位 Javadoc**：Javadoc 含"模拟/仿真/演示/占位/placeholder"于生产路径被拒。
- [x] **FR-6 放行白名单**：测试目录（`src/test/`）/ Migration SQL（`V*.sql`）/ `dev` profile bean。

## 接口契约 / 页面契约
N·A —— 本卡是 CI 静态扫描脚本 + 集成，无运行时接口/页面。

## 数据与迁移
N·A —— 工程门禁不落库。

## 视角清单（11 视角逐条）
1. **产品架构**：门禁强制后端真实；规则即可执行约束。
2. **产品体验**：N·A —— 后端门禁。
3. **系统与数据架构**：CI 静态扫描（AST/正则）阻断合入生产路径。
4. **临床医疗安全**：★阻断写死医学常量 + 伪造算法结果，防假临床逻辑（核心 §6/#18）。
5. **知识与数据治理**：阻断假证据/假哈希（核心 §7/§8）。
6. **安全合规与监管**：UUID 充哈希 → 强制 SM3/SHA-256（核心 §8 完整性凭证）。
7. **集团化与多租户治理**：N·A。
8. **集成与互操作**：★阻断 Math.random 伪造 Ping/RTT/重试（核心 §10，呼应 A14 诚实化）。
9. **运维 / SRE / 国产化**：门禁纳入 CI；国密 hash 合规。
10. **质量与真实性审计**：★本卡主战场 —— T-GATE 后端半（核心 §13）；反 catch 吞错、反伪造。
11. **AI / 模型治理与可降级**：阻断 LLM B0 写死候选/伪造模型输出（核心 §11/#18）。

## 适用不变量
- 命中核心约束：**#18 真实性** · **§13 T-GATE 后端门禁** · **§8 完整性 hash（SM3）** · **§11 禁伪造模型输出**。
- 本卡落点：可执行的 CI 静态扫描脚本 + 阻断合入，把"后端不得伪造"做成合不进去的硬墙。

## 验收 + 验证
- [x] **AC-1（FR-1）**：生产路径含 `Math.random()` 的 PR 被 CI 拒。
- [x] **AC-2（FR-2）**：写死 `"高血压"/"I10"` 字符串于生产路径被拒。
- [x] **AC-3（FR-3）**：catch 吞错返回成功的写法被捕获拒绝。
- [x] **AC-4（FR-4/5）**：UUID 充哈希 + 占位 Javadoc 于生产路径被拒。
- [x] **AC-5（FR-6）**：测试/迁移/dev profile 白名单正常放行不误杀。
- 关联 A1–A9：横切（所有后端真实性前提）。
- T-GATE：本卡**即** T-GATE 后端半。
- B0 验收：工程门禁，天然 B0。

## 完工证据
- 代码 permalink：本 PR 覆盖 `scripts/authenticity-guard.mjs`、`scripts/authenticity-guard.test.mjs`、`.github/workflows/ci.yml` 中现有 `guard-rules` 集成。
- 测试：`node --test scripts/authenticity-guard.test.mjs` 通过，21/21；覆盖 Math.random、医学常量、catch success、UUID 充 hash、占位 Javadoc、时间戳/hashCode 伪 hash、memory 占位导出、模拟同步证据、默认科室、`@RequestBody Map` 和测试目录 / 迁移 SQL / dev profile bean 白名单。
- 扫描：`node scripts/authenticity-guard.mjs --mode=inventory` 通过，扫描 711 个受控文件，未发现阻断项；`node scripts/authenticity-guard.mjs --mode=changed --base=origin/main` 通过，未发现阻断项。
- T-GATE：迁移规约测试 6/6 与 changed 扫描通过；配置边界测试 2/2 与 changed 扫描通过；`scripts/check-comment-zh.sh` 0 fail / 0 warn；`git diff --check` 通过。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。

## 大卡工序（2d，后端工程/CI）
- PR1：Math.random + 医学常量 + catch 吞错 检测 → AC-1/2/3。
- PR2：UUID 充哈希 + 占位 Javadoc + 白名单 + CI 集成 → AC-4/5。
