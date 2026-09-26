import { request, requestJson } from './http.js';

const id = encodeURIComponent;

export function listComments(taskId, options = {}) {
  return request(`/api/tasks/${id(taskId)}/comments`, options);
}

export function createComment(taskId, payload, options = {}) {
  return requestJson(`/api/tasks/${id(taskId)}/comments`, 'POST', payload, options);
}

export function deleteComment(commentId, options = {}) {
  return request(`/api/comments/${id(commentId)}`, { ...options, method: 'DELETE' });
}
