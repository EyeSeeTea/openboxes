/* eslint-env jest */
import React from 'react';

import { fireEvent, render, screen } from '@testing-library/react';
import NotificationDropdown from 'custom/notifications/components/NotificationDropdown';

import '@testing-library/jest-dom';

jest.mock('date-fns', () => ({
  formatDistanceToNow: () => '2 hours ago',
}));
jest.mock('react-redux', () => ({
  useSelector: () => (id, defaultMessage) => defaultMessage || id,
}));
jest.mock('utils/Translate', () => {
  // eslint-disable-next-line global-require, no-shadow
  const React = require('react');
  const Translate = ({ defaultMessage }) => React.createElement('span', null, defaultMessage);
  Translate.translateWithDefaultMessage = (translate) => translate;
  return Translate;
});

const NOTIFICATION_UNREAD = {
  id: 'n-1',
  title: 'Low stock alert',
  createdAt: '2026-05-22T08:00:00Z',
  read: false,
};
const NOTIFICATION_READ = {
  id: 'n-2',
  title: 'Shipment arrived',
  createdAt: '2026-05-21T08:00:00Z',
  read: true,
};

const renderDropdown = (overrides = {}) => {
  const onMarkRead = jest.fn();
  const onMarkAllRead = jest.fn();
  const onTabChange = jest.fn();
  const onLoadMore = jest.fn();
  const utils = render(
    <NotificationDropdown
      notifications={[]}
      unreadCount={0}
      activeTab="unread"
      onTabChange={onTabChange}
      onMarkRead={onMarkRead}
      onMarkAllRead={onMarkAllRead}
      onLoadMore={onLoadMore}
      hasMore={false}
      loadingMore={false}
      {...overrides}
    />,
  );
  return {
    ...utils, onMarkRead, onMarkAllRead, onTabChange, onLoadMore,
  };
};

describe('NotificationDropdown', () => {
  describe('empty state', () => {
    it('shows the unread-empty message on the Unread tab', () => {
      renderDropdown();
      expect(screen.getByText('No unread notifications')).toBeInTheDocument();
    });

    it('shows the all-empty message on the All tab', () => {
      renderDropdown({ activeTab: 'all' });
      expect(screen.getByText('No notifications yet')).toBeInTheDocument();
    });

    it('does not show the mark-all button when unreadCount is 0', () => {
      renderDropdown();
      expect(screen.queryByRole('button', { name: 'Mark all as read' })).not.toBeInTheDocument();
    });
  });

  describe('notification list', () => {
    it('renders each notification title and timestamp', () => {
      renderDropdown({
        notifications: [NOTIFICATION_UNREAD, NOTIFICATION_READ],
        unreadCount: 1,
      });
      expect(screen.getByText('Low stock alert')).toBeInTheDocument();
      expect(screen.getByText('Shipment arrived')).toBeInTheDocument();
      expect(screen.getAllByText('2 hours ago')).toHaveLength(2);
    });

    it('renders unread dot only for unread notifications', () => {
      renderDropdown({
        notifications: [NOTIFICATION_UNREAD, NOTIFICATION_READ],
        unreadCount: 1,
      });
      expect(screen.getAllByLabelText('Unread')).toHaveLength(1);
    });
  });

  describe('mark all as read', () => {
    it('shows mark-all button when unreadCount > 0', () => {
      renderDropdown({ notifications: [NOTIFICATION_UNREAD], unreadCount: 1 });
      expect(screen.getByRole('button', { name: 'Mark all as read' })).toBeInTheDocument();
    });

    it('calls onMarkAllRead when mark-all button is clicked', () => {
      const { onMarkAllRead } = renderDropdown({
        notifications: [NOTIFICATION_UNREAD],
        unreadCount: 1,
      });
      fireEvent.click(screen.getByRole('button', { name: 'Mark all as read' }));
      expect(onMarkAllRead).toHaveBeenCalledTimes(1);
    });
  });

  describe('mark single notification as read', () => {
    it('calls onMarkRead with the notification id when an unread row is clicked', () => {
      const { onMarkRead } = renderDropdown({
        notifications: [NOTIFICATION_UNREAD],
        unreadCount: 1,
      });
      fireEvent.click(screen.getByText('Low stock alert'));
      expect(onMarkRead).toHaveBeenCalledWith('n-1');
    });

    it('does not call onMarkRead when an already-read row is clicked', () => {
      const { onMarkRead } = renderDropdown({
        notifications: [NOTIFICATION_READ],
        unreadCount: 0,
      });
      fireEvent.click(screen.getByText('Shipment arrived'));
      expect(onMarkRead).not.toHaveBeenCalled();
    });
  });

  describe('modal opens on row click', () => {
    it('opens the modal with the notification title when a row is clicked', () => {
      renderDropdown({
        notifications: [NOTIFICATION_UNREAD],
        unreadCount: 1,
      });
      fireEvent.click(screen.getByText('Low stock alert'));
      expect(screen.getByRole('dialog', { name: 'Low stock alert' })).toBeInTheDocument();
    });

    it('opens the modal for a read notification without calling onMarkRead', () => {
      const { onMarkRead } = renderDropdown({
        notifications: [NOTIFICATION_READ],
        unreadCount: 0,
      });
      fireEvent.click(screen.getByText('Shipment arrived'));
      expect(screen.getByRole('dialog', { name: 'Shipment arrived' })).toBeInTheDocument();
      expect(onMarkRead).not.toHaveBeenCalled();
    });

    it('closes the modal when the close button is clicked', () => {
      renderDropdown({
        notifications: [NOTIFICATION_UNREAD],
        unreadCount: 1,
      });
      fireEvent.click(screen.getByText('Low stock alert'));
      expect(screen.getByRole('dialog', { name: 'Low stock alert' })).toBeInTheDocument();
      fireEvent.click(screen.getByRole('button', { name: 'Close' }));
      expect(screen.queryByRole('dialog', { name: 'Low stock alert' })).not.toBeInTheDocument();
    });
  });

  describe('tabs', () => {
    it('renders Unread and All tab buttons', () => {
      renderDropdown({ unreadCount: 3 });
      expect(screen.getByRole('tab', { name: /Unread/ })).toBeInTheDocument();
      expect(screen.getByRole('tab', { name: 'All' })).toBeInTheDocument();
    });

    it('marks the active tab via aria-selected', () => {
      renderDropdown({ activeTab: 'all', unreadCount: 0 });
      expect(screen.getByRole('tab', { name: 'All' })).toHaveAttribute('aria-selected', 'true');
      expect(screen.getByRole('tab', { name: 'Unread' })).toHaveAttribute('aria-selected', 'false');
    });

    it('calls onTabChange when a tab is clicked', () => {
      const { onTabChange } = renderDropdown({ unreadCount: 1 });
      fireEvent.click(screen.getByRole('tab', { name: 'All' }));
      expect(onTabChange).toHaveBeenCalledWith('all');
    });

    it('shows the unread count badge on the Unread tab', () => {
      renderDropdown({ unreadCount: 7 });
      expect(screen.getByRole('tab', { name: /Unread.*7/ })).toBeInTheDocument();
    });
  });
});
