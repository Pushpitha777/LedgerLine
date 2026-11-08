import axios from 'axios';

const api = axios.create({
  baseURL: '',
  headers: {
    'Content-Type': 'application/json',
  },
});

export const generateIdempotencyKey = () => {
  return 'idemp-' + crypto.randomUUID();
};

api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('ledgerline_token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && error.response.status === 401) {
      if (!window.location.pathname.includes('/login')) {
        localStorage.removeItem('ledgerline_token');
        localStorage.removeItem('ledgerline_user');
      }
    }
    return Promise.reject(error);
  }
);

export const authApi = {
  login: (data) => api.post('/api/auth/login', data),
  register: (data) => api.post('/api/auth/register', data),
  getMe: () => api.get('/api/auth/me'),
};

export const accountsApi = {
  list: () => api.get('/api/accounts'),
  getById: (id) => api.get(`/api/accounts/${id}`),
  create: (data) => api.post('/api/accounts', data),
  getStatement: (id, page = 0, size = 50) =>
    api.get(`/api/accounts/${id}/statement?page=${page}&size=${size}`),
};

export const transactionsApi = {
  deposit: (data, key) =>
    api.post('/api/transactions/deposit', data, {
      headers: { 'Idempotency-Key': key || generateIdempotencyKey() },
    }),
  withdraw: (data, key) =>
    api.post('/api/transactions/withdraw', data, {
      headers: { 'Idempotency-Key': key || generateIdempotencyKey() },
    }),
  transfer: (data, key) =>
    api.post('/api/transactions/transfer', data, {
      headers: { 'Idempotency-Key': key || generateIdempotencyKey() },
    }),
  list: (page = 0, size = 20) =>
    api.get(`/api/transactions?page=${page}&size=${size}`),
};

export const auditorApi = {
  getSummary: () => api.get('/api/auditor/ledger-summary'),
  getEntries: (page = 0, size = 50) =>
    api.get(`/api/auditor/ledger-entries?page=${page}&size=${size}`),
  getAuditLogs: (page = 0, size = 50) =>
    api.get(`/api/auditor/audit-logs?page=${page}&size=${size}`),
};

export const adminApi = {
  getUsers: (page = 0, size = 50) =>
    api.get(`/api/admin/users?page=${page}&size=${size}`),
  getAccounts: (page = 0, size = 50) =>
    api.get(`/api/admin/accounts?page=${page}&size=${size}`),
  updateStatus: (id, data) =>
    api.patch(`/api/admin/accounts/${id}/status`, data),
};

export default api;
