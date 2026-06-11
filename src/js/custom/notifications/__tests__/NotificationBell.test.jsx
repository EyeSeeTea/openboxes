/* eslint-env jest */
import React from 'react';

import { fireEvent, render, screen } from '@testing-library/react';
import useNotifications from 'custom/notifications/hooks/useNotifications';

import '@testing-library/jest-dom';

// Mock the hook so the component renders without Redux/API setup
jest.mock('custom/notifications/hooks/useNotifications');
// Stub the dropdown so Bell tests stay focused on bell behaviour
jest.mock('custom/notifications/components/NotificationDropdown', () => {
  const MockDropdown = () => <div data-testid="notification-dropdown" />;
  return MockDropdown;
});

// eslint-disable-next-line import/first
import NotificationBell from 'custom/notifications/components/NotificationBell';

const DEFAULT_HOOK = {
  notifications: [],
  unreadCount: 0,
  loading: false,
  error: null,
  markRead: jest.fn(),
  markAllRead: jest.fn(),
};

describe('NotificationBell', () => {
  beforeEach(() => {
    useNotifications.mockReturnValue(DEFAULT_HOOK);
  });

  describe('badge visibility', () => {
    it('does not render the badge when unreadCount is 0', () => {
      render(<NotificationBell />);
      expect(screen.queryByText(/\d/)).not.toBeInTheDocument();
    });

    it('renders the badge with the unread count', () => {
      useNotifications.mockReturnValue({ ...DEFAULT_HOOK, unreadCount: 5 });
      render(<NotificationBell />);
      expect(screen.getByText('5')).toBeInTheDocument();
    });

    it('caps the badge at 99+ when unreadCount exceeds 99', () => {
      useNotifications.mockReturnValue({ ...DEFAULT_HOOK, unreadCount: 120 });
      render(<NotificationBell />);
      expect(screen.getByText('99+')).toBeInTheDocument();
    });
  });

  describe('button accessibility', () => {
    it('labels the button with unread count when there are unread notifications', () => {
      useNotifications.mockReturnValue({ ...DEFAULT_HOOK, unreadCount: 3 });
      render(<NotificationBell />);
      expect(screen.getByRole('button', { name: 'Notifications, 3 unread' })).toBeInTheDocument();
    });

    it('labels the button without count when nothing is unread', () => {
      render(<NotificationBell />);
      expect(screen.getByRole('button', { name: 'Notifications' })).toBeInTheDocument();
    });
  });

  describe('dropdown toggle', () => {
    it('does not render the dropdown initially', () => {
      render(<NotificationBell />);
      expect(screen.queryByTestId('notification-dropdown')).not.toBeInTheDocument();
    });

    it('shows the dropdown after the bell button is clicked', async () => {
      render(<NotificationBell />);
      fireEvent.click(screen.getByRole('button', { name: 'Notifications' }));
      expect(screen.getByTestId('notification-dropdown')).toBeInTheDocument();
    });

    it('closes the dropdown when the bell button is clicked a second time', async () => {
      render(<NotificationBell />);
      const button = screen.getByRole('button', { name: 'Notifications' });
      fireEvent.click(button);
      fireEvent.click(button);
      expect(screen.queryByTestId('notification-dropdown')).not.toBeInTheDocument();
    });

    it('closes the dropdown on outside click', async () => {
      render(
        <div>
          <NotificationBell />
          <button type="button">Outside</button>
        </div>,
      );
      fireEvent.click(screen.getByRole('button', { name: 'Notifications' }));
      expect(screen.getByTestId('notification-dropdown')).toBeInTheDocument();

      fireEvent.mouseDown(screen.getByRole('button', { name: 'Outside' }));
      expect(screen.queryByTestId('notification-dropdown')).not.toBeInTheDocument();
    });
  });
});
