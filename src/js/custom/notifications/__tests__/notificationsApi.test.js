/* eslint-env jest */
import {
  getNotifications,
  markAllRead,
  markRead,
  markUnread,
} from 'custom/notifications/api/notificationsApi';

import apiClient from 'utils/apiClient';

jest.mock('utils/apiClient', () => ({
  get: jest.fn(),
  put: jest.fn(),
}));

const BASE_URL = '/openboxes/api/custom/notifications';
const AJAX_HEADERS = { headers: { 'X-Requested-With': 'XMLHttpRequest' } };

describe('notificationsApi', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('getNotifications', () => {
    it('calls GET with default params and the XHR header', () => {
      apiClient.get.mockResolvedValueOnce({ data: { data: [] } });
      getNotifications();
      expect(apiClient.get).toHaveBeenCalledWith(BASE_URL, {
        ...AJAX_HEADERS,
        params: { unreadOnly: false, limit: 20, offset: 0 },
      });
    });

    it('passes custom params when provided', () => {
      apiClient.get.mockResolvedValueOnce({ data: { data: [] } });
      getNotifications({ unreadOnly: true, limit: 5 });
      expect(apiClient.get).toHaveBeenCalledWith(BASE_URL, {
        ...AJAX_HEADERS,
        params: { unreadOnly: true, limit: 5, offset: 0 },
      });
    });

    it('forwards a non-zero offset', () => {
      apiClient.get.mockResolvedValueOnce({ data: { data: [] } });
      getNotifications({ offset: 20 });
      expect(apiClient.get).toHaveBeenCalledWith(BASE_URL, {
        ...AJAX_HEADERS,
        params: { unreadOnly: false, limit: 20, offset: 20 },
      });
    });
  });

  describe('markRead', () => {
    it('calls PUT with the notification id in the path and the XHR header', () => {
      apiClient.put.mockResolvedValueOnce({});
      markRead('notif-42');
      expect(apiClient.put).toHaveBeenCalledWith(`${BASE_URL}/notif-42/read`, null, AJAX_HEADERS);
    });
  });

  describe('markAllRead', () => {
    it('calls PUT on the read-all endpoint with the XHR header', () => {
      apiClient.put.mockResolvedValueOnce({});
      markAllRead();
      expect(apiClient.put).toHaveBeenCalledWith(`${BASE_URL}/read-all`, null, AJAX_HEADERS);
    });
  });

  describe('getNotifications with optional type/since/before/read params', () => {
    it('omits type, since, before, read when not provided', () => {
      apiClient.get.mockResolvedValueOnce({ data: { data: [] } });
      getNotifications();
      expect(apiClient.get).toHaveBeenCalledWith(BASE_URL, {
        ...AJAX_HEADERS,
        params: { unreadOnly: false, limit: 20, offset: 0 },
      });
    });

    it('includes type when provided', () => {
      apiClient.get.mockResolvedValueOnce({ data: { data: [] } });
      getNotifications({ type: 'SHIPMENT' });
      expect(apiClient.get).toHaveBeenCalledWith(BASE_URL, {
        ...AJAX_HEADERS,
        params: {
          unreadOnly: false, limit: 20, offset: 0, type: 'SHIPMENT',
        },
      });
    });

    it('includes read when provided', () => {
      apiClient.get.mockResolvedValueOnce({ data: { data: [] } });
      getNotifications({ read: false });
      expect(apiClient.get).toHaveBeenCalledWith(BASE_URL, {
        ...AJAX_HEADERS,
        params: {
          unreadOnly: false, limit: 20, offset: 0, read: false,
        },
      });
    });

    it('includes since and before when both are provided', () => {
      apiClient.get.mockResolvedValueOnce({ data: { data: [] } });
      getNotifications({ since: '2026-01-01T00:00:00Z', before: '2026-06-01T00:00:00Z' });
      expect(apiClient.get).toHaveBeenCalledWith(BASE_URL, {
        ...AJAX_HEADERS,
        params: {
          unreadOnly: false,
          limit: 20,
          offset: 0,
          since: '2026-01-01T00:00:00Z',
          before: '2026-06-01T00:00:00Z',
        },
      });
    });

    it('includes all when type + since + before + read are provided', () => {
      apiClient.get.mockResolvedValueOnce({ data: { data: [] } });
      getNotifications({
        type: 'SYSTEM', since: '2026-01-01T00:00:00Z', before: '2026-06-01T00:00:00Z', read: true,
      });
      expect(apiClient.get).toHaveBeenCalledWith(BASE_URL, {
        ...AJAX_HEADERS,
        params: {
          unreadOnly: false,
          limit: 20,
          offset: 0,
          type: 'SYSTEM',
          since: '2026-01-01T00:00:00Z',
          before: '2026-06-01T00:00:00Z',
          read: true,
        },
      });
    });
  });

  describe('markUnread', () => {
    it('calls PUT on the unread endpoint with the notification id and the XHR header', () => {
      apiClient.put.mockResolvedValueOnce({});
      markUnread('notif-99');
      expect(apiClient.put).toHaveBeenCalledWith(`${BASE_URL}/notif-99/unread`, null, AJAX_HEADERS);
    });
  });
});
