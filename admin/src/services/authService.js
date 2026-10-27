// SAGE Admin Panel - Authentication & Session Service
import { apiClient } from './apiClient';

const ADMIN_TOKEN_KEY = 'sage_admin_token';
const ADMIN_USER_KEY = 'sage_admin_user';

export const authService = {
  getCurrentAdmin: () => {
    try {
      const stored = localStorage.getItem(ADMIN_USER_KEY);
      if (stored) {
        return JSON.parse(stored);
      }
    } catch (e) {
      console.error('Failed to parse admin user storage:', e);
    }
    return null;
  },

  getAdminToken: () => {
    return localStorage.getItem(ADMIN_TOKEN_KEY);
  },

  loginAsAdmin: async (username, password) => {
    try {
      const res = await apiClient('/auth/admin/token', {
        method: 'POST',
        body: JSON.stringify({
          username: username || 'admin@aicte-india.gov.in',
          password: password || 'SAGE@Admin2026!',
        }),
      });

      if (res && res.access_token) {
        // Enforce that only Administrator role can log into the Admin Console
        if (res.user?.role !== 'Administrator' && res.user?.role !== 'Admin') {
          authService.logout();
          throw new Error('Access Denied: This account lacks Administrator authorization.');
        }

        localStorage.setItem(ADMIN_TOKEN_KEY, res.access_token);
        localStorage.setItem(ADMIN_USER_KEY, JSON.stringify(res.user));
        return res.user;
      }
      throw new Error('Authentication response did not contain an access token.');
    } catch (err) {
      console.error('Admin authentication failed:', err);
      throw err;
    }
  },

  verifySession: async () => {
    try {
      const token = localStorage.getItem(ADMIN_TOKEN_KEY);
      if (!token) return null;
      const user = await apiClient('/auth/me');
      if (user && user.role === 'Administrator') {
        localStorage.setItem(ADMIN_USER_KEY, JSON.stringify(user));
        return user;
      }
      authService.logout();
      return null;
    } catch {
      authService.logout();
      return null;
    }
  },

  logout: () => {
    localStorage.removeItem(ADMIN_TOKEN_KEY);
    localStorage.removeItem(ADMIN_USER_KEY);
  },
};
