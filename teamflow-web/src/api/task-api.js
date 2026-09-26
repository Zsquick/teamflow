import { request, requestJson } from './http.js';

const id = encodeURIComponent;

export function listTasks(projectId, page = 1, size = 20, status = null, options = {}) {
  const query = new URLSearchParams({ projectId, page: String(page), size: String(size) });
  if (status) {
    query.set('status', status);
  }
  return request(`/api/tasks?${query}`, options);
}

export function getTask(taskId, options = {}) {
  return request(`/api/tasks/${id(taskId)}`, options);
}

export function createTask(payload, options = {}) {
  return requestJson('/api/tasks', 'POST', payload, options);
}

export function updateTask(taskId, payload, options = {}) {
  return requestJson(`/api/tasks/${id(taskId)}`, 'PUT', payload, options);
}

export function changeTaskStatus(taskId, payload, options = {}) {
  return requestJson(`/api/tasks/${id(taskId)}/status`, 'PATCH', payload, options);
}

export function deleteTask(taskId, version, options = {}) {
  const query = new URLSearchParams({ version: String(version) });
  return request(`/api/tasks/${id(taskId)}?${query}`, {
    ...options,
    method: 'DELETE'
  });
}
