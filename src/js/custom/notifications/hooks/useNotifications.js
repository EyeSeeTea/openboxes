import {
  useCallback, useEffect, useRef, useState,
} from 'react';

import * as Sentry from '@sentry/react';
import {
  getNotifications,
  getUnreadCount,
  markAllRead as apiMarkAllRead,
  markRead as apiMarkRead,
} from 'custom/notifications/api/notificationsApi';

const reportError = (err, tag) => {
  // 401 is expected when the session has expired; the apiClient response
  // interceptor handles the redirect — don't double-report.
  if (err?.response?.status === 401) return;
  Sentry.captureException(err, { tags: { custom_notifications: tag } });
};

const POLL_INTERVAL_MS = 30000;
const PAGE_SIZE = 20;

const extractCount = (data) => (typeof data?.unreadCount === 'number' ? data.unreadCount : 0);

const extractItems = (data) => {
  const items = Array.isArray(data) ? data : (data?.data || []);
  return items;
};

const useNotifications = ({ unreadOnly = true, open = false, limit = PAGE_SIZE } = {}) => {
  const [notifications, setNotifications] = useState([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [hasMore, setHasMore] = useState(false);
  const [error, setError] = useState(null);
  const mountedRef = useRef(true);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  // Badge count poll — cheap hot path, runs always, independent of `open`.
  useEffect(() => {
    const fetchCount = async () => {
      try {
        const { data } = await getUnreadCount();
        if (!mountedRef.current) return;
        setUnreadCount(extractCount(data));
      } catch (err) {
        if (!mountedRef.current) return;
        reportError(err, 'unreadCount');
      }
    };

    fetchCount();
    const intervalId = setInterval(fetchCount, POLL_INTERVAL_MS);
    return () => clearInterval(intervalId);
  }, []);

  // List fetch — only when the dropdown is open. Replace page 1 on open;
  // reset to empty on close so a reopen is always fresh.
  useEffect(() => {
    if (!open) {
      setNotifications([]);
      setHasMore(false);
      return undefined;
    }

    let active = true;
    const fetchList = async () => {
      setLoading(true);
      try {
        const { data } = await getNotifications({ unreadOnly, limit, offset: 0 });
        if (!active || !mountedRef.current) return;
        const items = extractItems(data);
        setNotifications(items);
        setUnreadCount(extractCount(data));
        setHasMore(items.length === limit);
        setError(null);
      } catch (err) {
        if (!active || !mountedRef.current) return;
        setError(err);
        reportError(err, 'fetch');
      } finally {
        if (active && mountedRef.current) setLoading(false);
      }
    };

    fetchList();
    return () => {
      active = false;
    };
  }, [open, unreadOnly, limit]);

  const loadMore = useCallback(async () => {
    if (loadingMore || !hasMore) return;
    setLoadingMore(true);
    try {
      const { data } = await getNotifications({
        unreadOnly,
        limit,
        offset: notifications.length,
      });
      if (!mountedRef.current) return;
      const items = extractItems(data);
      setNotifications((prev) => [...prev, ...items]);
      setHasMore(items.length === limit);
      setError(null);
    } catch (err) {
      if (!mountedRef.current) return;
      setError(err);
      reportError(err, 'loadMore');
    } finally {
      if (mountedRef.current) setLoadingMore(false);
    }
  }, [loadingMore, hasMore, unreadOnly, limit, notifications.length]);

  const markRead = useCallback(async (id) => {
    const target = notifications.find((n) => n.id === id);
    const wasUnread = target && !target.read;
    setNotifications((prev) => prev.map((n) => (n.id === id ? { ...n, read: true } : n)));
    if (wasUnread) setUnreadCount((prev) => Math.max(0, prev - 1));
    try {
      await apiMarkRead(id);
    } catch (err) {
      if (!mountedRef.current) return;
      setNotifications((prev) => prev.map((n) => (n.id === id ? { ...n, read: target.read } : n)));
      if (wasUnread) setUnreadCount((prev) => prev + 1);
      setError(err);
      reportError(err, 'markRead');
    }
  }, [notifications]);

  const markAllRead = useCallback(async () => {
    const prevNotifications = notifications;
    const prevUnreadCount = unreadCount;
    setNotifications((prev) => prev.map((n) => ({ ...n, read: true })));
    setUnreadCount(0);
    try {
      await apiMarkAllRead();
    } catch (err) {
      if (!mountedRef.current) return;
      setNotifications(prevNotifications);
      setUnreadCount(prevUnreadCount);
      setError(err);
      reportError(err, 'markAllRead');
    }
  }, [notifications, unreadCount]);

  return {
    notifications,
    unreadCount,
    loading,
    loadingMore,
    hasMore,
    error,
    markRead,
    markAllRead,
    loadMore,
  };
};

export default useNotifications;
