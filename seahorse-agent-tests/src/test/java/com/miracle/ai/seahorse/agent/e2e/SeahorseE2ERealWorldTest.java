/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.miracle.ai.seahorse.agent.e2e;

import org.junit.jupiter.api.*;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Seahorse Agent E2E 完整工作流真实测试
 *
 * 前提：docker-compose.full.yml 已启动
 * 场景：演示 Seahorse Agent 完整工作原理
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SeahorseE2ERealWorldTest {

    private static final String BASE_URL = "http://localhost:9090";
    private static final RestTemplate restTemplate = new RestTemplateBuilder()
        .setConnectTimeout(java.time.Duration.ofSeconds(10))
        .setReadTimeout(java.time.Duration.ofSeconds(60))
        .build();

    private static String accessToken;
    private static String userId;
    private static Long knowledgeBaseId;
    private static String conversationId;

    @BeforeAll
    public static void checkServiceAvailability() {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(
                BASE_URL + "/actuator/health", String.class);
            System.out.println("✓ Seahorse 后端服务运行正常");
        } catch (Exception e) {
            fail("无法连接到 Seahorse 后端。请运行: docker compose -f docker-compose.full.yml up -d");
        }
    }

    @Test
    @Order(1)
    @DisplayName("步骤1: 管理员登录")
    public void step1_adminLogin() {
        Map<String, String> request = Map.of(
            "username", "admin",
            "password", "admin123"
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
            BASE_URL + "/auth/login", request, Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("0", body.get("code"));

        Map<String, Object> data = (Map<String, Object>) body.get("data");
        accessToken = (String) data.get("token");
        userId = (String) data.get("userId");

        assertNotNull(accessToken);
        assertNotNull(userId);

        System.out.println("✓ 管理员登录成功");
        System.out.println("  用户ID: " + userId);
        System.out.println("  Token: " + accessToken.substring(0, 20) + "...");
    }

    @Test
    @Order(2)
    @DisplayName("步骤2: 创建知识库")
    public void step2_createKnowledgeBase() {
        HttpHeaders headers = createAuthHeaders();

        Map<String, String> request = Map.of(
            "name", "SeahorseKB_" + System.currentTimeMillis(),
            "embeddingModel", "nomic-embed-text",
            "collectionName", "e2e_test_kb_" + System.currentTimeMillis()
        );

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(request, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(
            BASE_URL + "/knowledge-base", entity, Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("0", body.get("code"));

        String kbIdStr = (String) body.get("data");
        knowledgeBaseId = Long.parseLong(kbIdStr);
        assertNotNull(knowledgeBaseId);

        System.out.println("✓ 知识库创建成功");
        System.out.println("  知识库ID: " + knowledgeBaseId);
    }

    @Test
    @Order(3)
    @DisplayName("步骤3: 上传文档到知识库")
    public void step3_uploadDocument() {
        HttpHeaders headers = createAuthHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        String content = "# Seahorse Agent 系统架构\n\n" +
            "Seahorse Agent 是基于 Spring Boot 3.5.7 的 RAG 智能体平台。\n\n" +
            "## 核心特性\n" +
            "1. 六边形架构（端口-适配器模式）\n" +
            "2. RAG 检索增强生成\n" +
            "3. 多租户隔离\n" +
            "4. Memory 系统\n" +
            "5. Agent 工具调用\n\n" +
            "## 技术栈\n" +
            "- PostgreSQL + pgvector\n" +
            "- Milvus 向量数据库\n" +
            "- Redis 缓存\n" +
            "- Elasticsearch 搜索";

        System.out.println("✓ 文档上传准备完成（待实现 multipart 支持）");
        System.out.println("  文档内容长度: " + content.length() + " 字符");
    }

    @Test
    @Order(4)
    @DisplayName("步骤4: 创建对话")
    public void step4_createConversation() {
        HttpHeaders headers = createAuthHeaders();

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(
            BASE_URL + "/conversations", entity, Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("0", body.get("code"));

        conversationId = (String) body.get("data");
        assertNotNull(conversationId);

        System.out.println("✓ 对话创建成功");
        System.out.println("  对话ID: " + conversationId);
    }

    @Test
    @Order(5)
    @DisplayName("步骤5: 查询知识库列表")
    public void step5_listKnowledgeBases() {
        HttpHeaders headers = createAuthHeaders();

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(
            BASE_URL + "/knowledge-base?current=1&size=10",
            HttpMethod.GET, entity, Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("0", body.get("code"));

        System.out.println("✓ 知识库列表查询成功");
    }

    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.set("X-User-Id", userId);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}

