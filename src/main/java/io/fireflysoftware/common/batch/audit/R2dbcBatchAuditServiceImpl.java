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


package io.fireflysoftware.common.batch.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * R2DBC-based implementation of BatchAuditService using a repository instead of
 * hand-written SQL.
 */
public class R2dbcBatchAuditServiceImpl implements BatchAuditService {

    private static final Logger log = LoggerFactory.getLogger(R2dbcBatchAuditServiceImpl.class);

    private final R2dbcBatchAuditRepositoryImpl repository;

    public R2dbcBatchAuditServiceImpl(R2dbcBatchAuditRepositoryImpl repository) {
        this.repository = repository;
        log.info("Initialized R2dbcBatchAuditService with repository {}", repository.getClass().getSimpleName());
    }

    @Override
    public BatchAuditRecord start(String jobName) {
        BatchAuditRecord r = new BatchAuditRecord(jobName);
        repository.insert(r);
        log.debug("[AUDIT] start job='{}' id='{}' at={} status={}", jobName, r.getId(), r.getStartTime(), r.getStatus());
        return r;
    }

    @Override
    public void success(BatchAuditRecord record) {
        record.markSuccess();
        repository.update(record);
        log.debug("[AUDIT] success job='{}' id='{}' endTime={} status={}", record.getJobName(), record.getId(), record.getEndTime(), record.getStatus());
    }

    @Override
    public void fail(BatchAuditRecord record, Exception e) {
        record.markFailed(e);
        repository.update(record);
        log.warn("[AUDIT] fail job='{}' id='{}' endTime={} status={} error={}", record.getJobName(), record.getId(), record.getEndTime(), record.getStatus(), e.toString());
    }
}
