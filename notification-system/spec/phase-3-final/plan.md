# Phase 3 — Final: Plan

Implements [`spec.md`](spec.md). Modifies `notification-service` and
`notification-worker` from Phase 2; adds a `notification-analytics` module.
This phase has the most moving parts — work the sub-sections in order (each
is independently testable) rather than all at once.

## Module layout changes

```
notification-service/
  domain/       + NotificationLog, NotificationTemplate, NotificationSetting
  repository/   + NotificationLogRepository, NotificationTemplateRepository,
                  NotificationSettingRepository
  ratelimit/    RateLimiter (interface) + SlidingWindowRateLimiter impl
  security/     AppCredentialFilter (validates X-App-Key/X-App-Secret)
  config/       AppCredentialProperties (@ConfigurationProperties record)

notification-worker/
  service/      TemplateRenderer, DedupeChecker, RetryPolicy
  consumer/     *EventConsumer updated: dedupe check → render template →
                send → update notification_log → emit analytics event →
                retry/DLQ on failure

notification-analytics/          (new module)
  consumer/     AnalyticsEventConsumer (Kafka topic: notification.events)
  domain/       NotificationEvent (analytics entity, distinct from the
                messaging DTO of the same conceptual purpose)
  repository/   NotificationEventRepository
  controller/   GET endpoints for querying events (internal/admin use)
```

## Tasks — Reliability (dedupe, retry, persistence)

1. Add `NotificationLog` entity + repository; write a `PENDING` row in
   `notification-service` at intake, before publishing to the queue.
2. Add `DedupeChecker` in the worker: look up `notification_log` by id
   before sending; short-circuit if status is already `SENT`/`DELIVERED`.
3. Add `RetryPolicy`: on provider failure, increment `retry_count`,
   republish to the same topic (or a `*.retry` topic with a delay) up to a
   max; beyond max, mark `FAILED` and publish to a `*.dlq` topic instead.
4. Update `notification_log` status transitions from the worker after each
   outcome (`SENT`, `DELIVERED` if the provider confirms, `FAILED`).
5. Add `GET /v1/notifications/{id}/status` reading `notification_log`.

## Tasks — Templates

6. Add `NotificationTemplate` entity + repository + seed data/migration.
7. Add `TemplateRenderer` in the worker (simple variable substitution, e.g.
   `{{name}}` placeholders) — no templating engine dependency needed unless
   requirements grow.
8. Extend request DTO with `template_id` + `template_vars`; worker renders
   before calling the provider. Keep the Phase 1/2 free-form `content` path
   working for callers that don't use templates.

## Tasks — Settings / opt-out

9. Add `NotificationSetting` entity + repository + a simple
   `POST /v1/users/{id}/settings` endpoint to opt in/out per channel.
10. Check `NotificationSetting` in `notification-service` at intake (fail
    fast with a clear response) and again in the worker before sending
    (defense in depth against stale cache).

## Tasks — Rate limiting

11. Add `RateLimiter` interface + `SlidingWindowRateLimiter` impl (reuse the
    algorithm from `rate-limiter/api-gateway-ratelimiter` in this repo if its
    logic is directly portable — otherwise reimplement the sliding-window
    counter against Redis, since Redis is already available from Phase 2).
12. Enforce in `notification-service` at intake, keyed by
    `(user_id, channel)`; return `429` when exceeded.

## Tasks — Auth

13. Add `AppCredentialProperties` record + `AppCredentialFilter`
    (`OncePerRequestFilter`) validating `X-App-Key`/`X-App-Secret` against
    configured/stored app credentials; reject with `401` before controller
    logic runs.

## Tasks — Monitoring

14. Add Micrometer + `/actuator/prometheus` to `notification-service` and
    `notification-worker`; expose Kafka consumer lag (via Micrometer's
    Kafka binder) as the key scaling signal called out in design.md.

## Tasks — Analytics / event tracking

15. Scaffold `notification-analytics` module; add `NotificationEvent`
    entity + repository.
16. From `notification-service` and `notification-worker`, publish a
    lifecycle event (`PENDING` at intake, `SENT`/`FAILED` post-send) to a
    `notification.events` Kafka topic.
17. Add `AnalyticsEventConsumer` in `notification-analytics` persisting
    each event.
18. Add `POST /v1/notifications/{id}/click` in `notification-service`
    (or directly in `notification-analytics` if click traffic should bypass
    the main service) publishing a `CLICKED` event.

## Testing

- Unit (`@ExtendWith(MockitoExtension.class)`): `DedupeChecker`,
  `RetryPolicy`, `TemplateRenderer`, `RateLimiter` impl, `AppCredentialFilter`
  — each in isolation.
- Integration (`*IT`, Testcontainers: Postgres, Redis, Kafka): full
  intake → dedupe → template render → send → log update → analytics event
  flow; a retry-then-succeed scenario; a rate-limit-exceeded scenario; an
  opted-out-user scenario.

## Definition of done

Matches the "Definition of done" list in [`spec.md`](spec.md) — all six
bullets verified by an integration test, not just manual checking.
