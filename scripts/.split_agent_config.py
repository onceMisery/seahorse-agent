import io
import re

MAIN = 'seahorse-agent-spring-boot-autoconfigure/src/main/java/com/miracle/ai/seahorse/agent/adapters/spring/SeahorseAgentKernelAgentAutoConfiguration.java'
NEW = 'seahorse-agent-spring-boot-autoconfigure/src/main/java/com/miracle/ai/seahorse/agent/adapters/spring/AgentToolAdaptersConfiguration.java'

src = io.open(MAIN, encoding='utf-8').read()
lines = src.split('\n')

# 1-based anchors
def find_line(pred, start=0):
    for i in range(start, len(lines)):
        if pred(lines[i]):
            return i
    raise SystemExit('anchor not found')

search_bean_idx = find_line(lambda l: 'public SearchKnowledgeBaseToolPortAdapter seahorseSearchKnowledgeBaseToolPortAdapter(' in l)
# walk back to the '@Bean' line directly above (skip annotations)
bean_idx = search_bean_idx
while lines[bean_idx].strip() != '@Bean':
    bean_idx -= 1
# include preceding blank line as cut start
cut_start = bean_idx  # 0-based index of '@Bean' line

mcp_end_idx = find_line(lambda l: l.strip() == '}' and 'parseCsv' in '\n'.join(lines[search_bean_idx:search_bean_idx+1]), search_bean_idx) if False else None
# find parseCsv method end: locate its start then its closing brace at indent 4
parse_csv_idx = find_line(lambda l: 'private List<String> parseCsv(String value) {' in l, search_bean_idx)
end_idx = parse_csv_idx
depth = 0
started = False
while True:
    line = lines[end_idx]
    depth += line.count('{') - line.count('}')
    if '{' in line:
        started = True
    if started and depth == 0:
        break
    end_idx += 1
# end_idx now at parseCsv closing brace (0-based)
cut_end = end_idx  # inclusive

moved_block = '\n'.join(lines[cut_start:cut_end + 1])

# 2) main file: remove the moved block plus the preceding blank line
before = lines[:cut_start]
after = lines[cut_end + 1:]
# drop one dangling blank line at the join if both sides are blank
if before and before[-1].strip() == '' and after and after[0].strip() == '':
    after = after[1:]
new_main_body = '\n'.join(before + after)

# 3) relax visibility of shared constants/condition types used by the moved beans
new_main_body = new_main_body.replace(
    '    private static final String PROP_',
    '    static final String PROP_')
new_main_body = new_main_body.replace(
    '    private @interface ConditionalOnAgentRuntimeEnabled {',
    '    @interface ConditionalOnAgentRuntimeEnabled {')
new_main_body = new_main_body.replace(
    '    private static final class AgentRuntimeEnabledCondition implements Condition {',
    '    static final class AgentRuntimeEnabledCondition implements Condition {')
new_main_body = new_main_body.replace(
    '    private @interface ConditionalOnAdvancedLocalAgentToolEnabled {',
    '    @interface ConditionalOnAdvancedLocalAgentToolEnabled {')
new_main_body = new_main_body.replace(
    '    private static final class AdvancedLocalAgentToolEnabledCondition implements Condition {',
    '    static final class AdvancedLocalAgentToolEnabledCondition implements Condition {')
new_main_body = new_main_body.replace(
    '    private static String legacyPropertyName(String propertyName) {',
    '    static String legacyPropertyName(String propertyName) {')

# 4) add @Import on the main class and the import statement
new_main_body = new_main_body.replace(
    '@Configuration(proxyBeanMethods = false)\n@ConditionalOnSeahorseAgentProperty(',
    '@Configuration(proxyBeanMethods = false)\n@Import(AgentToolAdaptersConfiguration.class)\n@ConditionalOnSeahorseAgentProperty(', 1)
if 'import org.springframework.context.annotation.Import;' not in new_main_body:
    new_main_body = new_main_body.replace(
        'import org.springframework.context.annotation.Configuration;',
        'import org.springframework.context.annotation.Configuration;\nimport org.springframework.context.annotation.Import;')

io.open(MAIN, 'w', encoding='utf-8', newline='\n').write(new_main_body)

# 5) build the new configuration class with the moved block, reusing the main imports
imports_match = re.search(r'(import [a-zA-Z0-9_.*]+;\n)+', src)
imports = imports_match.group(0) if imports_match else ''
header_end = src.index('package ')
header = src[:header_end]

new_src = header + 'package com.miracle.ai.seahorse.agent.adapters.spring;\n\n' + imports + \
    '\n/**\n' \
    ' * 内置工具适配器豆组（自 {@link SeahorseAgentKernelAgentAutoConfiguration} 外提）。\n' \
    ' * 仅装配：检索/记忆/沙箱/Web 等内建工具适配器与 MCP 桥接，经主自动配置 @Import 挂载。\n' \
    ' */\n' \
    '@Configuration(proxyBeanMethods = false)\n' \
    'class AgentToolAdaptersConfiguration {\n\n' \
    + moved_block + '\n}\n'

io.open(NEW, 'w', encoding='utf-8', newline='\n').write(new_src)
print(f"moved {cut_end - cut_start + 1} lines; main now {len(new_main_body.split(chr(10)))} lines, new file {len(new_src.split(chr(10)))} lines")
