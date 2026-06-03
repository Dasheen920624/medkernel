# 设计附录 A：规则与路径 DSL 文法（可实现版）

> 关联：`design.md`。本附录给出规则与路径的权威 JSON 文法、统一条件内核、证据链结构与真实临床示例，供后续 AI 直接照此实现序列化/反序列化与求值。
> 字段路径示例对齐 `engine.context.canonical.*`：如 `observations[].valueNumeric`、`medications[].code`、`conditions[].code`、`patient.birthDate`、`patient.gender`。
>
> **派生字段（Derived Field）**：部分临床常用量不是 canonical 原始字段，而是求值期计算的派生值，由字段目录显式登记（`derived=true`），如 `patient.age`（由 `patient.birthDate` 与评估时刻计算）、`patient.bodyWeightKg`（取最近一次体重 Observation）。派生字段在 DSL 中像普通字段一样引用，由 `ConditionEvaluator` 在求值期解析。`CanonicalPatient` 真实字段为 `birthDate`/`gender`/`allergies`/`specialPopulations`（无 age/sex/weight 原始列）。

---

## A1. 统一条件文法（规则 when 与路径 edge.guard 共用）

```
Group  := { "all": Node[] } | { "any": Node[] } | { "not": Node }
Node   := Group | Leaf
Leaf   := { "expr": Expr, "operator": Operator, "value"?: Operand, "ui"?: UiHint }

Expr   := { "field": FieldPath,
            "select"?: "latest"|"first"|"max"|"min"|"avg"|"sum"|"count",
            "where"?: Group,          // 对集合元素的过滤，复用同一条件文法
            "over"?: Duration }       // ISO-8601，如 PT6H / P2D；相对评估时刻或基准事件

Operand := { "const": <json>, "unit"?: Ucum }      // 常量（可带单位）
         | { "field": FieldPath }                   // 字段对字段
         | { "fn": FunctionName, "args": {..} }      // 受控临床公式
         | { "valueSet": ValueSetCode }             // 术语值集成员

UiHint := { "id"?: string, "label"?: string, "valueKind"?: ValueKind, "valueSet"?: string }
```

- `ui` 仅前端旁注，后端求值忽略，保证 L2↔L3 无损往返。
- 完整算子与函数枚举见附录 D（`design-enums-glossary.md`）。
- **路径边 `guard` 与规则 `when` 使用同一 `Group` 文法**，由统一条件内核 `ConditionEvaluator` 求值（见 design.md §13.1）。

### 向后兼容

旧路径边扁平条件 `{ "fact": "x", "operator": "equals", "value": 1 }` SHALL 被适配为单叶子：`{ "all": [ { "expr": { "field": "x" }, "operator": "equals", "value": { "const": 1 } } ] }`。线上既有边无需迁移即可运行。

---

## A2. 规则 DSL 顶层文法

```
RuleDsl := {
  "when":          Group,
  "then":          ActionCard[],          // 分级动作，见 A3
  "applicability": Applicability,         // 适用域，见 A4
  "explain":       ExplainSpec,           // 解释模板
  "missingPolicy": "UNKNOWN_AS_FALSE" | "UNKNOWN_AS_BLOCK",
  "meta":          { "schemaVersion": "1.0", "packageVersion": string }
}
```

### A3. 分级动作卡片（对齐 CDS Hooks Card）

```
ActionCard := {
  "atSeverity":  "LOW"|"MEDIUM"|"HIGH"|"CRITICAL",   // 命中达到该级别时产出
  "actionCode":  string,                              // INFO/REMIND/STRONG_REMINDER/BLOCK/SUGGEST_ORDER/AUTO_DOCUMENT
  "indicator":   "info"|"warning"|"critical",
  "summary":     string,
  "detail":      string,
  "source":      { "label": string, "url"?: string, "evidenceLevel"?: string },
  "suggestions": [ { "label": string, "actionType": string, "payload"?: <json> } ],
  "overrideReasons"?: string[],
  "requiresPhysicianConfirmation": boolean
}
```

### A4. 适用域

```
Applicability := {
  "population": { "include"?: Group, "exclude"?: Group },  // 复用条件文法
  "orgScope":   { "groupIds"?: string[], "hospitalIds"?: string[], "deptIds"?: string[] },
  "settings":   ("INPATIENT"|"OUTPATIENT"|"ED"|"FOLLOWUP")[],
  "effective":  { "from"?: date, "to"?: date, "rolloutPercent"?: number }
}
```

