# C1 前端底座与视觉债 · 深度审计报告

> 审计日期：2026-05-29 · 审计人：Claude · backlog：BASE-06/08/10 = done
> **审计结论：⚠️ 底座真实但防造假门禁失效（系统性根因）。1H / 2M。**

## 概览
`frontend/src/app`(路由/PageShell/六态/状态机 Badge) + `shared/ui`(MetricGrid/分页/详情抽屉/导出/保存视图) + `shared/config`(路由元数据) + `eslint-rules/no-page-mock.js`。

## 十维度要点
- **业务正确性 ✅**：七业务域菜单、路由元数据单一源、PageShell、六态、状态机 Badge、7 步流组件、服务端分页底座真实（BASE-06/08）。
- **视觉债 ✅ 基本达标**：设计 token、CSS Module（各区 *.module.css），生产代码无硬编码颜色/inline style 的门禁存在（BASE-10）。
- **代码净化门禁 🔴 C1-H-01（系统性根因 R1）**：`eslint-rules/no-page-mock.js` 仅拦 SHOUTY_CASE 命名的对象数组，**camelCase 一律放行**；且实际有 **13 个页面直接 `/* eslint-disable medkernel/no-page-mock */`** 整文件关闭。导致 A7/A10/A12/A14/A15 等前端假闭环全面回潮（真实性审计 R1 未根治）。
- **可观测性/测试 🟡 C1-M-01**：前端无前端错误监控；测试见 D2（仅冒烟）。

## Findings
| Sev | ID | 一句话 | 位置 |
|---|---|---|---|
| High | C1-H-01 | no-page-mock 门禁可被 camelCase + eslint-disable 绕过（13 页已关） | `eslint-rules/no-page-mock.js` |
| Medium | C1-M-01 | 缺前端运行时错误监控 | app |
| Medium | C1-M-02 | 13 页 eslint-disable 清单需逐页清理 | 见 C2/C3/C6 |

合计：C0 H1 M2 L0

## 改造（系统性，优先）
- **C1-H-01**：重写 no-page-mock——检测"页面内大体量对象数组字面量""catch 块内 message.success/伪造成功""`api?.length>0 ? api : 写死`兜底"，camelCase 同样拦截；禁止 `eslint-disable` 本规则（用 lint config 锁定）。加门禁后会爆出 13 页存量假闭环，逐页改六态 + 真接口。约 1.5 天（含逐页整改协调）。

## 总评
底座（路由/六态/状态机/分页/视觉 token）**名副其实**；但 **no-page-mock 门禁失效是前端假闭环系统性回潮的根因**，应作为前端整改的第一优先（先修门禁，再逐页清理）。
