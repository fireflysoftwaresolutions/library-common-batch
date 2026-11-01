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


package io.fireflysoftware.common.batch.scheduling;

import io.fireflysoftware.common.batch.api.BatchJob;
import io.fireflysoftware.common.batch.api.ScheduledBatch;
import io.fireflysoftware.common.batch.audit.BatchAuditRecord;
import io.fireflysoftware.common.batch.audit.BatchAuditService;
import io.fireflysoftware.common.batch.lock.DistributedLock;
import io.fireflysoftware.common.batch.lock.DistributedLockManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

import java.time.Duration;
import java.util.Optional;

/**
 * Central scheduling service that registers and runs batch jobs according to their metadata.
 */
public class BatchSchedulerServiceImpl {

    private static final Logger log = LoggerFactory.getLogger(BatchSchedulerServiceImpl.class);

    private static final Duration DEFAULT_LOCK_AT_MOST_FOR = Duration.ofMinutes(10);
    private static final Duration DEFAULT_LOCK_AT_LEAST_FOR = Duration.ZERO;

    private final TaskScheduler taskScheduler;
    private final BatchAuditService auditService;
    private final DistributedLockManager lockManager; // optional
    private final Duration lockAtMostFor;
    private final Duration lockAtLeastFor;

    /**
     * Creates a scheduler service without distributed locking.
     */
    public BatchSchedulerServiceImpl(TaskScheduler taskScheduler, BatchAuditService auditService) {
        this(taskScheduler, auditService, null, DEFAULT_LOCK_AT_MOST_FOR, DEFAULT_LOCK_AT_LEAST_FOR);
    }

    /**
     * Creates a scheduler service with an explicit DistributedLockManager.
     */
    public BatchSchedulerServiceImpl(TaskScheduler taskScheduler, BatchAuditService auditService, DistributedLockManager lockManager) {
        this(taskScheduler, auditService, lockManager, DEFAULT_LOCK_AT_MOST_FOR, DEFAULT_LOCK_AT_LEAST_FOR);
    }

    /**
     * Full-args constructor allowing to customize locking durations.
     *
     * @param taskScheduler scheduler used to trigger jobs based on cron
     * @param auditService  audit service to record executions
     * @param lockManager   optional distributed lock manager (null to disable)
     * @param lockAtMostFor default maximum lock duration
     * @param lockAtLeastFor default minimum lock duration
     */
    public BatchSchedulerServiceImpl(TaskScheduler taskScheduler, BatchAuditService auditService, DistributedLockManager lockManager,
                                 Duration lockAtMostFor, Duration lockAtLeastFor) {
        this.taskScheduler = taskScheduler;
        this.auditService = auditService;
        this.lockManager = lockManager;
        this.lockAtMostFor = lockAtMostFor != null ? lockAtMostFor : DEFAULT_LOCK_AT_MOST_FOR;
        this.lockAtLeastFor = lockAtLeastFor != null ? lockAtLeastFor : DEFAULT_LOCK_AT_LEAST_FOR;
    }

    /**
     * Registers a job using its @ScheduledBatch metadata; no scheduling occurs if disabled.
     *
     * @param job  the job implementation
     * @param meta annotation metadata found on the job class
     */
    public void registerJob(BatchJob job, ScheduledBatch meta) {
        if (meta == null) {
            log.warn("Attempted to register job {} without @ScheduledBatch annotation; skipping.", job.getName());
            return;
        }
        if (!meta.enabled()) {
            log.info("Job {} is disabled; not scheduling.", job.getName());
            return;
        }
        log.info("Registering scheduled job name='{}' cron='{}'", job.getName(), meta.cron());
        taskScheduler.schedule(() -> runJob(job), new CronTrigger(meta.cron()));
    }

    private void runJob(BatchJob job) {
        if (lockManager == null) {
            log.debug("Executing job '{}' without distributed lock", job.getName());
            executeWithAudit(job);
            return;
        }
        String key = "batch:" + job.getName();
        Optional<DistributedLock> lock = lockManager.tryLock(key, DEFAULT_LOCK_AT_MOST_FOR, DEFAULT_LOCK_AT_LEAST_FOR);
        if (lock.isEmpty()) {
            log.debug("Skipping execution of job '{}' because distributed lock '{}' is held.", job.getName(), key);
            return;
        }
        try (DistributedLock acquired = lock.get()) {
            log.debug("Executing job '{}' with acquired lock '{}'", job.getName(), key);
            executeWithAudit(job);
        } catch (Exception e) {
            // executeWithAudit handles auditing; just log here if unlock fails
            log.warn("Exception during job '{}' execution or unlock: {}", job.getName(), e.getMessage(), e);
        }
    }

    private void executeWithAudit(BatchJob job) {
        log.debug("Starting audited execution for job '{}'", job.getName());
        BatchAuditRecord record = auditService.start(job.getName());
        try {
            job.execute();
            auditService.success(record);
            log.debug("Finished job '{}' successfully", job.getName());
        } catch (Exception e) {
            auditService.fail(record, e);
            log.warn("Job '{}' failed: {}", job.getName(), e.toString());
        }
    }
}
