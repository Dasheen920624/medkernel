/**
 * CLI 后端 API 客户端（DATASVC-01 FR-3/5/7）。
 *
 * 薄 HTTP 客户端：携带 Bearer 鉴权令牌调用后端受控合同，**不直连库、不绕治理**；
 * 解析 {@code ApiResult} 成功信封取 data，错误走 RFC 7807 ProblemDetail 转结构化 {@link CliApiError}
 * （不泄漏内部）；后端不可达诚实报「不可达」不伪造数据（铁律 #1）。
 */

export class CliApiError extends Error {
  constructor(message, { code = null, status = null } = {}) {
    super(message);
    this.name = 'CliApiError';
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
      // 后端不可达：诚实报错，不伪造数据（铁律 #1 / FR-7 CLI 降级）。
      throw new CliApiError('后端不可达，未返回数据（请检查 MEDKERNEL_API_BASE 与网络连通）', {
        code: 'CLI-NET-001',
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
      // RFC 7807 ProblemDetail：detail/title + 可选业务错误码；不泄漏内部异常/SQL。
      const detail =
        (parsed && (parsed.detail || parsed.message || parsed.title)) || `请求失败（HTTP ${res.status}）`;
      const code = (parsed && (parsed.code || parsed.errorCode)) || null;
      throw new CliApiError(detail, { code, status: res.status });
    }

    // ApiResult 成功信封取 data；非信封响应原样返回。
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
