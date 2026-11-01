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


package io.fireflysoftware.common.batch;

import io.fireflysoftware.common.batch.audit.BatchAuditService;
import io.fireflysoftware.common.batch.audit.NoOpBatchAuditServiceImpl;
import io.fireflysoftware.common.batch.autoconfig.BatchAutoConfiguration;
import io.fireflysoftware.common.batch.autoconfig.ScheduledBatchRegistrarImpl;
import io.fireflysoftware.common.batch.scheduling.BatchSchedulerServiceImpl;
import io.fireflysoftware.common.batch.testsupport.CapturingTaskScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;

class AutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(BatchAutoConfiguration.class));

    @Test
    void defaultBeansAreProvided() {
        contextRunner.run(ctx -> {
            assertThat(ctx).hasSingleBean(TaskScheduler.class);
            assertThat(ctx.getBean(TaskScheduler.class)).isInstanceOf(ThreadPoolTaskScheduler.class);

            assertThat(ctx).hasSingleBean(BatchAuditService.class);
            assertThat(ctx.getBean(BatchAuditService.class)).isInstanceOf(NoOpBatchAuditServiceImpl.class);

            assertThat(ctx).hasSingleBean(BatchSchedulerServiceImpl.class);
            assertThat(ctx).hasSingleBean(ScheduledBatchRegistrarImpl.class);
        });
    }

    @Test
    void userProvidedAuditServiceOverridesDefault() {
        contextRunner.withUserConfiguration(CustomAuditConfig.class).run(ctx -> {
            assertThat(ctx).hasSingleBean(BatchAuditService.class);
            assertThat(ctx.getBean(BatchAuditService.class).getClass().getName()).isEqualTo(CustomAuditService.class.getName());
            assertThat(ctx.getBeansOfType(NoOpBatchAuditServiceImpl.class)).isEmpty();
        });
    }

    @Test
    void userProvidedTaskSchedulerOverridesDefault() {
        contextRunner.withUserConfiguration(CustomSchedulerConfig.class).run(ctx -> {
            assertThat(ctx).hasSingleBean(TaskScheduler.class);
            assertThat(ctx.getBean(TaskScheduler.class)).isInstanceOf(CapturingTaskScheduler.class);
            assertThat(ctx.getBeansOfType(ThreadPoolTaskScheduler.class)).isEmpty();
        });
    }

    @Configuration
    static class CustomAuditConfig {
        @Bean
        BatchAuditService customAuditService() {
            return new CustomAuditService();
        }
    }

    static class CustomAuditService implements BatchAuditService {
        @Override
        public io.fireflysoftware.common.batch.audit.BatchAuditRecord start(String jobName) {
            return new io.fireflysoftware.common.batch.audit.BatchAuditRecord(jobName);
        }
        @Override
        public void success(io.fireflysoftware.common.batch.audit.BatchAuditRecord record) { }
        @Override
        public void fail(io.fireflysoftware.common.batch.audit.BatchAuditRecord record, Exception e) { }
    }

    @Configuration
    static class CustomSchedulerConfig {
        @Bean
        TaskScheduler taskScheduler() {
            return new CapturingTaskScheduler();
        }
    }
}