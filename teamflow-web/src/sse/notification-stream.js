import { requestRaw } from '../api/http.js';

/**
 * 用 Fetch + ReadableStream 消费 SSE，使请求可以携带 Authorization 头。
 * 原生 EventSource 不能设置自定义请求头，因此本项目不直接使用 EventSource。
 */
export function createNotificationStream({ onEvent, onError, onReconnect }) {
  let abortController = null;
  let reconnectTimer = null;
  let runningPromise = null;
  let retryResolver = null;
  let closed = true;
  let retryDelay = 1000;
  let lastEventId = '';
  let connectedOnce = false;
  const maxRetryDelay = 30000;
  const stableConnectionMs = 10000;

  const waitForRetry = () => new Promise((resolve) => {
    retryResolver = resolve;
    reconnectTimer = window.setTimeout(() => {
      reconnectTimer = null;
      retryResolver = null;
      resolve();
    }, retryDelay);
  });

  const consume = async (response) => {
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    try {
      while (!closed) {
        const { value, done } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        let boundary = buffer.search(/(?:\r\n|\r|\n){2}/);
        while (boundary >= 0) {
          const block = buffer.slice(0, boundary);
          const separator = buffer.slice(boundary).match(/^(?:\r\n|\r|\n){2}/)?.[0] ?? '\n\n';
          buffer = buffer.slice(boundary + separator.length);
          dispatchBlock(block);
          boundary = buffer.search(/(?:\r\n|\r|\n){2}/);
        }
      }
      buffer += decoder.decode();
      if (!closed && buffer.trim()) dispatchBlock(buffer.trimEnd());
    } finally {
      reader.releaseLock();
    }
  };

  const dispatchBlock = (block) => {
    if (!block) return;
    let eventType = 'message';
    let eventId = null;
    const dataLines = [];
    for (const line of block.split(/\r\n|\r|\n/)) {
      if (!line || line.startsWith(':')) continue;
      const colon = line.indexOf(':');
      const field = colon < 0 ? line : line.slice(0, colon);
      let value = colon < 0 ? '' : line.slice(colon + 1);
      if (value.startsWith(' ')) value = value.slice(1);
      if (field === 'event') eventType = value;
      else if (field === 'id') eventId = value;
      else if (field === 'data') dataLines.push(value);
      else if (field === 'retry' && /^\d+$/.test(value)) {
        retryDelay = Math.min(maxRetryDelay, Math.max(1000, Number(value)));
      }
    }
    if (eventId != null && !eventId.includes('\u0000')) lastEventId = eventId;
    if (dataLines.length === 0) return;
    const rawData = dataLines.join('\n');
    let data;
    try {
      data = JSON.parse(rawData);
    } catch (error) {
      onError?.(new Error(`通知事件 ${eventType} 的数据无法解析`, { cause: error }));
      return;
    }
    onEvent?.({ type: eventType, id: eventId, data });
  };

  const run = async () => {
    while (!closed) {
      abortController = new AbortController();
      let connectedAt = 0;
      try {
        const headers = { Accept: 'text/event-stream' };
        if (lastEventId) headers['Last-Event-ID'] = lastEventId;
        const response = await requestRaw('/api/notifications/stream', {
          signal: abortController.signal,
          headers,
          timeoutMs: 0
        });
        validateEventStream(response);
        connectedAt = Date.now();
        if (connectedOnce) {
          try {
            await onReconnect?.({ lastEventId });
          } catch (error) {
            onError?.(error);
          }
        }
        connectedOnce = true;
        await consume(response);
        if (!closed) throw new Error('通知连接已断开');
      } catch (error) {
        if (closed || error?.name === 'AbortError') break;
        onError?.(error);
      } finally {
        if (connectedAt > 0 && Date.now() - connectedAt >= stableConnectionMs) {
          retryDelay = 1000;
        }
        abortController = null;
      }
      if (closed) break;
      await waitForRetry();
      retryDelay = Math.min(maxRetryDelay, retryDelay * 2);
    }
  };

  const validateEventStream = (response) => {
    if (!response.body) {
      throw new Error('当前浏览器或代理不支持通知流');
    }
    const contentType = response.headers.get('content-type') ?? '';
    if (!contentType.includes('text/event-stream')) {
      throw new Error('服务器没有返回通知事件流');
    }
  };

  return {
    async connect() {
      if (runningPromise) return runningPromise;
      closed = false;
      runningPromise = run().finally(() => {
        runningPromise = null;
      });
      return runningPromise;
    },
    close() {
      if (closed) return;
      closed = true;
      abortController?.abort();
      abortController = null;
      if (reconnectTimer != null) {
        window.clearTimeout(reconnectTimer);
        reconnectTimer = null;
        retryResolver?.();
        retryResolver = null;
      }
    }
  };
}
