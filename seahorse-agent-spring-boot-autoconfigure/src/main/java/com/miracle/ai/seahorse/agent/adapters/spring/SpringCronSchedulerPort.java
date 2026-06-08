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

import com.miracle.ai.seahorse.agent.ports.outbound.schedule.SchedulerPort;
import org.springframework.scheduling.support.CronExpression;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 基于 Spring CronExpression 的调度时间计算 adapter。
 */
public class SpringCronSchedulerPort implements SchedulerPort {

    @Override
    public Instant nextRun(String cron, Instant from) {
        if (cron == null || cron.isBlank()) {
            return null;
        }
        Instant base = from == null ? Instant.now() : from;
        ZonedDateTime next = CronExpression.parse(cron.trim())
                .next(ZonedDateTime.ofInstant(base, ZoneId.systemDefault()));
        return next == null ? null : next.toInstant();
    }
}
