/* eslint-env jest */
import {
  getNotifications,
  markAllRead,
  markRead,
} from 'custom/notifications/api/notificationsApi';

import apiClient from 'utils/apiClient';

jest.mock('utils/apiClient', () => ({
  get: jest.fn(),
  put: jest.fn(),
}));

const BASE_URL = '/openboxes/api/custom/notifications';

describe('notificationsApi', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('getNotifications', () => {
    it('calls GET with default params', () => {
      apiClient.get.mockResolvedValueOnce({ data: { data: [] } });
      getNotifications();
      expect(apiClient.get).toHaveBeenCalledWith(BASE_URL, {
        params: { unreadOnly: false, limit: 20 },
      });
    });

    it('passes custom params when provided', () => {
      apiClient.get.mockResolvedValueOnce({ data: { data: [] } });
      getNotifications({ unreadOnly: true, limit: 5 });
      expect(apiClient.get).toHaveBeenCalledWith(BASE_URL, {
        params: { unreadOnly: true, limit: 5 },
      });
    });
  });

  describe('markRead', () => {
    it('calls PUT with the notification id in the path', () => {
      apiClient.put.mockResolvedValueOnce({});
      markRead('notif-42');
      expect(apiClient.put).toHaveBeenCalledWith(`${BASE_URL}/notif-42/read`);
    });
  });

  describe('markAllRead', () => {
    it('calls PUT on the read-all endpoint', () => {
      apiClient.put.mockResolvedValueOnce({});
      markAllRead();
      expect(apiClient.put).toHaveBeenCalledWith(`${BASE_URL}/read-all`);
    });
  });
});
