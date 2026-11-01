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

import io.fireflysoftware.common.batch.api.BatchJob;
import io.fireflysoftware.common.batch.api.ScheduledBatch;
import io.fireflysoftware.common.batch.config.BatchProperties;
import io.fireflysoftware.common.batch.scheduling.BatchSchedulerServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.util.Map;

/**
 * Scans for beans implementing BatchJob and annotated with @ScheduledBatch,
 * registering them with the BatchSchedulerService when the context is ready.
 */
public class ScheduledBatchRegistrarImpl implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(ScheduledBatchRegistrarImpl.class);

    private final ApplicationContext applicationContext;
    private final BatchSchedulerServiceImpl schedulerService;
    private final BatchProperties properties;

    /**
     * Creates a registrar that will discover and register BatchJob beans after the context is ready.
     */
    public ScheduledBatchRegistrarImpl(ApplicationContext applicationContext, BatchSchedulerServiceImpl schedulerService, BatchProperties properties) {
        this.applicationContext = applicationContext;
        this.schedulerService = schedulerService;
        this.properties = properties;
    }

    /**
     * Lifecycle callback invoked after all singletons are created; performs job discovery and registration.
     */
    @Override
    public void afterSingletonsInstantiated() {
        if (!properties.isEnabled() || !properties.getScheduler().isEnabled()) {
            log.info("Batch scheduling is disabled by configuration (firefly.batch.enabled={}, firefly.batch.scheduler.enabled={}). Skipping job registration.",
                    properties.isEnabled(), properties.getScheduler().isEnabled());
            return;
        }
        Map<String, BatchJob> jobs = applicationContext.getBeansOfType(BatchJob.class);
        if (jobs.isEmpty()) {
            log.info("No BatchJob beans found to register.");
            return;
        }
        jobs.forEach((beanName, job) -> {
            Class<?> targetClass = AopUtils.getTargetClass(job);
            ScheduledBatch meta = AnnotatedElementUtils.findMergedAnnotation(targetClass, ScheduledBatch.class);
            if (meta != null) {
                schedulerService.registerJob(job, meta);
            } else {
                log.debug("Bean '{}' of type {} is a BatchJob but not annotated with @ScheduledBatch; skipping.", beanName, targetClass.getName());
            }
        });
    }
}
