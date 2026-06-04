# 附录 E — 端到端走查：房颤抗凝（一条贯穿全机制的主线）

> 用一个真实专病，把平台权威→集团/分院/卫生院/科室覆盖→运行期解析→审计重放完整串起来，验证设计自洽、无缝隙。涉及机制在括号标注对应章节/附录。

## 场景设定（组织）
```
PLATFORM（平台权威）
└ TENANT: 华东医疗集团
   └ REGION: 华东医共体
      ├ FACILITY(医院): 中心总院 ── DEPARTMENT: 心内科 / 急诊科
      ├ FACILITY(医院): 城南分院 ── DEPARTMENT: 心内科
      └ FACILITY(卫生院): 古镇卫生院（独立运作，直挂租户也可）
```
（组织树可跳级、FACILITY 带类型、专病为维度——附录 O）

## 步骤 1：平台发布权威知识包（§3 / §7 / 附录 L）
平台管理员发布 **`plat:pkg:af-anticoag@v1`**，含：
- `plat:rule:cha2ds2-vasc`（评分规则，FREE）
- `plat:rule:has-bled`（出血风险，FREE）
- `plat:rule:doac-contraindication`（DOAC 禁忌核查，**LOCKED**，安全单调）
- `plat:path:af-anticoag-care`（路径，REVIEW）
- 依赖：上述规则引用 `plat:field:egfr`(肾功能字段)、`plat:dict:atc-anticoagulants`(ATC 抗凝药字典)（依赖图，附录 D1）
- applicable_scope：`specialty=AF`（附录 O3b）
- 元数据：来源《2023 ESC 房颤指南》、GRADE 高、复审周期 12 月（附录 L2）
- 发布走质量门：术语绑定完整、依赖可解析、LOCKED 单调通过、影响模拟通过、签名（附录 L5 / R1）

此刻：**所有租户/机构零操作即引用 v1**（§3.2，引用非复制）。

## 步骤 2：集团统一首选药（§4.2 INHERITABLE）
华东集团对 `plat:rule:doac-first-line` 在 **TENANT 节点、specialty=AF、propagation=INHERITABLE** 做 REPLACE（统一首选阿哌沙班）。
→ 总院、分院、卫生院默认复用集团版本（解析时祖先 INHERITABLE 命中）。

## 步骤 3：城南分院按肾功能人群定制剂量（REVIEW + 安全单调，§4.4 / 附录 S2）
城南分院对 `plat:rule:doac-dose`（REVIEW）在 **该 FACILITY、specialty=AF & cohort=RENAL_IMPAIR** 做 REPLACE：eGFR<30 时下调剂量。
- 因 REVIEW → 进评审队列，评审通过 + 签名后 PUBLISHED（附录 L1）。
- 系统校验"更严格"方向（剂量更保守）→ 通过；若试图放宽则被拒（附录 S2）。
- propagation=INHERITABLE → 下沉到城南分院心内科。

## 步骤 4：古镇卫生院能力受限（DISABLE + ADD，§4.1）
卫生院无 DOAC 库存，仅用华法林：
- 对 `plat:rule:doac-first-line` 做 **DISABLE**（本院不用）。
- 但 `plat:rule:doac-contraindication` 是 **LOCKED → DISABLE 被拒**（附录 S2，安全不可关）。
- **ADD** 本院独有 `t:guangzhen:rule:warfarin-inr-monitor`（INR 监测，EXCLUSIVE 仅本院）。

## 步骤 5：床旁运行期解析（§8 / 附录 D / 附录 N）
城南分院心内科，一位 eGFR=25 的房颤患者就诊，ClinicalEvent 触发：
- 按 `tenantId=华东, orgUnit=城南心内科, dimensions={specialty=AF, cohort=RENAL_IMPAIR}` 批量解析有效包（§7.2）。
- 解析结果（最具体优先 + 传播 + 维度命中 + tie-break）：
  - cha2ds2-vasc → 平台 v1（PLATFORM）
  - doac-first-line → 集团版（TENANT INHERITABLE）
  - doac-dose → 城南 RENAL_IMPAIR 版（FACILITY，命中 cohort 维度）
  - doac-contraindication → 平台 v1（LOCKED，强制生效）
- 依赖协同解析：egfr 字段、ATC 字典在**同一 epoch**取版本，自洽不撕裂（附录 D3/D4）。
- 解析 P99≤50ms（命中缓存，附录 N1）。

## 步骤 6：固化与审计（附录 S5 / S8）
本次决策的有效集以 `VersionReplayBinding` 钉定（每个 asset_identity→content_hash）。审计记录：用了哪些版本、平台还是覆盖、为何 doac-first-line 取集团版、LOCKED 强制生效。事后可法律级重放。

## 步骤 7：平台升级与影响（§8.5 / §11 / 附录 R）
平台发布 `af-anticoag@v2`（更新出血风险阈值）：
- 城南 doac-dose 是 REPLACE → 收"上游已变更，建议 rebase"信号（§11），按 NOTIFY 通道提示。
- 卫生院 doac-contraindication LOCKED 安全更新 → 无视其任何 PINNED **强制下发**（§8.5 安全例外）。
- 升级前平台侧已做 what-if：影响 N 家机构、回放历史病例决策变化（附录 R1）；可灰度放量（附录 R2）。

## 结论
该主线无缝穿过：平台权威、引用非复制、四正交轴（组织树跳级 + 专病/人群维度）、REPLACE/DISABLE/ADD、复用/独有传播、LOCKED 安全单调、依赖一致性快照、惰性解析 SLA、决策固化重放、升级通道与影响 rebase、模拟灰度——**设计自洽、可落地**。
