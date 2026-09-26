import { authStore } from '../state/auth-store.js';
import { isInvalidSessionError } from '../auth/session-errors.js';

const SUCCESS_CODE = 'COMMON_0000';
const API_BASE_URL = String(import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '');
const configuredTimeout = Number(import.meta.env.VITE_API_TIMEOUT_MS ?? 15000);
const DEFAULT_TIMEOUT_MS = Number.isFinite(configuredTimeout) && configuredTimeout >= 0
  ? configuredTimeout
  : 15000;
let refreshPromise = null;

export class ApiError extends Error {
  constructor(message, { status = 0, code = 'CLIENT_REQUEST_FAILED', data = null, cause } = {}) {
    super(message || '请求失败');
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.data = data;
    if (cause !== undefined) this.cause = cause;
  }
}

function apiUrl(path) {
  if (typeof path !== 'string' || !path.startsWith('/')) {
    throw new TypeError('API 路径必须以 / 开头');
  }
  return `${API_BASE_URL}${path}`;
}

async function parseEnvelope(response) {
  const contentType = response.headers.get('content-type') ?? '';
  if (!contentType.includes('application/json') && !/application\/[\w.+-]+\+json\b/i.test(contentType)) {
    throw new ApiError('服务器返回了无法识别的数据格式', {
      status: response.status,
      code: 'CLIENT_INVALID_RESPONSE'
    });
  }
  let envelope;
  try {
    envelope = await response.json();
  } catch {
    throw new ApiError('服务器返回的 JSON 无法解析', {
      status: response.status,
      code: 'CLIENT_INVALID_RESPONSE'
    });
  }
  if (!envelope || typeof envelope.code !== 'string') {
    throw new ApiError('服务器返回格式不完整', {
      status: response.status,
      code: 'CLIENT_INVALID_RESPONSE'
    });
  }
  return envelope;
}

async function responseError(response) {
  try {
    const envelope = await parseEnvelope(response.clone());
    return new ApiError(envelope.message || `请求失败（${response.status}）`, {
      status: response.status,
      code: envelope.code,
      data: envelope.data
    });
  } catch (error) {
    if (error instanceof ApiError && error.code !== 'CLIENT_INVALID_RESPONSE') {
      return error;
    }
    return new ApiError(`请求失败（${response.status}）`, {
      status: response.status,
      code: 'CLIENT_HTTP_ERROR'
    });
  }
}

async function refreshAccessToken() {
  if (!authStore.refreshToken) {
    throw new ApiError('登录已过期，请重新登录', { status: 401, code: 'COMMON_0003' });
  }
  if (!refreshPromise) {
    refreshPromise = (async () => {
      let response;
      try {
        response = await fetchWithTimeout(apiUrl('/api/auth/refresh'), {
          method: 'POST',
          headers: {
            Accept: 'application/json',
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({ refreshToken: authStore.refreshToken })
        }, DEFAULT_TIMEOUT_MS);
      } catch (cause) {
        if (cause instanceof ApiError) throw cause;
        throw new ApiError('网络连接失败，请检查后重试', { status: 0, code: 'CLIENT_NETWORK_ERROR', cause });
      }
      if (!response.ok) {
        throw await responseError(response);
      }
      const envelope = await parseEnvelope(response);
      if (envelope.code !== SUCCESS_CODE || !envelope.data) {
        throw new ApiError(envelope.message || '刷新登录状态失败', {
          status: response.status,
          code: envelope.code,
          data: envelope.data
        });
      }
      authStore.setSession(envelope.data, authStore.currentUser);
      return envelope.data;
    })().catch((error) => {
      if (isInvalidSessionError(error)) authStore.clear();
      throw error;
    }).finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
}

/**
 * 发送统一的 HTTP 请求。
 *
 * @param {string} path REST API 路径
 * @param {RequestInit} options Fetch 请求选项
 * @returns {Promise<unknown>} 后端响应中的 data 数据
 */
export async function request(path, options = {}) {
  const response = await requestRaw(path, options);
  if (response.status === 204) {
    return null;
  }
  const envelope = await parseEnvelope(response);
  if (envelope.code !== SUCCESS_CODE) {
    throw new ApiError(envelope.message || '业务请求失败', {
      status: response.status,
      code: envelope.code,
      data: envelope.data
    });
  }
  return envelope.data;
}

/** 只发送 JSON 的便捷封装；FormData 请求不要手动设置 Content-Type。 */
export async function requestJson(path, method, body, options = {}) {
  const headers = new Headers(options.headers);
  headers.set('Content-Type', 'application/json');
  return request(path, {
    ...options,
    method,
    headers,
    body: body == null ? undefined : JSON.stringify(body)
  });
}

/** 为流式下载或 SSE 返回未经消费的 Response。 */
export async function requestRaw(path, options = {}) {
  const {
    skipAuthRefresh = false,
    _retried = false,
    timeoutMs = DEFAULT_TIMEOUT_MS,
    ...fetchOptions
  } = options;
  const headers = new Headers(fetchOptions.headers);
  if (!headers.has('Accept')) {
    headers.set('Accept', 'application/json');
  }
  if (authStore.accessToken && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${authStore.accessToken}`);
  }

  let response;
  try {
    response = await fetchWithTimeout(apiUrl(path), { ...fetchOptions, headers }, timeoutMs);
  } catch (cause) {
    if (cause instanceof ApiError) throw cause;
    if (cause?.name === 'AbortError') {
      throw cause;
    }
    throw new ApiError('网络连接失败，请检查后重试', {
      status: 0,
      code: 'CLIENT_NETWORK_ERROR',
      cause
    });
  }

  const canRefresh = response.status === 401
    && !_retried
    && !skipAuthRefresh
    && !path.startsWith('/api/auth/')
    && Boolean(authStore.refreshToken);
  if (canRefresh) {
    await refreshAccessToken();
    if (fetchOptions.signal?.aborted) {
      throw new DOMException('请求已取消', 'AbortError');
    }
    return requestRaw(path, { ...fetchOptions, skipAuthRefresh, timeoutMs, _retried: true });
  }
  if (!response.ok) {
    const error = await responseError(response);
    const protectedApiRejectedSession = response.status === 401
      && !path.startsWith('/api/auth/')
      && (_retried || !authStore.refreshToken);
    if (protectedApiRejectedSession) authStore.clear();
    throw error;
  }
  return response;
}

async function fetchWithTimeout(url, options, timeoutMs) {
  const normalizedTimeout = Number(timeoutMs);
  if (!Number.isFinite(normalizedTimeout) || normalizedTimeout <= 0) {
    return fetch(url, options);
  }

  const timeoutController = new AbortController();
  const callerSignal = options.signal;
  const abortFromCaller = () => timeoutController.abort(callerSignal.reason);
  if (callerSignal?.aborted) abortFromCaller();
  else callerSignal?.addEventListener('abort', abortFromCaller, { once: true });

  let timedOut = false;
  const timer = window.setTimeout(() => {
    timedOut = true;
    timeoutController.abort();
  }, normalizedTimeout);
  try {
    return await fetch(url, { ...options, signal: timeoutController.signal });
  } catch (cause) {
    if (timedOut) {
      throw new ApiError(`请求超时（${normalizedTimeout} ms）`, {
        status: 0,
        code: 'CLIENT_TIMEOUT',
        data: { timeoutMs: normalizedTimeout },
        cause
      });
    }
    throw cause;
  } finally {
    window.clearTimeout(timer);
    callerSignal?.removeEventListener('abort', abortFromCaller);
  }
}
