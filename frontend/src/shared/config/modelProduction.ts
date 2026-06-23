/**
 * 模型生产控制台的受管枚举目录。
 *
 * <p>这些值来自后端 ProviderType 与模型能力合同，不是页面模拟数据。
 */
export const MODEL_PROVIDER_TYPE_OPTIONS = [
  { label: "OpenAI 兼容服务", value: "OPENAI_COMPATIBLE" },
  { label: "Claude", value: "CLAUDE" },
  { label: "Dify", value: "DIFY" },
  { label: "院内 Ollama", value: "OLLAMA" },
] as const;

export const MODEL_CAPABILITY_OPTIONS = [
  { value: "knowledge.discovery", label: "临床知识关联发现" },
  { value: "knowledge.production.knowledge", label: "正式医学知识生产" },
  { value: "knowledge.extract", label: "病历语义实体提取" },
  { value: "terminology.map", label: "标准术语映射" },
  { value: "rule.draft", label: "临床规则草案拟定" },
  { value: "pathway.draft", label: "临床路径草案拟定" },
  { value: "cdss.explain", label: "临床决策解释" },
  { value: "quality.semantic-check", label: "病历内涵质控" },
  { value: "followup.draft", label: "随访草案拟定" },
] as const;
