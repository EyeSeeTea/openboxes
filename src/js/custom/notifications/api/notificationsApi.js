import apiClient from 'utils/apiClient';

const BASE_URL = '/openboxes/api/custom/notifications';

export const getUnreadCount = () =>
  apiClient.get(`${BASE_URL}/unread-count`);

export const getNotifications = ({ unreadOnly = false, limit = 20, offset = 0 } = {}) =>
  apiClient.get(BASE_URL, { params: { unreadOnly, limit, offset } });

export const markRead = (id) =>
  apiClient.put(`${BASE_URL}/${id}/read`);

export const markAllRead = () =>
  apiClient.put(`${BASE_URL}/read-all`);
