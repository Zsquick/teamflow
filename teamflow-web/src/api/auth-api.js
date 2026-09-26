import { requestJson } from './http.js';

export function register(payload, options = {}) {
  return requestJson('/api/auth/register', 'POST', payload, {
    ...options,
    skipAuthRefresh: true
  });
}

export function login(payload, options = {}) {
  return requestJson('/api/auth/login', 'POST', payload, {
    ...options,
    skipAuthRefresh: true
  });
}

export function refresh(refreshToken, options = {}) {
  return requestJson('/api/auth/refresh', 'POST', { refreshToken }, {
    ...options,
    skipAuthRefresh: true
  });
}

export function logout(refreshToken, options = {}) {
  return requestJson('/api/auth/logout', 'POST', { refreshToken }, options);
}
