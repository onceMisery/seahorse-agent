#!/bin/bash
# Task 1-12 完成情况快速检查

echo "=== DeerFlow Plan Task 1-12 完成情况 ==="
echo

echo "Task 1: Stream Events绑定"
test -f frontend/src/stores/chatStreamHandlers.ts && echo "✅ chatStreamHandlers.ts 存在" || echo "❌ 缺失"
grep -q "applyAgentStreamEventToMessage" frontend/src/stores/chatStreamHandlers.ts 2>/dev/null && echo "✅ 事件处理函数已实现" || echo "❌ 未实现"
echo

echo "Task 2: Encoding Guard"
cd frontend && npm run build >/dev/null 2>&1 && echo "✅ 前端构建成功" || echo "❌ 构建失败"
cd ..
echo

echo "Task 3: Snapshot Hydration"
grep -q "applyAgentRunSnapshotToMessage" frontend/src/stores/chatStreamHandlers.ts 2>/dev/null && echo "✅ snapshot处理函数已实现" || echo "❌ 未实现"
grep -q "timeline.*snapshotTimeline" frontend/src/stores/chatStreamHandlers.ts 2>/dev/null && echo "✅ timeline hydration 已实现" || echo "❌ 未实现"
grep -q "sources.*snapshotSources" frontend/src/stores/chatStreamHandlers.ts 2>/dev/null && echo "✅ sources hydration 已实现" || echo "❌ 未实现"
echo

echo "Task 4: Artifact Workspace"
ls seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/agent/tool/*Generation*.java >/dev/null 2>&1 && echo "✅ Generation tools 存在" || echo "❌ 缺失"
test -f frontend/src/components/chat/workbench/ArtifactInspectorTab.tsx && echo "✅ ArtifactInspectorTab 存在" || echo "❌ 缺失"
echo

echo "Task 5: Artifact Preview"
grep -q "download\|preview" frontend/src/components/chat/workbench/ArtifactInspectorTab.tsx 2>/dev/null && echo "✅ 预览/下载功能存在" || echo "⚠️ 待确认"
echo

echo "Task 6: Skill Surface"
test -f frontend/src/components/chat/SkillTrigger.tsx && echo "✅ SkillTrigger 存在" || echo "❌ 缺失"
ls seahorse-agent-spring-boot-autoconfigure/src/main/resources/skills/public/*.json 2>/dev/null | wc -l | xargs -I {} echo "✅ {} 个public skills"
echo

echo "Task 7: Tool Catalog"
test -f seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseToolCatalogController.java && echo "✅ ToolCatalogController 存在" || echo "❌ 缺失"
echo

echo "Task 8: Cost Visibility"
grep -q "costSummary\|AgentRunCost" frontend/src/stores/chatStore.ts 2>/dev/null && echo "✅ cost字段存在" || echo "❌ 缺失"
test -f seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseAgentRunController.java && echo "✅ AgentRunController 存在" || echo "❌ 缺失"
echo

echo "Task 9: Approval Flow"
grep -q "approval" frontend/src/stores/chatStreamHandlers.ts 2>/dev/null && echo "✅ approval处理已实现" || echo "❌ 未实现"
echo

echo "Task 10: Event Timeline"
grep -q "timeline" frontend/src/stores/chatStreamHandlers.ts 2>/dev/null && echo "✅ timeline处理已实现" || echo "❌ 未实现"
test -f frontend/src/components/chat/workbench/TimelineTab.tsx && echo "✅ TimelineTab 存在" || echo "⚠️ 待确认组件名"
echo

echo "Task 11: Admin Event Replay"
test -f seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseAgentRunController.java && echo "✅ 事件查询API存在" || echo "❌ 缺失"
echo

echo "Task 12: deer-flow Reference"
test -f docs/aegis/plans/2026-06-08-seahorse-deerflow-web-alignment-plan.md && echo "✅ DeerFlow计划文档存在" || echo "❌ 缺失"
echo

echo "=== 总体评估 ==="
echo "快速检查完成。详细验证需要运行测试和手动检查功能。"
