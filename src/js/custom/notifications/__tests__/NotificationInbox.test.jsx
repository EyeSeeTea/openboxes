/* eslint-env jest */
import React from 'react';

import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import {
  getNotifications, markAllRead, markRead, markUnread,
} from 'custom/notifications/api/notificationsApi';
import { toBeforeIso, toSinceIso } from 'custom/notifications/utils/dateFilters';

import '@testing-library/jest-dom';

jest.mock('@sentry/react', () => ({ captureException: jest.fn() }));
jest.mock('components/DataTable/TablePagination', () => {
  const React = require('react');
  return ({ totalData }) => React.createElement('div', { 'data-testid': 'table-pagination' }, String(totalData));
});
jest.mock('custom/notifications/api/notificationsApi', () => ({
  getNotifications: jest.fn(),
  markRead: jest.fn(),
  markUnread: jest.fn(),
  getUnreadCount: jest.fn(),
  markAllRead: jest.fn(),
}));
jest.mock('date-fns', () => ({
  ...jest.requireActual('date-fns'),
  formatDistanceToNow: () => '2 hours ago',
}));
// FilterForm and filter fields have deep dependency trees — stub them out
jest.mock('components/Filter/FilterForm', () => {
  const React = require('react');
  const FilterFormStub = ({ updateFilterParams }) => (
    <button
      type="button"
      data-testid="apply-filters"
      onClick={() => updateFilterParams({
        type: { value: 'SHIPMENT' },
        read: { value: 'UNREAD' },
        since: '01/15/2026',
        before: '06/20/2026',
      })}
    >
      Apply
    </button>
  );
  return FilterFormStub;
});
jest.mock('custom/notifications/components/NotificationInboxFilterFields', () => ({
  __esModule: true,
  default: () => ({}),
  FILTER_FIELD_KEYS: ['read', 'type', 'since', 'before'],
  buildEmptyFilterValues: () => ({
    read: '', type: '', since: '', before: '',
  }),
}));
jest.mock('custom/notifications/hooks/useNotificationInboxFilters', () => ({ setFilterParams }) => ({
  defaultValues: {},
  setFilterValues: (values) => setFilterParams(values),
}));
jest.mock('react-redux', () => ({
  useSelector: () => (id, defaultMessage) => defaultMessage || id,
}));
jest.mock('utils/Translate', () => {
  const React = require('react');
  const Translate = ({ defaultMessage }) => React.createElement('span', null, defaultMessage);
  Translate.translateWithDefaultMessage = (translate) => translate;
  return Translate;
});
jest.mock('react-router-dom', () => ({
  useHistory: () => ({
    location: { search: '', pathname: '/openboxes/notification/inbox' },
    push: jest.fn(),
  }),
}));

const UNREAD = {
  id: 'n-1', title: 'Unread notification', type: 'SHIPMENT', createdAt: '2026-05-01T10:00:00Z', read: false, body: 'Body text',
};
const READ = {
  id: 'n-2', title: 'Read notification', type: 'SYSTEM', createdAt: '2026-05-02T10:00:00Z', read: true, body: '',
};

const mockList = (items = []) => {
  getNotifications.mockResolvedValueOnce({
    data: { data: items, unreadCount: items.filter((n) => !n.read).length, totalCount: items.length },
  });
};

// NotificationInbox imports SCSS — silence the transform error in Jest
jest.mock('custom/notifications/styles/_inbox.scss', () => {}, { virtual: true });

// eslint-disable-next-line import/first
const NotificationInbox = require('custom/notifications/pages/NotificationInbox').default;

describe('NotificationInbox', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('renders the list of notifications and empty detail pane', async () => {
    mockList([UNREAD, READ]);
    render(<NotificationInbox />);

    await waitFor(() => {
      expect(screen.getByText('Unread notification')).toBeInTheDocument();
      expect(screen.getByText('Read notification')).toBeInTheDocument();
    });

    expect(screen.getByText('Select a notification to read it')).toBeInTheDocument();
  });

  it('shows empty state when there are no notifications', async () => {
    mockList([]);
    render(<NotificationInbox />);

    await waitFor(() => {
      expect(screen.getByText('No notifications found')).toBeInTheDocument();
    });
  });

  it('selecting an unread row calls markRead exactly once', async () => {
    mockList([UNREAD]);
    markRead.mockResolvedValueOnce({});
    render(<NotificationInbox />);

    await waitFor(() => screen.getByText('Unread notification'));
    fireEvent.click(screen.getByText('Unread notification'));

    expect(markRead).toHaveBeenCalledTimes(1);
    expect(markRead).toHaveBeenCalledWith('n-1');
  });

  it('selecting an already-read row does not call markRead', async () => {
    mockList([READ]);
    render(<NotificationInbox />);

    await waitFor(() => screen.getByText('Read notification'));
    fireEvent.click(screen.getByText('Read notification'));

    expect(markRead).not.toHaveBeenCalled();
  });

  it('mark-unread button in detail pane calls markUnread', async () => {
    const alreadyRead = { ...READ, read: true };
    mockList([alreadyRead]);
    markUnread.mockResolvedValueOnce({});
    render(<NotificationInbox />);

    await waitFor(() => screen.getByText('Read notification'));
    fireEvent.click(screen.getByText('Read notification'));

    const markUnreadBtn = await screen.findByRole('button', { name: 'Mark as unread' });
    fireEvent.click(markUnreadBtn);

    expect(markUnread).toHaveBeenCalledTimes(1);
    expect(markUnread).toHaveBeenCalledWith('n-2');
  });

  it('shows the mark-all-read button when there are unread notifications and calls markAllRead', async () => {
    mockList([UNREAD]);
    markAllRead.mockResolvedValueOnce({});
    mockList([]); // refetch after marking all read
    render(<NotificationInbox />);

    const btn = await screen.findByRole('button', { name: 'Mark all as read' });
    fireEvent.click(btn);

    expect(markAllRead).toHaveBeenCalledTimes(1);
  });

  it('hides the mark-all-read button when there are no unread notifications', async () => {
    mockList([READ]);
    render(<NotificationInbox />);

    await waitFor(() => screen.getByText('Read notification'));
    expect(screen.queryByRole('button', { name: 'Mark all as read' })).not.toBeInTheDocument();
  });

  it('applying combined filters sends type, read, and ISO-converted dates together', async () => {
    mockList([]);
    // After filter apply, a fresh fetch is triggered
    mockList([]);
    render(<NotificationInbox />);

    await waitFor(() => screen.getByTestId('apply-filters'));
    fireEvent.click(screen.getByTestId('apply-filters'));

    await waitFor(() => {
      const calls = getNotifications.mock.calls;
      const filterCall = calls.find((call) => call[0] && call[0].type === 'SHIPMENT');
      expect(filterCall).toBeDefined();
      expect(filterCall[0].read).toBe(false);
      expect(filterCall[0].since).toBe(toSinceIso('01/15/2026'));
      expect(filterCall[0].before).toBe(toBeforeIso('06/20/2026'));
    });
  });
});
