# Phase 2 — Enhanced: Plan

Implements [`spec.md`](spec.md). Modifies `notification-service` from Phase 1
and adds a new `notification-worker` module. Adds infra via
`docker-compose.yml` at `notification-system/` root (Postgres, Redis, and a
Kafka broker — recommended over RabbitMQ here for per-partition ordering and
easy replay, matching "each notification type has a distinct message
queue").

## Module layout changes

```
notification-service/            (from Phase 1, modified)
  service/      NotificationService impls now publish to Kafka instead of
                calling provider interfaces directly
  cache/        UserCacheService (cache-aside: Redis → UserRepository)
  messaging/    NotificationEventProducer, NotificationEvent (DTO)
  config/       KafkaProducerConfig, RedisConfig

notification-worker/             (new module)
  src/main/java/.../worker/
    consumer/   PushEventConsumer, SmsEventConsumer, EmailEventConsumer
    service/    reuses provider interfaces (PushProvider, SmsProvider,
                EmailProvider) — copy or extract to a shared module if
                duplication becomes a problem
    config/     KafkaConsumerConfig
```

Consider extracting `domain`, `provider`, and `dto` packages from Phase 1
into a shared `notification-common` module now that two services need them,
if duplication is more than trivial.

## Infra

Add to `notification-system/docker-compose.yml`:
- `postgres` (replaces H2)
- `redis`
- `kafka` (+ `zookeeper` or KRaft mode) with topics: `notification.push`,
  `notification.sms`, `notification.email`

## Tasks

1. Add Postgres + Redis + Kafka to `docker-compose.yml`; switch
   `application.yml` prod/default profile from H2 to Postgres.
2. Add `NotificationEvent` DTO (notification_id, channel, user_id, payload)
   — this is what goes on the queue.
3. Add `NotificationEventProducer` (Kafka producer) — one `send` method,
   topic selected by channel.
4. Add `UserCacheService`: cache-aside wrapper around `UserRepository`/
   `DeviceRepository` using `RedisTemplate` or Spring Cache (`@Cacheable`),
   keyed by `user_id`.
5. Modify Phase 1 `NotificationService` impls: generate `notification_id`,
   look up user/device via `UserCacheService` (not repo directly), publish
   `NotificationEvent` via producer, return `QUEUED` response — remove the
   direct provider calls from this module.
6. Scaffold `notification-worker` module (Spring Boot, Spring Kafka).
7. Move/reuse `PushProvider`/`SmsProvider`/`EmailProvider` interfaces + stub
   impls into the worker (or shared module per note above).
8. Implement `PushEventConsumer`/`SmsEventConsumer`/`EmailEventConsumer`:
   `@KafkaListener` per topic, deserialize `NotificationEvent`, call the
   matching provider.
9. Update Phase 1 unit tests: `NotificationService` tests now mock
   `UserCacheService` + `NotificationEventProducer` instead of repos/providers
   directly.
10. Add integration tests for the worker: Testcontainers Kafka, publish a
    test event, assert the stub provider was invoked.

## Testing

- Unit: `notification-service` service layer mocks cache + producer
  (`@ExtendWith(MockitoExtension.class)`).
- Integration (`*IT`, `@SpringBootTest` + Testcontainers): end-to-end
  produce → consume → provider-called flow, and a cache-hit-vs-miss test
  against real Redis.

## Definition of done

- `docker-compose up` brings up Postgres, Redis, Kafka, `notification-service`
  (2 instances), and `notification-worker`.
- POST to any Phase 1 endpoint returns `202 QUEUED`, and the corresponding
  worker log shows the stub provider was called.
- Stopping/restarting a worker does not lose in-flight messages (Kafka
  consumer group offset behavior verified manually or via test).
