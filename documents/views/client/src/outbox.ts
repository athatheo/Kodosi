import type { Sent } from "./client";

/**
 * The batch on its way stays here until its answer comes, so a view can move it or send it
 * again.
 */
export class Outbox<Change> {
  private sent: Change[] = [];
  private queued: Change[] = [];

  add(change: Change): void {
    this.queued.push(change);
  }

  unanswered(): Change[] {
    return [...this.sent, ...this.queued];
  }

  next(): Change[] {
    if (this.sent.length === 0) {
      this.sent = this.queued;
      this.queued = [];
    }
    return this.sent;
  }

  answered(): void {
    this.sent = [];
  }

  /**
   * A snapshot holds the batch that the document took. A batch that it refused does not go again,
   * nor one that it may have taken before it lost what it knew of this client, as that would go in
   * twice; the view says so. The changes after it go on.
   */
  loaded(sent: Sent): boolean {
    const doubtful = sent === "refused" || sent === "unknown";
    const dropped = doubtful && this.sent.length > 0;
    if (doubtful || sent === "taken") this.sent = [];
    return dropped;
  }

  clear(): void {
    this.sent = [];
    this.queued = [];
  }

  move(move: (change: Change) => Change | null): void {
    const moved = (changes: Change[]) => changes.flatMap((change) => move(change) ?? []);
    this.sent = moved(this.sent);
    this.queued = moved(this.queued);
  }
}

export function batchOf<Change>(changes: readonly Change[]): unknown | null {
  if (changes.length === 0) return null;
  return changes.length === 1 ? changes[0] : { kind: "group", changes };
}
