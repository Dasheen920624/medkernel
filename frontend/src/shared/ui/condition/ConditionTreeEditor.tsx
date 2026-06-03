/**
 * 递归条件树编辑器（RULE-01 / PATH-01，OpenSpec pathway-rule-authoring-overhaul P1-2）。
 *
 * <p>受控组件：`value` 为顶层 {@link RuleGroup}，`onChange` 回传新根。规则 `when` 与
 * 路径边 `guard` 共用本组件，支持任意深度「条件组(all/any/可取反)+叶子」嵌套，
 * 直接解决「条件树只有一层」。字段路径目前为文本输入，后续接入字段目录选择器（P2）。
 */
import { Button, Card, Input, Select, Space, Switch, Tag, Tooltip } from "antd";
import { DeleteOutlined, PlusOutlined, BranchesOutlined } from "@ant-design/icons";

import styles from "./ConditionTreeEditor.module.css";

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

const { Option } = Select;

const OPERATOR_LABELS: Record<RuleOperator, string> = {
  exists: "存在",
  equals: "等于",
  not_equals: "不等于",
  contains: "包含",
  gt: "大于",
  gte: "大于等于",
  lt: "小于",
  lte: "小于等于",
  in: "属于集合",
  not_in: "不属于集合",
};

const VALUE_KIND_LABELS: Record<RuleValueKind, string> = {
  empty: "无比较值",
  string: "文本",
  number: "数值",
  boolean: "布尔",
  list: "集合",
};

function valueKindForOperator(operator: RuleOperator, current: RuleValueKind): RuleValueKind {
  if (!operatorNeedsValue(operator)) return "empty";
  return current === "empty" ? "string" : current;
}

export interface ConditionTreeEditorProps {
  value: RuleGroup;
  onChange: (next: RuleGroup) => void;
  maxDepth?: number;
  readOnly?: boolean;
}

export function ConditionTreeEditor({
  value,
  onChange,
  maxDepth = MAX_TREE_DEPTH,
  readOnly = false,
}: ConditionTreeEditorProps) {
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
    const needsValue = operatorNeedsValue(leaf.operator);
    return (
      <div
        key={leaf.id}
        data-testid="condition-leaf"
        className="rounded-md border border-gray-200 bg-white p-3"
      >
        <Space wrap align="start" className="mk-full-width">
          <Input
            aria-label="条件标签"
            placeholder="条件标签"
            value={leaf.label}
            disabled={readOnly}
            className={styles.label}
            onChange={(e) => patchLeaf(leaf.id, { label: e.target.value })}
          />
          <Input
            aria-label="上下文字段路径"
            placeholder="如 observations[].valueNumeric"
            value={leaf.fact}
            disabled={readOnly}
            className={styles.fact}
            onChange={(e) => patchLeaf(leaf.id, { fact: e.target.value })}
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
            {(Object.keys(OPERATOR_LABELS) as RuleOperator[]).map((op) => (
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
            {(Object.keys(VALUE_KIND_LABELS) as RuleValueKind[]).map((kind) => (
              <Option key={kind} value={kind}>
                {VALUE_KIND_LABELS[kind]}
              </Option>
            ))}
          </Select>
          <Input
            aria-label="比较值"
            placeholder={leaf.valueKind === "list" ? "多个值用英文逗号分隔" : "比较值"}
            value={leaf.value === undefined ? "" : String(leaf.value)}
            disabled={readOnly || !needsValue}
            className={styles.value}
            onChange={(e) => patchLeaf(leaf.id, { value: e.target.value })}
          />
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
  };

  const renderGroup = (group: RuleGroup, isRoot: boolean) => (
    <Card
      key={group.id}
      size="small"
      data-testid="condition-group"
      className="border-emerald-200 bg-emerald-50/30"
      title={
        <Space wrap>
          <Tag color="green">{isRoot ? "条件根组" : "子条件组"}</Tag>
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
          child.kind === "leaf" ? renderLeaf(child) : renderGroup(child, false),
        )}
        {!readOnly && (
          <Space wrap>
            <Button
              aria-label="新增条件"
              icon={<PlusOutlined />}
              size="small"
              onClick={() => addLeaf(group.id)}
            >
              条件
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

  return renderGroup(value, true);
}

export default ConditionTreeEditor;
