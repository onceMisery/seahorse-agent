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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.learning;

/**
 * 工单不存在。
 *
 * <p>单独建一个异常类型而不是复用 {@link IllegalArgumentException}，是为了让 Web 层能把它
 * 翻译成 404 而不是 400——"找不到这个工单"和"你传的参数不合法"在接口契约上是两件事，
 * 前端也要据此决定是提示"已被删除"还是"刷新重试"。
 */
public class PolicyAdjustmentTicketNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PolicyAdjustmentTicketNotFoundException(String ticketId) {
        super("policy adjustment ticket not found: " + ticketId);
    }
}
