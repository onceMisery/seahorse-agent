import io
import re

PORTS_MEM = 'seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/inbound/memory/'
SVC = 'seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/retrieval/'
WEB = 'seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/'
ACF = 'seahorse-agent-spring-boot-autoconfigure/src/main/java/com/miracle/ai/seahorse/agent/adapters/spring/'


def read(p):
    return io.open(p, encoding='utf-8').read()


def write(p, s):
    io.open(p, 'w', encoding='utf-8', newline='\n').write(s)


# 1) Evaluation port absorbs harness operations
p = PORTS_MEM + 'MemoryRecallEvaluationInboundPort.java'
src = read(p)
src = src.replace('''    MemoryRecallEvaluationReport evaluate(MemoryRecallEvaluationCommand command);
}''', '''    MemoryRecallEvaluationReport evaluate(MemoryRecallEvaluationCommand command);

    /**
     * 运行一份金标准画像(通常由源码仓维护)的召回基准。
     */
    MemoryRecallEvaluationReport runProfile(String profileName);

    /**
     * 列出可用的金标准画像名。
     */
    java.util.List<String> listProfiles();
}''')
write(p, src)

# 2) harness service implements the merged port
p = SVC + 'MemoryRecallGoldenHarnessService.java'
src = read(p)
src = src.replace('import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryRecallEvaluationInboundPort;',
                  'import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryRecallEvaluationInboundPort;')
src = src.replace('public class MemoryRecallGoldenHarnessService implements MemoryRecallGoldenHarnessInboundPort {',
                  'public class MemoryRecallGoldenHarnessService implements MemoryRecallEvaluationInboundPort {')
write(p, src)

# 3) consumers: controller/feature beans switch type
for path in [WEB + 'SeahorseMemoryRecallGoldenHarnessController.java',
             WEB + 'SeahorseMemoryRecallEvaluationController.java',
             SVC + 'MemoryRecallEvaluationService.java']:
    src = read(path)
    src = src.replace('import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryRecallGoldenHarnessInboundPort;\n', '')
    src = src.replace('MemoryRecallGoldenHarnessInboundPort', 'MemoryRecallEvaluationInboundPort')
    write(path, src)

# 4) autoconfigurations: drop the harness bean, evaluation bean serves both
for path in [ACF + 'SeahorseAgentKernelMemoryAutoConfiguration.java',
             ACF + 'SeahorseAgentMemoryRecallAutoConfiguration.java']:
    src = read(path)
    src = src.replace('import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryRecallGoldenHarnessInboundPort;\n', '')
    src = re.sub(r'    @Bean\n(@ConditionalOn[^\n]*\n(?:@Conditional[^\n]*\n)*)    @ConditionalOnMissingBean\(MemoryRecallGoldenHarnessInboundPort\.class\)\n    public \w+ seahorse\w*RecallGoldenHarness\w*\(([^)]*)\) \{\n(?:.*\n)*?    \}\n\n', '', src)
    src = re.sub(r'    @Bean\n(@ConditionalOn[^\n]*\n)+    @ConditionalOnMissingBean\(MemoryRecallGoldenHarnessInboundPort\.class\)\n    public \w+ seahorse\w*[Hh]arness\w*\([^)]*\) \{\n(?:[^\n]*\n)*?    \}\n\n', '', src)
    src = src.replace('MemoryRecallGoldenHarnessInboundPort', 'MemoryRecallEvaluationInboundPort')
    write(path, src)
print('harness merge applied (script phase)')
