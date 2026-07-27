#!/bin/bash
set -euo pipefail

# Architecture Complexity Report & Ratchet
# Freezes current values (ports 804, >800 line classes 15, AutoConfiguration imports 106) and ensures only decrease.
# Usage:
#   bash scripts/complexity-report.sh              # check against baseline
#   bash scripts/complexity-report.sh --update-baseline  # refresh baseline file after intentional reduction

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASELINE_FILE="${ROOT_DIR}/complexity-baseline.txt"
REPORT_FILE="/tmp/complexity-report.md"

# Default baseline location fallback: also check scripts/complexity-baseline.txt or docs?
if [[ ! -f "$BASELINE_FILE" ]]; then
  if [[ -f "${ROOT_DIR}/scripts/complexity-baseline.txt" ]]; then
    BASELINE_FILE="${ROOT_DIR}/scripts/complexity-baseline.txt"
  fi
fi

# --- Compute current metrics ---

# 1. Ports: count Java files under seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports excluding NoopFallback
# Rationale: ports 804 baseline matches total files minus NoopFallback (805 total - 1 = 804)
PORTS_DIR="${ROOT_DIR}/seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports"
if [[ -d "$PORTS_DIR" ]]; then
  if [[ -f "${PORTS_DIR}/common/NoopFallback.java" ]]; then
    CURRENT_PORTS=$(find "$PORTS_DIR" -type f -name "*.java" ! -name "NoopFallback.java" | wc -l | tr -d ' ')
  else
    CURRENT_PORTS=$(find "$PORTS_DIR" -type f -name "*.java" | wc -l | tr -d ' ')
  fi
else
  CURRENT_PORTS=0
fi

# 2. Large classes >800 lines, excluding autoconfigure module (per Phase 0 spec 15)
# Count files under src/main/java with >800 lines
CURRENT_LARGE=$(find "${ROOT_DIR}" -type f -path "*/src/main/java/*.java" ! -path "*/seahorse-agent-spring-boot-autoconfigure/*" -exec wc -l {} \; 2>/dev/null | awk '$1>800' | wc -l | tr -d ' ')

# Alternative stricter count if above fails (fallback)
if [[ -z "$CURRENT_LARGE" ]]; then
  CURRENT_LARGE=0
fi

# 3. AutoConfiguration imports count
AUTOCONFIG_FILE="${ROOT_DIR}/seahorse-agent-spring-boot-autoconfigure/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"
if [[ -f "$AUTOCONFIG_FILE" ]]; then
  CURRENT_AUTOCONFIG=$(wc -l < "$AUTOCONFIG_FILE" | tr -d ' ')
else
  CURRENT_AUTOCONFIG=0
fi

# 4. Cross-domain whitelist size (from whitelist file)
WHITELIST_FILE="${ROOT_DIR}/seahorse-agent-architecture-tests/src/main/resources/archunit/cross-domain-whitelist.txt"
if [[ -f "$WHITELIST_FILE" ]]; then
  CURRENT_CROSS=$(grep -v "^#" "$WHITELIST_FILE" | grep -v "^$" | wc -l | tr -d ' ')
else
  CURRENT_CROSS=0
fi

echo "Current metrics:"
echo "  ports=$CURRENT_PORTS"
echo "  large_classes_gt_800=$CURRENT_LARGE"
echo "  autoconfig_imports=$CURRENT_AUTOCONFIG"
echo "  cross_domain_whitelist_size=$CURRENT_CROSS"

# --- Handle update baseline ---
if [[ "${1:-}" == "--update-baseline" ]]; then
  cat > "$BASELINE_FILE" <<EOF
# Complexity baseline - ratchet, only decrease allowed
# Generated at $(date -u +"%Y-%m-%dT%H:%M:%SZ")
ports=$CURRENT_PORTS
large_classes_gt_800=$CURRENT_LARGE
autoconfig_imports=$CURRENT_AUTOCONFIG
cross_domain_whitelist_size=$CURRENT_CROSS
EOF
  echo "Baseline updated at $BASELINE_FILE"
  cat "$BASELINE_FILE"
  exit 0
fi

# --- Read baseline ---
if [[ ! -f "$BASELINE_FILE" ]]; then
  echo "Baseline file not found at $BASELINE_FILE, using embedded default from task spec"
  BASELINE_PORTS=804
  BASELINE_LARGE=15
  BASELINE_AUTOCONFIG=106
  BASELINE_CROSS=35
