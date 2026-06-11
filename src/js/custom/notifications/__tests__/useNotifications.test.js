/* eslint-env jest */
import { act, renderHook } from '@testing-library/react-hooks';
import {
  getNotifications,
  getUnreadCount,
  markAllRead as apiMarkAllRead,
  markRead as apiMarkRead,
} from 'custom/notifications/api/notificationsApi';
import useNotifications from 'custom/notifications/hooks/useNotifications';

jest.mock('@sentry/react', () => ({ captureException: jest.fn() }));
jest.mock('custom/notifications/api/notificationsApi');
jest.useFakeTimers();

const NOTIFICATION_A = {
  id: 'n-1', title: 'First', createdAt: '2026-05-01T10:00:00Z', read: false,
};
const NOTIFICATION_B = {
  id: 'n-2', title: 'Second', createdAt: '2026-05-02T10:00:00Z', read: true,
};

const PAGE = Array.from({ length: 20 }, (_, i) => ({
  id: `p-${i}`, title: `Item ${i}`, createdAt: '2026-05-03T10:00:00Z', read: false,
}));
const PAGE_2 = Array.from({ length: 5 }, (_, i) => ({
  id: `q-${i}`, title: `More ${i}`, createdAt: '2026-05-04T10:00:00Z', read: false,
}));

const mockCount = (count) => {
  getUnreadCount.mockResolvedValueOnce({ data: { unreadCount: count } });
};

const mockList = (items = [], unreadCount = items.filter((n) => !n.read).length) => {
  getNotifications.mockResolvedValueOnce({ data: { data: items, unreadCount } });
};

