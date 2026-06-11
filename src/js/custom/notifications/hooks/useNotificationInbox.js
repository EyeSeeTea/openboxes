import {
  useCallback, useEffect, useRef, useState,
} from 'react';

import {
  getNotifications,
  markAllRead as apiMarkAllRead,
  markRead as apiMarkRead,
  markUnread as apiMarkUnread,
} from 'custom/notifications/api/notificationsApi';
import { extractItems, reportError } from 'custom/notifications/utils/fetchHelpers';

export const PAGE_SIZES = [5, 10, 20, 25, 50, 100];
const DEFAULT_PAGE_SIZE = 20;

const useNotificationInbox = ({
  type, since, before, read,
} = {}) => {
  const [notifications, setNotifications] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [selectedId, setSelectedId] = useState(null);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSizeState] = useState(DEFAULT_PAGE_SIZE);
  const [totalCount, setTotalCount] = useState(0);
  const [unreadCount, setUnreadCount] = useState(0);
  const mountedRef = useRef(true);

  // Reset to the first page when the filters change. Done during render (not in an
  // effect) so `page` is already 0 when the fetch effect runs — otherwise a filter
  // change on page > 0 would fire two fetches (filter change, then the page reset).
  const filterKey = `${type}|${since}|${before}|${read}`;
  const [prevFilterKey, setPrevFilterKey] = useState(filterKey);
  if (prevFilterKey !== filterKey) {
    setPrevFilterKey(filterKey);
    setPage(0);
  }

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  const fetchPage = useCallback(async () => {
    setLoading(true);
    try {
      const { data } = await getNotifications({
        unreadOnly: false,
        limit: pageSize,
        offset: page * pageSize,
        type,
        since,
        before,
        read,
      });
      if (!mountedRef.current) return;
      const items = extractItems(data);
      setNotifications(items);
      setTotalCount(data?.totalCount ?? items.length);
      setUnreadCount(data?.unreadCount ?? 0);
      setError(null);
    } catch (err) {
      if (!mountedRef.current) return;
      setError(err);
      reportError(err, 'inbox_fetch');
    } finally {
      if (mountedRef.current) setLoading(false);
    }
  }, [type, since, before, read, page, pageSize]);

  useEffect(() => {
    fetchPage();
  }, [fetchPage]);

  const setPageSize = useCallback((newSize) => {
    setPageSizeState(newSize);
    setPage(0);
  }, []);

  const selectNotification = useCallback(async (id) => {
    setSelectedId(id);
    const target = notifications.find((n) => n.id === id);
    if (!target || target.read) return;
    // Optimistic update — badge reconciles on next 30s poll
    setNotifications((prev) => prev.map((n) => (n.id === id ? { ...n, read: true } : n)));
    try {
      await apiMarkRead(id);
    } catch (err) {
      if (!mountedRef.current) return;
      setNotifications((prev) => prev.map((n) => (n.id === id ? { ...n, read: false } : n)));
      setError(err);
      reportError(err, 'inbox_markRead');
    }
  }, [notifications]);

  const markUnread = useCallback(async (id) => {
    const target = notifications.find((n) => n.id === id);
    if (!target || !target.read) return;
    setNotifications((prev) => prev.map((n) => (n.id === id ? { ...n, read: false } : n)));
    try {
      await apiMarkUnread(id);
    } catch (err) {
      if (!mountedRef.current) return;
      setNotifications((prev) => prev.map((n) => (n.id === id ? { ...n, read: true } : n)));
      setError(err);
      reportError(err, 'inbox_markUnread');
    }
  }, [notifications]);

  const markAllRead = useCallback(async () => {
    try {
      await apiMarkAllRead();
      if (!mountedRef.current) return;
      fetchPage();
    } catch (err) {
      if (!mountedRef.current) return;
      reportError(err, 'inbox_markAllRead');
    }
  }, [fetchPage]);

  const selectedNotification = notifications.find((n) => n.id === selectedId) || null;
  const totalPages = Math.max(1, Math.ceil(totalCount / pageSize));

  return {
    notifications,
    loading,
    error,
    selectedId,
    selectedNotification,
    selectNotification,
    markUnread,
    markAllRead,
    unreadCount,
    page,
    pageSize,
    totalCount,
    totalPages,
    canNext: page < totalPages - 1,
    canPrevious: page > 0,
    setPage,
    setPageSize,
    pageSizeOptions: PAGE_SIZES,
    refresh: fetchPage,
  };
};

export default useNotificationInbox;
