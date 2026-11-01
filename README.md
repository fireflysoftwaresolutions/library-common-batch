<a href="https://fireflysoftware.io/openplatform/">
<picture>
  <source media="(max-width: 640px) and (prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/firefly-oss/.github/main/profile/assets/banner-narrow-dark.svg">
  <source media="(max-width: 640px)" srcset="https://raw.githubusercontent.com/firefly-oss/.github/main/profile/assets/banner-narrow-light.svg">
  <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/firefly-oss/.github/main/profile/assets/banner-dark.svg">
  <img src="https://raw.githubusercontent.com/firefly-oss/.github/main/profile/assets/banner-light.svg" alt="Firefly OpenPlatform - Library - Common Batch" width="100%">
</picture>
</a>

# Firefly OpenPlatform - Library - Common Batch

Developed by the Firefly Software Foundation. [dev@fireflysoftware.io](mailto:dev@fireflysoftware.io)

[Website](https://fireflysoftware.io/) · [OpenPlatform](https://fireflysoftware.io/openplatform/) · [Module catalogue](https://fireflysoftware.io/openplatform/modules/)

Utility library for scheduling and running batch jobs in Spring Boot microservices, featuring:
- Automatic job discovery and registration via annotations
- Optional execution auditing (NoOp or R2DBC persistence)
- Optional distributed locking based on ShedLock to avoid concurrent runs in a cluster
- Auto-configuration, centralized properties, and optional Flyway migrations


## Table of contents
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
  - [Maven](#maven)
- [Concepts and APIs](#concepts-and-apis)
  - [BatchJob](#batchjob)
  - [@ScheduledBatch](#scheduledbatch)
- [Basic configuration](#basic-configuration)
  - [Sample application.yml](#sample-applicationyml)
  - [Available properties](#available-properties)
- [Execution auditing](#execution-auditing)
  - [NoOp mode (default)](#noop-mode-default)
  - [R2DBC mode](#r2dbc-mode)
  - [Flyway migration for auditing](#flyway-migration-for-auditing)
- [Distributed locking](#distributed-locking)
  - [ShedLock requirements](#shedlock-requirements)
  - [Flyway migration for ShedLock](#flyway-migration-for-shedlock)
- [Usage examples](#usage-examples)
  - [Simple job](#simple-job)
  - [Enable R2DBC auditing](#enable-r2dbc-auditing)
  - [Configure distributed locking with JDBC](#configure-distributed-locking-with-jdbc)
- [Using it from external microservices](#using-it-from-external-microservices)
  - [Minimum steps](#minimum-steps)
  - [Customization and extensibility](#customization-and-extensibility)
- [Troubleshooting / FAQ](#troubleshooting--faq)
- [Compatibility](#compatibility)


## Features
- Automatic discovery of beans that implement `BatchJob` and are annotated with `@ScheduledBatch`.
- Scheduling via Cron using Spring's `TaskScheduler` (or a custom one if you define it).
- Auditing of start/success/failure of each execution.
- Optional integration with ShedLock (if a `LockProvider` is present in the context) to ensure only one instance runs a job at a time across the cluster.
- Configuration properties under the `firefly.batch.*` prefix.
- Optional inclusion of the library's Flyway migrations without losing your own configured locations.


## Requirements
- Java and Spring Boot (auto-configuration via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`).
- For persistent auditing: Spring Data R2DBC + your database's R2DBC driver.
- For distributed locking: nothing to add from your side — this library brings ShedLock core and the R2DBC provider transitively and will auto-create a `LockProvider` if a `ConnectionFactory` exists.
- If you prefer a different provider (e.g., JDBC), add the corresponding ShedLock provider and declare your own `LockProvider` bean (it will override the default).
- To apply library migrations: `flyway-core` on the classpath.


## Installation
### Maven
```xml
<dependency>
  <groupId>io.fireflysoftware</groupId>
  <artifactId>library-common-batch</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Concepts and APIs
### BatchJob
Base interface that defines a batch job:
```java
public interface BatchJob {
  String getName();
  void execute();
}
```
Any exception thrown from `execute()` will be audited as a failure.

### @ScheduledBatch
Annotation to indicate a `BatchJob` should be scheduled with cron:
```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ScheduledBatch {
  String cron();          // cron expression
  boolean enabled() default true;
  String description() default "";
}
```


## Basic configuration
Auto-configuration creates and configures the main beans when `firefly.batch.enabled=true` (default):
- Default `TaskScheduler` (if you don't define one) using `firefly.batch.scheduler.*`.
- Default `BatchAuditService`:
  - `NoOpBatchAuditServiceImpl` by default
  - `R2dbcBatchAuditServiceImpl` if `mode=r2dbc` and a `ConnectionFactory` + `R2dbcEntityTemplate` are available
- `DistributedLockManager` (ShedLock) if a `LockProvider` exists and `firefly.batch.lock.enabled=true`.
- Automatic job registration via `ScheduledBatchRegistrarImpl` if `firefly.batch.scheduler.enabled=true`.

### Sample application.yml
```yaml
spring:
  application:
    name: my-microservice

firefly:
  batch:
    enabled: true
    scheduler:
      enabled: true
      pool-size: 2
      thread-name-prefix: batch-scheduler-
    audit:
      enabled: true
      mode: noop   # noop | r2dbc
      table: batch_audit
      create-table: false # If true and Flyway on classpath, adds the library migration
    lock:
      enabled: true
      provider: auto
      create-table: false # If true and Flyway on classpath, adds the library migration
      at-most-for: 10m
      at-least-for: 0s
```

### Available properties
- `firefly.batch.enabled`: enable/disable the library (default true).
- `firefly.batch.scheduler.enabled`: enable automatic job registration (default true).
- `firefly.batch.scheduler.pool-size`: thread pool size for the internal `TaskScheduler` (default 2).
- `firefly.batch.scheduler.thread-name-prefix`: thread name prefix for the scheduler.
- `firefly.batch.audit.enabled`: enable/disable auditing (default true). If false, NoOp is always used.
- `firefly.batch.audit.mode`: `noop` (default) or `r2dbc`.
- `firefly.batch.audit.table`: audit table name (used by the persistent model).
- `firefly.batch.audit.create-table`: if true, adds the library Flyway location to create the table.
- `firefly.batch.lock.enabled`: enable/disable distributed locking (default true).
- `firefly.batch.lock.provider`: informational for now (`auto`/`shedlock`), the manager is enabled if a `LockProvider` exists.
- `firefly.batch.lock.at-most-for`: maximum lock duration (default 10m).
- `firefly.batch.lock.at-least-for`: minimum lock duration (default 0s).
- `firefly.batch.lock.create-table`: if true, adds the library Flyway location to create the ShedLock table.


## Execution auditing
### NoOp mode (default)
Does not persist anything, but the audit flow exists for future use. Useful if you don't need database traceability.

### R2DBC mode
Requirements:
- Dependencies: `spring-boot-starter-data-r2dbc` (or equivalents), your DB's R2DBC driver, and `io.r2dbc:r2dbc-spi` (already an optional dependency of this library).
- A `ConnectionFactory` bean in the context.
- Property `firefly.batch.audit.mode=r2dbc` and `firefly.batch.audit.enabled=true`.

The implementation uses `R2dbcEntityTemplate` to insert/update `BatchAuditRecord` entities.

### Flyway migration for auditing
If you add `flyway-core` and enable `firefly.batch.audit.create-table=true`, the library adds the location:
```
classpath:db/migration/firefly/batch
```
It contains the script `V1__create_batch_audit_table.sql` with a default table `batch_audit`.


## Distributed locking
The library will attempt to acquire a distributed lock before running each job if a ShedLock `LockProvider` exists in the context and `firefly.batch.lock.enabled=true`.
- The lock is acquired with key `batch:<jobName>`.
- Configurable durations `at-most-for` and `at-least-for` are used.

### ShedLock setup
By default, library-common-batch auto-configures a ShedLock `LockProvider` using R2DBC when:
- `firefly.batch.lock.enabled=true` (default)
- an `io.r2dbc.spi.ConnectionFactory` bean exists
- the ShedLock R2DBC provider is on the classpath (this library brings it transitively)

You do NOT need to declare a `LockProvider` bean in your microservice.

If you prefer a different provider (e.g., JDBC), simply declare your own `LockProvider` bean and it will override the default. Example with JDBC:
```java
@Configuration
class ShedLockConfig {
  @Bean
  public LockProvider lockProvider(DataSource dataSource) {
    return new JdbcTemplateLockProvider(
      JdbcTemplateLockProvider.Configuration.builder()
        .withJdbcTemplate(new JdbcTemplate(dataSource))
        .withTableName("shedlock")
        .build()
    );
  }
}
```

### Flyway migration for ShedLock
If you add `flyway-core` and enable `firefly.batch.lock.create-table=true`, the library adds the location:
```
classpath:db/migration/firefly/lock
```
It includes `V1__create_shedlock_table.sql` to create the `shedlock` table compatible with ShedLock's default schema (works with JDBC and R2DBC providers).


## Usage examples
### Simple job
```java
import org.springframework.stereotype.Component;
import io.fireflysoftware.common.batch.api.BatchJob;
import io.fireflysoftware.common.batch.api.ScheduledBatch;

@Component
@ScheduledBatch(
  cron = "0 0/5 * * * *",                // every 5 minutes
  description = "Generates the metrics report"
)
public class MetricsReportJob implements BatchJob {
  @Override
  public String getName() { return "metrics-report"; }

  @Override
  public void execute() {
    // Job logic
    System.out.println("Generating metrics report...");
  }
}
```
When the microservice starts, the job is registered automatically and scheduled using the configured `TaskScheduler`.

### Enable R2DBC auditing
```xml
<!-- pom.xml (or build.gradle.kts) -->
<!-- Typical dependencies -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-r2dbc</artifactId>
</dependency>
<dependency>
  <groupId>io.r2dbc</groupId>
  <artifactId>r2dbc-postgresql</artifactId> <!-- or your DB driver -->
</dependency>
<dependency>
  <groupId>org.flywaydb</groupId>
  <artifactId>flyway-core</artifactId>
</dependency>
```
```yaml
# application.yml
spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/app
    username: user
    password: pass

firefly:
  batch:
    audit:
      enabled: true
      mode: r2dbc
      create-table: true # add the library's audit migration
```

### Configure distributed locking with JDBC
```xml
<dependency>
  <groupId>net.javacrumbs.shedlock</groupId>
  <artifactId>shedlock-core</artifactId>
</dependency>
<dependency>
  <groupId>net.javacrumbs.shedlock</groupId>
  <artifactId>shedlock-provider-jdbc-template</artifactId>
</dependency>
<dependency>
  <groupId>org.flywaydb</groupId>
  <artifactId>flyway-core</artifactId>
</dependency>
```
```java
@Configuration
class ShedLockConfig {
  @Bean
  LockProvider lockProvider(DataSource dataSource) {
    return new JdbcTemplateLockProvider(
      JdbcTemplateLockProvider.Configuration.builder()
        .withJdbcTemplate(new JdbcTemplate(dataSource))
        .withTableName("shedlock")
        .build()
    );
  }
}
```
```yaml
firefly:
  batch:
    lock:
      enabled: true
      create-table: true # add the library's shedlock migration
      at-most-for: 10m
      at-least-for: 0s
```


## Using it from external microservices
### Minimum steps
1. Add the `io.fireflysoftware:library-common-batch` dependency to your project.
2. Define one or more beans that implement `BatchJob` and annotate them with `@ScheduledBatch`.
3. (Optional) Enable persistent auditing with R2DBC if you need database traceability.
4. (Optional) Override the default `LockProvider` if you prefer a different provider (e.g., JDBC). By default, an R2DBC `LockProvider` is auto-configured when a `ConnectionFactory` exists.
5. (Optional) Set `create-table` in `audit`/`lock` and add `flyway-core` to include the library migrations.

No manual registration is required: the library detects and schedules jobs during context initialization.

### Customization and extensibility
- Custom `TaskScheduler`: define a `TaskScheduler` bean and the auto-configuration will not create the default one.
- Custom `BatchAuditService`: define your own bean and the library's one will be ignored (`@ConditionalOnMissingBean`).
- Disable everything: `firefly.batch.enabled=false`.
- Disable only job registration: `firefly.batch.scheduler.enabled=false`.
- Disable auditing: `firefly.batch.audit.enabled=false`.
- Disable distributed locking: `firefly.batch.lock.enabled=false`.


## Troubleshooting / FAQ
- "My job is not scheduled": ensure your bean implements `BatchJob` and the class (proxy target if applicable) has `@ScheduledBatch`.
- "It runs on all instances": that's expected; use ShedLock (`LockProvider`) so only one instance executes per window.
- "R2DBC does not work": ensure a `ConnectionFactory` is configured and `spring-data-r2dbc`/driver is on the classpath.
- "Tables are not created": add `flyway-core` and enable `create-table` on the corresponding property.
- "I want to change the thread pool": use `firefly.batch.scheduler.pool-size` or define your own `TaskScheduler`.


## Compatibility
- Tested with Spring Boot (auto-config). Requires modern Spring versions that support AutoConfiguration via `imports`.
- The provided migrations are generic (simple SQL) and should work in most DBs supported by the providers (verify types if you use a specific DB).

---

Suggestions or improvements? Open an issue or a PR in the corresponding repository.
