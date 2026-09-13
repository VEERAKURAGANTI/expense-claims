import client, { apiErrorMessage } from './client';

export { apiErrorMessage };


export const authApi = {
  login: (email, password) => client.post('/api/auth/login', { email, password }),
  demoUsers: () => client.get('/api/auth/demo-users'),
  me: () => client.get('/api/auth/me'),
};

export const claimsApi = {
  dashboard: () => client.get('/api/claims'),
  detail: (id) => client.get(`/api/claims/${id}`),
  attachmentUrl: (id) => `${client.defaults.baseURL}/api/claims/${id}/attachment`,
  createDraft: (formData) =>
    client.post('/api/claims/draft', formData, { headers: { 'Content-Type': 'multipart/form-data' } }),
  edit: (id, body) => client.put(`/api/claims/${id}`, body),
  discard: (id) => client.delete(`/api/claims/${id}`),
  submit: (id, confirmNotDuplicate) => client.post(`/api/claims/${id}/submit`, { confirmNotDuplicate }),
};

export const managerApi = {
  queue: () => client.get('/api/manager/queue'),
  approve: (id) => client.post(`/api/manager/claims/${id}/approve`),
  reject: (id, note) => client.post(`/api/manager/claims/${id}/reject`, { note }),
};

export const financeApi = {
  queue: () => client.get('/api/finance/queue'),
  pay: (id, overrideNote) => client.post(`/api/finance/claims/${id}/pay`, { overrideNote }),
  report: (month) => client.get('/api/finance/report', { params: month ? { month } : {} }),
};

export const CATEGORIES = ['TRAVEL', 'MEALS', 'TAXI', 'ACCOMMODATION', 'SUPPLIES', 'OTHER'];
