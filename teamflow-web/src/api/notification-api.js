import { request } from './http.js';

export function listNotifications(page = 1, size = 20, options = {}) {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  return request(`/api/notifications?${query}`, options);
}

export function countUnread(options = {}) {
  return request('/api/notifications/unread-count', options);
}

export function markNotificationRead(notificationId, options = {}) {
  return request(`/api/notifications/${encodeURIComponent(notificationId)}/read`, {
    ...options,
    method: 'PATCH'
  });
}
