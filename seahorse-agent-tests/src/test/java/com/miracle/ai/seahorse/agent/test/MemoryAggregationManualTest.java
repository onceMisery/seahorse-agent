package com.miracle.ai.seahorse.agent.test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryAggregationInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionResult;

/**
 * 记忆聚合手动触发测试
 */
@SpringBootApplication
public class MemoryAggregationManualTest implements CommandLineRunner {

    @Autowired(required = false)
    private MemoryAggregationInboundPort aggregationPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(MemoryAggregationManualTest.class, args);
        System.exit(SpringApplication.exit(context));
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("=== 记忆聚合手动触发测试 ===");

        if (aggregationPort == null) {
            System.err.println("❌ MemoryAggregationInboundPort未注入，请检查配置是否启用");
            return;
        }

        // 查找有足够消息的对话
        String sql = "SELECT conversation_id, user_id FROM t_message " +
                     "WHERE deleted=0 GROUP BY conversation_id, user_id " +
                     "HAVING COUNT(*) >= 5 LIMIT 1";

        jdbcTemplate.query(sql, rs -> {
            String conversationId = rs.getString("conversation_id");
            Long userId = rs.getLong("user_id");

            System.out.println("找到对话: " + conversationId + ", 用户: " + userId);

            // 手动触发聚合
            System.out.println("触发聚合...");
            MemoryIngestionResult result = aggregationPort.flushManually(
                String.valueOf(userId),
                conversationId,
                "default"
            );

            System.out.println("聚合结果: " + result);
        });

        // 验证长期记忆
        Long ltmCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM t_long_term_memory WHERE deleted=0",
            Long.class
        );
        System.out.println("长期记忆数量: " + ltmCount);

        // 验证用户画像
        Long profileCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM t_user_profile_fact WHERE deleted=0",
            Long.class
        );
        System.out.println("用户画像数量: " + profileCount);

        System.out.println("=== 测试完成 ===");
    }
}
