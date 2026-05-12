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
    REJECT("reject");

    private final String value;

    StreamEventType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
