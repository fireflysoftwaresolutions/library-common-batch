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


package io.fireflysoftware.common.batch.lock;

import io.r2dbc.spi.ConnectionFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;

import java.time.Instant;

/**
 * Minimal repository for ShedLock table operations using Spring Data R2DBC mapping API.
 */
class R2dbcLockRepositoryImpl {

    private final R2dbcEntityTemplate template;

    R2dbcLockRepositoryImpl(ConnectionFactory connectionFactory) {
        this.template = new R2dbcEntityTemplate(connectionFactory);
    }

    /**
     * Try to insert a new lock row. Returns true if inserted, false if it already exists.
     */
    boolean tryInsert(ShedlockRecord record) {
        try {
            template.insert(record).block();
            return true;
        } catch (DataIntegrityViolationException e) {
            // Row already exists
            return false;
        }
    }

    /**
     * Try to acquire an existing (expired) lock by updating it if lock_until <= now.
     * Returns number of updated rows (0 or 1).
     */
    long updateIfExpired(String name, Instant now, Instant newLockUntil, String lockedBy) {
        Criteria c = Criteria.where("name").is(name)
                .and("lock_until").lessThanOrEquals(now);
        Update u = Update.update("lock_until", newLockUntil)
                .set("locked_at", now)
                .set("locked_by", lockedBy);
        Long updated = template.update(ShedlockRecord.class)
                .matching(Query.query(c))
                .apply(u)
                .block();
        return updated != null ? updated : 0L;
    }

    /**
     * Release the lock by setting lock_until to the atLeastUntil value. Only the current holder updates the row.
     */
    void release(String name, Instant atLeastUntil, String lockedBy) {
        Criteria c = Criteria.where("name").is(name)
                .and("locked_by").is(lockedBy);
        Update u = Update.update("lock_until", atLeastUntil);
        template.update(ShedlockRecord.class)
                .matching(Query.query(c))
                .apply(u)
                .block();
    }
}
