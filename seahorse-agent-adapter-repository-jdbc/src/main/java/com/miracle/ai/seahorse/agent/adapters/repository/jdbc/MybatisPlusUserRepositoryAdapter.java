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

package com.miracle.ai.seahorse.agent.adapters.repository.jdbc;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.entity.UserDO;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.mapper.UserMapper;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserCreateValues;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserPage;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserUpdateValues;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * MyBatis Plus 用户仓储适配器。
 *
 * <p>保持与原 JdbcUserRepositoryAdapter 相同的租户过滤逻辑，
 * 通过 {@link JdbcTenantSupport#resolveTenantId()} 获取当前租户 ID。
 */
public class MybatisPlusUserRepositoryAdapter implements UserRepositoryPort {

    private final UserMapper userMapper;
    private final JdbcTemplate jdbcTemplate;

    public MybatisPlusUserRepositoryAdapter(UserMapper userMapper, DataSource dataSource) {
        this.userMapper = Objects.requireNonNull(userMapper, "userMapper must not be null");
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public Optional<UserRecord> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        LambdaQueryWrapper<UserDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDO::getId, id)
                .eq(UserDO::getTenantId, JdbcTenantSupport.resolveTenantId());
        UserDO entity = userMapper.selectOne(wrapper);
        return entity != null ? Optional.of(toDomain(entity)) : Optional.empty();
    }

    @Override
    public Optional<UserRecord> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        LambdaQueryWrapper<UserDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDO::getUsername, username)
                .eq(UserDO::getTenantId, JdbcTenantSupport.resolveTenantId());
        UserDO entity = userMapper.selectOne(wrapper);
        return entity != null ? Optional.of(toDomain(entity)) : Optional.empty();
    }

    @Override
    public boolean usernameExists(String username, Long excludedId) {
        if (username == null || username.isBlank()) {
            return false;
        }
        LambdaQueryWrapper<UserDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDO::getUsername, username)
                .eq(UserDO::getTenantId, JdbcTenantSupport.resolveTenantId());
        if (excludedId != null) {
            wrapper.ne(UserDO::getId, excludedId);
        }
        return userMapper.selectCount(wrapper) > 0;
    }

    @Override
    public UserPage page(long current, long size, String keyword) {
        long actualCurrent = Math.max(current, 1);
        long actualSize = size <= 0 ? 10 : size;

        LambdaQueryWrapper<UserDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDO::getTenantId, JdbcTenantSupport.resolveTenantId());
        if (keyword != null && !keyword.isBlank()) {
            String trimmed = keyword.trim();
            wrapper.and(w -> w.like(UserDO::getUsername, trimmed)
                    .or().like(UserDO::getRole, trimmed));
        }
        wrapper.orderByDesc(UserDO::getUpdateTime);

        Page<UserDO> page = new Page<>(actualCurrent, actualSize);
        Page<UserDO> result = userMapper.selectPage(page, wrapper);

        List<UserRecord> records = result.getRecords().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());

        long total = result.getTotal();
        long pages = total == 0 ? 0 : (total + actualSize - 1) / actualSize;
        return new UserPage(records, total, actualSize, actualCurrent, pages);
    }

    @Override
    public Long create(UserCreateValues values) {
        Objects.requireNonNull(values, "values must not be null");
        UserDO entity = new UserDO();
        entity.setId(JdbcMemorySupport.nextId());
        entity.setUsername(values.username());
        entity.setPassword(values.password());
        entity.setRole(values.role());
        entity.setAvatar(values.avatar());
        entity.setTenantId(JdbcTenantSupport.resolveTenantId());
        entity.setDeleted(0);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        userMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public boolean update(Long id, UserUpdateValues values) {
        if (id == null) {
            return false;
        }
        Objects.requireNonNull(values, "values must not be null");

        LambdaUpdateWrapper<UserDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UserDO::getId, id)
                .eq(UserDO::getTenantId, JdbcTenantSupport.resolveTenantId());

        if (values.username() != null) {
            wrapper.set(UserDO::getUsername, values.username());
        }
        if (values.password() != null) {
            wrapper.set(UserDO::getPassword, values.password());
        }
        if (values.role() != null) {
            wrapper.set(UserDO::getRole, values.role());
        }
        if (values.avatar() != null) {
            wrapper.set(UserDO::getAvatar, values.avatar().isBlank() ? null : values.avatar().trim());
        }
        wrapper.set(UserDO::getUpdateTime, LocalDateTime.now());

        return userMapper.update(null, wrapper) > 0;
    }

    @Override
    public boolean delete(Long id) {
        if (id == null) {
            return false;
        }
        LambdaUpdateWrapper<UserDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UserDO::getId, id)
                .eq(UserDO::getTenantId, JdbcTenantSupport.resolveTenantId())
                .set(UserDO::getUpdateTime, LocalDateTime.now());
        return userMapper.delete(wrapper) > 0;
    }

    private UserRecord toDomain(UserDO entity) {
        return new UserRecord(
                entity.getId(),
                entity.getUsername(),
                entity.getPassword(),
                entity.getRole(),
                entity.getAvatar(),
                entity.getTenantId(),
                toInstant(entity.getCreateTime()),
                toInstant(entity.getUpdateTime()));
    }

    private Instant toInstant(LocalDateTime ldt) {
        return ldt == null ? null : ldt.atZone(ZoneId.systemDefault()).toInstant();
    }
}
