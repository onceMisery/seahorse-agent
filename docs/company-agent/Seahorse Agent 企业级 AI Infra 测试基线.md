# Seahorse Agent 企业级 AI Infra 测试基线

本文档记录 AI Infra Phase 0/1 开始执行时的推荐验证命令和已知阻断。

## 推荐命令

后端 kernel 快速验证：

```powershell
.\mvnw -pl seahorse-agent-kernel -am test
```

Phase 1 kernel 行为验证：

```powershell
.\mvnw -pl seahorse-agent-tests -am '-Dtest=KernelAgentDefinitionServiceTests,KernelAgentRunServiceTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

后续 JDBC 切片验证：

```powershell
.\mvnw -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=*Agent*Repository*Test' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

完整集成验证：

```powershell
.\mvnw -pl seahorse-agent-tests -am test
```

前端构建：

```powershell
cd frontend
npm run build
```

前端 lint：

```powershell
cd frontend
npm run lint
```

## 当前已知问题

- `npm run lint` 当前被既有 ESLint 配置阻断：`plugin:react-refresh/recommended` 暴露了 ESLint 8 不接受的顶层 `name` 属性。
- AI Infra 原型页已通过 `npm run build` 和公开路由 HTTP 200 验证。

## Phase 0/1 验收关注点

- 架构基线文档可通过 `rg -n "Agent Runtime|Tool Gateway|ContextPack|Agent Identity" docs/company-agent` 查到关键术语。
- 新增 Agent 代码位于固定包路径。
- Phase 1 kernel 测试覆盖 draft 创建、发布版本不可变、非 admin 拒绝、disabled agent 禁止 run、cancel 幂等。
