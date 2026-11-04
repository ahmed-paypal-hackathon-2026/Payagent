import type { AuditEventApiDTO } from "./client";

export type DealEventEnvelope = Omit<AuditEventApiDTO, "createdAt"> & { timestamp: string };

export type StreamStatus = "connecting" | "live" | "polling";

function toWsUrl(apiBaseUrl: string): string {
  const url = new URL(apiBaseUrl);
  url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
  url.pathname = "/ws";
  return url.toString();
}

function encodeFrame(command: string, headers: Record<string, string>): string {
  let frame = command;
  for (const [key, value] of Object.entries(headers)) {
    frame += `\n${key}:${value}`;
  }
  return `${frame}\n\n\0`;
}

function frameOf(raw: string): { command: string; body: string } {
  const firstNewline = raw.indexOf("\n");
  const command = firstNewline === -1 ? raw : raw.slice(0, firstNewline);
  const separator = raw.indexOf("\n\n");
  const body = separator === -1 ? "" : raw.slice(separator + 2);
  return { command, body };
}

export function openDealEventStream(options: {
  apiBaseUrl: string;
  dealId: string;
  token: string;
  onEnvelope: (envelope: DealEventEnvelope) => void;
  onStatus: (status: StreamStatus) => void;
}): () => void {
  const { apiBaseUrl, dealId, token, onEnvelope, onStatus } = options;
  let socket: WebSocket | null = null;
  let closed = false;
  let buffer = "";

  function handleMessageFrame(frame: string) {
    const { command, body } = frameOf(frame);
    if (command === "CONNECTED") {
      socket?.send(encodeFrame("SUBSCRIBE", { id: "deal-events", destination: `/topic/deals/${dealId}` }));
      onStatus("live");
      return;
    }
    if (command === "ERROR") {
      onStatus("polling");
      return;
    }
    if (command !== "MESSAGE") {
      return;
    }
    if (!body) {
      return;
    }
    try {
      const parsed = JSON.parse(body) as DealEventEnvelope;
      if (parsed && typeof parsed.seq === "number" && typeof parsed.eventType === "string") {
        onEnvelope(parsed);
      }
    } catch {
      // Non-JSON message frames are not deal envelopes; ignore them.
    }
  }

  try {
    socket = new WebSocket(toWsUrl(apiBaseUrl));
  } catch {
    onStatus("polling");
    return () => {};
  }

  socket.onopen = () => {
    let host = "localhost";
    try {
      host = new URL(apiBaseUrl).host;
    } catch {
      host = "localhost";
    }
    socket?.send(
      encodeFrame("CONNECT", {
        "accept-version": "1.2",
        "heart-beat": "0,0",
        host,
        Authorization: `Bearer ${token}`,
      }),
    );
  };

  socket.onmessage = (event) => {
    if (typeof event.data !== "string") return;
    buffer += event.data;
    let terminator = buffer.indexOf("\0");
    while (terminator !== -1) {
      const frame = buffer.slice(0, terminator);
      buffer = buffer.slice(terminator + 1);
      handleMessageFrame(frame);
      terminator = buffer.indexOf("\0");
    }
  };

  socket.onerror = () => {
    onStatus("polling");
  };

  socket.onclose = () => {
    if (!closed) onStatus("polling");
  };

  return () => {
    closed = true;
    try {
      socket?.close();
    } catch {
      // The socket may already be gone; closing is best-effort.
    }
  };
}
