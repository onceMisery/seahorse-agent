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

package com.miracle.ai.seahorse.agent.kernel.domain.common.exception;

/**
 * 业务异常基类。
 *
 * <p>所有可预期的业务规则违反均应继承此类，提供 HTTP 状态码映射能力。
 */
public abstract class SeahorseBusinessException extends SeahorseException {

    private final int httpStatus;

    protected SeahorseBusinessException(String errorCode, String message, int httpStatus) {
        super(errorCode, message);
        this.httpStatus = httpStatus;
    }

    protected SeahorseBusinessException(String errorCode, String message, int httpStatus, Throwable cause) {
        super(errorCode, message, cause);
        this.httpStatus = httpStatus;
    }

    /**
     * 建议的 HTTP 响应状态码。
     */
    public int getHttpStatus() {
        return httpStatus;
    }
}
