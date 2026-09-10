# Tech Debt

## UserCacheService: N+1 risk when multi-recipient batch sending is added

`UserCacheService.getUserContact(Long userId)` (notification-service/src/main/java/com/example/notification/cache/UserCacheService.java:19)
is only ever called once per request today (`request.firstRecipientUserId()` in
`EmailNotificationService`/`SmsNotificationService`/`PushNotificationService`, all line 28), so there's no N+1
currently. But `NotificationRequest.to()` is already a `List<RecipientRef>`, with a comment noting
"Phase 1 sends to a single recipient per request; the first entry wins" — implying multi-recipient batch
support is planned.

If a future implementation loops over `request.to()` calling `getUserContact` per recipient, each cache miss
costs 2 SQL round trips (`userRepository.findById` + `deviceRepository.findByUserId`), since:

- `getUserContact` is `@Cacheable` per single `userId` — no batch cache method exists.
- `DeviceRepository` only exposes `findByUserId(Long)` — no `findByUserIdIn(Collection<Long>)`.
- `UserRepository` doesn't use `findAllById` — relies solely on inherited `findById`.

For N recipients this is 2N queries instead of 2.

**Fix when batch sending is implemented:** add a batch entry point, e.g. `getUserContacts(List<Long> userIds)`,
using `userRepository.findAllById(userIds)` + a new `deviceRepository.findByUserIdIn(userIds)`, grouping devices
by `userId`, falling back to per-user Redis cache lookups only for uncached ids.
