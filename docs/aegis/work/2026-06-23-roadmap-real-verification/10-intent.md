# Roadmap Real Verification Intent

## Requested Outcome

根据 `docs/roadmap/architecture-roadmap-and-vision.md` 和 `docs/analysis/roadmap-completion-status-report.md`，把已经开发但仍缺真实运行证据的能力逐项走真实案例验证，优先稳定近期功能。

## Scope

- 真实 Docker / API / 页面验证优先于纯单元测试。
- 覆盖路线图“真实 Test Case 门禁”里的已合入能力：消息树、角色卡、运行方案、运行实验、AgentScope、MCP stdio/HTTP、OpenAPI/A2A/内置工具目录、治理后台、Docker 本地验证链路。
- 覆盖完成情况报告里明确标注“运行层面需实际验证”的近期/中期能力：RAG 冒烟、记忆画像、RAG 评测、记忆治理、full compose smoke。
- 验证时发现真实缺陷则做最小修复，并用同一真实链路复测。

## Non-Goals

- 不宣称整个路线图完成。
- 不为了验证而清空 Docker 数据卷或破坏用户已有数据。
- UI 和简单通路不强行 TDD；复杂后端缺陷修复再补必要的 focused regression。
- 不把“接口存在”“单测存在”当成真实验收完成。

## Baseline Read Set

- `docs/roadmap/architecture-roadmap-and-vision.md`
- `docs/analysis/roadmap-completion-status-report.md`
- `docs/aegis/work/2026-06-15-architecture-roadmap-implementation/20-checkpoint.md`
- `docs/aegis/work/2026-06-15-architecture-roadmap-implementation/90-evidence.md`

## Stop Condition

- `done`: 文档列出的所有已开发但未真实验证功能都有当前环境下的真实运行证据，或明确被降级为未实现/待产品化并写入剩余风险。
- `needs-verification`: 已修复或已有脚本，但尚未在当前 Docker/API/页面环境得到证据。
- `blocked`: 同一外部依赖或环境问题连续阻断三轮，且无法继续从其他功能域取得有效进展。
- `scope-exceeded`: 继续推进会变成实现未开发的大型路线图能力，而不是验证已开发能力。
