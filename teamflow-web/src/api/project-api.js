import { request, requestJson } from './http.js';

const id = encodeURIComponent;

export function listProjects(teamId, options = {}) {
  const query = new URLSearchParams({ teamId });
  return request(`/api/projects?${query}`, options);
}

export function getProject(projectId, options = {}) {
  return request(`/api/projects/${id(projectId)}`, options);
}

export function createProject(payload, options = {}) {
  return requestJson('/api/projects', 'POST', payload, options);
}

export function updateProject(projectId, payload, options = {}) {
  return requestJson(`/api/projects/${id(projectId)}`, 'PUT', payload, options);
}
