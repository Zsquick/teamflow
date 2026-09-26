import { request } from './http.js';

export function createImportJob(projectId, csvFile, options = {}) {
  const formData = new FormData();
  formData.append('file', csvFile);
  const query = new URLSearchParams({ projectId });
  return request(`/api/import-jobs?${query}`, {
    ...options,
    method: 'POST',
    body: formData
  });
}

export function getImportJob(importJobId, options = {}) {
  return request(`/api/import-jobs/${encodeURIComponent(importJobId)}`, options);
}

export function startImportJob(importJobId, options = {}) {
  return request(`/api/import-jobs/${encodeURIComponent(importJobId)}/start`, {
    ...options,
    method: 'POST'
  });
}
