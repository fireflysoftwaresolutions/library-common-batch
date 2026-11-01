/*
 * Copyright 2025 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package io.fireflysoftware.common.batch.autoconfig;

import io.fireflysoftware.common.batch.audit.BatchAuditService;
import io.fireflysoftware.common.batch.audit.R2dbcBatchAuditServiceImpl;
import io.fireflysoftware.common.batch.audit.NoOpBatchAuditServiceImpl;
import io.fireflysoftware.common.batch.audit.R2dbcBatchAuditRepositoryImpl;
import io.fireflysoftware.common.batch.config.BatchProperties;
import io.fireflysoftware.common.batch.lock.DistributedLockManager;
import io.fireflysoftware.common.batch.lock.ShedLockDistributedLockManagerImpl;
import io.fireflysoftware.common.batch.scheduling.BatchSchedulerServiceImpl;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.r2dbc.R2dbcLockProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@AutoConfiguration(after = R2dbcLockProviderAutoConfiguration.class)
@EnableConfigurationProperties(BatchProperties.class)
/**
 * Main auto-configuration that wires the scheduler, auditing, optional distributed locking,
 * and the registrar that discovers and registers annotated BatchJob beans.
 */
public class BatchAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(BatchAutoConfiguration.class);

    /**
     * Provides a default TaskScheduler if the application has not defined one.
     * Pool size and thread name prefix are taken from BatchProperties.
     */
    @Bean
    @ConditionalOnMissingBean(TaskScheduler.class)
    @ConditionalOnProperty(prefix = "firefly.batch", name = "enabled", havingValue = "true", matchIfMissing = true)
    public TaskScheduler batchTaskScheduler(BatchProperties props) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(props.getScheduler().getPoolSize());
        scheduler.setThreadNamePrefix(props.getScheduler().getThreadNamePrefix());
        scheduler.initialize();
        log.info("Initialized TaskScheduler with poolSize={} threadNamePrefix='{}'", props.getScheduler().getPoolSize(), props.getScheduler().getThreadNamePrefix());
        return scheduler;
    }

    /**
     * Creates a R2DBC-based BatchAuditService if auditing is enabled and a ConnectionFactory is available.
     * Falls back to NoOp implementation when disabled or no ConnectionFactory is present.
     */
    @Bean
    @ConditionalOnMissingBean(BatchAuditService.class)
    @ConditionalOnProperty(prefix = "firefly.batch.audit", name = "mode", havingValue = "r2dbc")
    public BatchAuditService r2dbcBatchAuditService(ObjectProvider<io.r2dbc.spi.ConnectionFactory> cfProvider, BatchProperties props) {
        if (!props.getAudit().isEnabled()) {
            log.info("Audit disabled by property firefly.batch.audit.enabled=false; using NoOpBatchAuditService");
            return new NoOpBatchAuditServiceImpl();
        }
        io.r2dbc.spi.ConnectionFactory cf = cfProvider.getIfAvailable();
        if (cf == null) {
            log.warn("No R2DBC ConnectionFactory found; falling back to NoOpBatchAuditService");
            return new NoOpBatchAuditServiceImpl();
        }
        R2dbcBatchAuditRepositoryImpl repo = new R2dbcBatchAuditRepositoryImpl(cf);
        log.info("Initialized R2dbcBatchAuditService (mode=r2dbc)");
        return new R2dbcBatchAuditServiceImpl(repo);
    }

    /**
     * Provides the default NoOp BatchAuditService when no other implementation is defined.
     */
    @Bean
    @ConditionalOnMissingBean(BatchAuditService.class)
    public BatchAuditService defaultBatchAuditService() {
        log.info("Initialized default NoOpBatchAuditService (mode=noop)");
        return new NoOpBatchAuditServiceImpl();
    }

    /**
     * Adapts an existing ShedLock LockProvider into the library's DistributedLockManager SPI.
     */
    @Bean
    @ConditionalOnMissingBean(DistributedLockManager.class)
    @ConditionalOnBean(LockProvider.class)
    @ConditionalOnProperty(prefix = "firefly.batch.lock", name = "enabled", havingValue = "true", matchIfMissing = true)
    public DistributedLockManager shedLockDistributedLockManager(LockProvider lockProvider) {
        log.info("Initialized ShedLockDistributedLockManager with LockProvider {}", lockProvider.getClass().getSimpleName());
        return new ShedLockDistributedLockManagerImpl(lockProvider);
    }

    /**
     * Creates the central BatchSchedulerService using the configured TaskScheduler,
     * chosen BatchAuditService, and optionally a DistributedLockManager.
     */
    @Bean
    @ConditionalOnMissingBean(BatchSchedulerServiceImpl.class)
    @ConditionalOnProperty(prefix = "firefly.batch", name = "enabled", havingValue = "true", matchIfMissing = true)
    public BatchSchedulerServiceImpl batchSchedulerService(TaskScheduler taskScheduler,
                                                       BatchAuditService auditService,
                                                       ObjectProvider<DistributedLockManager> lockManagerProvider,
                                                       BatchProperties props) {
        DistributedLockManager effectiveLockManager = props.getLock().isEnabled() ? lockManagerProvider.getIfAvailable() : null;
        log.info("Initialized BatchSchedulerService with auditService={} lockManager={} atMostFor={} atLeastFor={}",
                auditService.getClass().getSimpleName(),
                effectiveLockManager != null ? effectiveLockManager.getClass().getSimpleName() : "none",
                props.getLock().getAtMostFor(), props.getLock().getAtLeastFor());
        return new BatchSchedulerServiceImpl(taskScheduler, auditService, effectiveLockManager,
                props.getLock().getAtMostFor(), props.getLock().getAtLeastFor());
    }

    /**
     * Registers all beans that implement BatchJob and are annotated with @ScheduledBatch.
     */
    @Bean
    @ConditionalOnProperty(prefix = "firefly.batch", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ScheduledBatchRegistrarImpl scheduledBatchRegistrar(ApplicationContext applicationContext,
                                                          BatchSchedulerServiceImpl schedulerService,
                                                          BatchProperties props) {
        log.info("Initialized ScheduledBatchRegistrar (firefly.batch.enabled={}, scheduler.enabled={})", props.isEnabled(), props.getScheduler().isEnabled());
        return new ScheduledBatchRegistrarImpl(applicationContext, schedulerService, props);
    }
}
