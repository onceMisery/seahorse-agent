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

import com.miracle.ai.seahorse.agent.kernel.application.knowledge.KnowledgeBasePermissionService;
import com.miracle.ai.seahorse.agent.kernel.application.knowledge.RequireKbPermission;
import com.miracle.ai.seahorse.agent.kernel.domain.knowledge.KnowledgeBasePermission;
import com.miracle.ai.seahorse.agent.kernel.exception.ForbiddenException;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;

import java.lang.annotation.Annotation;
import java.util.Optional;

/**
 * AOP 切面：拦截 {@link RequireKbPermission} 注解，校验当前用户对知识库的访问权限。
 *
 * <p>从方法参数中提取 kbId（Long 类型且名为 kbId 或标注 @PathVariable），
 * 再通过 {@link KnowledgeBasePermissionService} 进行权限判定。
 */
@Aspect
public class KbPermissionAspect {

    private static final Logger LOGGER = LoggerFactory.getLogger(KbPermissionAspect.class);

    private final CurrentUserPort currentUserPort;
    private final KnowledgeBasePermissionService permissionService;

    public KbPermissionAspect(CurrentUserPort currentUserPort,
                              KnowledgeBasePermissionService permissionService) {
        this.currentUserPort = currentUserPort;
        this.permissionService = permissionService;
    }

    @Around("@annotation(requireKbPermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint,
                                  RequireKbPermission requireKbPermission) throws Throwable {
        CurrentUser user = currentUserPort.requireCurrentUser();
        String requiredPermission = requireKbPermission.value();
        Long kbId = extractKbId(joinPoint);

        if (kbId == null) {
            LOGGER.warn("@RequireKbPermission: 无法从方法参数中提取 kbId，跳过权限校验");
            return joinPoint.proceed();
        }

        Optional<KnowledgeBasePermission> permission =
                permissionService.checkPermission(kbId, user.userId());

        boolean allowed = permission.map(p -> isSufficient(p.permission(), requiredPermission))
                .orElse(false);

        if (!allowed) {
            LOGGER.warn("知识库权限不足: userId={}, kbId={}, required={}, actual={}",
                    user.userId(), kbId, requiredPermission,
                    permission.map(KnowledgeBasePermission::permission).orElse("NONE"));
            throw new ForbiddenException(
                    "知识库权限不足，需要 " + requiredPermission + " 权限",
                    "knowledge_base", String.valueOf(kbId));
        }

        return joinPoint.proceed();
    }

    /**
     * 判断实际权限是否满足所需权限。
     * 权限层级：OWNER > EDITOR > VIEWER。
     */
    private boolean isSufficient(String actual, String required) {
        if (KnowledgeBasePermission.OWNER.equals(actual)) {
            return true; // OWNER 拥有所有权限
        }
        if (KnowledgeBasePermission.EDITOR.equals(actual)) {
            return KnowledgeBasePermission.EDITOR.equals(required)
                    || KnowledgeBasePermission.VIEWER.equals(required);
        }
        if (KnowledgeBasePermission.VIEWER.equals(actual)) {
            return KnowledgeBasePermission.VIEWER.equals(required);
        }
        return false;
    }

    /**
     * 从方法参数中提取 kbId：
     * 1. 优先查找名为 "kbId" 的 Long 参数
     * 2. 其次查找标注 @PathVariable 且名为 "kbId" 的参数
     */
    private Long extractKbId(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        if (paramNames == null || args == null) {
            return null;
        }

        // Strategy 1: find parameter named "kbId" of type Long
        for (int i = 0; i < paramNames.length; i++) {
            if ("kbId".equals(paramNames[i]) && args[i] instanceof Long) {
                return (Long) args[i];
            }
        }

        // Strategy 2: find @PathVariable Long parameter
        Annotation[][] paramAnnotations = signature.getMethod().getParameterAnnotations();
        for (int i = 0; i < paramAnnotations.length; i++) {
            if (args[i] instanceof Long) {
                for (Annotation ann : paramAnnotations[i]) {
                    if (ann instanceof PathVariable) {
                        return (Long) args[i];
                    }
                }
            }
        }

        return null;
    }
}
