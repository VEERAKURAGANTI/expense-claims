export const claimsApi = {
  dashboard: () => client.get('/api/claims'),

  detail: (id) => client.get(`/api/claims/${id}`),

  // Direct URL — do NOT use this for protected browser navigation
  attachmentUrl: (id) =>
    `${client.defaults.baseURL}/api/claims/${id}/attachment`,

  // Authenticated request for protected receipt images
  attachment: (id) =>
    client.get(`/api/claims/${id}/attachment`, {
      responseType: 'blob',
    }),

  createDraft: (formData) =>
    client.post('/api/claims/draft', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    }),

  edit: (id, body) => client.put(`/api/claims/${id}`, body),

  discard: (id) => client.delete(`/api/claims/${id}`),

  submit: (id, confirmNotDuplicate) =>
    client.post(`/api/claims/${id}/submit`, { confirmNotDuplicate }),
};