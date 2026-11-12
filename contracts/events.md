# Drive Partner — Realtime WebSocket & FCM Push Contracts

**Canonical Gateway Endpoint:** `GET /ws/v1/driver` (WebSocket upgrade)  
**Server Authoritative Rule:** Neither WebSocket events nor FCM notifications carry passenger PII, raw GPS coordinates, bank details, or financial ledger balances. Clients **always** treat events as minimal invalidation/wakeup signals and fetch full state over authenticated REST (`/api/v1/...`).

---

## 1. WebSocket Authentication & Handshake

- **URL:** `ws://<host>:8080/ws/v1/driver` (local) / `wss://<api-host>/ws/v1/driver` (staging/production).
- **Auth:** `Authorization: Bearer <ID_TOKEN_OR_SESSION_JWT>` header (fallback query param `?token=<jwt>` only if client transport prevents upgrade headers).
- **Failure:** Missing/invalid token closes the WebSocket with code `1008` (`UNAUTHORIZED`).
- **Connection Ack:** Upon connection, the server emits:
  ```json
  {
    "eventId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "eventType": "connection.ready",
    "resourceId": "<driverId>",
    "occurredAtUtc": "2026-11-11T10:15:30.000Z",
    "schemaVersion": 1
  }
  ```

---

## 2. Canonical Event Envelope (`RealtimeEventEnvelope`)

All WebSocket payloads share the exact 5-field JSON envelope parsed by `OkHttpWebSocketRealtimeBoundary.kt`:

```json
{
  "eventId": "uuid-v4",
  "eventType": "offer.created",
  "resourceId": "uuid-of-offer-trip-payout-or-notification",
  "occurredAtUtc": "2026-11-11T10:15:30.000Z",
  "schemaVersion": 1
}
```

### Supported `eventType` Values & Client Actions

| WebSocket `eventType` | FCM Push `type` | `resourceId` Meaning | Android Client Action |
|---|---|---|---|
| `offer.created` | `NEW_OFFER` | `offerId` | Deduplicate `eventId`, fetch `GET /api/v1/offers/active` |
| `offer.expired` | `OFFER_EXPIRED` | `offerId` | Remove offer or resync `GET /api/v1/offers/active` |
| `offer.superseded` / `offer.withdrawn` | `OFFER_SUPERSEDED` | `offerId` | Remove offer & notify user another driver claimed it |
| `trip.updated` | `TRIP_UPDATED` | `tripId` | Fetch authoritative `GET /api/v1/trips/active` |
| `payout.updated` | `PAYOUT_UPDATED` | `payoutId` | Refresh `GET /api/v1/wallet` and `GET /api/v1/payouts` |
| `notification.created` | `NOTIFICATION_CREATED` | `notificationId` | Refresh `GET /api/v1/notifications` & unread badge |

---

## 3. Deduplication, Reconnect & Ordering Guarantees

1. **Client Deduplication:** `RealtimeEventDeduplicator` maintains a bounded FIFO set (last 500 `eventId` values). Duplicate deliveries are silently ignored.
2. **Schema Version Guard:** Unknown `schemaVersion > 1` events are discarded safely without crashing (`SchemaVersionUnsupported`).
3. **Reconnect Re-synchronization:** Whenever the WebSocket disconnects and reconnects (with exponential backoff) or the app enters `ON_RESUME`, Android triggers authoritative REST pulls (`/api/v1/offers/active`, `/api/v1/trips/active`, `/api/v1/notifications`) to cover missed events during offline gaps.
4. **FCM Wakeup Fallback (`DrivePartnerPushBoundary`):** Minimal data payload `{ "type": "...", "entityId": "<uuid>", "dedupeKey": "<string>" }` passes through `DrivePartnerPushBoundary.handleRemoteMessage(...)` and calls `DispatchRealtimeBoundary.onPushHintReceived(...)`, never persisting balance or passenger PII locally.
