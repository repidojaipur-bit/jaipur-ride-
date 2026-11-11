import { randomUUID } from "node:crypto";

export type RealtimeEventType =
  | "OFFER_CREATED"
  | "OFFER_UPDATED"
  | "OFFER_WITHDRAWN"
  | "TRIP_UPDATED"
  | "DRIVER_VERIFICATION_UPDATED"
  | "PAYOUT_UPDATED"
  | "NOTIFICATION_CREATED";

export interface MinimalRealtimeEvent {
  eventId: string;
  eventType: RealtimeEventType;
  occurredAtUtc: string;
  resourceId: string;
  schemaVersion: 1;
}

export interface RealtimeSocket {
  readonly readyState: number;
  send(data: string): void;
  on(event: "close", listener: () => void): void;
}

/**
 * WebSocket pub/sub hub transmitting minimal opaque notification envelopes only
 * (`04_TECHSPEC.md` Section 5 & `06_RULES` RT-001..003). Never broadcasts PII,
 * coordinates, or wallet balances over WS frames.
 */
export class RealtimeHub {
  private readonly connectionsByDriver = new Map<string, Set<RealtimeSocket>>();

  register(driverId: string, socket: RealtimeSocket): void {
    let set = this.connectionsByDriver.get(driverId);
    if (!set) {
      set = new Set();
      this.connectionsByDriver.set(driverId, set);
    }
    set.add(socket);

    socket.on("close", () => {
      set?.delete(socket);
      if (set && set.size === 0) {
        this.connectionsByDriver.delete(driverId);
      }
    });
  }

  emitToDriver(driverId: string, eventType: RealtimeEventType, resourceId: string): void {
    const sockets = this.connectionsByDriver.get(driverId);
    if (!sockets || sockets.size === 0) return;

    const envelope: MinimalRealtimeEvent = {
      eventId: randomUUID(),
      eventType,
      occurredAtUtc: new Date().toISOString(),
      resourceId,
      schemaVersion: 1
    };
    const serialized = JSON.stringify(envelope);
    for (const sock of sockets) {
      if (sock.readyState === 1) {
        sock.send(serialized);
      }
    }
  }
}
