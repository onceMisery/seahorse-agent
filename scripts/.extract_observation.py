import io
import re

path = 'seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/retrieval/HybridMemoryRecallPipeline.java'
src = io.open(path, encoding='utf-8').read()
lines = src.split('\n')

MOVE_METHODS = [
    'recordRecallChannel', 'emitChannelMetric', 'channelOutcome',
    'recordRecallFusion', 'recordRecallRerank', 'emitStageMetric',
    'traceDetails', 'fusionExplanations', 'fusionExplanation',
]

def method_block(name):
    """return (start_idx, end_idx_inclusive) of the method with the given name"""
    for i, l in enumerate(lines):
        if re.search(r'(private|static|public)[^(]*[ ]' + name + r'\(', l):
            depth = 0
            started = False
            j = i
            while True:
                depth += lines[j].count('{') - lines[j].count('}')
                if '{' in lines[j]:
                    started = True
                if started and depth == 0:
                    return (i, j)
                j += 1
    raise SystemExit('method not found: ' + name)

# carve the movable method blocks (bottom-up so indices stay valid)
blocks = {}
spans = []
for name in MOVE_METHODS:
    s, e = method_block(name)
    spans.append((s, e, name))
spans.sort(reverse=True)
removed = set()
for s, e, name in spans:
    blocks[name] = '\n'.join(lines[s:e + 1])
    for k in range(s, e + 1):
        removed.add(k)

# constants that move: TRACE_*, FUSION_METADATA_*, OBSERVATION_* (all package-private static)
const_pattern = re.compile(r'    (static final String (?:TRACE_|FUSION_METADATA_|OBSERVATION_)[A-Z_0-9]+ = .*)')
moved_constants = []
for i, l in enumerate(lines):
    m = const_pattern.match(l)
    if m:
        moved_constants.append('    ' + m.group(1))
        removed.add(i)

# OBSERVATION_ constants are package-private already (lines 147-149) -> re-declare in support; drop from main
remaining = '\n'.join(l for i, l in enumerate(lines) if i not in removed)

# rewire call sites in the main class
new_main = remaining
for name in MOVE_METHODS:
    new_main = re.sub(r'(?<![\w.])' + name + r'\(', 'observationSupport.' + name + '(', new_main)
# the main class keeps traceContext/aliasTraceDetails/activeTrackNames and shared helpers;
# make the shared helpers static package-private so the support class can call them
for helper in ['candidateIds', 'candidateIdsFromChannelResults', 'safeCandidates', 'safeChannelResults', 'putIfPresent']:
    new_main = re.sub(r'    private (static )?(List<String>|List<MemoryRecallCandidate>|List<List<MemoryRecallCandidate>>|Map<String, Object>|void) ' + helper + r'\(',
                      lambda m: '    static ' + (m.group(2) + ' ') + helper + '(', new_main)
# fix internal static-call sites in main that now need qualification? same-package static calls work unqualified.

# add the observationSupport field + init in the canonical constructor
new_main = new_main.replace(
    '    private final ObservationPort observationPort;',
    '    private final ObservationPort observationPort;\n    private final MemoryRecallObservationSupport observationSupport;', 1)
new_main = new_main.replace(
    '        this.observationPort = Objects.requireNonNullElseGet(observationPort, ObservationPort::noop);',
    '        this.observationPort = Objects.requireNonNullElseGet(observationPort, ObservationPort::noop);\n'
    '        this.observationSupport = new MemoryRecallObservationSupport(\n'
    '                this.traceRecorder, this.observationPort, this.fusionPolicy);', 1)

# make RecallTraceContext package-private so the support class can see it
new_main = new_main.replace('    private record RecallTraceContext(', '    record RecallTraceContext(', 1)

# main keeps the three alias TRACE_KEY constants? no: aliasTraceDetails uses them -> qualify via support
for key in ['TRACE_KEY_ALIAS_CANONICAL_ENTITY_ID', 'TRACE_KEY_ALIAS_ENTITY_TYPE', 'TRACE_KEY_ALIAS_CONFIDENCE_LEVEL']:
    new_main = re.sub(r'(?<![\w.])' + key + r'\b', 'MemoryRecallObservationSupport.' + key, new_main)

io.open(path, 'w', encoding='utf-8', newline='\n').write(new_main)

# build the support class
header_end = src.index('package ')
header = src[:header_end]
support = header + '''package com.miracle.ai.seahorse.agent.kernel.application.memory.retrieval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryRecallCandidate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTraceRecorder;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.MemoryRecallChannelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTraceEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 召回观测协作者：通道/融合/重排的 trace 事件与 best-effort 指标上报。
 * 只依赖 traceRecorder/observationPort/fusionPolicy；候选去重与别名解析留在主流水线。
 */
final class MemoryRecallObservationSupport {

''' + '\n'.join(moved_constants) + '''

    private final MemoryTraceRecorder traceRecorder;
    private final ObservationPort observationPort;
    private final MemoryFusionPolicy fusionPolicy;

    MemoryRecallObservationSupport(MemoryTraceRecorder traceRecorder,
                                   ObservationPort observationPort,
                                   MemoryFusionPolicy fusionPolicy) {
        this.traceRecorder = Objects.requireNonNullElseGet(traceRecorder, MemoryTraceRecorder::noop);
        this.observationPort = Objects.requireNonNullElseGet(observationPort, ObservationPort::noop);
        this.fusionPolicy = Objects.requireNonNullElseGet(fusionPolicy, MemoryFusionPolicy::defaults);
    }

''' + '\n\n'.join(blocks.values()) + '\n}\n'

io.open('seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/retrieval/MemoryRecallObservationSupport.java',
        'w', encoding='utf-8', newline='\n').write(support)
print('observation support extracted; main now', len(new_main.split('\n')), 'lines')
