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

import java.time.Instant;
import java.util.Map;

/**
 * 任务事件响应 DTO（SSE data 体）。
 *
 * @param seq     事件序号（单调递增），前端用于断线重连去重
 * @param type    事件类型（点号命名）
 * @param message 人类可读阶段描述
 * @param data    结构化附加数据
 * @param at      事件时间
 */
public record TaskEventResponse(
        long seq,
        String type,
        String message,
        Map<String, Object> data,
        Instant at
) {
}
