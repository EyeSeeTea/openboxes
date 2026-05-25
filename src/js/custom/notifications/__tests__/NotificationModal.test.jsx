/* eslint-env jest */
import React from 'react';

import { fireEvent, render, screen } from '@testing-library/react';
import NotificationModal from 'custom/notifications/components/NotificationModal';

import '@testing-library/jest-dom';

const NOTIFICATION_BASE = {
  id: 'n-1',
  title: 'Low stock alert',
  body: '<p>Stock is below threshold.</p>',
  createdAt: '2026-05-22T08:00:00Z',
  read: false,
  linkUrl: null,
};

const renderModal = (props = {}) => {
  const onClose = jest.fn();
  const onOpenLink = jest.fn();
  const utils = render(
    <NotificationModal
      notification={NOTIFICATION_BASE}
      onClose={onClose}
      onOpenLink={onOpenLink}
      {...props}
    />,
  );
  return { ...utils, onClose, onOpenLink };
};

describe('NotificationModal', () => {
  describe('null notification', () => {
    it('renders nothing when notification is null', () => {
      const { container } = render(
        <NotificationModal notification={null} onClose={jest.fn()} onOpenLink={jest.fn()} />,
      );
      expect(container.firstChild).toBeNull();
    });
  });

  describe('content rendering', () => {
    it('renders the notification title', () => {
      renderModal();
      expect(screen.getByText('Low stock alert')).toBeInTheDocument();
    });

    it('renders the HTML body inside a sandboxed iframe with links opening in new tabs', () => {
      renderModal();
      const frame = document.querySelector('.notification-modal__frame');
      expect(frame).toHaveAttribute('sandbox', 'allow-popups allow-popups-to-escape-sandbox');
      expect(frame).toHaveAttribute('srcdoc', '<base target="_blank"><p>Stock is below threshold.</p>');
    });

    it('renders createdAt value', () => {
      renderModal();
      expect(screen.getByText('2026-05-22T08:00:00Z')).toBeInTheDocument();
    });

    it('shows "No additional details" when body is null', () => {
      renderModal({ notification: { ...NOTIFICATION_BASE, body: null } });
      expect(screen.getByText('No additional details')).toBeInTheDocument();
    });

    it('shows "No additional details" when body is empty string', () => {
      renderModal({ notification: { ...NOTIFICATION_BASE, body: '' } });
      expect(screen.getByText('No additional details')).toBeInTheDocument();
    });
  });

  describe('close behavior', () => {
    it('calls onClose when the X close button is clicked', () => {
      const { onClose } = renderModal();
      fireEvent.click(screen.getByRole('button', { name: 'Close' }));
      expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('calls onClose when the backdrop is clicked', () => {
      const { onClose } = renderModal();
      const backdrop = document.querySelector('.notification-modal__backdrop');
      fireEvent.click(backdrop);
      expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('calls onClose when Escape key is pressed', () => {
      const { onClose } = renderModal();
      fireEvent.keyDown(document, { key: 'Escape' });
      expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('does not call onClose when a non-Escape key is pressed', () => {
      const { onClose } = renderModal();
      fireEvent.keyDown(document, { key: 'Enter' });
      expect(onClose).not.toHaveBeenCalled();
    });
  });

  describe('Open link button', () => {
    it('shows "Open link" button for same-origin linkUrl', () => {
      renderModal({ notification: { ...NOTIFICATION_BASE, linkUrl: '/openboxes/shipment/123' } });
      expect(screen.getByRole('button', { name: 'Open link' })).toBeInTheDocument();
    });

    it('calls onOpenLink with linkUrl when "Open link" is clicked', () => {
      const { onOpenLink } = renderModal({
        notification: { ...NOTIFICATION_BASE, linkUrl: '/openboxes/shipment/123' },
      });
      fireEvent.click(screen.getByRole('button', { name: 'Open link' }));
      expect(onOpenLink).toHaveBeenCalledWith('/openboxes/shipment/123');
    });

    it('does not show "Open link" when linkUrl is null', () => {
      renderModal({ notification: { ...NOTIFICATION_BASE, linkUrl: null } });
      expect(screen.queryByRole('button', { name: 'Open link' })).not.toBeInTheDocument();
    });

    it('does not show "Open link" when linkUrl is external', () => {
      renderModal({ notification: { ...NOTIFICATION_BASE, linkUrl: 'https://evil.example/x' } });
      expect(screen.queryByRole('button', { name: 'Open link' })).not.toBeInTheDocument();
    });

    it('does not show "Open link" when linkUrl starts with //', () => {
      renderModal({ notification: { ...NOTIFICATION_BASE, linkUrl: '//evil.example/x' } });
      expect(screen.queryByRole('button', { name: 'Open link' })).not.toBeInTheDocument();
    });
  });
});
