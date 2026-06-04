# 附录 O — 组织与作用域模型修正（继承轴的正确性）

> 核查结论：现有"七层组织树"作为骨架方向对，但有 6 处实质缺陷，会使真实医疗结构与"专病诊疗"场景建不出来。本附录给出修正后的权威模型，作为继承解析轴的基础。

## O1 现状与缺陷（基于真实代码）
- `shared/context/OrgLevel`：TENANT→GROUP→HOSPITAL→CAMPUS→SITE→DEPARTMENT→SPECIALTY，且 `canHaveParent = (parent.ordinal()+1==ordinal())`（**只能挂紧邻上一层**），TENANT 注释为"平台租户根"。
- `versioning/VersionReleaseScopeType`：ALL/GROUP/HOSPITAL/CAMPUS/SITE/DEPARTMENT/SPECIALTY/**BED_PERCENT**。
- `pkg/ReleaseScopeType`：ALL/GROUP/HOSPITAL/CAMPUS/SITE/DEPARTMENT/SPECIALTY。

缺陷：
1. **严格相邻、不可跳级/缺省层**：单院区医院（无 CAMPUS）、独立卫生院/医共体成员直挂租户、单体医院（无 GROUP）均无法表达。
2. **SPECIALTY(专病) 误作树叶且只挂一个科室**：专病是跨科室横切维度（MDT），非组织节点——与平台核心场景（专病诊疗）冲突。
3. **缺 PLATFORM 平台层**：TENANT 兼作"平台根"，混淆平台与租户，违背"平台权威高于所有租户"。
4. **BED_PERCENT 混入作用域**：实为发布灰度策略，不是层级。
5. **单父树、无 DAG**：矩阵归属（病区∈科室且∈专科线、集团共享专科中心）无法表达。
6. **缺病区(WARD/护理单元)层 + 三套枚举命名不一致(TENANT vs ALL)**。

## O2 修正：四个正交轴

### 轴 1 — 权威层 Authority Tier
`PLATFORM ⊃ TENANT`。PLATFORM 为高于所有租户的权威只读源（§3）。组织树根改为 TENANT（法人/集团账户），不再兼任"平台根"。

### 轴 2 — 组织树 Org Hierarchy（层级**可选、可跳级**）
建议层级（行政主干，节点带 `level` + 机构 `facilityType`）：

```
TENANT        法人/集团账户（租户根）
  └ REGION    联合体/区域：集团 / 医联体 / 医共体 / 区域卫健（GROUP 泛化）
     └ FACILITY  机构：医院 / 社区卫生服务中心 / 卫生院 / 站点（HOSPITAL+SITE 归一，facilityType 区分）
        └ CAMPUS   院区（可选）
           └ DEPARTMENT  科室
              └ WARD       病区 / 护理单元（新增，可选）
```
- **BED / PATIENT 不入组织树**：作为运行期叶子（床位级规则用就诊上下文表达），不落组织节点。
- **放宽父子规则**：`canHaveParent(parent)` 改为"parent 的层级严格高于自身即可"（允许跳过可选层），而非 `ordinal()+1`。
- **可选 DAG**：除主父 `parent_id` 外，允许次级归属边（matrix membership）表达矩阵关系；解析时主链优先，次链按显式声明合并（确定性 tie-break，附录 O4）。

### 轴 3 — 横切维度 Cross-cutting Scope（走 `applicableScope`，非树）
与组织节点正交、可组合：
- **SPECIALTY 专病**：房颤 / 脓毒症 / 糖尿病…（从树中**移出**，成为一等横切维度）。
- **CARE_SETTING 就诊场景**：门诊 / 住院 / 急诊 / ICU / 日间。
- **COHORT 人群**：儿童 / 老年 / 孕产 / 肾功能不全…
- **ROLE 角色**：医生 / 护士 / 药师。

`applicableScope` 表达为维度键值集合（如 `specialty=AF & setting=ED`）；解析时与组织节点做"命中即适用"，越具体维度组合优先（呼应 §5 tie-break）。

### 轴 4 — 发布策略 Rollout Strategy（独立枚举，移出 scope）
`ALL / ORG_SUBTREE / ORG_LIST / CANARY_BED_PERCENT / STAGED`。**BED_PERCENT 迁到此处**，与组织作用域彻底分离（呼应升级通道 §8.5）。

## O3 解析公式（修正后）
```
有效版本(身份 A, 机构节点 N, 维度 dims, 时刻 t) =
   在 [PLATFORM 基线] 之上，
   沿 [组织树闭包 ROOT→…→N（含可选层跳过、DAG 次链）] 应用覆盖，
   且覆盖的 applicableScope 命中 dims，
   最具体（组织更深 + 维度更精）者优先。
```
专病因此天然落地：平台发"房颤抗凝包"（specialty=AF 维度），集团/分院/科室在各自组织节点 × 专病维度上做覆盖，互不串扰。

## O4 枚举归一
- 三套作用域枚举统一为：**组织层级 `OrgLevel`（轴2）** + **横切维度 `ScopeDimension`（轴3）** + **发布策略 `RolloutStrategy`（轴4）**。
- 顶层统一用 `PLATFORM/TENANT`，废弃 `ALL` 歧义命名（兼容别名过渡）。
- `VersionReleaseScopeType`/`pkg.ReleaseScopeType` 收敛到上述三者。

## O5 迁移影响（平稳）
- `OrgLevel` 增 REGION 泛化/WARD/facilityType 为**加列+放宽校验**，存量 GROUP/HOSPITAL/SITE 平滑映射（GROUP→REGION、HOSPITAL/SITE→FACILITY+type）。
- SPECIALTY 从树迁为维度：存量 SPECIALTY 节点转为 `specialty` 维度标签 + 其覆盖改写 applicableScope（回填 playbook 附录 N4 覆盖此项）。
- BED_PERCENT 从 scope 迁到 RolloutStrategy：影响面仅发布侧，少量代码。
- 全程双写/影子读校验，零回归。

## O6 待决（评审确认）
- **D8 组织树修正幅度**：默认 **全量采纳**（加 REGION 泛化/FACILITY 归一/WARD/放宽父子/DAG/专病维度化/BED_PERCENT 迁策略）。若求最小改动，可仅先做"放宽父子 + 加 PLATFORM + 专病维度化 + BED_PERCENT 迁出"四项最关键修正，FACILITY 归一/WARD/DAG 二期。→ **默认全量，最小集作为 P0 子集先行**。
