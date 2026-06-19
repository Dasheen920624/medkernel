# 演练机构规则来源清单

本清单是十条机构定制演练规则的来源与安全边界证据。规则仅用于 `pilot-hospital` 沙盘机构，不自动关联平台主源生产规则，不构成真实患者诊疗依据。沙盘治理角色签署只验证工程流程，不替代生产医学专家双签。

| 规则                      | 权威来源                                                                                                                                                                      | 文号或版本                                 | 发布/版本日期    | 检索日期   |
| ------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------ | ---------------- | ---------- |
| `SBX.LAB.CRITICAL.K`      | [国家卫生健康委：医疗质量安全核心制度要点解读](https://www.nhc.gov.cn/zwgk/jdjd/201804/5dfb30387c74412194c7f05b878d4f87.shtml)                                                | 国卫医发〔2018〕8号                        | 2018-04-18       | 2026-06-19 |
| `SBX.MED.WARFARIN.ASA`    | [DailyMed：WARFARIN SODIUM tablet label](https://dailymed.nlm.nih.gov/dailymed/drugInfo.cfm?setid=558b7a0d-5490-4c1b-802e-3ab3f1efe760)                                       | SETID 558b7a0d-5490-4c1b-802e-3ab3f1efe760 | 2026-06-03       | 2026-06-19 |
| `SBX.ORDER.CONTRAST.CKD`  | [American College of Radiology：ACR Manual on Contrast Media](https://www.acr.org/Clinical-Resources/Clinical-Tools-and-Reference/Contrast-Manual)                            | 在线现行版                                 | 以检索日页面为准 | 2026-06-19 |
| `SBX.DX.ACS`              | [European Society of Cardiology：2023 ACS Guidelines](https://www.escardio.org/guidelines/clinical-practice-guidelines/all-esc-practice-guidelines/acute-coronary-syndromes/) | 2023 指南                                  | 2023-08-25       | 2026-06-19 |
| `SBX.REPORT.CRITICAL`     | [国家卫生健康委：医疗质量安全核心制度要点解读](https://www.nhc.gov.cn/zwgk/jdjd/201804/5dfb30387c74412194c7f05b878d4f87.shtml)                                                | 国卫医发〔2018〕8号                        | 2018-04-18       | 2026-06-19 |
| `SBX.DISCHARGE.CHECK`     | [WHO：Medication Safety in Transitions of Care](https://www.who.int/publications/i/item/WHO-UHC-SDS-2019.9)                                                                   | WHO/UHC/SDS/2019.9                         | 2019-05-01       | 2026-06-19 |
| `SBX.FOLLOWUP.INR`        | [DailyMed：WARFARIN SODIUM tablet label](https://dailymed.nlm.nih.gov/dailymed/drugInfo.cfm?setid=558b7a0d-5490-4c1b-802e-3ab3f1efe760)                                       | SETID 558b7a0d-5490-4c1b-802e-3ab3f1efe760 | 2026-06-03       | 2026-06-19 |
| `SBX.INSURANCE.DRG`       | [国家医疗保障局政策法规目录](https://www.nhsa.gov.cn/col/col14/index.html)                                                                                                    | 医保办发〔2024〕9号                        | 2024-07-23       | 2026-06-19 |
| `SBX.QUALITY.RECORD`      | [国家卫生健康委：医疗质量管理办法解读](https://www.nhc.gov.cn/zwgk/jdjd/201610/8e7ef364c1a84f33a7e40291eaf70a3f.shtml)                                                        | 国家卫生和计划生育委员会令第10号           | 2016-10-18       | 2026-06-19 |
| `SBX.RECORD.COMPLETENESS` | [原卫生部：病历书写基本规范（试行）](https://www.nhc.gov.cn/wjw/gfxwj/200205/8348500efb5b490c8db6519e818e96e3.shtml)                                                          | 卫医发〔2002〕190号                        | 2002-08-16       | 2026-06-19 |

## 安全口径

- 血钾、eGFR 等数值仅作为演练机构参数或边界样例，不宣称为全国统一阈值。
- 肌钙蛋白按检验系统结构化异常标识判断，不硬编码跨试剂平台的统一数值界限。
- 医保与病历质量规则只提示结构完整性和流程风险，不自动拒付、不自动诊断。
- 所有高风险动作均要求医师人工确认；沙盘 `BLOCK` 只影响演练结果，不产生真实外部阻断。
- 配置包版本由规则清单内容摘要生成；运行方传递实际包版本，场景不固定版本。
