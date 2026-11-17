import { expect, spyOn, test } from "bun:test";
import type { ToClient, ToDocument } from "@documents/contract";
import { Client, type Editor, type Pipe, type Sent } from "./client";
import { batchOf, Outbox } from "./outbox";

class TestPipe implements Pipe {
  readonly sent: ToDocument[] = [];
  private handler: (frame: Uint8Array) => void = () => {};

  send(frame: Uint8Array): void {
    this.sent.push(JSON.parse(new TextDecoder().decode(frame.subarray(1))));
  }

  onMessage(handler: (frame: Uint8Array) => void): void {
    this.handler = handler;
  }

  async deliver(message: ToClient): Promise<void> {
    const json = new TextEncoder().encode(JSON.stringify(message));
    const frame = new Uint8Array(json.length + 1);
    frame.set(json, 1);
    this.handler(frame);
    await settled();
  }
}

function settled(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, 0));
}

class TestEditor implements Editor {
  readonly loads: Sent[] = [];
  edits = 0;

  load(_snapshot: unknown, _person: unknown, sent: Sent): void {
    this.loads.push(sent);
  }

  apply(): void {}

  batch(): unknown | null {
    return this.edits > 0 ? { edits: this.edits } : null;
  }

  peers(): void {}
  saved(): void {}
  closed(): void {}
  destroy(): void {}
}

class OutboxEditor implements Editor {
  readonly outbox = new Outbox<string>();
  dropped = false;

  load(_snapshot: unknown, _person: unknown, sent: Sent): void {
    this.dropped = this.outbox.loaded(sent) || this.dropped;
  }

  apply(_changed: unknown, own: boolean): void {
    if (own) this.outbox.answered();
  }

  batch(): unknown | null {
    return batchOf(this.outbox.next());
  }

  peers(): void {}
  saved(): void {}
  closed(): void {}
  destroy(): void {}
}

function joined(rev: number, seq: number | null = 0, epoch = 7): ToClient {
  return {
    type: "joined",
    id: "ana",
    name: "Ana",
    canEdit: true,
    epoch,
    rev,
    seq,
    peers: [],
    catchup: { kind: "snapshot", snapshot: {} },
  };
}

function kinds(pipe: TestPipe): string[] {
  return pipe.sent.map((message) => message.type);
}

test("an edit made while the client joins goes once the document took it in", async () => {
  const editor = new TestEditor();
  const client = new Client();
  client.use(editor);
  const pipe = new TestPipe();
  client.attach(pipe);
  editor.edits = 1;
  client.pull();
  await settled();
  expect(kinds(pipe)).toEqual(["join"]);
  await pipe.deliver(joined(3));
  await settled();
  expect(kinds(pipe)).toEqual(["join", "change"]);
  expect(pipe.sent[1]).toMatchObject({ seq: 1, base: 3 });
});

test("a view that fails to load still joins, and its edits go", async () => {
  const editor = new TestEditor();
  editor.load = () => {
    throw new Error("The view broke.");
  };
  let joins = 0;
  const client = new Client(() => joins++);
  client.use(editor);
  const pipe = new TestPipe();
  client.attach(pipe);
  editor.edits = 1;
  const logged = spyOn(console, "error").mockImplementation(() => {});
  try {
    await pipe.deliver(joined(3));
    await settled();
    expect(logged).toHaveBeenCalledTimes(1);
  } finally {
    logged.mockRestore();
  }
  expect(joins).toBe(1);
  expect(kinds(pipe)).toEqual(["join", "change"]);
});

test("a snapshot after a refused batch says so to the editor", async () => {
  const editor = new TestEditor();
  const client = new Client();
  client.use(editor);
  const pipe = new TestPipe();
  client.attach(pipe);
  await pipe.deliver(joined(0));
  editor.edits = 1;
  client.pull();
  await settled();
  await pipe.deliver({ type: "refused", seq: 1, reason: "invalid" });
  await settled();
  expect(pipe.sent.at(-1)).toMatchObject({ type: "join", epoch: 0, rev: 0 });
  await pipe.deliver(joined(2));
  expect(editor.loads).toEqual(["none", "refused"]);
});

