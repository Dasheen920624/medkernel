# 推荐中枢 134 复验

> 日期：2026-06-11
>
> 环境：`https://193.112.107.134`
>
> 范围：`OPT-IA-01` / `OPT-TRACE-01` 第一批体验实现复验

## 结论

`/cdss/fatigue` 已在 134 前端发布后复验通过。医生账号可在桌面和 390px 移动视口看到「提醒与推荐中枢」、推荐链路总览、患者 / traceId / 来源对象检索、统计卡和真实推荐列表；桌面端可打开推荐详情抽屉并看到七段链路（触发事件、命中规则、知识来源、路径上下文、待办 / 通知、医生反馈、药师复核）。本轮页面级横向溢出为 0。

## 发布与回滚证据

- 发布来源：`codex-demo-drill-recommendation-hub-17f4cc4d`
- 远端备份：`/zoesoft/medkernel/backups/deploy-20260611-154250`
- 健康检查：`/medkernel/actuator/health/readiness` 返回 `UP`
- 复验脚本：`node scripts/drill/recommendation-hub-ui-proof.mjs`
- 复验摘要：[00-recommendation-hub-134-proof.json](00-recommendation-hub-134-proof.json)

## 截图索引

| 文件                                                                         | 说明                                                       |
| ---------------------------------------------------------------------------- | ---------------------------------------------------------- |
| [01-desktop-recommendation-hub.png](01-desktop-recommendation-hub.png)       | 医生桌面端查看推荐中枢、链路总览、筛选区、统计卡和推荐列表 |
| [02-desktop-recommendation-drawer.png](02-desktop-recommendation-drawer.png) | 医生打开推荐详情抽屉，查看七段推荐来源链路                 |
| [03-mobile-recommendation-hub.png](03-mobile-recommendation-hub.png)         | 390px 移动视口首屏查看推荐中枢与链路节点                   |
| [04-mobile-recommendation-search.png](04-mobile-recommendation-search.png)   | 390px 移动视口滚动到筛选区，确认检索控件单列可用           |
