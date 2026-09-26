import { request } from './http.js';

export function getProjectDashboard(projectId, options = {}) {
  return request(`/api/dashboard/projects/${encodeURIComponent(projectId)}`, options);
}
