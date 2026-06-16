#!/usr/bin/env bash
# Seahorse Agent 文档过期引用检测
# 用于 CI 或手动检查，扫描 docs/ 目录中的已知过期引用模式。
#
# 用法: ./scripts/check-doc-staleness.sh
# 退出码: 0=无过期引用 1=发现过期引用

set -euo pipefail

DOCS_DIR="docs"
FOUND=0

echo "扫描 ${DOCS_DIR}/ 中的过期引用..."

# 模式 1: 旧 compose 文件引用
echo ""
echo "[检查 1] 旧 docker-compose 子文件引用"
if rg -n "file://resources/docker" "$DOCS_DIR" --glob "*.md" --glob "!docs/architecture/current-code-architecture.md" --glob "!docs/zh/content/_ARCHIVED_NOTICE.md" 2>/dev/null; then
  echo "  ⚠ 发现旧 compose 子文件引用"
  FOUND=1
else
  echo "  ✓ 无旧 compose 子文件引用"
fi

# 模式 2: 旧 quick-start 引用
echo ""
echo "[检查 2] 旧 quick-start 引用"
if rg -n "file://docs/quick-start" "$DOCS_DIR" --glob "*.md" --glob "!docs/zh/content/_ARCHIVED_NOTICE.md" 2>/dev/null; then
  echo "  ⚠ 发现旧 quick-start 引用"
  FOUND=1
else
  echo "  ✓ 无旧 quick-start 引用"
fi

# 模式 3: 旧模型名引用 (qwen-plus, qwen-emb-8b)
echo ""
echo "[检查 3] 旧模型名引用 (qwen-plus, qwen-emb-8b)"
if rg -n "qwen-(plus|emb-8b)" "$DOCS_DIR" --glob "*.md" --glob "!docs/zh/content/_ARCHIVED_NOTICE.md" 2>/dev/null; then
  echo "  ⚠ 发现旧模型名引用"
  FOUND=1
else
  echo "  ✓ 无旧模型名引用"
fi

# 模式 4: 旧 /admin/traces 路径（排除前端路由说明和归档声明）
echo ""
echo "[检查 4] 后端 Trace 路径 /admin/traces（不含前端路由说明）"
# 这里只检查架构文档和路线图中是否还有错误引用
if rg -n "/admin/traces" docs/architecture docs/roadmap 2>/dev/null; then
  echo "  ⚠ 架构/路线图文档中仍有 /admin/traces 后端路径引用"
  FOUND=1
else
  echo "  ✓ 架构/路线图文档中无 /admin/traces 引用"
fi

# 模式 5: MILVUS_DIMENSION=1024 残留
echo ""
echo "[检查 5] .env 中 MILVUS_DIMENSION=1024 残留"
if grep -n "MILVUS_DIMENSION=1024" .env 2>/dev/null; then
  echo "  ⚠ .env 中仍有 MILVUS_DIMENSION=1024"
  FOUND=1
else
  echo "  ✓ .env 中无 MILVUS_DIMENSION=1024"
fi

# 汇总
echo ""
echo "========================================"
if [[ "$FOUND" -eq 1 ]]; then
  echo "  ⚠ 发现过期引用，请修正后重新提交"
  exit 1
else
  echo "  ✓ 无过期引用"
  exit 0
fi
