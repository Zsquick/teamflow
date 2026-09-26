import { request } from './http.js';

export function getCurrentUser(options = {}) {
  return request('/api/users/me', options);
}