test("a first batch goes again only when the document knows the client", async () => {
  for (const [seq, sent] of [
    [0, "lost"],
    [null, "unknown"],
  ] as const) {
    const editor = new TestEditor();
    const client = new Client();
    client.use(editor);
    const pipe = new TestPipe();
    client.attach(pipe);
    await pipe.deliver(joined(0));
    editor.edits = 1;
    client.pull();
    await settled();
    client.detach();
    const again = new TestPipe();
    client.attach(again);
    await again.deliver(joined(0, seq, 8));
    expect(editor.loads).toEqual(["none", sent]);
  }
});

test("a snapshot says what became of the batch on its way, as far as the document knows", async () => {
  for (const [seq, sent] of [
    [2, "taken"],
    [1, "lost"],
    [0, "unknown"],
    [null, "unknown"],
  ] as const) {
    const editor = new TestEditor();
    const client = new Client();
    client.use(editor);
    const pipe = new TestPipe();
    client.attach(pipe);
    await pipe.deliver(joined(0));
    editor.edits = 1;
    client.pull();
    await settled();
    const id = (pipe.sent[0] as { client: string }).client;
    await pipe.deliver({ type: "changed", rev: 1, author: "Ana", client: id, seq: 1, body: {} });
    await settled();
    expect(pipe.sent.at(-1)).toMatchObject({ type: "change", seq: 2 });
    client.detach();
    const again = new TestPipe();
    client.attach(again);
    await again.deliver(joined(1, seq, 8));
    expect(editor.loads).toEqual(["none", sent]);
  }
});

test("a batch that the document refused goes no more, and what came after it goes on", async () => {
  const editor = new OutboxEditor();
  const client = new Client();
  client.use(editor);
  const pipe = new TestPipe();
  client.attach(pipe);
  await pipe.deliver(joined(0));
  editor.outbox.add("refused");
  client.pull();
  editor.outbox.add("after");
  await settled();
  await pipe.deliver({ type: "refused", seq: 1, reason: "invalid" });
  await pipe.deliver(joined(0));
  await settled();
  expect(
    pipe.sent.map((message) => (message.type === "change" ? message.body : message.type)),
  ).toEqual(["join", "refused", "join", "after"]);
  expect(editor.dropped).toBe(true);
});

test("a batch that the document may have taken before it lost what it knew goes no more", async () => {
  const editor = new OutboxEditor();
  const client = new Client();
  client.use(editor);
  const pipe = new TestPipe();
  client.attach(pipe);
  await pipe.deliver(joined(0));
  editor.outbox.add("first");
  client.pull();
  await settled();
  const id = (pipe.sent[0] as { client: string }).client;
  await pipe.deliver({ type: "changed", rev: 1, author: "Ana", client: id, seq: 1, body: "first" });
  editor.outbox.add("doubtful");
  client.pull();
  editor.outbox.add("after");
  await settled();
  client.detach();
  const again = new TestPipe();
  client.attach(again);
  await again.deliver(joined(1, 0, 8));
  await settled();
  expect(
    again.sent.map((message) => (message.type === "change" ? message.body : message.type)),
  ).toEqual(["join", "after"]);
  expect(editor.dropped).toBe(true);
});

test("a batch that the document took goes once, and what came after it goes on the snapshot", async () => {
  const editor = new OutboxEditor();
  const client = new Client();
  client.use(editor);
  const pipe = new TestPipe();
  client.attach(pipe);
  await pipe.deliver(joined(0));
  editor.outbox.add("taken");
  client.pull();
  editor.outbox.add("after");
  await settled();
  client.detach();
  const again = new TestPipe();
  client.attach(again);
  await again.deliver(joined(1, 1, 8));
  await settled();
  expect(
    again.sent.map((message) => (message.type === "change" ? message.body : message.type)),
  ).toEqual(["join", "after"]);
  expect(editor.dropped).toBe(false);
});