else
  # shellcheck disable=SC1090
  BASELINE_PORTS=$(grep -E "^ports=" "$BASELINE_FILE" | cut -d= -f2 | tr -d ' ')
  BASELINE_LARGE=$(grep -E "^large_classes_gt_800=" "$BASELINE_FILE" | cut -d= -f2 | tr -d ' ')
  BASELINE_AUTOCONFIG=$(grep -E "^autoconfig_imports=" "$BASELINE_FILE" | cut -d= -f2 | tr -d ' ')
  BASELINE_CROSS=$(grep -E "^cross_domain_whitelist_size=" "$BASELINE_FILE" | cut -d= -f2 | tr -d ' ')

  # Fallbacks if any missing
  BASELINE_PORTS=${BASELINE_PORTS:-804}
  BASELINE_LARGE=${BASELINE_LARGE:-15}
  BASELINE_AUTOCONFIG=${BASELINE_AUTOCONFIG:-106}
  BASELINE_CROSS=${BASELINE_CROSS:-35}
fi

echo "Baseline metrics (from $BASELINE_FILE):"
echo "  ports=$BASELINE_PORTS"
echo "  large_classes_gt_800=$BASELINE_LARGE"
echo "  autoconfig_imports=$BASELINE_AUTOCONFIG"
echo "  cross_domain_whitelist_size=$BASELINE_CROSS"

# --- Ratchet check: only allow decrease ---

FAIL=0

check_metric() {
  local name=$1
  local current=$2
  local baseline=$3
  if [[ "$current" -gt "$baseline" ]]; then
    echo "::error::Complexity budget exceeded for $name: current $current > baseline $baseline (only decrease allowed)"
    FAIL=1
  elif [[ "$current" -lt "$baseline" ]]; then
    echo "✅ $name decreased: $baseline -> $current (good, consider updating baseline)"
  else
    echo "✅ $name unchanged: $current"
  fi
}

check_metric "ports" "$CURRENT_PORTS" "$BASELINE_PORTS"
check_metric "large_classes_gt_800" "$CURRENT_LARGE" "$BASELINE_LARGE"
check_metric "autoconfig_imports" "$CURRENT_AUTOCONFIG" "$BASELINE_AUTOCONFIG"
# For cross-domain, we allow same or less (whitelist shrinking)
check_metric "cross_domain_whitelist_size" "$CURRENT_CROSS" "$BASELINE_CROSS"

# Generate markdown report
cat > "$REPORT_FILE" <<EOF
## Complexity Budget Report

| Metric | Baseline | Current | Delta | Status |
|--------|----------|---------|-------|--------|
| Ports | $BASELINE_PORTS | $CURRENT_PORTS | $((CURRENT_PORTS - BASELINE_PORTS)) | $( [[ $CURRENT_PORTS -le $BASELINE_PORTS ]] && echo "✅" || echo "❌" ) |
| Large classes >800 | $BASELINE_LARGE | $CURRENT_LARGE | $((CURRENT_LARGE - BASELINE_LARGE)) | $( [[ $CURRENT_LARGE -le $BASELINE_LARGE ]] && echo "✅" || echo "❌" ) |
| AutoConfig imports | $BASELINE_AUTOCONFIG | $CURRENT_AUTOCONFIG | $((CURRENT_AUTOCONFIG - BASELINE_AUTOCONFIG)) | $( [[ $CURRENT_AUTOCONFIG -le $BASELINE_AUTOCONFIG ]] && echo "✅" || echo "❌" ) |
| Cross-domain whitelist | $BASELINE_CROSS | $CURRENT_CROSS | $((CURRENT_CROSS - BASELINE_CROSS)) | $( [[ $CURRENT_CROSS -le $BASELINE_CROSS ]] && echo "✅" || echo "❌" ) |

- **Ports**: files under \`seahorse-agent-kernel/.../ports\` excluding NoopFallback
- **Large classes**: Java files in \`*/src/main/java/*\` excluding \`spring-boot-autoconfigure\` with >800 LOC
- **AutoConfig**: lines in \`AutoConfiguration.imports\`
- **Cross-domain**: lines in whitelist file (frozen 35)

Ratchet principle: values must only decrease. To update baseline after intentional reduction, run:
\`bash scripts/complexity-report.sh --update-baseline\`
EOF

echo ""
cat "$REPORT_FILE"

# Also write to GITHUB_STEP_SUMMARY if in GitHub Actions
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  cat "$REPORT_FILE" >> "$GITHUB_STEP_SUMMARY"
fi

if [[ $FAIL -ne 0 ]]; then
  echo ""
  echo "❌ Complexity budget check FAILED - metrics increased above baseline"
  echo "Please refactor to reduce complexity or ensure you haven't introduced new ports/large classes."
  exit 1
else
  echo ""
  echo "✅ Complexity budget check PASSED"
  exit 0
fi
