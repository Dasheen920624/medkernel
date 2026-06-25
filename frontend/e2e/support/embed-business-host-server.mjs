import { createServer } from "node:http";

import { resolveEmbedAppBase, resolveEmbedOrigin } from "./embedBaseUrl.mjs";

const host = "127.0.0.1";
const port = 4174;
const embedAppBase = resolveEmbedAppBase(process.env.E2E_BASE_URL);
const embedOrigin = resolveEmbedOrigin(embedAppBase);

const html = `<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>院内业务系统工作站</title>
    <style>
      * { box-sizing: border-box; }
      body { margin: 0; font-family: Arial, sans-serif; color: #17202a; background: #f4f6f8; }
      header { padding: 16px 24px; color: #fff; background: #24503f; }
      h1 { margin: 0; font-size: 22px; }
      main { display: grid; grid-template-columns: minmax(280px, 360px) minmax(0, 1fr); gap: 16px; padding: 16px; }
      section { padding: 16px; border: 1px solid #d7dde3; border-radius: 8px; background: #fff; }
      dl { display: grid; grid-template-columns: 90px 1fr; gap: 8px; margin: 16px 0 0; }
      dt { color: #5d6d7e; }
      dd { margin: 0; }
      iframe { width: 100%; min-height: 720px; border: 1px solid #b8c2cc; border-radius: 6px; background: #fff; }
      #host-feedback { margin-top: 16px; padding: 12px; border-left: 4px solid #1f8f5f; background: #edf8f2; white-space: pre-wrap; }
      @media (max-width: 760px) { main { grid-template-columns: 1fr; } }
    </style>
  </head>
  <body>
    <header><h1>院内业务系统工作站</h1></header>
    <main>
      <section aria-label="当前患者">
        <strong>检验结果复核</strong>
        <dl>
          <dt>患者</dt><dd>MPI-E2E-001</dd>
          <dt>就诊</dt><dd>ENC-E2E-001</dd>
          <dt>血清钾</dt><dd>6.8 mmol/L</dd>
        </dl>
        <div id="host-feedback" data-testid="host-feedback">等待 MedKernel 医师反馈</div>
      </section>
      <section aria-label="临床建议">
        <iframe
          id="medkernel-frame"
          title="MedKernel 临床建议"
          src="${embedAppBase}/embed/launch?token=host-e2e-token"
          sandbox="allow-forms allow-same-origin allow-scripts"
        ></iframe>
      </section>
    </main>
    <script>
      const frame = document.getElementById("medkernel-frame");
      const feedback = document.getElementById("host-feedback");
      window.addEventListener("message", (event) => {
        if (event.origin !== "${embedOrigin}") return;
        if (event.source !== frame.contentWindow) return;
        if (event.data?.source !== "MEDKERNEL_CDSS_EMBED") return;
        feedback.textContent = JSON.stringify(event.data, null, 2);
      });
    </script>
  </body>
</html>`;

const server = createServer((request, response) => {
  if (request.url !== "/") {
    response.writeHead(404, { "Content-Type": "text/plain; charset=utf-8" });
    response.end("Not Found");
    return;
  }
  response.writeHead(200, {
    "Content-Type": "text/html; charset=utf-8",
    "Cache-Control": "no-store",
  });
  response.end(html);
});

server.listen(port, host);

for (const signal of ["SIGINT", "SIGTERM"]) {
  process.on(signal, () => server.close(() => process.exit(0)));
}
