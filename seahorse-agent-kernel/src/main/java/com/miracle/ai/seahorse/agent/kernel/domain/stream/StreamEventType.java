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

package com.miracle.ai.seahorse.agent.kernel.domain.stream;

/**
 * Seahorse 流式响应事件类型。
 */
public enum StreamEventType {

    /**
     * 会话与任务元信息。
     */
    META("meta"),

    /**
     * 模型增量消息。
     */
    MESSAGE("message"),

    /**
     * 模型回复完成。
     */
    FINISH("finish"),

    /**
     * SSE 流完成标记。
     */
    DONE("done"),

    /**
     * 流式任务取消。
     */
    CANCEL("cancel"),

    /**
     * 请求被拒绝。
     */
    REJECT("reject"),

    /**
     * Agent 任务时间线。
     */
    AGENT_TIMELINE("agent.timeline"),

    /**
     * Agent run 已创建并开始执行。
     */
    RUN_STARTED("run_started"),

    /**
     * Agent run snapshot for Web SSE resume.
     */
    RUN_SNAPSHOT("run_snapshot"),

    /**
     * Agent step started.
     */
    STEP_STARTED("step_started"),

    /**
     * Agent step progress update.
     */
    STEP_PROGRESS("step_progress"),

    /**
     * Agent step finished.
     */
    STEP_FINISHED("step_finished"),

    /**
     * Tool call started.
     */
    TOOL_CALL_STARTED("tool_call_started"),

    /**
     * Tool call is waiting for the current user to decide.
     */
    TOOL_CALL_WAITING_USER("tool_call_waiting_user"),

    /**
     * Tool call finished with a successful or failed observation.
     */
    TOOL_CALL_FINISHED("tool_call_finished"),

    /**
     * Runtime selected a skill for the current run.
     */
    SKILL_SELECTED("skill.selected"),

    /**
     * Runtime injected skill body content.
     */
    SKILL_LOADED("skill.loaded"),

    /**
     * Runtime skipped or rejected a skill.
     */
    SKILL_SKIPPED("skill.skipped"),

    /**
     * A progressive skill resource was loaded.
     */
    SKILL_RESOURCE_LOADED("skill.resource_loaded"),

    /**
     * Source or citation was found.
     */
    SOURCE_FOUND("source_found"),

    /**
     * Run artifact was created.
     */
    ARTIFACT_CREATED("artifact_created"),

    ARTIFACT_START("artifact_start"),

    /**
     * Recoverable runtime error.
     */
    RECOVERABLE_ERROR("recoverable_error"),

    /**
     * Structured agent source event.
     */
    AGENT_SOURCE("agent.source"),

    /**
     * Structured agent artifact event.
     */
    AGENT_ARTIFACT("agent.artifact"),

    /**
     * Structured agent approval event.
     */
    AGENT_APPROVAL("agent.approval"),

    /**
     * Structured agent quota event.
     */
    AGENT_QUOTA("agent.quota"),

    /**
     * Structured agent memory event.
     */
    AGENT_MEMORY("agent.memory"),

    /**
     * 产物增量内容 delta（边生成边渲染）。
     */
    ARTIFACT_CONTENT("artifact_content"),

    ARTIFACT_END("artifact_end"),

    /**
     * 产物生成完成，携带最终 metadata。
     */
    ARTIFACT_COMPLETE("artifact_complete");

    private final String value;

    StreamEventType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
