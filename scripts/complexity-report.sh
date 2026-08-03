#!/usr/bin/env bash
set -euo pipefail

# Report Phase 0 architecture metrics and reject any drift above the reviewed baseline.
# Usage:
#   bash scripts/complexity-report.sh
#   bash scripts/complexity-report.sh --update-baseline

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASELINE_FILE="${ROOT_DIR}/complexity-baseline.txt"
REPORT_FILE="${TMPDIR:-/tmp}/seahorse-complexity-report.md"

if [[ ! -f "$BASELINE_FILE" ]]; then
  echo "::error::Complexity baseline not found: $BASELINE_FILE"
  exit 2
fi

PORTS_DIR="${ROOT_DIR}/seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports"
# Reporting fallback only. The blocking Port definition and count are owned by
# PortArchitectureTest, which scans compiled public interfaces with ArchUnit.
CURRENT_PORTS=$(grep -R -l -E '^[[:space:]]*public[[:space:]]+interface[[:space:]]+' "$PORTS_DIR" --include='*.java' | wc -l | tr -d ' ')
CURRENT_PORT_INBOUND=$(grep -R -l -E '^[[:space:]]*public[[:space:]]+interface[[:space:]]+' "$PORTS_DIR/inbound" --include='*.java' | wc -l | tr -d ' ')
CURRENT_PORT_OUTBOUND=$(grep -R -l -E '^[[:space:]]*public[[:space:]]+interface[[:space:]]+' "$PORTS_DIR/outbound" --include='*.java' | wc -l | tr -d ' ')
CURRENT_PORT_COMMON=$((CURRENT_PORTS - CURRENT_PORT_INBOUND - CURRENT_PORT_OUTBOUND))
CURRENT_PORT_FILES=$(find "$PORTS_DIR" -type f -name "*.java" | wc -l | tr -d ' ')

CURRENT_LARGE=$(find "$ROOT_DIR" \
  -path "${ROOT_DIR}/.git" -prune -o \
  -path "${ROOT_DIR}/.worktrees" -prune -o \
  -path "*/target" -prune -o \
  -type f -path "*/src/main/java/*.java" \
  ! -path "*/seahorse-agent-spring-boot-autoconfigure/*" \
  -exec wc -l {} \; 2>/dev/null \
  | awk '$1 > 800 { count++ } END { print count + 0 }')

AUTOCONFIG_FILE="${ROOT_DIR}/seahorse-agent-spring-boot-autoconfigure/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"
CURRENT_AUTOCONFIG=$(awk '{ sub(/\r$/, "") } !/^[[:space:]]*#/ && NF { count++ } END { print count + 0 }' "$AUTOCONFIG_FILE")

WHITELIST_FILE="${ROOT_DIR}/seahorse-agent-architecture-tests/src/main/resources/archunit/cross-domain-whitelist.txt"
CURRENT_CROSS=$(awk '{ sub(/\r$/, "") } !/^[[:space:]]*#/ && NF { count++ } END { print count + 0 }' "$WHITELIST_FILE")

if [[ "${1:-}" == "--update-baseline" ]]; then
  cat > "$BASELINE_FILE" <<EOF
# Complexity baseline captured from the current reviewed source tree.
# Ratchet policy: values may only decrease.
port_interfaces=$CURRENT_PORTS
port_inbound=$CURRENT_PORT_INBOUND
port_outbound=$CURRENT_PORT_OUTBOUND
port_common=$CURRENT_PORT_COMMON
port_java_files_info=$CURRENT_PORT_FILES
large_classes_gt_800=$CURRENT_LARGE
autoconfig_registrations=$CURRENT_AUTOCONFIG
cross_domain_whitelist_size=$CURRENT_CROSS
EOF
  echo "Baseline updated: $BASELINE_FILE"
  exit 0
fi

read_metric() {
  local name=$1
  local value
  value=$(awk -F= -v key="$name" '{ sub(/\r$/, "") } $1 == key { gsub(/[[:space:]]/, "", $2); print $2 }' "$BASELINE_FILE")
  if [[ ! "$value" =~ ^[0-9]+$ ]]; then
    echo "::error::Missing or invalid baseline metric: $name"
    exit 2
  fi
  printf '%s' "$value"
}

BASELINE_PORTS=$(read_metric port_interfaces)
BASELINE_PORT_FILES=$(read_metric port_java_files_info)
BASELINE_LARGE=$(read_metric large_classes_gt_800)
BASELINE_AUTOCONFIG=$(read_metric autoconfig_registrations)
BASELINE_CROSS=$(read_metric cross_domain_whitelist_size)

FAIL=0
check_metric() {
  local name=$1
  local current=$2
  local baseline=$3
  if (( current > baseline )); then
    echo "::error::Complexity budget exceeded for $name: $current > $baseline"
    FAIL=1
  else
    echo "PASS $name: current=$current baseline=$baseline"
  fi
}

check_metric ports "$CURRENT_PORTS" "$BASELINE_PORTS"
check_metric port_java_files_info "$CURRENT_PORT_FILES" "$BASELINE_PORT_FILES"
check_metric large_classes_gt_800 "$CURRENT_LARGE" "$BASELINE_LARGE"
check_metric autoconfig_registrations "$CURRENT_AUTOCONFIG" "$BASELINE_AUTOCONFIG"
check_metric cross_domain_whitelist_size "$CURRENT_CROSS" "$BASELINE_CROSS"

status() {
  if (( $1 <= $2 )); then printf 'PASS'; else printf 'FAIL'; fi
}

cat > "$REPORT_FILE" <<EOF
## Complexity Budget Report

| Metric | Baseline | Current | Delta | Status |
|---|---:|---:|---:|---|
| Public Port interfaces (ArchUnit is authoritative) | $BASELINE_PORTS | $CURRENT_PORTS | $((CURRENT_PORTS - BASELINE_PORTS)) | $(status "$CURRENT_PORTS" "$BASELINE_PORTS") |
| Java files under ports (informational) | $BASELINE_PORT_FILES | $CURRENT_PORT_FILES | $((CURRENT_PORT_FILES - BASELINE_PORT_FILES)) | $(status "$CURRENT_PORT_FILES" "$BASELINE_PORT_FILES") |
| Large classes >800 | $BASELINE_LARGE | $CURRENT_LARGE | $((CURRENT_LARGE - BASELINE_LARGE)) | $(status "$CURRENT_LARGE" "$BASELINE_LARGE") |
| AutoConfig imports | $BASELINE_AUTOCONFIG | $CURRENT_AUTOCONFIG | $((CURRENT_AUTOCONFIG - BASELINE_AUTOCONFIG)) | $(status "$CURRENT_AUTOCONFIG" "$BASELINE_AUTOCONFIG") |
| Cross-domain class pairs | $BASELINE_CROSS | $CURRENT_CROSS | $((CURRENT_CROSS - BASELINE_CROSS)) | $(status "$CURRENT_CROSS" "$BASELINE_CROSS") |
EOF

cat "$REPORT_FILE"
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  cat "$REPORT_FILE" >> "$GITHUB_STEP_SUMMARY"
fi

if (( FAIL != 0 )); then
  echo "Complexity budget check FAILED"
  exit 1
fi

echo "Complexity budget check PASSED"
