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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.adapters.web.KbPermissionAspect;
import com.miracle.ai.seahorse.agent.adapters.web.SuperAdminAspect;
import com.miracle.ai.seahorse.agent.adapters.web.TrialExpiredInterceptor;
import com.miracle.ai.seahorse.agent.kernel.application.knowledge.KnowledgeBasePermissionService;
import com.miracle.ai.seahorse.agent.kernel.application.trial.KernelTrialService;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * AOP 切面与拦截器自动配置。
 *
 * <p>注册以下组件：
 * <ul>
 *   <li>{@link KbPermissionAspect} — 知识库权限 AOP 切面</li>
 *   <li>{@link SuperAdminAspect} — 超级管理员权限 AOP 切面</li>
 *   <li>{@link TrialExpiredInterceptor} — 试用期到期拦截器</li>
 *   <li>{@link AgentPopularityRecalculationJob} — Agent 热度定时重算</li>
 * </ul>
 */
@Configuration
@EnableAspectJAutoProxy
public class SeahorseAgentAopAutoConfiguration {

    /**
     * 知识库权限校验切面。
     */
    @Bean
    @ConditionalOnBean({CurrentUserPort.class, KnowledgeBasePermissionService.class})
    @ConditionalOnMissingBean(KbPermissionAspect.class)
    public KbPermissionAspect kbPermissionAspect(CurrentUserPort currentUserPort,
                                                  KnowledgeBasePermissionService permissionService) {
        return new KbPermissionAspect(currentUserPort, permissionService);
    }

    /**
     * 超级管理员权限校验切面。
     */
    @Bean
    @ConditionalOnBean(CurrentUserPort.class)
    @ConditionalOnMissingBean(SuperAdminAspect.class)
    public SuperAdminAspect superAdminAspect(
            CurrentUserPort currentUserPort,
            @Value("${seahorse.admin.allowed-ips:}") List<String> allowedIps) {
        return new SuperAdminAspect(currentUserPort, allowedIps);
    }

    /**
     * 试用期到期拦截器 Bean。
     */
    @Bean
    @ConditionalOnBean({CurrentUserPort.class, KernelTrialService.class})
    @ConditionalOnMissingBean(TrialExpiredInterceptor.class)
    public TrialExpiredInterceptor trialExpiredInterceptor(CurrentUserPort currentUserPort,
                                                            KernelTrialService trialService,
                                                            ObjectMapper objectMapper) {
        return new TrialExpiredInterceptor(currentUserPort, trialService, objectMapper);
    }

    /**
     * 注册试用期拦截器到 Spring MVC 拦截器链。
     */
    @Bean
    @ConditionalOnBean(TrialExpiredInterceptor.class)
    public WebMvcConfigurer seahorseTrialInterceptorConfigurer(
            ObjectProvider<TrialExpiredInterceptor> interceptorProvider) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                TrialExpiredInterceptor interceptor = interceptorProvider.getIfAvailable();
                if (interceptor != null) {
                    registry.addInterceptor(interceptor)
                            .addPathPatterns("/api/**")
                            .excludePathPatterns("/api/auth/**", "/api/billing/plans");
                }
            }
        };
    }
}
