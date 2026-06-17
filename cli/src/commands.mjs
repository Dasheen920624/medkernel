/**
 * CLI 命令域（DATASVC-01 FR-3，规范 §8.5 首版 6 命令域）。
 *
 * 每个命令域只经后端受控合同（受控工具执行入口 / 只读统计端点 / 异步导出端点）取数，**不直连库、不绕治理**。
 * `exports` 走后端经 SYS-06 审批闸控制的异步导出端点，**不绕导出审批、不伪造任务**（铁律 #1）。
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
  agent: '通过受控工具获取公域资料或回写 AI Agent 生产候选',
  privacy: '验证数据分级是否准入数据服务/CLI/MCP',
  exports: '提交与查看经审批闸控制的异步导出任务（submit/status/list/cancel/complete）',
  diagnostics: '检查服务连通、受控工具目录与状态',
};

function requireArg(positional, name) {
  return requireArgAt(positional, 0, name);
}

function requireArgAt(positional, index, name) {
  const value = positional[index];
  if (value == null || String(value).trim() === '') {
    throw new CliUsageError(`缺少参数 ${name}`);
  }
  return value;
}

function parseJsonArg(positional, index, name) {
  const raw = requireArgAt(positional, index, name);
  try {
    return JSON.parse(raw);
  } catch {
    throw new CliUsageError(`${name} 必须是合法 JSON`);
  }
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
    case 'agent':
      switch (action) {
        case 'submit-candidate':
          return {
            domain,
            action,
            result: await client.executeTool('submitProductionCandidate', {
              purpose,
              payload: parseJsonArg(positional, 0, 'payloadJson'),
            }),
          };
        case 'fetch-public-material':
          return {
            domain,
            action,
            result: await client.executeTool('fetchPublicMaterial', {
              purpose,
              payload: parseJsonArg(positional, 0, 'payloadJson'),
            }),
          };
        default:
          throw new CliUsageError(
            `agent 不支持的动作：${action || '(空)'}（可用：submit-candidate|fetch-public-material）`,
          );
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
      // 走后端经 SYS-06 审批闸控制的异步导出端点；CLI 不直连库、不绕导出审批、不伪造任务（铁律 #1）。
      switch (action) {
        case 'submit': {
          const exportType = requireArg(positional, 'exportType');
          const approvalId = requireArgAt(positional, 1, 'approvalId');
          const idempotencyKey = requireArgAt(positional, 2, 'idempotencyKey');
          const windowDays = options.windowDays == null ? 0 : Number(options.windowDays);
          return {
            domain,
            action,
            result: await client.post('/api/v1/engine-data/exports', {
              exportType,
              windowDays,
              approvalId,
              idempotencyKey,
            }),
          };
        }
        case 'status':
          return {
            domain,
            action,
            result: await client.get(
              `/api/v1/engine-data/exports/${encodeURIComponent(requireArg(positional, 'jobCode'))}`,
            ),
          };
        case 'list':
          return { domain, action, result: await client.get('/api/v1/engine-data/exports') };
        case 'cancel':
          return {
            domain,
            action,
            result: await client.post(
              `/api/v1/engine-data/exports/${encodeURIComponent(requireArg(positional, 'jobCode'))}/cancel`,
            ),
          };
        case 'complete': {
          // 登记导出完成走合规导出审批端点（audit.export，服务端鉴权）；CLI 不绕审批。
          const approvalId = requireArg(positional, 'approvalId');
          const jobCode = requireArgAt(positional, 1, 'jobCode');
          return {
            domain,
            action,
            result: await client.post(
              `/api/v1/compliance/exports/${encodeURIComponent(approvalId)}:complete-from-job`,
              { jobId: jobCode, reason: options.reason || `CLI 登记导出完成 ${jobCode}` },
            ),
          };
        }
        default:
          throw new CliUsageError(
            `exports 不支持的动作：${action || '(空)'}（可用：submit|status|list|cancel|complete）`,
          );
      }
    default:
      throw new CliUsageError(`未知命令域：${domain}（可用：${Object.keys(DOMAINS).join(' / ')}）`);
  }
}
