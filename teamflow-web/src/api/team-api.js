import { request, requestJson } from './http.js';

const id = encodeURIComponent;

export function listTeams(options = {}) {
  return request('/api/teams', options);
}

export function createTeam(payload, options = {}) {
  return requestJson('/api/teams', 'POST', payload, options);
}

export function listTeamMembers(teamId, options = {}) {
  return request(`/api/teams/${id(teamId)}/members`, options);
}

export function previewTeamInvitation(teamId, payload, options = {}) {
  return requestJson(`/api/teams/${id(teamId)}/invitations/preview`, 'POST', payload, options);
}

export function sendTeamInvitation(teamId, payload, options = {}) {
  return requestJson(`/api/teams/${id(teamId)}/invitations`, 'POST', payload, options);
}

export function listTeamInvitations(teamId, options = {}) {
  return request(`/api/teams/${id(teamId)}/invitations`, options);
}

export function revokeTeamInvitation(teamId, invitationId, options = {}) {
  return request(`/api/teams/${id(teamId)}/invitations/${id(invitationId)}/revoke`, {
    ...options,
    method: 'POST'
  });
}

export function updateTeamMemberRole(teamId, userId, payload, options = {}) {
  return requestJson(`/api/teams/${id(teamId)}/members/${id(userId)}/role`, 'PATCH', payload, options);
}

export function removeTeamMember(teamId, userId, options = {}) {
  return request(`/api/teams/${id(teamId)}/members/${id(userId)}`, {
    ...options,
    method: 'DELETE'
  });
}