### A5. 规则示例：肾功能受限下的肾毒性药物阻断

> 临床意图：eGFR < 30 且 处方中存在肾毒性药物（值集）→ 阻断并要求医师确认；缺关键数据按高危 fail-safe。

```json
{
  "when": {
    "all": [
      { "expr": { "field": "observations[].valueNumeric", "select": "latest",
                  "where": { "all": [ { "expr": { "field": "observations[].code" },
                                        "operator": "equals",
                                        "value": { "valueSet": "VS_SCR_LOINC" } } ] } },
        "operator": "lt",
        "value": { "fn": "eGFR", "args": { "scr": "observations[].valueNumeric",
                                            "age": "patient.age", "sex": "patient.gender" } },
        "ui": { "label": "肌酐对应 eGFR" } },
      { "expr": { "field": "medications[].code", "select": "count",
                  "where": { "all": [ { "expr": { "field": "medications[].code" },
                                        "operator": "in",
                                        "value": { "valueSet": "VS_NEPHROTOXIC_ATC" } },
                                      { "expr": { "field": "medications[].prescriptionStatus" },
                                        "operator": "equals", "value": { "const": "ACTIVE" } } ] } },
        "operator": "gte", "value": { "const": 1 },
        "ui": { "label": "在用肾毒性药物数量" } }
    ]
  },
  "then": [
    { "atSeverity": "HIGH", "actionCode": "BLOCK", "indicator": "critical",
      "summary": "肾功能受限，存在肾毒性药物，建议调整剂量或停用",
      "detail": "eGFR 低于 30 mL/min/1.73m²，当前医嘱含肾毒性药物。",
      "source": { "label": "院内肾脏安全用药规范", "evidenceLevel": "A" },
      "suggestions": [ { "label": "改为肾安全替代", "actionType": "SUGGEST_ORDER" } ],
      "overrideReasons": [ "透析中", "单次给药可接受", "已与肾内科会诊" ],
      "requiresPhysicianConfirmation": true }
  ],
  "applicability": { "settings": ["INPATIENT","ED"],
                     "population": { "exclude": { "all": [ { "expr": { "field": "patient.age" },
                                                            "operator": "lt", "value": { "const": 18 } } ] } } },
  "missingPolicy": "UNKNOWN_AS_BLOCK",
  "explain": { "summary": "依据真实快照的肾功能与在用药物做确定性判断" },
  "meta": { "schemaVersion": "1.0", "packageVersion": "2026.06" }
}
```

> 该 DSL 经统一 `ConditionEvaluator` 递归求值；嵌套 `all/any/not`、聚合 `count where`、公式 `eGFR`、值集 `in` 全部可由 L2 可视化产出，L3 仅供专家核查。

---

## A6. 路径 DSL 顶层文法

```
PathwayDsl := {
  "template":      { templateCode, name, diseaseCode, templateLevel, templateVersion,
                     startNodeCode, sourceRef, packageVersion },
  "entryCriteria": { "include"?: Group, "exclude"?: Group },
  "exitCriteria":  Group,
  "phases":        Phase[],
  "nodes":         PathwayNode[],
  "edges":         PathwayEdge[],
  "variancePolicy":{ "categories": string[], "reasonCodeSet"?: ValueSetCode, "allowReentry": boolean },
  "outcomeBindings": [ { "scope": "PHASE"|"MILESTONE"|"TEMPLATE", "ref": string, "indicatorCode": string } ]
}

Phase  := { "phaseCode", "name", "dayIndex"?: number,
            "milestones": [ { "milestoneCode", "name", "due": ClinicalClock, "achieveWhen"?: Group } ] }

PathwayNode := {
  "nodeCode", "name",
  "nodeType": "ASSESSMENT"|"DIAGNOSIS"|"TREATMENT"|"NURSING"|"CHECK"|"FOLLOWUP"|"QUALITY"
            |"DECISION"|"PARALLEL"|"WAIT"|"TIMER"|"SUBPATHWAY"|"MANUAL_GATE"|"ORDER_SET",
  "phaseCode"?: string, "sortOrder": number, "terminal": boolean,
  "roles"?: { "R"?: string[], "A"?: string[], "C"?: string[], "I"?: string[] },
  "orderSetRef"?: string,             // ORDER_SET 节点绑定医嘱套餐
  "subPathwayRef"?: string,           // SUBPATHWAY 节点引用子模板
  "clock"?: ClinicalClock,
  "metricBindings"?: [ { "metricCode": string, "required": boolean } ]
}

PathwayEdge := {
  "edgeCode", "fromNodeCode", "toNodeCode",
  "edgeType": "DEFAULT"|"CONDITION"|"VARIANCE"|"PHYSICIAN_DECISION"|"JOIN",
  "priority": number,
  "guard"?: Group                     // 与规则 when 同文法，缺省为默认边
}

ClinicalClock := { "baseEvent": string,                     // ADMISSION/SURGERY_START/...
                   "target": number, "min"?: number, "max"?: number, "unit": "MIN"|"HOUR"|"DAY",
                   "escalation"?: [ { "afterUnit": number, "action": string, "role"?: string } ] }
```

