# Notification System — Spec & Plan Index

Source of truth for requirements: [`../design.md`](../design.md).
This folder breaks that design into three implementable phases. Each phase has
a `spec.md` (what to build, contracts, data model, non-goals) and a `plan.md`
(how to build it: module layout, ordered tasks, tests, definition of done).

Work the phases in order. Do not start a phase's plan until the previous
phase's definition of done is met — each phase's plan assumes the previous
phase's code already exists and only adds/modifies what's listed.

| Phase | Maps to design.md section | Theme |
|---|---|---|
| [Phase 1 — Simple](phase-1-simple/spec.md) | "System Design simple" | Single server, synchronous send, prove the end-to-end flow |
| [Phase 2 — Enhanced](phase-2-enhanced/spec.md) | "System design enhanced" | Decouple with queues/workers, externalize DB/cache, horizontal scale |
| [Phase 3 — Final](phase-3-final/spec.md) | "System design deep dive" + "Final design" | Reliability, templates, settings/opt-out, rate limiting, auth, analytics |

## Tech stack (applies to all phases)

Per repo root `CLAUDE.md`: Spring Boot 4.x, Java 25, Google Java style, `var`
for obvious types, interfaces over concrete classes (SOLID), constructor
injection only, `@ConfigurationProperties` records for config,
`@ExtendWith(MockitoExtension.class)` unit tests / `@SpringBootTest` +
Testcontainers only for real Spring/DB/queue behavior.

## Starting a new session on a phase

Open the phase's `spec.md` and `plan.md`, plus this README for cross-phase
context. Each plan is self-contained enough to execute without re-reading the
other phases.
