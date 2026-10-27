import React, { createContext, useContext, useState, useEffect } from 'react';
import { authService } from '../services/authService';
import { USER_PORTAL_ROLES } from '../constants/roles';

const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(() => authService.getCurrentUser());
  const [loading, setLoading] = useState(false);
  const [authError, setAuthError] = useState(null);

  // Invalidate any stale administrator session found in user portal state
  useEffect(() => {
    if (user && (user.role === 'Administrator' || user.role === 'Admin')) {
      authService.logout();
      setUser(null);
    }
  }, [user]);

  const login = async (email, role, password) => {
    setLoading(true);
    setAuthError(null);
    try {
      const authenticatedUser = await authService.loginWithCredentials(email, role, password);
      setUser(authenticatedUser);
      return authenticatedUser;
    } catch (err) {
      setAuthError(err.message || 'Authentication failed');
      throw err;
    } finally {
      setLoading(false);
    }
  };

  const switchRole = async (newRole) => {
    setLoading(true);
    setAuthError(null);
    try {
      const switchedUser = await authService.loginByRole(newRole);
      setUser(switchedUser);
      return switchedUser;
    } catch (err) {
      setAuthError(err.message || 'Failed to switch role');
      throw err;
    } finally {
      setLoading(false);
    }
  };

  const logout = () => {
    authService.logout();
    setUser(null);
    setAuthError(null);
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        role: user?.role || null,
        isAuthenticated: !!user,
        loading,
        authError,
        login,
        switchRole,
        logout,
        availableRoles: USER_PORTAL_ROLES,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
