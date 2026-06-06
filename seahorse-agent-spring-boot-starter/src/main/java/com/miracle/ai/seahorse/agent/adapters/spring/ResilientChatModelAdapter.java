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

package com.miracle.ai.seahorse.agent.adapters.spring;

import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.common.exception.ExternalServiceException;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * 弹性 ChatModel 适配器（装饰器模式）。
 *
 * <p>基于 Resilience4j 提供三级降级策略：
 * <ol>
 *     <li>Level 1: 超时后快速失败</li>
 *     <li>Level 2: 重试（最多 2 次）</li>
 *     <li>Level 3: 熔断器打开时返回降级响应</li>
 * </ol>
 */
public class ResilientChatModelAdapter implements ChatModelPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResilientChatModelAdapter.class);
    private static final String FALLBACK_RESPONSE = "抱歉，AI 服务暂时不可用，请稍后重试。";

    private final ChatModelPort delegate;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final TimeLimiter timeLimiter;
    private final ExecutorService executorService;

    public ResilientChatModelAdapter(ChatModelPort delegate,
                                     CircuitBreakerRegistry circuitBreakerRegistry,
                                     RetryRegistry retryRegistry,
                                     TimeLimiterRegistry timeLimiterRegistry,
                                     ExecutorService executorService) {
        this.delegate = delegate;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("chatModel");
        this.retry = retryRegistry.retry("chatModel");
        this.timeLimiter = timeLimiterRegistry.timeLimiter("chatModel");
        this.executorService = executorService;
    }

    @Override
    public String chat(ChatRequest request, String modelId) {
        Supplier<CompletableFuture<String>> futureSupplier = () ->
                CompletableFuture.supplyAsync(() -> delegate.chat(request, modelId), executorService);

        Callable<String> timeLimited = TimeLimiter.decorateFutureSupplier(timeLimiter, futureSupplier);
        Callable<String> retryable = Retry.decorateCallable(retry, timeLimited);
        Callable<String> withCircuitBreaker = CircuitBreaker.decorateCallable(circuitBreaker, retryable);

        try {
            return withCircuitBreaker.call();
        } catch (TimeoutException e) {
            LOGGER.warn("Chat model timeout for model={}, falling back", modelId);
            return FALLBACK_RESPONSE;
        } catch (io.github.resilience4j.circuitbreaker.CallNotPermittedException e) {
            LOGGER.warn("Circuit breaker OPEN for chatModel, returning fallback");
            return FALLBACK_RESPONSE;
        } catch (ExternalServiceException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("Chat model call failed after retries for model={}", modelId, e);
            throw new ExternalServiceException("ChatModel", e.getMessage(), e);
        }
    }

    @Override
    public String chat(String modelId, List<ChatMessage> messages) {
        ChatRequest request = ChatRequest.builder()
                .messages(messages)
                .build();
        return chat(request, modelId);
    }
}
