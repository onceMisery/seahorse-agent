package com.miracle.ai.seahorse.agent.ports.inbound.chat;

import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StreamChatCommandTests {

    @Test
    void selectedSkillNamesNormalizedToKebabCase() {
        var cmd = new StreamChatCommand("q", "c", "t", "u", false,
                ChatMode.AGENT, null, null, null, List.of(),
                List.of("My_Skill", "  Another-Skill  "));
        assertEquals(List.of("my-skill", "another-skill"), cmd.selectedSkillNames());
    }

    @Test
    void selectedSkillNamesDeduplication() {
        var cmd = new StreamChatCommand("q", "c", "t", "u", false,
                ChatMode.AGENT, null, null, null, List.of(),
                List.of("deep-research", "Deep-Research", "deep_research"));
        assertEquals(List.of("deep-research"), cmd.selectedSkillNames());
    }

    @Test
    void selectedSkillNamesNullReturnsEmpty() {
        var cmd = new StreamChatCommand("q", "c", "t", "u", false,
                ChatMode.AGENT, null, null, null, List.of(), null);
        assertTrue(cmd.selectedSkillNames().isEmpty());
    }

    @Test
    void selectedSkillNamesOverLimitThrows() {
        assertThrows(IllegalArgumentException.class, () -> new StreamChatCommand(
                "q", "c", "t", "u", false, ChatMode.AGENT,
                null, null, null, List.of(),
                List.of("a", "b", "c", "d", "e", "f")));
    }

    @Test
    void selectedSkillNamesExactlyFiveAccepted() {
        var cmd = new StreamChatCommand("q", "c", "t", "u", false,
                ChatMode.AGENT, null, null, null, List.of(),
                List.of("a", "b", "c", "d", "e"));
        assertEquals(5, cmd.selectedSkillNames().size());
    }

    @Test
    void backwardCompatibleConstructorDefaultsSelectedSkillsToEmpty() {
        var cmd = new StreamChatCommand("q", "c", "t", "u", false);
        assertTrue(cmd.selectedSkillNames().isEmpty());
    }
}
