// SIH 26262 - Authentication Service connected to Spring Boot Backend
import { ROLES } from '../constants/roles';
import { apiClient } from './apiClient';

const AUTH_STORAGE_KEY = 'aicte_auth_user';
const TOKEN_STORAGE_KEY = 'aicte_auth_token';

export const authService = {
  getCurrentUser: () => {
    try {
      const stored = localStorage.getItem(AUTH_STORAGE_KEY);
      if (stored) {
        const parsed = JSON.parse(stored);
        if (parsed?.role === 'Administrator' || parsed?.role === 'Admin') {
          // Stale administrator session detected in regular portal - purge immediately
          authService.logout();
          return null;
        }
        return parsed;
      }
    } catch (e) {
      console.error('Failed to read auth from storage:', e);
    }
    return null;
  },

  loginByRole: async (role) => {
    if (role === 'Administrator' || role === 'Admin') {
      throw new Error('Administrative accounts cannot sign in through this portal. Please use the authorized administrative console.');
    }
    try {
      const res = await apiClient('/auth/token', {
        method: 'POST',
        body: JSON.stringify({ role }),
      });
      if (res && res.access_token) {
        if (res.user?.role === 'Administrator' || res.user?.role === 'Admin') {
          authService.logout();
          throw new Error('Administrative accounts cannot sign in through this portal. Please use the authorized administrative console.');
        }
        localStorage.setItem(TOKEN_STORAGE_KEY, res.access_token);
        localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(res.user));
        return res.user;
      }
      throw new Error('Authentication response did not return an access token.');
    } catch (e) {
      console.error('Backend loginByRole failed:', e);
      throw e;
    }
  },

  loginWithCredentials: async (email, role, password) => {
    try {
      const res = await apiClient('/auth/token', {
        method: 'POST',
        body: JSON.stringify({ username: email, role, password }),
      });
      if (res && res.access_token) {
        if (res.user?.role === 'Administrator' || res.user?.role === 'Admin') {
          authService.logout();
          throw new Error('Administrative accounts cannot sign in through this portal. Please use the authorized administrative console.');
        }
        localStorage.setItem(TOKEN_STORAGE_KEY, res.access_token);
        localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(res.user));
        return res.user;
      }
      throw new Error('Authentication failed: Missing token in response.');
    } catch (e) {
      console.error('Backend loginWithCredentials failed:', e);
      throw e;
    }
  },

  logout: () => {
    localStorage.removeItem(AUTH_STORAGE_KEY);
    localStorage.removeItem(TOKEN_STORAGE_KEY);
  },
};
