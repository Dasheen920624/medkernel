/**
 * 递归条件树编辑器（RULE-01 / PATH-01，OpenSpec pathway-rule-authoring-overhaul P1-2）。
 *
 * <p>受控组件：`value` 为顶层 {@link RuleGroup}，`onChange` 回传新根。规则 `when` 与
 * 路径边 `guard` 共用本组件，支持任意深度「条件组(all/any/可取反)+叶子」嵌套，
 * 直接解决「条件树只有一层」。字段路径接入 canonical 字段目录，选择后自动带出值类型与字典。
 */
import { useMemo } from "react";
import {
  Alert,
  AutoComplete,
  Button,
  Card,
  Input,
  Select,
  Space,
  Switch,
  Tag,
  Tooltip,
} from "antd";
import { DeleteOutlined, PlusOutlined, BranchesOutlined } from "@ant-design/icons";

import styles from "./ConditionTreeEditor.module.css";

import { buildFieldCatalogOptions } from "@/shared/config/contextFieldOptions";
import {
  RULE_OPERATOR_LABELS as OPERATOR_LABELS,
  RULE_VALUE_KIND_LABELS as VALUE_KIND_LABELS,
  defaultValueKindForOperator,
  isClinicalRuleOperator,
} from "@/shared/config/ruleOperatorCatalog";
import {
  MAX_TREE_DEPTH,
  addChildToGroup,
  createGroup,
  createLeaf,
  operatorNeedsValue,
  removeNodeById,
  treeDepth,
  updateNodeById,
  type RuleGroup,
  type RuleLeaf,
  type RuleLogic,
  type RuleOperator,
  type RuleValueKind,
} from "@/shared/config/conditionModel";
import type { ContextFieldDescriptor } from "@/shared/api/hooks";
import StandardTermValueAutoComplete from "./StandardTermValueAutoComplete";

const { Option } = Select;

const EDITABLE_OPERATOR_KEYS = (Object.keys(OPERATOR_LABELS) as RuleOperator[]).filter(
  (operator) => !isClinicalRuleOperator(operator),
);

const EDITABLE_VALUE_KIND_KEYS = (Object.keys(VALUE_KIND_LABELS) as RuleValueKind[]).filter(
  (kind) =>
    !["range", "measurement", "temporal", "derived", "critical_flag", "staleness"].includes(kind),
);

function valueKindForOperator(operator: RuleOperator, current: RuleValueKind): RuleValueKind {
  return defaultValueKindForOperator(operator, current);
}

function valueKindForDataType(dataType?: string | null): RuleValueKind {
  switch (dataType) {
    case "number":
      return "number";
    case "boolean":
      return "boolean";
    case "list":
      return "list";
    default:
      return "string";
  }
}

export interface ConditionTreeEditorProps {
  value: RuleGroup;
  onChange: (next: RuleGroup) => void;
  maxDepth?: number;
  readOnly?: boolean;
  fieldCatalog?: ContextFieldDescriptor[];
  fieldCatalogError?: boolean;
}

