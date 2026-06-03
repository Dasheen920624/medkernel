# 设计附录 E：受控临床公式库（精确算式与金标准）

> 关联：`design.md §3.2(d)`、`design-data-model.md`(clinical_function 表)。
> 原则：每个公式为白名单注册的命名纯函数，声明入参字段、单位要求、适用人群、文献来源；落地必须有金标准单测（与权威计算器一致，数值容差见各条）。禁止运行期任意表达式注入。
> 入参来源说明：年龄为派生字段 `patient.age`（由 `patient.birthDate` 求值期计算）；性别 `patient.gender`；体重/身高来自体征 Observation（如体重 LOINC `29463-7`），不在 Patient 上。

---

## E1. eGFR（CKD-EPI 2021，去种族）

- 入参：Scr（血肌酐，mg/dL）、age（岁）、gender。
- 算式：
  ```
  κ = (女) 0.7 | (男) 0.9
  α = (女) -0.241 | (男) -0.302
  eGFR = 142 × min(Scr/κ, 1)^α × max(Scr/κ, 1)^(-1.200) × 0.9938^age × (女 ? 1.012 : 1)
  ```
- 单位：结果 mL/min/1.73m²；Scr 须为 mg/dL（若快照为 μmol/L 由单位服务换算：mg/dL = μmol/L ÷ 88.42）。
- 适用：成人（age ≥ 18）；儿童用 Bedside Schwartz（单列）。
- 金标准算例：男、60 岁、Scr 1.2 mg/dL → eGFR ≈ 69 mL/min/1.73m²（容差 ±1）。
- 来源：NKF/ASN 2021 CKD-EPI creatinine equation。

## E2. CrCl（Cockcroft-Gault）

- 入参：age、weight(kg)、Scr(mg/dL)、gender。
- 算式：`CrCl = ((140 − age) × weight × (女 ? 0.85 : 1)) / (72 × Scr)`
- 单位：mL/min。
- 适用：成人剂量调整；肥胖/水肿患者体重选择须按院内规范（理想/调整体重）——体重选择由字段绑定声明。
- 金标准算例：男、60 岁、80 kg、Scr 1.2 → 74.07 mL/min（容差 ±0.1）。

## E3. BSA（体表面积）

- Mosteller：`BSA = sqrt(height_cm × weight_kg / 3600)`
- DuBois：`BSA = 0.007184 × height_cm^0.725 × weight_kg^0.425`
- 单位：m²；变体由 `clinical_function.variant` 选择（默认 Mosteller）。
- 金标准算例（Mosteller）：170 cm、70 kg → 1.82 m²（容差 ±0.01）。

## E4. BMI

- 入参：weight(kg)、height(m)。
- 算式：`BMI = weight / height²`（height 为米；若快照为 cm 单位服务换算）。
- 金标准算例：70 kg、1.70 m → 24.22（容差 ±0.01）。

## E5. 校正钙（Corrected Calcium）

- 入参：measuredCa(mg/dL)、albumin(g/dL)。
- 算式（常规单位）：`correctedCa = measuredCa + 0.8 × (4.0 − albumin)`
- SI 变体（mmol/L、g/L）：`correctedCa = totalCa + 0.02 × (40 − albumin_g_per_L)`
- 金标准算例：measuredCa 8.0、albumin 2.0 → 9.6 mg/dL。

## E6. 阴离子间隙（Anion Gap）

- 入参：Na、Cl、HCO3（mmol/L），可选 K。
- 算式：`AG = Na − (Cl + HCO3)`（含钾变体：`AG = (Na + K) − (Cl + HCO3)`）。
- 参考：常规 8–12 mmol/L（含钾 12–16）。
- 金标准算例：Na 140、Cl 104、HCO3 24 → 12。

## E7. Child-Pugh 肝功能分级

- 入参：bilirubin、albumin、INR（或 PT 延长秒数）、ascites（无/轻/中重）、encephalopathy（无/I-II/III-IV）。
- 计分（各 1–3 分，合计 5–15）：
  | 项 | 1 分 | 2 分 | 3 分 |
  |---|---|---|---|
  | 胆红素 mg/dL | <2 | 2–3 | >3 |
  | 白蛋白 g/dL | >3.5 | 2.8–3.5 | <2.8 |
  | INR | <1.7 | 1.7–2.3 | >2.3 |
  | 腹水 | 无 | 轻 | 中重 |
  | 肝性脑病 | 无 | I–II | III–IV |
- 分级：A 5–6、B 7–9、C 10–15。
- 输出：分值 + 分级，均可作为操作数/动作依据。

## E8. CHA₂DS₂-VASc（房颤卒中风险）【v1】

- 计分：充血性心衰 1、高血压 1、年龄≥75 为 2、糖尿病 1、卒中/TIA/血栓史 2、血管病 1、年龄 65–74 为 1、女性 1（最高 9）。
- 入参映射自诊断值集与派生年龄/性别；输出分值。
- 临床用法：男 ≥1 / 女 ≥2 建议抗凝评估（结合 HAS-BLED 出血风险）。

## E8b. HAS-BLED（房颤抗凝出血风险）【v1】

- 计分（各项命中计 1，最高 9）：高血压(收缩压>160) 1、肾功能异常 1、肝功能异常 1、卒中史 1、出血史或出血倾向 1、INR 不稳定（TTR 低） 1、年龄>65 1、合用抗血小板/NSAID 1、嗜酒 1。
- 入参：诊断/检验/用药值集 + 派生年龄；肾/肝异常可复用 eGFR/Child-Pugh 阈值。
- 临床用法：≥3 提示高出血风险，需谨慎抗凝、纠正可逆因素并加强监测（非禁忌抗凝）。
- 来源：2010 Pisters 等 HAS-BLED 评分。

## E9. 按体重剂量（dosePerKg）

- 入参：dose（与 doseUnit）、weight(kg)。
- 算式：`dosePerKg = dose / weight`，单位 doseUnit/kg。
- 用法：与药品-人群最大 mg/kg 阈值（值集/参数表）比较，超限触发动作。儿科必用。

---

## E9b. 首版范围（决策，见附录 H6 第 5 项）

- **v1**：E1 eGFR、E2 CrCl、E3 BSA(Mosteller)、E4 BMI、E5 校正钙、E6 阴离子间隙、E8 CHA₂DS₂-VASc、E8b HAS-BLED。
- **v2**：E7 Child-Pugh、E9 通用 dosePerKg、MELD、Bedside Schwartz（儿科 eGFR）、BSA(DuBois) 等。
- 房颤专病所需评分（CHA₂DS₂-VASc / HAS-BLED）已提前到 v1。

## E10. 注册与安全约束

- 每个公式以 `clinical_function`（见附录 B）注册：`fn_name + variant + input_spec(入参字段与单位) + population_note + source_ref + package_version`。
- 入参缺失或单位不符 → 返回 UNKNOWN 并产出 `ENG-RULE-007`/`ENG-RULE-008` 证据，**不估算、不取默认**。
- 公式变更即新 `package_version`，旧规则锁定旧版本，保证可回滚、可重放。
- 金标准单测覆盖每个公式及其变体；CI 不过禁止发布（兑现医疗安全红线）。
