# Phase 3 — Final: Spec

Maps to `design.md` → "System design deep dive" + "Final design". Goal: add
every remaining production concern called out in the design doc on top of
the decoupled architecture from [Phase 2](../phase-2-enhanced/spec.md).

## In scope

1. **Reliability**
   - Persist every notification to a `notification_log` table at intake
     time (status: `PENDING`), before it's queued.
   - Dedupe: worker checks `notification_log` (or a fast cache flag) by
     `notification_id` before sending; if already `SENT`, discard silently.
   - Retry: on provider failure, worker republishes the event back to its
     queue with a bounded retry count and backoff; after N failures, status
     → `FAILED` and the event moves to a dead-letter queue instead of
     retrying forever.
2. **Notification templates** — `notification_template` table (id, channel,
   body, CTA). Requests reference a `template_id` + variables instead of raw
   content; the worker renders the template before calling the provider.
3. **Notification settings / opt-out** — `notification_setting` table
   (user_id, channel, opted_in boolean). Checked at intake (fail fast) and
   again at the worker (defense in depth) before sending.
4. **Rate limiting** — cap notifications per user per channel per time
   window (e.g. sliding window, consistent with the `rate-limiter` project
   elsewhere in this repo). Enforced at intake; over-limit requests get
   `429 Too Many Requests`.
5. **Auth** — client services authenticate with `appKey` + `appSecret`
   (header-based). Invalid/missing credentials → `401 Unauthorized`.
6. **Monitoring** — expose queue depth / consumer lag metrics (Micrometer)
   so workers can be scaled up/down; not auto-scaling logic itself, just the
   metrics surface.
7. **Analytics / event tracking** — emit lifecycle events (`PENDING`,
   `SENT`, `DELIVERED`, `ERROR`, `CLICKED`, `UNSUBSCRIBED`) to an
   `notification-analytics` sink; a click-tracking endpoint records
   `CLICKED` when a user opens a tracked link/deep link.

## Out of scope

- A full analytics dashboard/BI layer — this phase only ensures events are
  captured and queryable, not visualized.
- Multi-region/DR — not called out in design.md, not addressed here.

## Data model additions

**notification_log**

| field | type |
|---|---|
| notification_id | UUID, PK |
| channel | enum |
| user_id | FK |
| status | enum(PENDING, SENT, FAILED, DELIVERED) |
| retry_count | int |
| created_at / updated_at | timestamp |

**notification_template**

| field | type |
|---|---|
| id | UUID, PK |
| channel | enum |
| body | text (supports variable placeholders) |
| cta | string, nullable |

**notification_setting**

| field | type |
|---|---|
| user_id | FK |
| channel | enum |
| opted_in | boolean |

**notification_event** (analytics)

| field | type |
|---|---|
| id | UUID, PK |
| notification_id | FK |
| event_type | enum(PENDING, SENT, DELIVERED, ERROR, CLICKED, UNSUBSCRIBED) |
| occurred_at | timestamp |

## API changes

- All `POST /v1/notifications/*` endpoints now require `X-App-Key` /
  `X-App-Secret` headers.
- Request body gains `template_id` + `template_vars` (replaces free-form
  `content` from earlier phases, or supports both — free-form path is kept
  for internal/system notifications without a template).
- New: `POST /v1/notifications/{notification_id}/click` — records a
  `CLICKED` analytics event.
- New: `GET /v1/notifications/{notification_id}/status` — reads
  `notification_log` for delivery status.

## Definition of done

- A duplicate request with the same `notification_id` results in exactly
  one delivery.
- A user who has opted out of a channel never receives a notification on
  that channel, verified by test.
- Exceeding the per-user rate limit on a channel returns `429`.
- Requests without valid `appKey`/`appSecret` are rejected with `401`.
- The `notification_log` for a given `notification_id` reflects its true
  lifecycle state after send/retry/failure.
- Analytics events are recorded for every lifecycle transition and for
  click-through.
