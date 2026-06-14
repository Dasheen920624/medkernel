/**
 * MCP 服务后端 API 客户端（DATASVC-01 FR-4/5/7）。
 *
 * 薄 HTTP 客户端：携带 Bearer 令牌调后端受控工具合同，**不直连库、不绕治理**；解析 {@code ApiResult}
 * 取 data，错误走 RFC 7807 ProblemDetail 转结构化 {@link McpApiError} 不泄漏内部；后端不可达诚实报错
 * 不伪造数据（铁律 #1）。
 */

export class McpApiError extends Error {
  constructor(message, { code = null, status = null } = {}) {
    super(message);
    this.name = 'McpApiError';
    this.code = code;
    this.status = status;
  }
}

export function createClient({ baseUrl, token, fetchImpl = fetch } = {}) {
  async function request(method, path, body) {
    let res;
    try {
      res = await fetchImpl(baseUrl + path, {
        method,
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json',
          Accept: 'application/json',
        },
        body: body == null ? undefined : JSON.stringify(body),
      });
    } catch {
      throw new McpApiError('后端不可达，未返回数据（请检查 MEDKERNEL_API_BASE 与网络连通）', {
        code: 'MCP-NET-001',
      });
    }

    const text = await res.text();
    let parsed = null;
    if (text) {
      try {
        parsed = JSON.parse(text);
      } catch {
        parsed = null;
      }
    }

    if (!res.ok) {
      const detail =
        (parsed && (parsed.detail || parsed.message || parsed.title)) || `请求失败（HTTP ${res.status}）`;
      const code = (parsed && (parsed.code || parsed.errorCode)) || null;
      throw new McpApiError(detail, { code, status: res.status });
    }

    if (parsed && Object.prototype.hasOwnProperty.call(parsed, 'data')) {
      return parsed.data;
    }
    return parsed;
  }

  return {
    get: (path) => request('GET', path),
    executeTool: (toolName, body) =>
      request('POST', `/api/v1/engine-data/tools/${encodeURIComponent(toolName)}:execute`, body),
  };
}
