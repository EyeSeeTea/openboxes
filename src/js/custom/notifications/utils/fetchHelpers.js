import * as Sentry from '@sentry/react';

// Shared by the bell (useNotifications) and inbox (useNotificationInbox) hooks.
export const reportError = (err, tag) => {
  // 401 is expected when the session has expired; the apiClient response
  // interceptor handles the redirect — don't double-report.
  if (err?.response?.status === 401) return;
  Sentry.captureException(err, { tags: { custom_notifications: tag } });
};

// The list endpoint returns `{ data: [...] }`; tolerate a bare array too.
export const extractItems = (data) => (Array.isArray(data) ? data : (data?.data || []));
