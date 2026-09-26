import { request } from './http.js';

const id = encodeURIComponent;

export function listReceivedInvitations(options = {}) {
  return request('/api/invitations', options);
}

export function acceptInvitation(invitationId, options = {}) {
  return request(`/api/invitations/${id(invitationId)}/accept`, {
    ...options,
    method: 'POST'
  });
}

export function rejectInvitation(invitationId, options = {}) {
  return request(`/api/invitations/${id(invitationId)}/reject`, {
    ...options,
    method: 'POST'
  });
}
