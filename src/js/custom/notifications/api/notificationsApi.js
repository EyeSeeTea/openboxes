import apiClient from 'utils/apiClient';

const BASE_URL = '/openboxes/api/custom/notifications';

// Marks these as XHR so Spring Security does NOT save them in its request cache as
// the post-login redirect target. Without it, a background poll firing against an
// expired session becomes the saved request and the user lands on this JSON endpoint
// after re-authenticating instead of the page they were on. (The 401 itself is
// handled by the shared apiClient interceptor; this header only affects the redirect.)
const AJAX_CONFIG = { headers: { 'X-Requested-With': 'XMLHttpRequest' } };

export const getUnreadCount = () =>
  apiClient.get(`${BASE_URL}/unread-count`, AJAX_CONFIG);

export const getNotifications = ({
  unreadOnly = false, limit = 20, offset = 0, type, since, before, read,
} = {}) => {
  const params = { unreadOnly, limit, offset };
  if (type != null) params.type = type;
  if (since != null) params.since = since;
  if (before != null) params.before = before;
  if (read != null) params.read = read;
  return apiClient.get(BASE_URL, { ...AJAX_CONFIG, params });
};

export const markRead = (id) =>
  apiClient.put(`${BASE_URL}/${id}/read`, null, AJAX_CONFIG);

export const markAllRead = () =>
  apiClient.put(`${BASE_URL}/read-all`, null, AJAX_CONFIG);

export const markUnread = (id) =>
  apiClient.put(`${BASE_URL}/${id}/unread`, null, AJAX_CONFIG);
