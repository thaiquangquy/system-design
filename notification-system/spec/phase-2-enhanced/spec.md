# Phase 2 — Enhanced: Spec

Maps to `design.md` → "System design enhanced". Goal: remove the single
point of failure and the performance bottleneck from Phase 1 by decoupling
request intake from provider delivery, and by externalizing state so the
notification service can scale horizontally.

Builds on [Phase 1](../phase-1-simple/spec.md) — same entities and channels,
different delivery path.

## In scope

- Externalize DB (Postgres) and cache (Redis) — no longer implicit/local to
  one instance.
- Introduce one message queue per notification type: push-ios, push-android
  (or a combined push queue keyed by platform), sms, email.
- `notification-service` becomes a thin producer: validate request, resolve
  user/device/settings via cache-aside (Redis → Postgres on miss), publish an
  event to the right queue, return `202 Accepted` immediately (soft
  real-time — see design.md requirement).
- New `notification-worker` service(s): consume from queues, call the
  provider interfaces from Phase 1, independently scalable and deployable
  per channel.
- `notification-service` can now run as multiple instances behind a load
  balancer (stateless — all state in Postgres/Redis).

## Out of scope (deferred to Phase 3)

- Retry-on-failure, dedupe, notification log/persistence of delivery status.
- Templates, opt-out settings enforcement, rate limiting, auth,
  analytics/event tracking.

## API

Same three endpoints as Phase 1 (`POST /v1/notifications/{push|sms|email}`),
same request shape. Response changes to reflect async acceptance:

```json
{ "status": "QUEUED", "notification_id": "uuid" }
```

`notification_id` is generated at intake time and carried through the queue
event so downstream phases can correlate logs/retries/analytics against it.

## Data flow

```
Client → notification-service → Redis/Postgres (lookup) → Queue → Worker → Provider → Device
```

## Non-functional requirements

- Soft real-time: brief queue delay under load is acceptable; no delay
  budget enforced yet (formalized via rate limiting/monitoring in Phase 3).
- `notification-service` must be stateless so instances can be added/removed
  freely.
- Each channel's queue is independent — a backlog in SMS must not block push
  or email.

## Definition of done

- Publishing a notification via the API results in a message on the correct
  queue, consumed by a worker, and delivered via the same stub providers
  from Phase 1.
- `notification-service` runs with 2+ instances behind a local load balancer
  (or documented as horizontally scalable — no shared local state) without
  behavior change.
- Cache-aside verified: a second lookup for the same user/device hits Redis,
  not Postgres (observable via logs/metrics in a test).
