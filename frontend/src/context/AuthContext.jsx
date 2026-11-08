import React, { createContext, useContext, useState, useEffect } from 'react';
import { authApi } from '../api/client';

const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(() => {
    const saved = localStorage.getItem('ledgerline_user');
    return saved ? JSON.parse(saved) : null;
  });
  const [token, setToken] = useState(() => localStorage.getItem('ledgerline_token'));
  const [loading, setLoading] = useState(false);

  const login = async (username, password) => {
    setLoading(true);
    try {
      const res = await authApi.login({ username, password });
      const data = res.data.data;
      setUser(data);
      setToken(data.token);
      localStorage.setItem('ledgerline_token', data.token);
      localStorage.setItem('ledgerline_user', JSON.stringify(data));
      return { success: true };
    } catch (err) {
      const message = err.response?.data?.message 
        || (err.code === 'ERR_NETWORK' || !err.response 
            ? 'Cannot connect to backend server. Please make sure the Spring Boot backend is running on port 8080.' 
            : 'Authentication failed');
      return { success: false, message };
    } finally {
      setLoading(false);
    }
  };

  const register = async (userData) => {
    setLoading(true);
    try {
      const res = await authApi.register(userData);
      return { success: true, data: res.data.data };
    } catch (err) {
      const message = err.response?.data?.message 
        || (err.code === 'ERR_NETWORK' || !err.response 
            ? 'Cannot connect to backend server. Please make sure the Spring Boot backend is running on port 8080.' 
            : 'Registration failed');
      const validation = err.response?.data?.validationErrors;
      return { success: false, message, validation };
    } finally {
      setLoading(false);
    }
  };

  const logout = () => {
    setUser(null);
    setToken(null);
    localStorage.removeItem('ledgerline_token');
    localStorage.removeItem('ledgerline_user');
  };

  const hasRole = (role) => {
    return user?.roles?.includes(role) || false;
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        loading,
        login,
        register,
        logout,
        hasRole,
        isAuthenticated: !!token,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);
