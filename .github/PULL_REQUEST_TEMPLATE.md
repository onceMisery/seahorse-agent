## 变更说明

<!-- 简要描述本 PR 解决的问题和所做的变更 -->

## 变更类型

- [ ] 新功能 / Bug 修复
- [ ] 重构 / 性能优化
- [ ] 文档更新
- [ ] 基础设施 / 配置变更

## 文档同步检查

在提交前请确认以下检查项：

- [ ] **Controller 路径变更**：已同步更新 `docs/architecture/current-code-architecture.md` Section 4 的 API 路径表
- [ ] **新增端点**：已检查 `docs/TROUBLESHOOTING_GUIDE.md` 是否需要补充对应端点的排障说明
- [ ] **`.env` 变量变更**：已同步更新 `.env.example` 和 `.env.full.example`
- [ ] **前端路由变更**：已检查 `docs/deployment/enterprise-mode.md` 页面路径表是否需要同步
- [ ] **Feature Gate 变更**：已确认新增/删除的 feature gate 在 `.env` 和 `.env.full.example` 中一致

## 测试验证

- [ ] 单元测试通过
- [ ] 本地 Docker 环境验证（如适用）
- [ ] 运行 `./scripts/check-doc-staleness.sh` 无过期引用

## 影响范围

- 受影响模块：
- 是否需要数据库迁移：
- 是否需要前端重新构建：
