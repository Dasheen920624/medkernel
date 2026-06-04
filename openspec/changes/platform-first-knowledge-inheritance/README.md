# platform-first-knowledge-inheritance

平台优先知识继承与统一分发底座的架构设计（先设计后实现）。

## 一句话
所有医疗知识/字典/规则/路径/字段目录以 **平台版本为默认权威**；租户/机构 **按身份引用、不预同步副本**；仅按需 **copy-on-write 覆盖**（REPLACE/DISABLE/ADD），覆盖可声明 **复用(INHERITABLE)/独有(EXCLUSIVE)**；有效版本按机构 **惰性解析**；并把现有 **4 套并行版本 + 3 套并行包** 收敛到统一 `versioning`+`pkg` 底座（**一体化**）。

## 阅读顺序
1. `proposal.md` — 为什么 / 改什么 / 影响（含被收敛的并行机制清单）
2. `design.md` — 权威架构蓝图（四层模型、解析算法、收敛映射、护栏、升级通道、场景走查、对接表、价值、待决项）
3. **附录**（深化设计）：
   - `design-org-scope-model.md`（附录 O）⭐ **七层组织结构纠偏**：四正交轴（权威层/组织树可跳级/专病横切维度/发布策略）
   - `design-authoring-experience.md`（附录 M）易用：三视角、就地建例外、四态徽标、批量复用、开通向导
   - `design-safety-authority.md`（附录 S）权威：override_policy、LOCKED 安全单调性、不可篡改、决策固化重放、权限分离
   - `design-stability-operations.md`（附录 N）平稳：解析 SLA/缓存、零停机迁移、存量回填、离线对账、可观测漂移看板
   - `design-asset-dependency-integrity.md`（附录 D）依赖图、引用完整性、协同解析、一致性快照(epoch)
   - `design-lifecycle-governance.md`（附录 L）生命周期状态机、循证溯源、弃用后继、质量门、身份治理
   - `design-simulation-rollout.md`（附录 R）发布前 what-if 模拟、灰度放量、批量/模板/克隆
   - `design-interoperability-entitlement.md`（附录 I）CDS Hooks/FHIR/CQL 互操作、第三方 API 契约、授权许可
   - `design-worked-example.md`（附录 E）端到端走查：房颤抗凝贯穿全机制
   - `design-glossary-decisions.md`（附录 G）⭐ 术语/枚举总表 + 决策清单（落地单一真相）
4. `tasks.md` — P0→P6 分阶段落地
5. `specs/` — 11 能力增量：
   - `platform-authority` 平台权威层
   - `copy-on-write-inheritance` 覆盖与传播（复用/独有）
   - `inheritance-resolution` 惰性继承解析
   - `unified-asset-versioning` 统一版本底座
   - `unified-package-distribution` 统一知识包分发
   - `tenant-onboarding-reference` 开通引用制
   - `org-scope-model` 组织与作用域四正交轴（纠偏）
   - `asset-dependency-integrity` 依赖与一致性
   - `authoring-lifecycle-governance` 生命周期与治理
   - `simulation-rollout` 模拟与灰度
   - `interoperability-entitlement` 互操作与授权

## 验证
```
npx openspec validate platform-first-knowledge-inheritance --strict
```

## 状态
设计完成、`--strict` 通过，待评审。落地不在本变更内（本变更仅蓝图）。
