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
import io.fireflysoftware.common.batch.audit.R2dbcBatchAuditServiceImpl;
import io.fireflysoftware.common.batch.autoconfig.BatchAutoConfiguration;
import io.fireflysoftware.common.batch.autoconfig.FlywayBatchAuditAutoConfiguration;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryMetadata;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class AuditAutoConfigurationTests {

    private final ApplicationContextRunner baseRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(BatchAutoConfiguration.class, FlywayBatchAuditAutoConfiguration.class));

    @Test
    void defaultModeIsNoopEvenIfConnectionFactoryExists() {
        baseRunner.withUserConfiguration(CFConfig.class).run(ctx -> {
            assertThat(ctx).hasSingleBean(BatchAuditService.class);
            assertThat(ctx.getBean(BatchAuditService.class)).isInstanceOf(NoOpBatchAuditServiceImpl.class);
        });
    }

    @Test
    void r2dbcModeCreatesR2dbcAuditServiceWhenConnectionFactoryPresent() {
        baseRunner.withUserConfiguration(CFConfig.class)
                .withPropertyValues("firefly.batch.audit.mode=r2dbc")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(BatchAuditService.class);
                    assertThat(ctx.getBean(BatchAuditService.class)).isInstanceOf(R2dbcBatchAuditServiceImpl.class);
                });
    }

    @Test
    void r2dbcModeWithoutConnectionFactoryFallsBackToNoop() {
        baseRunner.withPropertyValues("firefly.batch.audit.mode=r2dbc").run(ctx -> {
            assertThat(ctx).hasSingleBean(BatchAuditService.class);
            assertThat(ctx.getBean(BatchAuditService.class)).isInstanceOf(NoOpBatchAuditServiceImpl.class);
        });
    }

    @Test
    void flywayCustomizerIsRegisteredWhenCreateTableEnabled() {
        baseRunner.withPropertyValues("firefly.batch.audit.create-table=true").run(ctx -> {
            assertThat(ctx.getBeansOfType(FlywayConfigurationCustomizer.class)).isNotEmpty();
            FlywayConfigurationCustomizer customizer = ctx.getBean(FlywayConfigurationCustomizer.class);
            // Smoke test: can call customize without exceptions with a bare FluentConfiguration
            customizer.customize(new FluentConfiguration());
        });
    }

    @Test
    void flywayCustomizerIsNotRegisteredByDefault() {
        baseRunner.run(ctx -> {
            assertThat(ctx.getBeansOfType(FlywayConfigurationCustomizer.class)).isEmpty();
        });
    }

    @Configuration
    static class CFConfig {
        @Bean
        ConnectionFactory connectionFactory() {
            return new DummyConnectionFactory();
        }
    }

    static class DummyConnectionFactory implements ConnectionFactory {
        @Override
        public reactor.core.publisher.Mono<io.r2dbc.spi.Connection> create() {
            return Mono.error(new UnsupportedOperationException("Not used in tests"));
        }
        @Override
        public ConnectionFactoryMetadata getMetadata() { return () -> "H2"; }
    }
}
