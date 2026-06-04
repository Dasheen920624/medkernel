# platform-first-knowledge-inheritance

平台优先知识继承与统一分发底座的架构设计（先设计后实现）。

## 一句话
所有医疗知识/字典/规则/路径/字段目录以 **平台版本为默认权威**；租户/机构 **按身份引用、不预同步副本**；仅按需 **copy-on-write 覆盖**（REPLACE/DISABLE/ADD），覆盖可声明 **复用(INHERITABLE)/独有(EXCLUSIVE)**；有效版本按机构 **惰性解析**；并把现有 **4 套并行版本 + 3 套并行包** 收敛到统一 `versioning`+`pkg` 底座（**一体化**）。

## 阅读顺序
1. `proposal.md` — 为什么 / 改什么 / 影响（含被收敛的并行机制清单）
2. `design.md` — 权威架构蓝图（四层模型、解析算法、收敛映射、场景走查、对接表、待决项）
3. `tasks.md` — P0→P6 分阶段落地
4. `specs/` — 6 能力增量：
   - `platform-authority` 平台权威层
   - `copy-on-write-inheritance` 覆盖与传播（复用/独有）
   - `inheritance-resolution` 惰性继承解析
   - `unified-asset-versioning` 统一版本底座
   - `unified-package-distribution` 统一知识包分发
   - `tenant-onboarding-reference` 开通引用制

## 验证
```
npx openspec validate platform-first-knowledge-inheritance --strict
```

## 状态
设计完成、`--strict` 通过，待评审。落地不在本变更内（本变更仅蓝图）。
