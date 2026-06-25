# Evidence

## Baseline Evidence

- `docs/roadmap/architecture-roadmap-and-vision.md` 明确把已合入但缺真实测试的能力放入“真实 Test Case 门禁”。
- `docs/analysis/roadmap-completion-status-report.md` 明确当前状态是代码基础设施基本就位，但 RAG、记忆、RAG 评测、full compose 等运行层面仍需补实际验证。
- `docs/aegis/work/2026-06-15-architecture-roadmap-implementation/20-checkpoint.md` 和 `90-evidence.md` 记录了历史 full compose smoke、RAG/记忆证据、以及仍未覆盖的真实 UI/运行链路边界。

## Role Card Runtime Evidence

- Root cause: 当前 Docker 旧数据库卷没有 `asset_source='SYSTEM'` 的系统角色卡；初始化 SQL 只在数据库首次创建时执行，启动升级器原本只补列不补系统预置数据。
- Repair: `JdbcChatSchemaUpgrade` 启动时幂等补齐 7 张系统角色卡和 5 个系统运行方案。
- Build/deploy: `.\mvnw.cmd package -B -pl seahorse-agent-bootstrap -am -DskipTests '-Dmaven.test.skip=true' '-Dspotless.check.skip=true'` 通过；新的 backend jar 已复制到 `seahorse-backend:/app/app.jar` 并重启。
- Health: `GET http://127.0.0.1:9090/actuator/health` 返回 `{"status":"UP"}`。
- DB evidence: `sa_role_card` 中系统角色卡为 7；`sa_run_profile` 中系统运行方案为 5。
- API evidence: 登录后 `GET http://127.0.0.1/api/role-cards` 返回 7 张角色卡：通用助手、需求分析师、代码开发助手、测试质量审查、文档知识库助手、数据分析助手、AgentScope 调试助手。
- Browser evidence: Playwright 真实浏览器脚本打开 `/admin/role-cards` 能看到系统角色卡，打开 `/chat` 后能在 Role card 下拉选择“通用助手”。
- Screenshot evidence:
  - `output/playwright/role-card-admin-page.png`
  - `output/playwright/role-card-chat-select.png`

## Verification Commands Already Run

```powershell
.\mvnw.cmd -B -pl seahorse-agent-adapter-repository-jdbc -DskipTests '-Dmaven.test.skip=true' '-Dspotless.check.skip=true' compile
```

Result: build success.

```powershell
npx --yes -p @playwright/test playwright test -c output/playwright/playwright.config.js --reporter=line
```

Result: 1 passed, covering system role cards visible and selectable.

## Evidence Gap

- Role card context influence on actual model response and `t_run_context_snapshot` remains unproven.
- Run profile inheritance in `/chat`, message branch/fork restoration, run experiment trial execution, RAG evaluation report, MCP stdio example, AgentScope failure isolation, and governance backend error/permission states remain open.