### A7. 路径示例（节选）：脓毒症 1 小时集束化（time-zero=识别时刻）

```json
{
  "phases": [
    { "phaseCode": "BUNDLE_1H", "name": "1小时集束化", "dayIndex": 0,
      "milestones": [
        { "milestoneCode": "M_LACTATE", "name": "测乳酸",
          "due": { "baseEvent": "SEPSIS_RECOGNITION", "target": 60, "unit": "MIN" } },
        { "milestoneCode": "M_ABX", "name": "经验性抗生素",
          "due": { "baseEvent": "SEPSIS_RECOGNITION", "target": 60, "unit": "MIN",
                   "escalation": [ { "afterUnit": 60, "action": "ESCALATE", "role": "ICU_ATTENDING" } ] } }
      ] }
  ],
  "nodes": [
    { "nodeCode": "N_DECIDE", "name": "乳酸是否>2", "nodeType": "DECISION", "phaseCode": "BUNDLE_1H", "sortOrder": 2, "terminal": false },
    { "nodeCode": "N_FLUID", "name": "晶体液复苏", "nodeType": "ORDER_SET", "orderSetRef": "OS_SEPSIS_FLUID", "sortOrder": 3,
      "roles": { "R": ["ED_NURSE"], "A": ["ED_ATTENDING"] }, "terminal": false }
  ],
  "edges": [
    { "edgeCode": "E_HIGH_LACT", "fromNodeCode": "N_DECIDE", "toNodeCode": "N_FLUID", "edgeType": "CONDITION", "priority": 1,
      "guard": { "all": [ { "expr": { "field": "observations[].valueNumeric", "select": "latest",
                                       "where": { "all": [ { "expr": { "field": "observations[].code" },
                                                             "operator": "equals", "value": { "valueSet": "VS_LACTATE" } } ] } },
                            "operator": "gt", "value": { "const": 2, "unit": "mmol/L" } } ] } }
  ],
  "variancePolicy": { "categories": ["CLINICAL","SYSTEM","PATIENT"], "allowReentry": true },
  "outcomeBindings": [ { "scope": "MILESTONE", "ref": "M_ABX", "indicatorCode": "QI_SEPSIS_ABX_1H" } ]
}
```

---

## A8. 证据链结构（求值产出，可审计、可重放）

沿用并扩展现有 `RuleDslEvaluator` 的 `conditionEvidence`：

```
Evidence := {
  "result": "TRUE"|"FALSE"|"UNKNOWN",
  "conditionEvidence": [ {
     "fact": FieldPath, "sourcePath": string, "operator": Operator,
     "expected": <json>, "actual": <json>,
     "matched": boolean, "missing": boolean, "stale"?: boolean,
     "unitNormalized"?: { "from": Ucum, "to": Ucum, "factor": number },
     "functionApplied"?: { "fn": string, "inputs": <json>, "output": <json> }
  } ],
  "appliedValueSets"?: [ { "code": string, "version": string, "expandedCount": number } ],
  "evaluatedAt": instant, "packageVersion": string, "idempotencyKey": string
}
```

> 证据链 SHALL 与求值结果同步产出并入审计；给定快照 + packageVersion + DSL，求值 SHALL 可确定性重放（复用 `EvaluationIdempotencyKey`）。
