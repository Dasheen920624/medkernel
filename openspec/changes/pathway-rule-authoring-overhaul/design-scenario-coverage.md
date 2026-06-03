# 设计附录 C：临床场景覆盖矩阵（适配性验证）

> 关联：`design.md`、`design-dsl-grammar.md`。
> 目的：以代表性真实临床场景反向验证「设计能否表达各类医疗规则与路径」。每行给出场景、所需能力、是否可表达，以及暴露的前置依赖（如 canonical 模型缺口）。
> 结论先行：**绝大多数场景可由本设计表达**；唯一硬前置是新增 `CanonicalAllergyIntolerance`（药物-过敏类场景），已列入 P0 前置（见 `design.md §13.2`）。

## C1. 规则类场景

| # | 临床场景 | 所需能力 | 可表达 | 前置/备注 |
|---|---|---|---|---|
| 1 | 药物-药物相互作用（DDI，A 与 B 同开） | 集合 count + where + valueSet | ✅ | 需 DDI 值集 |
| 2 | 药物-过敏核查（开药命中既往过敏） | 跨资源 + valueSet 交叉反应 | ⚠️ | **需新增 CanonicalAllergyIntolerance** |
| 3 | 药物-疾病禁忌（如 NSAID + 消化道出血史） | conditions[].code in valueSet + medications | ✅ | — |
| 4 | 肾功能调量（eGFR<30 + 肾毒性药） | 受控公式 eGFR + valueSet + 嵌套 | ✅ | 见附录 A5 |
| 5 | 肝功能调量（Child-Pugh 分级） | 受控公式 + 多字段聚合 | ✅ | 公式入库 |
| 6 | 儿科按体重剂量（mg/kg 超限） | dosePerKg 公式 + 单位 + patient.weight | ✅ | — |
| 7 | 妊娠期禁用药 | applicability 人群 + 妊娠状态字段 | ✅ | 妊娠状态入字段目录 |
| 8 | 危急值回报（critical value callback） | is_critical + 时钟/动作 | ✅ | Observation.criticalFlag |
| 9 | 抗菌药物分级管理/特殊使用审批 | valueSet 分级 + applicability 科室 + BLOCK | ✅ | — |
| 10 | 重复用药/重复检验 | count over 窗口 + 去重治理 | ✅ | — |
| 11 | AKI 早期识别（肌酐 48h 内升高） | delta + over(PT48H) | ✅ | — |
| 12 | 持续高血压（连续 3 次） | sustained/trend(rising,3) | ✅ | — |
| 13 | 抗凝监测（INR 超治疗窗 between） | between + 参考范围 | ✅ | — |
| 14 | CHA₂DS₂-VASc / 风险评分阈值 | 受控评分公式 + 阈值 | ✅ | 评分公式入库 |
| 15 | 电解质纠正（校正钙、阴离子间隙） | correctedCalcium/anionGap 公式 | ✅ | — |
| 16 | 缺关键检验时高危拦截 | 三值逻辑 UNKNOWN_AS_BLOCK | ✅ | — |
| 17 | 医保规范/适应症核查 | valueSet + applicability + 诊断-医嘱匹配 | ✅ | — |
| 18 | 质量度量（如 VTE 预防率，CQL 对标） | 人群/分子/分母 + 回测 | ✅ | 可导出 CQL（P11） |
| 19 | 重症监护组合判据（多器官） | 深层嵌套 all/any/not | ✅ | 深度护栏内 |
| 20 | 单位混用比较（mmol/L vs mg/dL） | UCUM 单位归一 | ✅ | 换算因子配置 |

## C2. 路径类场景

| # | 临床路径场景 | 所需能力 | 可表达 | 备注 |
|---|---|---|---|---|
| P1 | 脓毒症 1h 集束化 | 里程碑时钟 SLA + 决策点 + 守卫边 | ✅ | 见附录 A7 |
| P2 | 急性心梗门球时间<90min | ClinicalClock baseEvent + 超时升级 | ✅ | — |
| P3 | 卒中绿色通道（DNT<60min） | 时钟 + 并行（影像+溶栓评估） | ✅ | PARALLEL fork/join |
| P4 | 择期手术围术期路径 | 阶段/天序 + 医嘱套餐 + 人工闸门 | ✅ | ORDER_SET/MANUAL_GATE |
| P5 | 围术期抗生素预防（切皮前 1h） | 时钟 baseEvent=SURGERY_START | ✅ | — |
| P6 | 化疗周期路径（多周期循环） | TIMER/WAIT + 子路径循环 | ✅ | SUBPATHWAY |
| P7 | 慢病管理（高血压/糖尿病随访） | FOLLOWUP + 随访时钟 + 结局指标 | ✅ | 对接随访 |
| P8 | 决策分支（病理结果决定治疗） | DECISION + 守卫边 valueSet | ✅ | — |
| P9 | 路径变异（患者拒绝/转科） | 变异捕获/分类/再入径 | ✅ | pathway_variance |
| P10 | 多病共存（同时在多条路径） | 多路径实例 + 冲突检测提示 | ✅ | 仅提示不自动改医嘱 |
| P11 | 集团→医院→科室差异化 | 多级模板继承 + diff 合并 | ✅ | — |
| P12 | 出院计划与再入院监测 | 出径标准 + 结局指标 LOS/再入院 | ✅ | 对接 EvaluationIndicator |

## C3. 前置与跟进（已随附录 H6 决策更新）

1. **CanonicalAllergyIntolerance（P0 必需）**：药物-过敏（#2）与过敏交叉反应所需，须先补结构化 canonical 资源、字段目录与对照（见 design.md §13.2）。
2. **可选 canonical 扩展**：Immunization、FamilyHistory、Coverage——非阻断，后续包版本。
3. **受控公式范围（已定，H6-5）**：v1 = eGFR/CrCl/BSA/BMI/校正钙/阴离子间隙/**CHA₂DS₂-VASc/HAS-BLED**（房颤优先）；v2 = Child-Pugh/dosePerKg(儿科)/MELD 等。各含金标准单测（P6）。
4. **值集建设清单**：肾毒性药 ATC、DDI 对、抗菌药分级、肌酐/乳酸 LOINC、口服抗凝药 B01A、出血高危诊断、妊娠禁用等，随包版本维护（P5）。
5. **术语系统（已定，H6-1）**：v1 用 ICD-10/ICD-9-CM-3/LOINC/ATC/医保编码/院内字典；SNOMED CT 置开关后。

## C4. 新增专病端到端（详见附录 I）

| 专病 | 关键能力 | 落点 |
|---|---|---|
| 慢性肾病 CKD | eGFR 分期 + 肾毒性 BLOCK + CrCl 调量 + 随访时钟 | 附录 I5 |
| 房颤抗凝 AF | CHA₂DS₂-VASc/HAS-BLED 决策 + OAC 联用/调量/INR 监测 | 附录 I5b |
| 脓毒症 Sepsis | 1h 集束化时钟 SLA + 决策守卫 + 抗生素过敏核查 | 附录 I5c（+ A7） |
