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

package com.miracle.ai.seahorse.agent.adapters.web;

import cn.dev33.satoken.stp.StpUtil;
import com.miracle.ai.seahorse.agent.kernel.model.AiModelConfig;
import com.miracle.ai.seahorse.agent.ports.outbound.config.AiModelConfigRepositoryPort;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * AI 模型配置 Web adapter
 */
@RestController
@RequestMapping("/admin/ai-config")
public class AiModelConfigController {

    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String KEY_MESSAGE = "message";
    private static final String SUCCESS_CODE = "0";
    private static final String ERROR_CODE = "1";

    private final AiModelConfigRepositoryPort configRepository;

    public AiModelConfigController(AiModelConfigRepositoryPort configRepository) {
        this.configRepository = configRepository;
    }

    @GetMapping
    public Map<String, Object> list() {
        try {
            StpUtil.checkLogin();
            List<AiModelConfig> configs = configRepository.findAll();
            List<Map<String, Object>> data = configs.stream()
                    .map(this::toResponseMap)
                    .collect(Collectors.toList());
            return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, data);
        } catch (Exception e) {
            return Map.of(KEY_CODE, ERROR_CODE, KEY_MESSAGE, "获取配置失败: " + e.getMessage());
        }
    }

    @GetMapping("/{key}")
    public Map<String, Object> getByKey(@PathVariable("key") String key) {
        try {
            StpUtil.checkLogin();
            return configRepository.findByKey(key)
                    .map(config -> Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, toResponseMap(config)))
                    .orElse(Map.of(KEY_CODE, ERROR_CODE, KEY_MESSAGE, "配置不存在"));
        } catch (Exception e) {
            return Map.of(KEY_CODE, ERROR_CODE, KEY_MESSAGE, "获取配置失败: " + e.getMessage());
        }
    }

    @PutMapping("/{key}")
    public Map<String, Object> update(@PathVariable("key") String key,
                                      @RequestBody Map<String, String> request) {
        try {
            StpUtil.checkLogin();
            String userId = StpUtil.getLoginIdAsString();

            String value = request.get("value");
            if (value == null || value.trim().isEmpty()) {
                return Map.of(KEY_CODE, ERROR_CODE, KEY_MESSAGE, "配置值不能为空");
            }

            configRepository.update(key, value, userId);

            return Map.of(KEY_CODE, SUCCESS_CODE, KEY_MESSAGE, "配置更新成功");
        } catch (Exception e) {
            return Map.of(KEY_CODE, ERROR_CODE, KEY_MESSAGE, "更新配置失败: " + e.getMessage());
        }
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> request) {
        try {
            StpUtil.checkLogin();
            String userId = StpUtil.getLoginIdAsString();

            AiModelConfig config = new AiModelConfig();
            config.setId(UUID.randomUUID().toString());
            config.setConfigKey((String) request.get("configKey"));
            config.setConfigValue((String) request.get("configValue"));
            config.setConfigType(AiModelConfig.ConfigType.valueOf((String) request.getOrDefault("configType", "STRING")));
            config.setEncrypted((Boolean) request.getOrDefault("encrypted", false));
            config.setDescription((String) request.get("description"));
            config.setCreatedBy(userId);
            config.setUpdatedBy(userId);
            config.setCreatedAt(LocalDateTime.now());
            config.setUpdatedAt(LocalDateTime.now());

            configRepository.save(config);

            return Map.of(KEY_CODE, SUCCESS_CODE, KEY_MESSAGE, "配置创建成功", KEY_DATA, toResponseMap(config));
        } catch (Exception e) {
            return Map.of(KEY_CODE, ERROR_CODE, KEY_MESSAGE, "创建配置失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{key}")
    public Map<String, Object> delete(@PathVariable("key") String key) {
        try {
            StpUtil.checkLogin();
            configRepository.delete(key);
            return Map.of(KEY_CODE, SUCCESS_CODE, KEY_MESSAGE, "配置删除成功");
        } catch (Exception e) {
            return Map.of(KEY_CODE, ERROR_CODE, KEY_MESSAGE, "删除配置失败: " + e.getMessage());
        }
    }

    private Map<String, Object> toResponseMap(AiModelConfig config) {
        String displayValue = config.isEncrypted() ? maskValue(config.getConfigValue()) : config.getConfigValue();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", config.getId());
        response.put("configKey", config.getConfigKey());
        response.put("configValue", config.isEncrypted() ? "" : config.getConfigValue());
        response.put("displayValue", displayValue); // 显示值，敏感信息脱敏
        response.put("configType", config.getConfigType().name());
        response.put("encrypted", config.isEncrypted());
        response.put("description", config.getDescription() != null ? config.getDescription() : "");
        response.put("createdBy", config.getCreatedBy() != null ? config.getCreatedBy() : "");
        response.put("updatedBy", config.getUpdatedBy() != null ? config.getUpdatedBy() : "");
        response.put("createdAt", config.getCreatedAt() != null ? config.getCreatedAt().toString() : "");
        response.put("updatedAt", config.getUpdatedAt() != null ? config.getUpdatedAt().toString() : "");
        return response;
    }

    private String maskValue(String value) {
        if (value == null || value.length() <= 8) {
            return "********";
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }
}
