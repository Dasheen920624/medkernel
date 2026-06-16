/**
 * MCP 协议处理（DATASVC-01 FR-4，初版 stdio JSON-RPC）。
 *
 * 把后端受控工具目录/执行入口映射为 MCP `initialize` / `tools/list` / `tools/call`：
 * tools/list 经后端 `/tools` 取真实受控工具目录；tools/call 派发后端 `/tools/{name}:execute`，
 * **不绕治理**（权限/脱敏/审计/降级由后端裁决）。工具执行失败以 MCP `isError` 结果返回结构化原因不泄漏内部；
 * 后端不可达透传诚实错误不伪造（铁律 #1）。
 */

export const PROTOCOL_VERSION = '2024-11-05';
export const SERVER_INFO = { name: 'medkernel-mcp', version: '0.1.0' };

const TOOLS_CATALOG_PATH = '/api/v1/engine-data/tools';

export async function handleRequest(message, { client } = {}) {
  const { id, method, params } = message;

  switch (method) {
    case 'initialize':
      return ok(id, {
        protocolVersion: PROTOCOL_VERSION,
        capabilities: { tools: {} },
        serverInfo: SERVER_INFO,
      });

    case 'notifications/initialized':
      // 通知无 id、无响应。
      return null;

    case 'tools/list': {
      const descriptors = (await client.get(TOOLS_CATALOG_PATH)) || [];
      return ok(id, { tools: descriptors.map(toMcpTool) });
    }

    case 'tools/call': {
      const name = params && params.name;
      const args = (params && params.arguments) || {};
      if (!name) {
        return fail(id, -32602, '缺少工具名 name');
      }
      try {
        const envelope = await client.executeTool(name, args);
        return ok(id, { content: [{ type: 'text', text: JSON.stringify(envelope) }] });
      } catch (toolError) {
        // 工具执行失败：MCP 约定以 isError 结果返回结构化原因（不泄漏内部异常/SQL）。
        return ok(id, { content: [{ type: 'text', text: toolError.message }], isError: true });
      }
    }

    default:
      return fail(id, -32601, `不支持的方法：${method}`);
  }
}

function toMcpTool(descriptor) {
  return {
    name: descriptor.name,
    description: `${descriptor.purpose}（数据级别 ${descriptor.dataLevel}，需权限 ${descriptor.requiredPermission}）`,
    inputSchema: {
      type: 'object',
      properties: {
        purpose: { type: 'string', description: '调用用途（必填，进后端审计）' },
        target: {
          type: 'string',
          description: '目标标识（单对象工具用，如规则ID/知识编码/数据分级/临床 launch 令牌）',
        },
        page: { type: 'integer', description: '分页页码（读模型类工具用）' },
        size: { type: 'integer', description: '分页大小（读模型类工具用）' },
        payload: { type: 'object', description: '结构化工具载荷（写回类工具用，如 submitProductionCandidate）' },
      },
      required: ['purpose'],
    },
  };
}

function ok(id, result) {
  return { jsonrpc: '2.0', id, result };
}

function fail(id, code, message) {
  return { jsonrpc: '2.0', id, error: { code, message } };
}
