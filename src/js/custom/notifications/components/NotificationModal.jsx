import React, { useEffect } from 'react';

import PropTypes from 'prop-types';

import Translate from 'utils/Translate';

const isSameOriginPath = (url) => typeof url === 'string' && url.startsWith('/') && !url.startsWith('//');

const NotificationModal = ({
  notification,
  onClose,
  onOpenLink,
}) => {
  useEffect(() => {
    if (!notification) return undefined;

    const handleKeyDown = (e) => {
      if (e.key === 'Escape') {
        onClose();
      }
    };

    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [notification, onClose]);

  if (!notification) {
    return null;
  }

  const hasBody = Boolean(notification.body && notification.body.trim());

  const handleBackdropClick = (e) => {
    if (e.target === e.currentTarget) {
      onClose();
    }
  };

  const handleOpenLink = () => {
    onOpenLink(notification.linkUrl);
    onClose();
  };

  return (
    <div
      className="notification-modal"
      role="dialog"
      aria-modal="true"
      aria-labelledby="notification-modal-title"
    >
      <div
        className="notification-modal__backdrop"
        onClick={handleBackdropClick}
        role="presentation"
      />
      <div className="notification-modal__dialog">
        <div className="notification-modal__header">
          <h5 id="notification-modal-title" className="notification-modal__title">
            {notification.title}
          </h5>
          <button
            type="button"
            className="notification-modal__close"
            onClick={onClose}
            aria-label="Close"
          >
            <Translate id="notifications.modal.close" defaultMessage="Close" />
          </button>
        </div>
        <div className="notification-modal__meta">
          {notification.createdAt}
        </div>
        <div className="notification-modal__body">
          {hasBody ? (
            // Reason: body is an email body (untrusted HTML). Sandboxed iframe isolates
            // email CSS from the app and blocks scripts. allow-popups lets user-clicked
            // links escape the frame; base target="_blank" forces them into a real browser
            // tab (without it, links navigate inside the sessionless frame → login redirect).
            <iframe
              className="notification-modal__frame"
              title={notification.title}
              sandbox="allow-popups allow-popups-to-escape-sandbox"
              srcDoc={`<base target="_blank">${notification.body}`}
            />
          ) : (
            <p className="notification-modal__no-body">
              <Translate id="notifications.modal.noBody" defaultMessage="No additional details" />
            </p>
          )}
        </div>
        {isSameOriginPath(notification.linkUrl) && (
          <div className="notification-modal__footer">
            <button
              type="button"
              className="notification-modal__open-link"
              onClick={handleOpenLink}
            >
              <Translate id="notifications.modal.openLink" defaultMessage="Open link" />
            </button>
          </div>
        )}
      </div>
    </div>
  );
};

NotificationModal.propTypes = {
  notification: PropTypes.shape({
    id: PropTypes.string.isRequired,
    title: PropTypes.string.isRequired,
    body: PropTypes.string,
    createdAt: PropTypes.string.isRequired,
    read: PropTypes.bool.isRequired,
    linkUrl: PropTypes.string,
  }),
  onClose: PropTypes.func.isRequired,
  onOpenLink: PropTypes.func.isRequired,
};

NotificationModal.defaultProps = {
  notification: null,
};

export default NotificationModal;
