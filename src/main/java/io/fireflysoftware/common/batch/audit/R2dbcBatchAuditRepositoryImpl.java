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

import io.r2dbc.spi.ConnectionFactory;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;

/**
 * Repository for persisting BatchAuditRecord using Spring Data R2DBC mapping
 * without hand-written SQL.
 */
public class R2dbcBatchAuditRepositoryImpl {

    private final R2dbcEntityTemplate template;

    public R2dbcBatchAuditRepositoryImpl(ConnectionFactory connectionFactory) {
        this.template = new R2dbcEntityTemplate(connectionFactory);
    }

    public void insert(BatchAuditRecord record) {
        template.insert(record).block();
    }

    public void update(BatchAuditRecord record) {
        template.update(record).block();
    }
}
