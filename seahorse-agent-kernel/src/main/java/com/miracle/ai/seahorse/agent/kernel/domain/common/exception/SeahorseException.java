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
 * Seahorse 异常体系根基类。
 *
 * <p>所有 Seahorse 业务异常和系统异常均继承此类，提供统一的错误码和消息格式。
 */
public abstract class SeahorseException extends RuntimeException {

    private final String errorCode;

    protected SeahorseException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected SeahorseException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * 机器可读的错误码，用于前端国际化和问题定位。
     */
    public String getErrorCode() {
        return errorCode;
    }
}
