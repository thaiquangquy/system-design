# Requirements
- Notification type: push notification, sms message, email
- Soft real-time: user will receive notification asap but in high load, slight delay is acceptable
- Supported devices: iOS, Android, laptop/desktop
- Triggers notification: by client application, scheduled on server-side
- Opt-out: able to opt out to stop receive notifications 
- Workload: 10M mobile push notification, 1M SMS, 5M emails

# High-level design

## Different types of notification
### iOS push notification
Provider → Apple Push Notification Service(APNS) → iOS

- Provider: build and send notification request to Apple Push Notification Service(APNS)
  - Device token: unique device identifier
  - Payload: JSON format payload
  - APNS
  - iOS device
### Android push notification
Provider → Firebase Cloud Messaging (FCM) → Android
### SMS message
Provider -> SMS service -> SMS

### Email
Provider -> Email Service -> Email

## Contact info gathering
On sign up -> require: mobile device tokens, phone numbers, email -> store data in DB
- Address & phone number: user table
- Device token: device table
one user could have multiple devices (1-n relation)

## System Design simple
### Design
- Client Service: any service that triggers notification sending events (many)
- Notification System: Provide APIs for client services and build notification payloads for third party services
  - Single server 
  - Need to support multiple 3rd servers with easy to extend
- 3rd party services: Delivering notification to users

```mermaid
flowchart LR
    S["Client Service"] --> NS["Notification System<br/>(single server)"]
    NS --> TP1["APNs / FCM"]
    NS --> TP2["SMS service"]
    NS --> TP3["Email service"]
    TP1 --> D1["iOS / Android"]
    TP2 --> D2["SMS"]
    TP3 --> D3["Email"]
```

### Cons
- Single point of failure
- Hard to scale
- Performance bottleneck: heavy task could affect overall system

## System design enhanced
### Improvement
- Move database and cache out of notification server
- Add more notification servers and setup auto horizontal scaling
- Include message queue to decouple system components

### Design
- Client Service: any service that triggers notification sending events (many)
- Notification System: Provide APIs for client services and build notification payloads for third party services
    - Basic validation to verify emails, phone numbers
    - Query database or cache to fetch data to render notification
    - Push notification data to message queues
    - Need to support multiple 3rd servers with easy to extend
- 3rd party services: Delivering notification to users
- Cache: user info, device info, notification template
- DB: store data of user, notification, settings
- Message queue: each notification type has a distinct message queue
- Workers: servers that pull notification events from message queues and send to 3rd services

### Flow

```mermaid
sequenceDiagram
    participant S as Service
    participant NS as Notification server
    participant DB as Cache / database
    participant Q as Queue
    participant W as Worker
    participant TP as Third-party service
    participant D as User device

    S->>NS: 1. Send notification request
    NS->>DB: 2. Fetch user info, device token, settings
    DB-->>NS: Metadata
    NS->>Q: 3. Push event (e.g. iOS PN queue)
    W->>Q: 4. Pull event
    W->>TP: 5. Send notification
    TP->>D: 6. Deliver to device
```

### Sample API
POST https://api.example.com/v1/sms/send
body:

```json
{
  "to": [
    {
      "user_id": 123
    }
  ],
  "from": {
    "email": ""
  },
  "subject": "",
  "content": [
    {
      "type": "",
      "value": ""
    }
  ]
}
```

# System design deep dive

## Reliability
- Never lost notification
  - Persist notification data in database
  - Implement retry mechanism
- Dedupe mechanism
  - When notification arrived, check its id, if sent -> discard
## Additional components and considerations
- Notification template
  - Body
  - CTA
- Notification setting
  - channel: push notification email or SMS
  - opt-in choice
- Rate limiting
  - Limit the number of noti user can receive
- Retry mechanism
- Need appKey & appSecret to secure push notification APIs. Authorization and authentication needed
- Monitor the queued notification: add or remove workers to adjust process speed
- Events tracking
  - Analytics user behavior: start, pending, error, sent, deliver, click, unsubscribe

## Final design

```mermaid
flowchart LR
ServiceN["Service N"]
NS["Notification servers<br/>- Authentication<br/>- Rate limit"]
Cache[("CACHE")]
DB[("DB<br/>device setting<br/>user info")]
IOSPN["iOS PN<br/>(queue)"]
Workers["Workers"]
APNs(["APNs"])
iOS["iOS"]
Analytics["Analytics service"]
Template["Notification template"]
Log[("Notification log")]

    ServiceN --> NS
    NS --> IOSPN
    NS -- "send pending" --> Analytics
    NS --> Cache
    Cache --> DB
    IOSPN --> Workers
    Workers -- "retry on error" --> IOSPN
    Workers -- "sent" --> Analytics
    Workers --> APNs
    APNs --> iOS
    iOS -- "click tracking" --> Analytics
    Workers --> Template
    Workers --> Log
```