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

package com.miracle.ai.seahorse.agent.kernel.application.agent.tool;

import com.miracle.ai.seahorse.agent.ports.outbound.agent.DescribedToolPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class GetDateTimeToolPortAdapter implements DescribedToolPort {

    public static final String TOOL_ID = "get_current_datetime";
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(TOOL_ID, "Get Current Date & Time",
            "Return the current server date and time. Use this when the user asks about the current time, date, day of week, or any temporal reference.",
            """
                    {"type":"object","required":[],"properties":{}}
                    """);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ToolInvocationResult invoke(String toolCallId, String toolId, Map<String, Object> arguments) {
        try {
            LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
            String[] dayOfWeekCn = {"星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"};
            String result = "当前时间: %s %s (%s)，时区: Asia/Shanghai".formatted(
                    now.format(DATE_FMT),
                    now.format(TIME_FMT),
                    dayOfWeekCn[now.getDayOfWeek().getValue() - 1]);
            return ToolInvocationResult.ok(result);
        } catch (Exception ex) {
            return ToolInvocationResult.failed("get_current_datetime failed: "
                    + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getName()));
        }
    }
}