describe('useNotifications', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    getUnreadCount.mockResolvedValue({ data: { unreadCount: 0 } });
  });

  describe('badge count poll', () => {
    it('sets unreadCount from getUnreadCount on mount, list untouched', async () => {
      mockCount(3);

      const { result, waitForNextUpdate } = renderHook(() => useNotifications());
      await waitForNextUpdate();

      expect(getUnreadCount).toHaveBeenCalledTimes(1);
      expect(result.current.unreadCount).toBe(3);
      expect(result.current.notifications).toEqual([]);
      expect(getNotifications).not.toHaveBeenCalled();
    });

    it('re-fetches the count after 30 seconds', async () => {
      mockCount(1);
      mockCount(4);

      const { result, waitForNextUpdate } = renderHook(() => useNotifications());
      await waitForNextUpdate();

      expect(result.current.unreadCount).toBe(1);

      act(() => {
        jest.advanceTimersByTime(30000);
      });
      await waitForNextUpdate();

      expect(getUnreadCount).toHaveBeenCalledTimes(2);
      expect(result.current.unreadCount).toBe(4);
    });

    it('clears the interval on unmount', async () => {
      mockCount(0);

      const { unmount, waitForNextUpdate } = renderHook(() => useNotifications());
      await waitForNextUpdate();

      unmount();
      act(() => {
        jest.advanceTimersByTime(30000);
      });

      expect(getUnreadCount).toHaveBeenCalledTimes(1);
    });
  });

  describe('list fetch', () => {
    it('does not fetch the list while open is false', async () => {
      mockCount(2);

      const { result, waitForNextUpdate } = renderHook(() => useNotifications({ open: false }));
      await waitForNextUpdate();

      expect(getNotifications).not.toHaveBeenCalled();
      expect(result.current.notifications).toEqual([]);
    });

    it('fetches page 1 when open becomes true and sets hasMore', async () => {
      mockCount(2);
      mockList([NOTIFICATION_A, NOTIFICATION_B], 1);

      const { result, waitForNextUpdate, rerender } = renderHook(
        ({ open }) => useNotifications({ open, unreadOnly: false }),
        { initialProps: { open: false } },
      );
      await waitForNextUpdate();

      rerender({ open: true });
      await waitForNextUpdate();

      expect(getNotifications).toHaveBeenCalledWith({ unreadOnly: false, limit: 20, offset: 0 });
      expect(result.current.notifications).toEqual([NOTIFICATION_A, NOTIFICATION_B]);
      expect(result.current.unreadCount).toBe(1);
      expect(result.current.hasMore).toBe(false);
    });

    it('sets hasMore true when a full page is returned', async () => {
      mockCount(20);
      mockList(PAGE, 20);

      const { result, waitForNextUpdate } = renderHook(() => useNotifications({ open: true }));
      await waitForNextUpdate();

      expect(result.current.notifications).toEqual(PAGE);
      expect(result.current.hasMore).toBe(true);
    });

    it('resets the list when open returns to false', async () => {
      mockCount(1);
      mockList([NOTIFICATION_A], 1);

      const { result, waitForNextUpdate, rerender } = renderHook(
        ({ open }) => useNotifications({ open }),
        { initialProps: { open: true } },
      );
      await waitForNextUpdate();
      expect(result.current.notifications).toEqual([NOTIFICATION_A]);

      rerender({ open: false });

      expect(result.current.notifications).toEqual([]);
      expect(result.current.hasMore).toBe(false);
    });

    it('sets error when the list fetch fails', async () => {
      mockCount(0);
      const fetchError = new Error('network');
      getNotifications.mockRejectedValueOnce(fetchError);

      const { result, waitForNextUpdate } = renderHook(() => useNotifications({ open: true }));
      await waitForNextUpdate();

      expect(result.current.error).toBe(fetchError);
      expect(result.current.notifications).toEqual([]);
    });
  });

  describe('loadMore', () => {
    it('appends the next page and updates hasMore', async () => {
      mockCount(25);
      mockList(PAGE, 25);
      mockList(PAGE_2, 25);

      const { result, waitForNextUpdate } = renderHook(() => useNotifications({ open: true }));
      await waitForNextUpdate();

      expect(result.current.hasMore).toBe(true);

      await act(async () => {
        await result.current.loadMore();
      });

      expect(getNotifications).toHaveBeenLastCalledWith({
        unreadOnly: true, limit: 20, offset: 20,
      });
      expect(result.current.notifications).toEqual([...PAGE, ...PAGE_2]);
      expect(result.current.hasMore).toBe(false);
    });
  });

  describe('markRead', () => {
    it('optimistically marks read and decrements unreadCount without re-fetching', async () => {
      mockCount(1);
      mockList([NOTIFICATION_A, NOTIFICATION_B], 1);
      apiMarkRead.mockResolvedValueOnce({});

      const { result, waitForNextUpdate } = renderHook(() => useNotifications({ open: true }));
      await waitForNextUpdate();

      expect(result.current.unreadCount).toBe(1);

      await act(async () => {
        await result.current.markRead('n-1');
      });

      expect(result.current.unreadCount).toBe(0);
      expect(result.current.notifications.find((n) => n.id === 'n-1').read).toBe(true);
      // No second list fetch — only the initial page-1 call.
      expect(getNotifications).toHaveBeenCalledTimes(1);
    });

    it('reverts the optimistic change on error', async () => {
      mockCount(1);
      mockList([NOTIFICATION_A, NOTIFICATION_B], 1);
      apiMarkRead.mockRejectedValueOnce(new Error('boom'));

      const { result, waitForNextUpdate } = renderHook(() => useNotifications({ open: true }));
      await waitForNextUpdate();

      await act(async () => {
        await result.current.markRead('n-1');
      });

      expect(result.current.unreadCount).toBe(1);
      expect(result.current.notifications.find((n) => n.id === 'n-1').read).toBe(false);
    });
  });

  describe('markAllRead', () => {
    it('optimistically marks all read and sets unreadCount to 0', async () => {
      mockCount(1);
      mockList([NOTIFICATION_A, NOTIFICATION_B], 1);
      apiMarkAllRead.mockResolvedValueOnce({});

      const { result, waitForNextUpdate } = renderHook(() => useNotifications({ open: true }));
      await waitForNextUpdate();

      await act(async () => {
        await result.current.markAllRead();
      });

      expect(result.current.unreadCount).toBe(0);
      expect(result.current.notifications.every((n) => n.read)).toBe(true);
    });

    it('reverts on error', async () => {
      mockCount(1);
      mockList([NOTIFICATION_A, NOTIFICATION_B], 1);
      apiMarkAllRead.mockRejectedValueOnce(new Error('boom'));

      const { result, waitForNextUpdate } = renderHook(() => useNotifications({ open: true }));
      await waitForNextUpdate();

      await act(async () => {
        await result.current.markAllRead();
      });

      expect(result.current.unreadCount).toBe(1);
      expect(result.current.notifications).toEqual([NOTIFICATION_A, NOTIFICATION_B]);
    });
  });
});
