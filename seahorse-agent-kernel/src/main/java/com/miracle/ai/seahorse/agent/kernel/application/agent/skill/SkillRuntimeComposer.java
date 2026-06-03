package com.miracle.ai.seahorse.agent.kernel.application.agent.skill;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillInjectMode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillRuntimeBlock;

import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

public class SkillRuntimeComposer {

    public static final int DEFAULT_PER_SKILL_CHAR_BUDGET = 6000;
    public static final int DEFAULT_TOTAL_CHAR_BUDGET = 24000;
    private static final String TRUNCATED_SUFFIX = "\n...[skill truncated]";

    private final int perSkillBudget;
    private final int totalBudget;

    public SkillRuntimeComposer() {
        this(DEFAULT_PER_SKILL_CHAR_BUDGET, DEFAULT_TOTAL_CHAR_BUDGET);
    }

    public SkillRuntimeComposer(int perSkillBudget, int totalBudget) {
        this.perSkillBudget = perSkillBudget <= 0 ? DEFAULT_PER_SKILL_CHAR_BUDGET : perSkillBudget;
        this.totalBudget = totalBudget <= 0 ? DEFAULT_TOTAL_CHAR_BUDGET : totalBudget;
    }

    public String compose(List<SkillRuntimeBlock> skills) {
        if (skills == null || skills.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        out.append("<skills>\n");
        out.append("The following skills are selected for this Agent version. Use them only when relevant.\n\n");
        for (SkillRuntimeBlock skill : skills) {
            if (skill == null) {
                continue;
            }
            String block = block(skill);
            if (out.length() + block.length() + "</skills>".length() > totalBudget) {
                out.append("...[skills truncated]\n");
                break;
            }
            out.append(block).append('\n');
        }
        out.append("</skills>");
        return out.toString();
    }

    private String block(SkillRuntimeBlock skill) {
        StringBuilder block = new StringBuilder();
        block.append("<skill name=\"").append(escapeAttr(skill.name())).append("\" revision=\"")
                .append(escapeAttr(skill.revisionId())).append("\">\n");
        block.append("Description: ").append(Objects.requireNonNullElse(skill.description(), "")).append('\n');
        if (!skill.allowedTools().isEmpty()) {
            StringJoiner tools = new StringJoiner(", ");
            skill.allowedTools().forEach(tools::add);
            block.append("Advisory tools: ").append(tools).append('\n');
        }
        if (skill.injectMode() == SkillInjectMode.METADATA_AND_BODY && !skill.content().isBlank()) {
            block.append("Instructions:\n").append(truncate(skill.content(), perSkillBudget)).append('\n');
        }
        block.append("</skill>\n");
        return block.toString();
    }

    private String truncate(String value, int budget) {
        if (value.length() <= budget) {
            return value;
        }
        int end = Math.max(0, budget - TRUNCATED_SUFFIX.length());
        return value.substring(0, end) + TRUNCATED_SUFFIX;
    }

    private String escapeAttr(String value) {
        return Objects.requireNonNullElse(value, "").replace("\"", "&quot;");
    }
}
