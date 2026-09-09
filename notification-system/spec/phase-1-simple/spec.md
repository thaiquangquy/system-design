# Phase 1 — Simple: Spec

Maps to `design.md` → "System Design simple". Goal: one deployable service
that proves the end-to-end flow (client → notification service → 3rd-party
provider → device) works, with no scaling, queueing, or reliability concerns
yet.

## In scope

- Single Spring Boot service, single instance.
- Three channels: push (iOS via APNs, Android via FCM), SMS, email.
- Synchronous send: request comes in, service calls the 3rd-party provider
  directly, returns the outcome in the HTTP response.
- Contact info storage: users and their devices, phone, email.
- Provider integration built behind an interface per channel so a real
  provider client can be swapped in later without touching callers
  (design.md: "Need to support multiple 3rd servers with easy to extend").

## Out of scope (deferred to later phases)

- Message queues, workers, async processing → Phase 2.
- Redis/shared cache → Phase 2.
- Retry, dedupe, notification log/persistence of send history → Phase 3.
- Notification templates, opt-out settings, rate limiting, auth
  (appKey/appSecret), analytics/event tracking → Phase 3.
- Horizontal scaling / load balancing → Phase 2.

## Data model

**User**

| field | type | notes |
|---|---|---|
| id | UUID/Long | PK |
| email | string | nullable if user has no email channel |
| phone | string | nullable, E.164 format |

**Device** (1 user → N devices)

| field | type | notes |
|---|---|---|
| id | UUID/Long | PK |
| user_id | FK → User | |
| platform | enum(IOS, ANDROID) | |
| token | string | device push token |

## API

Generic send endpoint, one per channel (mirrors design.md's sample API,
generalized across channels):

```
POST /v1/notifications/push
POST /v1/notifications/sms
POST /v1/notifications/email
```

Request body (shape from design.md, channel-specific fields optional per type):

```json
{
  "to": [{ "user_id": 123 }],
  "from": { "email": "" },
  "subject": "",
  "content": [{ "type": "text/plain", "value": "" }]
}
```

Response:

```json
{
  "status": "SENT" | "FAILED",
  "provider_message_id": "string, nullable",
  "error": "string, nullable"
}
```

- `user_id` resolves to `User` → its `Device`(s) for push, or `email`/`phone`
  for email/SMS. If the user has no contact info for that channel, respond
  `400 Bad Request`.
- No auth on these endpoints in this phase (trusted internal callers only).

## Non-functional requirements

- Basic field validation (email format, phone format, non-empty content).
- No SLA on latency beyond "reasonable" — synchronous call chain, no async.
- No persistence of notification history — logs (via SLF4J) are the only
  record of what was sent, for this phase.

## Definition of done

- All three endpoints work end-to-end against provider clients that can be
  stubbed/mocked (real provider credentials not required to demo).
- Unit tests cover controller + service layers per repo test rules.
- A `docker-compose.yml` (or embedded H2) is enough to run the service
  locally with zero external dependencies besides the DB.
