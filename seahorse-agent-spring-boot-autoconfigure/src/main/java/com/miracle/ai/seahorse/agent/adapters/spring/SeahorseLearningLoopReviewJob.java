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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicketReviewRunResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.ReviewMeasurement;
import com.miracle.ai.seahorse.agent.ports.inbound.learning.PolicyAdjustmentTicketManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.coordination.DistributedLockPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

/**
 * 回访的定时兜底任务（<b>默认不装配</b>，配了 {@code seahorse-agent.learning-loop.review-cron} 才生效）。
 *
 * <p>为什么默认走的是事件驱动而不是这里：回访的意义是"在产生新建议的同一时刻回头看上一次
 * 改动的效果"，它天然与"新跑了一次闭环"同频。定时任务是给"很久没人点运行闭环"的兜底，
 * 主路径仍然是 {@code runAndGenerateTickets} 顺带执行的那一轮。
 *
 * <p><b>重复触发的三道防线（与内核侧一致，这里补齐多实例那一道）：</b>
 * <ol>
 *   <li><b>分布式锁</b>：多实例部署时同一时刻只有一个实例真正执行——内核的冷却期
 *       是进程内判断，拦不住另一个实例。</li>
 *   <li><b>按小时分桶的 batchId</b>：{@code sched-yyyyMMddHH}。同一小时内的重复执行
 *       （重跑、锁过期后的补偿）在数据库唯一约束上被忽略。</li>
 *   <li><b>冷却期</b>：由内核按 {@code LearningLoopPolicy#reviewCooldown()} 判断。</li>
 * </ol>
 */
public class SeahorseLearningLoopReviewJob {

    private static final Logger LOG = LoggerFactory.getLogger(SeahorseLearningLoopReviewJob.class);
    private static final String LOCK_NAME = "job:learning-loop-review";
    private static final Duration LOCK_LEASE = Duration.ofMinutes(10);
    private static final DateTimeFormatter BUCKET = DateTimeFormatter.ofPattern("yyyyMMddHH", Locale.ROOT);

    private final PolicyAdjustmentTicketManagementInboundPort ticketPort;
    private final Function<String, ReviewMeasurement> measurementSource;
    private final DistributedLockPort lockPort;
    private final String tenantId;
    private final Clock clock;

    public SeahorseLearningLoopReviewJob(PolicyAdjustmentTicketManagementInboundPort ticketPort,
                                         Function<String, ReviewMeasurement> measurementSource,
                                         DistributedLockPort lockPort,
                                         String tenantId,
                                         Clock clock) {
        this.ticketPort = Objects.requireNonNull(ticketPort, "ticketPort must not be null");
        this.measurementSource = Objects.requireNonNullElseGet(measurementSource,
                () -> ignored -> ReviewMeasurement.unavailable());
        this.lockPort = Objects.requireNonNullElseGet(lockPort, DistributedLockPort::noop);
        this.tenantId = Objects.requireNonNullElse(tenantId, "default").trim();
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
    }

    @Scheduled(cron = "${seahorse-agent.learning-loop.review-cron}")
    public void reviewAppliedTickets() {
        if (!lockPort.tryLock(LOCK_NAME, Duration.ZERO, LOCK_LEASE)) {
            return;
        }
        try {
            ReviewMeasurement measurement = measurementSource.apply(tenantId);
            PolicyAdjustmentTicketReviewRunResult result = ticketPort.reviewAppliedTickets(
                    tenantId, batchId(), measurement);
            if (result == null) {
                return;
            }
            LOG.info("Learning loop review job finished: {}", result.note());
            if (result.hasRollbackRecommendation()) {
                LOG.warn("Learning loop review recommends rollback for {} applied ticket(s); "
                        + "tickets stay APPLIED until a human confirms the config rollback",
                        result.reviews().stream()
                                .filter(review -> review.rollbackRecommended())
                                .count());
            }
        } catch (RuntimeException ex) {
            // 回访是增强能力，不该因为评测数据源抖动就让定时任务把错误栈顶到监控里刷屏
            LOG.warn("Learning loop review job failed", ex);
        } finally {
            lockPort.unlock(LOCK_NAME);
        }
    }

    /** 按小时分桶：同小时内重复执行共享同一个 batchId，从而在唯一索引上被去重。 */
    String batchId() {
        return "sched-" + BUCKET.format(clock.instant().atZone(ZoneId.of("UTC")));
    }
}
