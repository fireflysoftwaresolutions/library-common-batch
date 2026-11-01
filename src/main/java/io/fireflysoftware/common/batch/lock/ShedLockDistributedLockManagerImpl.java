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

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.Duration;
import java.util.Optional;

/**
 * Adapter that uses ShedLock's LockProvider to implement DistributedLockManager.
 */
public class ShedLockDistributedLockManagerImpl implements DistributedLockManager {

    private static final Logger log = LoggerFactory.getLogger(ShedLockDistributedLockManagerImpl.class);

    private final LockProvider lockProvider;

    /**
     * Creates a manager that delegates locking to the given ShedLock LockProvider.
     */
    public ShedLockDistributedLockManagerImpl(LockProvider lockProvider) {
        this.lockProvider = lockProvider;
        log.info("Initialized ShedLockDistributedLockManager with provider {}", lockProvider.getClass().getSimpleName());
    }

    /**
     * Attempts to acquire a distributed lock using ShedLock for the provided key and durations.
     */
    @Override
    public Optional<DistributedLock> tryLock(String key, Duration lockAtMostFor, Duration lockAtLeastFor) {
        LockConfiguration cfg = new LockConfiguration(Instant.now(), key, lockAtMostFor, lockAtLeastFor);
        log.debug("[LOCK] Trying to acquire lock key='{}' atMostFor={} atLeastFor={}", key, lockAtMostFor, lockAtLeastFor);
        Optional<SimpleLock> simpleLock = lockProvider.lock(cfg);
        if (simpleLock.isEmpty()) {
            log.debug("[LOCK] Lock NOT acquired for key='{}'", key);
            return Optional.empty();
        }
        log.debug("[LOCK] Lock acquired for key='{}'", key);
        SimpleLock sl = simpleLock.get();
        return Optional.of(new DistributedLock() {
            @Override
            public void unlock() {
                try {
                    sl.unlock();
                    log.debug("[LOCK] Lock released for key='{}'", key);
                } catch (Exception e) {
                    log.warn("[LOCK] Error releasing lock for key='{}': {}", key, e.toString());
                    throw e;
                }
            }
        });
    }
}