export function ConditionTreeEditor({
  value,
  onChange,
  maxDepth = MAX_TREE_DEPTH,
  readOnly = false,
  fieldCatalog = [],
  fieldCatalogError = false,
}: ConditionTreeEditorProps) {
  const fieldOptions = useMemo(() => buildFieldCatalogOptions(fieldCatalog), [fieldCatalog]);
  const fieldByPath = useMemo(
    () => new Map(fieldCatalog.map((field) => [field.fieldPath, field])),
    [fieldCatalog],
  );

  const patchLeaf = (id: string, patch: Partial<RuleLeaf>) => {
    onChange(
      updateNodeById(value, id, (node) =>
        node.kind === "leaf" ? { ...node, ...patch } : node,
      ) as RuleGroup,
    );
  };

  const patchGroup = (id: string, patch: Partial<RuleGroup>) => {
    onChange(
      updateNodeById(value, id, (node) =>
        node.kind === "group" ? { ...node, ...patch } : node,
      ) as RuleGroup,
    );
  };

  const addLeaf = (groupId: string) => onChange(addChildToGroup(value, groupId, createLeaf()));
  const addGroup = (groupId: string) =>
    onChange(addChildToGroup(value, groupId, createGroup({ children: [createLeaf()] })));
  const removeNode = (id: string) => onChange(removeNodeById(value, id));

  const currentDepth = treeDepth(value);
  const canNest = currentDepth < maxDepth;

  const renderLeaf = (leaf: RuleLeaf) => {
    if (leaf.fragment) {
      return (
        <div key={leaf.id} data-testid="condition-fragment-leaf" className={styles.fragmentLeaf}>
          <Space wrap className="mk-full-width">
            <Tag color="green">条件片段</Tag>
            <span className={styles.nodeHint}>可复用条件，按引用或拷贝进入当前组</span>
            <span className="font-medium">
              {leaf.label || leaf.fragment.name || leaf.fragment.fragmentCode}
            </span>
            <span className="text-xs text-gray-500">
              {leaf.fragment.fragmentCode} · v{leaf.fragment.version} ·{" "}
              {leaf.fragment.packageVersion}
            </span>
            {!readOnly && (
              <Button
                aria-label="删除条件"
                icon={<DeleteOutlined />}
                size="small"
                onClick={() => removeNode(leaf.id)}
              />
            )}
          </Space>
        </div>
      );
    }
    const needsValue = operatorNeedsValue(leaf.operator);
    const selectedField = fieldByPath.get(leaf.fact);
    const codeSystem = selectedField?.codeSystem;
    return (
      <div key={leaf.id} data-testid="condition-leaf" className={styles.conditionLeaf}>
        <Space direction="vertical" size="small" className="mk-full-width">
          <Space wrap className="mk-full-width">
            <Tag color="blue">具体条件</Tag>
            <span className={styles.nodeHint}>单条判断，属于所在条件组</span>
          </Space>
          <Space wrap align="start" className="mk-full-width">
            <Input
              aria-label="条件标签"
              placeholder="条件标签"
              value={leaf.label}
              disabled={readOnly}
              className={styles.label}
              onChange={(e) => patchLeaf(leaf.id, { label: e.target.value })}
            />
            <AutoComplete
              aria-label="上下文字段路径"
              placeholder="如 observations[].valueNumeric"
              value={leaf.fact}
              options={fieldOptions}
              disabled={readOnly}
              className={styles.fact}
              filterOption={(input, option) => {
                const leafOption = option as { value?: string; label?: string } | undefined;
                const haystack =
                  `${leafOption?.value ?? ""} ${leafOption?.label ?? ""}`.toLowerCase();
                return haystack.includes(input.toLowerCase());
              }}
              onSelect={(fieldPath) => {
                const descriptor = fieldByPath.get(fieldPath);
                patchLeaf(leaf.id, {
                  fact: fieldPath,
                  valueKind: valueKindForDataType(descriptor?.dataType),
                });
              }}
              onChange={(next) => patchLeaf(leaf.id, { fact: next })}
            />
            <Select
              aria-label="算子"
              value={leaf.operator}
              disabled={readOnly}
              className={styles.operator}
              onChange={(operator: RuleOperator) =>
                patchLeaf(leaf.id, {
                  operator,
                  valueKind: valueKindForOperator(operator, leaf.valueKind),
                  value: operatorNeedsValue(operator) ? (leaf.value ?? "") : undefined,
                })
              }
            >
              {EDITABLE_OPERATOR_KEYS.map((op) => (
                <Option key={op} value={op}>
                  {OPERATOR_LABELS[op]}
                </Option>
              ))}
            </Select>
            <Select
              aria-label="比较值类型"
              value={leaf.valueKind}
              disabled={readOnly || !needsValue}
              className={styles.kind}
              onChange={(valueKind: RuleValueKind) => patchLeaf(leaf.id, { valueKind })}
            >
              {EDITABLE_VALUE_KIND_KEYS.map((kind) => (
                <Option key={kind} value={kind}>
                  {VALUE_KIND_LABELS[kind]}
                </Option>
              ))}
            </Select>
            {codeSystem && leaf.valueKind !== "list" ? (
              <StandardTermValueAutoComplete
                ariaLabel="比较值"
                codeSystem={codeSystem}
                value={leaf.value === undefined ? "" : String(leaf.value)}
                disabled={readOnly || !needsValue}
                className={styles.value}
                onChange={(next) => patchLeaf(leaf.id, { value: next })}
              />
            ) : (
              <Input
                aria-label="比较值"
                placeholder={leaf.valueKind === "list" ? "多个值用英文逗号分隔" : "比较值"}
                value={leaf.value === undefined ? "" : String(leaf.value)}
                disabled={readOnly || !needsValue}
                className={styles.value}
                onChange={(e) => patchLeaf(leaf.id, { value: e.target.value })}
              />
            )}
            {!readOnly && (
              <Button
                aria-label="删除条件"
                icon={<DeleteOutlined />}
                size="small"
                onClick={() => removeNode(leaf.id)}
              />
            )}
          </Space>
        </Space>
      </div>
    );
  };

  const renderGroup = (group: RuleGroup, depth = 0) => {
    const isRoot = depth === 0;
    const label = `${isRoot ? "条件根组" : "子条件组"} · 第 ${depth + 1} 层`;
    const hint = isRoot ? "整棵条件树的入口" : "先在本组内组合判断，再回到上层";
    return (
      <Card
        key={group.id}
        size="small"
        data-testid="condition-group"
        className={isRoot ? styles.rootGroupCard : styles.nestedGroupCard}
        title={
          <Space wrap>
            <Tag color={isRoot ? "geekblue" : "green"}>{label}</Tag>
            <span className={styles.nodeHint}>{hint}</span>
            <Select
              aria-label="条件组关系"
              value={group.logic}
              disabled={readOnly}
              size="small"
              className={styles.logic}
              onChange={(logic: RuleLogic) => patchGroup(group.id, { logic })}
            >
              <Option value="all">全部满足</Option>
              <Option value="any">任一满足</Option>
            </Select>
            <Tooltip title="对整组结果取反（NOT）">
              <Space size={4}>
                <span className="text-xs text-gray-500">取反</span>
                <Switch
                  aria-label="取反"
                  size="small"
                  checked={Boolean(group.negate)}
                  disabled={readOnly}
                  onChange={(checked) => patchGroup(group.id, { negate: checked })}
                />
              </Space>
            </Tooltip>
          </Space>
        }
        extra={
          !readOnly &&
          !isRoot && (
            <Button
              aria-label="删除条件组"
              icon={<DeleteOutlined />}
              size="small"
              onClick={() => removeNode(group.id)}
            />
          )
        }
      >
        <Space direction="vertical" size="small" className="mk-full-width">
          {group.children.map((child) =>
            child.kind === "leaf" ? renderLeaf(child) : renderGroup(child, depth + 1),
          )}
          {!readOnly && (
            <Space wrap>
              <Button
                aria-label="新增具体条件"
                icon={<PlusOutlined />}
                size="small"
                onClick={() => addLeaf(group.id)}
              >
                具体条件
              </Button>
              <Tooltip title={canNest ? "" : `已达最大嵌套深度 ${maxDepth}`}>
                <Button
                  aria-label="新增子条件组"
                  icon={<BranchesOutlined />}
                  size="small"
                  disabled={!canNest}
                  onClick={() => addGroup(group.id)}
                >
                  子条件组
                </Button>
              </Tooltip>
            </Space>
          )}
        </Space>
      </Card>
    );
  };

  return (
    <Space direction="vertical" size="small" className="mk-full-width">
      {fieldCatalogError ? (
        <Alert
          showIcon
          type="warning"
          message="字段目录暂不可用"
          description="当前只能保留已有字段路径，恢复字段目录后再新增或调整条件字段。"
        />
      ) : null}
      {renderGroup(value)}
    </Space>
  );
}

export default ConditionTreeEditor;
