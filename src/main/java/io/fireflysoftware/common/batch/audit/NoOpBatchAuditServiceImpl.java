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
 * Default no-op implementation of BatchAuditService. Logs lifecycle events but does not persist them.
 */
public class NoOpBatchAuditServiceImpl implements BatchAuditService {

    private static final Logger log = LoggerFactory.getLogger(NoOpBatchAuditServiceImpl.class);

    @Override
    public BatchAuditRecord start(String jobName) {
        BatchAuditRecord record = new BatchAuditRecord(jobName);
        log.debug("[BatchAudit] START job={} id={}", jobName, record.getId());
        return record;
    }

    @Override
    public void success(BatchAuditRecord record) {
        record.markSuccess();
        log.debug("[BatchAudit] SUCCESS job={} id={} durationMs={}",
                record.getJobName(), record.getId(),
                record.getEndTime() != null ? (record.getEndTime().toEpochMilli() - record.getStartTime().toEpochMilli()) : null);
    }

    @Override
    public void fail(BatchAuditRecord record, Exception e) {
        record.markFailed(e);
        log.warn("[BatchAudit] FAIL job={} id={} err={}", record.getJobName(), record.getId(), e != null ? e.getMessage() : null, e);
    }
}
