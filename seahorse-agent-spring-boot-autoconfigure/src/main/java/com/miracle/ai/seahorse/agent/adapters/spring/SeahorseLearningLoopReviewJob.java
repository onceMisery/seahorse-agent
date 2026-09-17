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
            if (measurement == null || !measurement.measurable()) {
                // 取不到测量值时内核会返回 empty(note)，但那句 note 不说是"哪个来源没取到"，
                // 这里把来源补上，否则配了 cron 却什么都不发生会无从下手
                LOG.warn("Learning loop review has no comparable measurement: source={}, tenantId={}",
                        measurement == null ? "none" : measurement.source(), tenantId);
            }
            PolicyAdjustmentTicketReviewRunResult result = ticketPort.reviewAppliedTickets(
                    tenantId, batchId(), measurement);
            if (result == null) {
                return;
            }
            if (result.reviews().isEmpty()) {
                // "一条都没写"必须能看出来是没数据、没工单、还是都在冷却期里
                LOG.warn("Learning loop review wrote nothing: note={}, scanned={}, skipped={}, tenantId={}",
                        result.note(), result.scanned(), result.skipped(), tenantId);
                return;
            }
            LOG.info("Learning loop review job finished: {} (written={}, scanned={}, skipped={})",
                    result.note(), result.reviews().size(), result.scanned(), result.skipped());
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

    /**
     * 实际生效的测量来源，供装配测试断言优先级。
     *
     * <p>优先级发生在装配层（显式 Bean ＞ 默认评测来源），而装配层的选择没有别的
     * 可观测出口——不加这个访问器，"显式 Bean 是否真的优先"就只能靠读代码确认，
     * 一旦有人调换两个分支的顺序，没有任何测试会红。
     */
    Function<String, ReviewMeasurement> measurementSource() {
        return measurementSource;
    }

    /** 按小时分桶：同小时内重复执行共享同一个 batchId，从而在唯一索引上被去重。 */
    String batchId() {
        return "sched-" + BUCKET.format(clock.instant().atZone(ZoneId.of("UTC")));
    }
}
