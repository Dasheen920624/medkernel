/**
 * CLI 命令域（DATASVC-01 FR-3，规范 §8.5 首版 6 命令域）。
 *
 * 每个命令域只经后端受控合同（受控工具执行入口 / 只读统计端点）取数，**不直连库、不绕治理**。
 * `exports` 后端异步导出尚未实现——诚实标缺口、不伪造任务、不绕导出审批（铁律 #1）。
 */

export class CliUsageError extends Error {
  constructor(message) {
    super(message);
    this.name = 'CliUsageError';
  }
}

/** 6 命令域用途（规范 §8.5），供帮助与 diagnostics 列举。 */
export const DOMAINS = {
  knowledge: '查询知识身份存在性、关键词检索、知识使用统计',
  rules: '检查规则解释、规则使用统计',
  'clinical-signals': '查看脱敏聚合后的临床信号与引擎降级情况',
  privacy: '验证数据分级是否准入数据服务/CLI/MCP',
  exports: '提交与查看异步导出任务（后端未实现，诚实标缺口）',
  diagnostics: '检查服务连通、受控工具目录与状态',
};

function requireArg(positional, name) {
  const value = positional[0];
  if (value == null || String(value).trim() === '') {
    throw new CliUsageError(`缺少参数 ${name}`);
  }
  return value;
}

export async function runCommand(client, domain, action, positional = [], options = {}) {
  const purpose = options.purpose || `CLI ${domain} ${action || ''}`.trim();
  switch (domain) {
    case 'diagnostics': {
      const tools = await client.get('/api/v1/engine-data/tools');
      return { domain, action: action || 'status', tools };
    }
    case 'rules':
      switch (action) {
        case 'usage':
          return { domain, action, result: await client.get('/api/v1/engine-data/rule-usage') };
        case 'explain':
          return {
            domain,
            action,
            result: await client.executeTool('explainRule', { purpose, target: requireArg(positional, 'ruleId') }),
          };
        default:
          throw new CliUsageError(`rules 不支持的动作：${action || '(空)'}（可用：usage|explain）`);
      }
    case 'knowledge':
      switch (action) {
        case 'search':
          return {
            domain,
            action,
            result: await client.executeTool('searchKnowledge', { purpose, target: requireArg(positional, 'keyword') }),
          };
        case 'exists':
          return {
            domain,
            action,
            result: await client.executeTool('checkKnowledgeExistence', {
              purpose,
              target: requireArg(positional, 'identityCode'),
            }),
          };
        case 'usage':
          return { domain, action, result: await client.get('/api/v1/engine-data/knowledge-usage') };
        default:
          throw new CliUsageError(`knowledge 不支持的动作：${action || '(空)'}（可用：search|exists|usage）`);
      }
    case 'clinical-signals':
      switch (action) {
        case 'list':
          return { domain, action, result: await client.get('/api/v1/engine-data/clinical-signals') };
        case 'summary':
          return { domain, action, result: await client.executeTool('summarizeEngineSignals', { purpose }) };
        default:
          throw new CliUsageError(`clinical-signals 不支持的动作：${action || '(空)'}（可用：list|summary）`);
      }
    case 'privacy':
      switch (action) {
        case 'validate':
          return {
            domain,
            action,
            result: await client.executeTool('validatePrivacyPolicy', { purpose, target: requireArg(positional, 'level') }),
          };
        default:
          throw new CliUsageError(`privacy 不支持的动作：${action || '(空)'}（可用：validate）`);
      }
    case 'exports':
      // 后端异步导出任务尚未实现：诚实标缺口，不调后端、不伪造任务、不绕导出审批（铁律 #1）。
      return {
        domain,
        action: action || '(空)',
        implemented: false,
        message: '后端异步导出任务尚未实现（DATASVC-01 后续切片）；CLI 不绕过导出审批，未伪造任务。',
      };
    default:
      throw new CliUsageError(`未知命令域：${domain}（可用：${Object.keys(DOMAINS).join(' / ')}）`);
  }
}
