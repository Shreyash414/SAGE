// SAGE Admin Panel - Administrative Operations Service
import { apiClient } from './apiClient';

export const adminService = {
  // Search & List users with pagination and criteria filtering
  getUsers: async ({ search = '', role = '', status = '', region = '', page = 0, size = 10 } = {}) => {
    const params = new URLSearchParams();
    if (search) params.append('search', search);
    if (role) params.append('role', role);
    if (status) params.append('status', status);
    if (region) params.append('region', region);
    params.append('page', page);
    params.append('size', size);

    return apiClient(`/admin/users?${params.toString()}`);
  },

  // Get user details and recent audit trail
  getUserDetail: async (userId) => {
    return apiClient(`/admin/users/${encodeURIComponent(userId)}`);
  },

  // Create a new user
  createUser: async (userData) => {
    return apiClient('/admin/users', {
      method: 'POST',
      body: JSON.stringify(userData),
    });
  },

  // Update existing user profile and role
  updateUser: async (userId, userData) => {
    return apiClient(`/admin/users/${encodeURIComponent(userId)}`, {
      method: 'PUT',
      body: JSON.stringify(userData),
    });
  },

  // Activate, suspend, or deactivate user account
  updateUserStatus: async (userId, { status, reason }) => {
    return apiClient(`/admin/users/${encodeURIComponent(userId)}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status, reason }),
    });
  },

  // Query audit logs with multi-attribute filtering and pagination
  getAuditLogs: async ({ eventType = '', actorId = '', targetEntityType = '', targetEntityId = '', page = 0, size = 15 } = {}) => {
    const params = new URLSearchParams();
    if (eventType) params.append('eventType', eventType);
    if (actorId) params.append('actorId', actorId);
    if (targetEntityType) params.append('targetEntityType', targetEntityType);
    if (targetEntityId) params.append('targetEntityId', targetEntityId);
    params.append('page', page);
    params.append('size', size);

    return apiClient(`/admin/audit-logs?${params.toString()}`);
  },

  // Get aggregate dashboard metrics
  getMetrics: async () => {
    return apiClient('/admin/metrics');
  },

  // Get recent authentication & user activity
  getActivity: async () => {
    return apiClient('/admin/activity');
  },

  // Backend Health Check
  getHealth: async () => {
    return apiClient('/health');
  },
};
